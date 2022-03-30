package me.ryanhamshire.GriefPrevention.listeners;

import be.garagepoort.mcioc.IocListener;
import me.ryanhamshire.GriefPrevention.ClaimsMode;
import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.IgnoreLoaderThread;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.PlayerKickBanTask;
import me.ryanhamshire.GriefPrevention.PvpProtectionService;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.WelcomeTask;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.sessions.LogoutMessagesService;
import me.ryanhamshire.GriefPrevention.sessions.NotificationService;
import me.ryanhamshire.GriefPrevention.sessions.SessionManager;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;

@IocListener
public class OnPlayerJoin implements Listener {

    private final PlayerDataRepository playerDataRepository;
    private final SessionManager sessionManager;
    private final ClaimService claimService;
    private final PvpProtectionService pvpProtectionService;
    private final NotificationService notificationService;
    private final LogoutMessagesService logoutMessagesService;
    private final BukkitUtils bukkitUtils;

    public OnPlayerJoin(PlayerDataRepository playerDataRepository, SessionManager sessionManager, ClaimService claimService, PvpProtectionService pvpProtectionService, NotificationService notificationService, LogoutMessagesService logoutMessagesService, BukkitUtils bukkitUtils) {
        this.playerDataRepository = playerDataRepository;
        this.sessionManager = sessionManager;
        this.claimService = claimService;
        this.pvpProtectionService = pvpProtectionService;
        this.notificationService = notificationService;
        this.logoutMessagesService = logoutMessagesService;
        this.bukkitUtils = bukkitUtils;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerID = player.getUniqueId();

        if (event.getJoinMessage() != null && notificationService.shouldSilenceNotification()) {
            event.setJoinMessage(null);
        }

        PlayerData playerData = this.playerDataRepository.getPlayerData(playerID);
        bukkitUtils.runTaskAsync(event.getPlayer(), () -> {
            playerData.lastSpawn = new Date().getTime();
            this.sessionManager.addSession(playerID);
            if (isNewToServer(player)) {
                playerData.noChatLocation = player.getLocation();
            }
            //if player has never played on the server before...

            if (!player.hasPlayedBefore()) {
                //may need pvp protection
                pvpProtectionService.checkPvpProtectionNeeded(player, GriefPrevention.isInventoryEmpty(player));

                //if in survival claims mode, send a message about the claim basics video (except for admins - assumed experts)
                if (ConfigLoader.config_claims_worldModes.get(player.getWorld()) == ClaimsMode.Survival && !player.hasPermission("griefprevention.adminclaims") && this.claimService.getClaims().size() > 10) {
                    WelcomeTask task = new WelcomeTask(player);
                    Bukkit.getScheduler().scheduleSyncDelayedTask(GriefPrevention.get(), task, ConfigLoader.config_claims_manualDeliveryDelaySeconds * 20L);
                }
            }
            //in case player has changed his name, on successful login, update UUID > Name mapping
            GriefPrevention.cacheUUIDNamePair(player.getUniqueId(), player.getName());

            //create a thread to load ignore information
            new IgnoreLoaderThread(playerID, playerData.ignoredPlayers).start();
        });

        //ensure we're not over the limit for this IP address
        InetAddress ipAddress = playerData.ipAddress;
        if (ipAddress != null) {
            int ipLimit = ConfigLoader.config_ipLimit;
            if (ipLimit > 0 && isNewToServer(player)) {
                int ipCount = 0;

                @SuppressWarnings("unchecked")
                Collection<Player> players = (Collection<Player>) GriefPrevention.get().getServer().getOnlinePlayers();
                for (Player onlinePlayer : players) {
                    if (onlinePlayer.getUniqueId().equals(player.getUniqueId())) continue;

                    PlayerData otherData = playerDataRepository.getPlayerData(onlinePlayer.getUniqueId());
                    if (ipAddress.equals(otherData.ipAddress) && isNewToServer(onlinePlayer)) {
                        ipCount++;
                    }
                }

                if (ipCount >= ipLimit) {
                    //kick player
                    PlayerKickBanTask task = new PlayerKickBanTask(player, MessageService.getMessage(Messages.TooMuchIpOverlap), "GriefPrevention IP-sharing limit.", false);
                    GriefPrevention.get().getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.get(), task, 100L);

                    //silence join message
                    event.setJoinMessage(null);
                    return;
                }
            }
        }

        //is he stuck in a portal frame?
        if (player.hasMetadata("GP_PORTALRESCUE")) {
            //If so, let him know and rescue him in 10 seconds. If he is in fact not trapped, hopefully chunks will have loaded by this time so he can walk out.
            MessageService.sendMessage(player, TextMode.Info, Messages.NetherPortalTrapDetectionMessage, 20L);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.getPortalCooldown() > 8 && player.hasMetadata("GP_PORTALRESCUE")) {
                        GriefPrevention.AddLogEntry("Rescued " + player.getName() + " from a nether portal.\nTeleported from " + player.getLocation().toString() + " to " + ((Location) player.getMetadata("GP_PORTALRESCUE").get(0).value()).toString(), CustomLogEntryTypes.Debug);
                        player.teleport((Location) player.getMetadata("GP_PORTALRESCUE").get(0).value());
                        player.removeMetadata("GP_PORTALRESCUE", GriefPrevention.get());
                    }
                }
            }.runTaskLater(GriefPrevention.get(), 200L);
        }
        //Otherwise just reset cooldown, just in case they happened to logout again...
        else
            player.setPortalCooldown(0);

        //if we're holding a logout message for this player, don't send that or this event's join message
        if (ConfigLoader.config_spam_logoutMessageDelaySeconds > 0) {
            String joinMessage = event.getJoinMessage();
            if (joinMessage != null && !joinMessage.isEmpty()) {
                Integer taskID = this.logoutMessagesService.get(player.getUniqueId());
                if (taskID != null && Bukkit.getScheduler().isQueued(taskID)) {
                    Bukkit.getScheduler().cancelTask(taskID);
                    player.sendMessage(event.getJoinMessage());
                    event.setJoinMessage("");
                }
            }
        }
    }

    private boolean isNewToServer(Player player) {
        return player.hasPlayedBefore();
    }

}

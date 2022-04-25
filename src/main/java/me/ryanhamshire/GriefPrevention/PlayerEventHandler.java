/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2011 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention;

import be.garagepoort.mcioc.IocListener;
import me.ryanhamshire.GriefPrevention.claims.ClaimBlockService;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.claims.ResizeClaimService;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.sessions.LogoutMessagesService;
import me.ryanhamshire.GriefPrevention.sessions.NotificationService;
import me.ryanhamshire.GriefPrevention.sessions.SessionManager;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.command.Command;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fish;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Item;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Mule;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.minecart.PoweredMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@IocListener
public class PlayerEventHandler implements Listener {
    private final PlayerDataRepository playerDataRepository;
    private final PvpProtectionService pvpProtectionService;
    private final BukkitUtils bukkitUtils;

    private final ArrayList<IpBanInfo> tempBannedIps = new ArrayList<>();
    private final long MILLISECONDS_IN_DAY = 1000 * 60 * 60 * 24;
    private Pattern howToClaimPattern = null;
    private final WordFinder bannedWordFinder;
    private final SpamDetector spamDetector = new SpamDetector();

    private final ClaimService claimService;
    private final ClaimBlockService claimBlockService;
    private final ResizeClaimService resizeClaimService;
    private final DataStore dataStore;
    private final SessionManager sessionManager;
    private final NotificationService notificationService;
    private final LogoutMessagesService logoutMessagesService;
    private final EntityEventHandler entityEventHandler;

    public PlayerEventHandler(PlayerDataRepository playerDataRepository, PvpProtectionService pvpProtectionService, BukkitUtils bukkitUtils, ClaimService claimService, ClaimBlockService claimBlockService, ResizeClaimService resizeClaimService, DataStore dataStore, SessionManager sessionManager, NotificationService notificationService, LogoutMessagesService logoutMessagesService, EntityEventHandler entityEventHandler) {
        this.playerDataRepository = playerDataRepository;
        bannedWordFinder = new WordFinder(playerDataRepository.loadBannedWords());
        this.pvpProtectionService = pvpProtectionService;
        this.bukkitUtils = bukkitUtils;

        this.claimService = claimService;
        this.claimBlockService = claimBlockService;
        this.resizeClaimService = resizeClaimService;
        this.dataStore = dataStore;
        this.sessionManager = sessionManager;
        this.notificationService = notificationService;
        this.logoutMessagesService = logoutMessagesService;
        this.entityEventHandler = entityEventHandler;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    synchronized void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!player.isOnline()) {
            event.setCancelled(true);
            return;
        }

        String message = event.getMessage();

        boolean muted = this.handlePlayerChat(player, message, event);
        Set<Player> recipients = event.getRecipients();

        //muted messages go out to only the sender
        if (muted) {
            recipients.clear();
            recipients.add(player);
        }

        //soft muted messages go out to all soft muted players
        else if (this.playerDataRepository.isSoftMuted(player.getUniqueId())) {
            String notificationMessage = "(Muted " + player.getName() + "): " + message;
            Set<Player> recipientsToKeep = new HashSet<>();
            for (Player recipient : recipients) {
                if (this.playerDataRepository.isSoftMuted(recipient.getUniqueId())) {
                    recipientsToKeep.add(recipient);
                } else if (recipient.hasPermission("griefprevention.eavesdrop")) {
                    recipient.sendMessage(ChatColor.GRAY + notificationMessage);
                }
            }
            recipients.clear();
            recipients.addAll(recipientsToKeep);

            GriefPrevention.AddLogEntry(notificationMessage, CustomLogEntryTypes.MutedChat, false);
        }

        //troll and excessive profanity filter
        else if (!player.hasPermission("griefprevention.spam") && this.bannedWordFinder.hasMatch(message)) {
            //allow admins to see the soft-muted text
            String notificationMessage = "(Muted " + player.getName() + "): " + message;
            for (Player recipient : recipients) {
                if (recipient.hasPermission("griefprevention.eavesdrop")) {
                    recipient.sendMessage(ChatColor.GRAY + notificationMessage);
                }
            }

            //limit recipients to sender
            recipients.clear();
            recipients.add(player);

            //if player not new warn for the first infraction per play session.
            if (!isNewToServer(player)) {
                PlayerData playerData = playerDataRepository.getPlayerData(player.getUniqueId());
                if (!playerData.profanityWarned) {
                    playerData.profanityWarned = true;
                    MessageService.sendMessage(player, TextMode.Err, Messages.NoProfanity);
                    event.setCancelled(true);
                    return;
                }
            }

            //otherwise assume chat troll and mute all chat from this sender until an admin says otherwise
            else if (ConfigLoader.config_trollFilterEnabled) {
                GriefPrevention.AddLogEntry("Auto-muted new player " + player.getName() + " for profanity shortly after join.  Use /SoftMute to undo.", CustomLogEntryTypes.AdminActivity);
                GriefPrevention.AddLogEntry(notificationMessage, CustomLogEntryTypes.MutedChat, false);
                playerDataRepository.toggleSoftMute(player.getUniqueId());
            }
        }

        //remaining messages
        else {
            //enter in abridged chat logs
            makeSocialLogEntry(player.getName(), message);

            //based on ignore lists, remove some of the audience
            if (!player.hasPermission("griefprevention.notignorable")) {
                Set<Player> recipientsToRemove = new HashSet<>();
                PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());
                for (Player recipient : recipients) {
                    if (!recipient.hasPermission("griefprevention.notignorable")) {
                        if (playerData.ignoredPlayers.containsKey(recipient.getUniqueId())) {
                            recipientsToRemove.add(recipient);
                        } else {
                            PlayerData targetPlayerData = this.playerDataRepository.getPlayerData(recipient.getUniqueId());
                            if (targetPlayerData.ignoredPlayers.containsKey(player.getUniqueId())) {
                                recipientsToRemove.add(recipient);
                            }
                        }
                    }
                }

                recipients.removeAll(recipientsToRemove);
            }
        }
    }

    //returns true if the message should be muted, true if it should be sent
    private boolean handlePlayerChat(Player player, String message, PlayerEvent event) {
        //FEATURE: automatically educate players about claiming land
        //watching for message format how*claim*, and will send a link to the basics video
        if (this.howToClaimPattern == null) {
            this.howToClaimPattern = Pattern.compile(MessageService.getMessage(Messages.HowToClaimRegex), Pattern.CASE_INSENSITIVE);
        }

        if (this.howToClaimPattern.matcher(message).matches()) {
            if (ConfigLoader.creativeRulesApply(player.getLocation())) {
                MessageService.sendMessage(player, TextMode.Info, Messages.CreativeBasicsVideo2, 10L, PlayerDataRepository.CREATIVE_VIDEO_URL);
            } else {
                MessageService.sendMessage(player, TextMode.Info, Messages.SurvivalBasicsVideo2, 10L, PlayerDataRepository.SURVIVAL_VIDEO_URL);
            }
        }

        //FEATURE: automatically educate players about the /trapped command
        //check for "trapped" or "stuck" to educate players about the /trapped command
        String trappedwords = MessageService.getMessage(
            Messages.TrappedChatKeyword
        );
        if (!trappedwords.isEmpty()) {
            String[] checkWords = trappedwords.split(";");

            for (String checkWord : checkWords) {
                if (!message.contains("/trapped")
                    && message.contains(checkWord)) {
                    MessageService.sendMessage(
                        player,
                        TextMode.Info,
                        Messages.TrappedInstructions,
                        10L
                    );
                    break;
                }
            }
        }

        //FEATURE: monitor for chat and command spam

        if (!ConfigLoader.config_spam_enabled) return false;

        //if the player has permission to spam, don't bother even examining the message
        if (player.hasPermission("griefprevention.spam")) return false;

        //examine recent messages to detect spam
        SpamAnalysisResult result = this.spamDetector.AnalyzeMessage(player.getUniqueId(), message, System.currentTimeMillis());

        //apply any needed changes to message (like lowercasing all-caps)
        if (event instanceof AsyncPlayerChatEvent) {
            ((AsyncPlayerChatEvent) event).setMessage(result.finalMessage);
        }

        //don't allow new players to chat after logging in until they move
        PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());
        if (playerData.noChatLocation != null) {
            Location currentLocation = player.getLocation();
            if (currentLocation.getBlockX() == playerData.noChatLocation.getBlockX() &&
                currentLocation.getBlockZ() == playerData.noChatLocation.getBlockZ()) {
                MessageService.sendMessage(player, TextMode.Err, Messages.NoChatUntilMove, 10L);
                result.muteReason = "pre-movement chat";
            } else {
                playerData.noChatLocation = null;
            }
        }

        //filter IP addresses
        if (result.muteReason == null) {
            if (GriefPrevention.get().containsBlockedIP(message)) {
                //block message
                result.muteReason = "IP address";
            }
        }

        return true;
    }

    //when a player uses a slash command...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    synchronized void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        String[] args = message.split(" ");

        String command = args[0].toLowerCase();

        CommandCategory category = this.getCommandCategory(command);

        Player player = event.getPlayer();
        PlayerData playerData = null;

        //if a whisper
        if (category == CommandCategory.Whisper && args.length > 1) {
            //determine target player, might be NULL

            Player targetPlayer = GriefPrevention.get().getServer().getPlayer(args[1]);

            //softmute feature
            if (this.playerDataRepository.isSoftMuted(player.getUniqueId()) && targetPlayer != null && !this.playerDataRepository.isSoftMuted(targetPlayer.getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            //if eavesdrop enabled and sender doesn't have the eavesdrop immunity permission, eavesdrop
            if (ConfigLoader.config_whisperNotifications && !player.hasPermission("griefprevention.eavesdropimmune")) {
                //except for when the recipient has eavesdrop immunity
                if (targetPlayer == null || !targetPlayer.hasPermission("griefprevention.eavesdropimmune")) {
                    StringBuilder logMessageBuilder = new StringBuilder();
                    logMessageBuilder.append("[[").append(event.getPlayer().getName()).append("]] ");

                    for (int i = 1; i < args.length; i++) {
                        logMessageBuilder.append(args[i]).append(" ");
                    }

                    String logMessage = logMessageBuilder.toString();

                    @SuppressWarnings("unchecked")
                    Collection<Player> players = (Collection<Player>) GriefPrevention.get().getServer().getOnlinePlayers();
                    for (Player onlinePlayer : players) {
                        if (onlinePlayer.hasPermission("griefprevention.eavesdrop") && !onlinePlayer.equals(targetPlayer) && !onlinePlayer.equals(player)) {
                            onlinePlayer.sendMessage(ChatColor.GRAY + logMessage);
                        }
                    }
                }
            }

            //ignore feature
            if (targetPlayer != null && targetPlayer.isOnline()) {
                //if either is ignoring the other, cancel this command
                playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());
                if (playerData.ignoredPlayers.containsKey(targetPlayer.getUniqueId()) && !targetPlayer.hasPermission("griefprevention.notignorable")) {
                    event.setCancelled(true);
                    MessageService.sendMessage(player, TextMode.Err, Messages.IsIgnoringYou);
                    return;
                }

                PlayerData targetPlayerData = this.playerDataRepository.getPlayerData(targetPlayer.getUniqueId());
                if (targetPlayerData.ignoredPlayers.containsKey(player.getUniqueId()) && !player.hasPermission("griefprevention.notignorable")) {
                    event.setCancelled(true);
                    MessageService.sendMessage(player, TextMode.Err, Messages.IsIgnoringYou);
                    return;
                }
            }
        }

        //if in pvp, block any pvp-banned slash commands
        if (playerData == null) playerData = this.playerDataRepository.getPlayerData(event.getPlayer().getUniqueId());

        if ((playerData.inPvpCombat() || playerData.siegeData != null) && ConfigLoader.config_pvp_blockedCommands.contains(command)) {
            event.setCancelled(true);
            MessageService.sendMessage(event.getPlayer(), TextMode.Err, Messages.CommandBannedInPvP);
            return;
        }

        //soft mute for chat slash commands
        if (category == CommandCategory.Chat && this.playerDataRepository.isSoftMuted(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        //if the slash command used is in the list of monitored commands, treat it like a chat message (see above)
        boolean isMonitoredCommand = (category == CommandCategory.Chat || category == CommandCategory.Whisper);
        if (isMonitoredCommand) {
            //if anti spam enabled, check for spam
            if (ConfigLoader.config_spam_enabled) {
                event.setCancelled(this.handlePlayerChat(event.getPlayer(), event.getMessage(), event));
            }

            if (!player.hasPermission("griefprevention.spam") && this.bannedWordFinder.hasMatch(message)) {
                event.setCancelled(true);
            }

            //unless cancelled, log in abridged logs
            if (!event.isCancelled()) {
                StringBuilder builder = new StringBuilder();
                for (String arg : args) {
                    builder.append(arg).append(' ');
                }

                makeSocialLogEntry(event.getPlayer().getName(), builder.toString());
            }
        }

        //if requires access trust, check for permission
        isMonitoredCommand = false;
        String lowerCaseMessage = message.toLowerCase();
        for (String monitoredCommand : ConfigLoader.config_claims_commandsRequiringAccessTrust) {
            if (lowerCaseMessage.startsWith(monitoredCommand)) {
                isMonitoredCommand = true;
                break;
            }
        }

        if (isMonitoredCommand) {
            Claim claim = this.claimService.getClaimAt(player.getLocation(), false, playerData.lastClaim);
            if (claim != null) {
                playerData.lastClaim = claim;
                Supplier<String> reason = claimService.checkPermission(claim, player, ClaimPermission.Access, event);
                if (reason != null) {
                    MessageService.sendMessage(player, TextMode.Err, reason.get());
                    event.setCancelled(true);
                }
            }
        }
    }

    private final ConcurrentHashMap<String, CommandCategory> commandCategoryMap = new ConcurrentHashMap<>();

    private CommandCategory getCommandCategory(String commandName) {
        if (commandName.startsWith("/")) commandName = commandName.substring(1);

        //if we've seen this command or alias before, return the category determined previously
        CommandCategory category = this.commandCategoryMap.get(commandName);
        if (category != null) return category;

        //otherwise build a list of all the aliases of this command across all installed plugins
        HashSet<String> aliases = new HashSet<>();
        aliases.add(commandName);
        aliases.add("minecraft:" + commandName);
        for (Plugin plugin : Bukkit.getServer().getPluginManager().getPlugins()) {
            if (!(plugin instanceof JavaPlugin))
                continue;
            JavaPlugin javaPlugin = (JavaPlugin) plugin;
            Command command = javaPlugin.getCommand(commandName);
            if (command != null) {
                aliases.add(command.getName().toLowerCase());
                aliases.add(plugin.getName().toLowerCase() + ":" + command.getName().toLowerCase());
                for (String alias : command.getAliases()) {
                    aliases.add(alias.toLowerCase());
                    aliases.add(plugin.getName().toLowerCase() + ":" + alias.toLowerCase());
                }
            }
        }

        //also consider vanilla commands
        Command command = Bukkit.getServer().getPluginCommand(commandName);
        if (command != null) {
            aliases.add(command.getName().toLowerCase());
            aliases.add("minecraft:" + command.getName().toLowerCase());
            for (String alias : command.getAliases()) {
                aliases.add(alias.toLowerCase());
                aliases.add("minecraft:" + alias.toLowerCase());
            }
        }

        //if any of those aliases are in the chat list or whisper list, then we know the category for that command
        category = CommandCategory.None;
        for (String alias : aliases) {
            if (ConfigLoader.config_eavesdrop_whisperCommands.contains("/" + alias)) {
                category = CommandCategory.Whisper;
            } else if (ConfigLoader.config_spam_monitorSlashCommands.contains("/" + alias)) {
                category = CommandCategory.Chat;
            }

            //remember the categories for later
            this.commandCategoryMap.put(alias.toLowerCase(), category);
        }

        return category;
    }

    static int longestNameLength = 10;

    static void makeSocialLogEntry(String name, String message) {
        StringBuilder entryBuilder = new StringBuilder(name);
        for (int i = name.length(); i < longestNameLength; i++) {
            entryBuilder.append(' ');
        }
        entryBuilder.append(": ").append(message);

        longestNameLength = Math.max(longestNameLength, name.length());
        //TODO: cleanup static
        GriefPrevention.AddLogEntry(entryBuilder.toString(), CustomLogEntryTypes.SocialActivity, true);
    }

    //when a player attempts to join the server...
    @EventHandler(priority = EventPriority.HIGHEST)
    void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();

        //all this is anti-spam code
        if (ConfigLoader.config_spam_enabled) {
            //FEATURE: login cooldown to prevent login/logout spam with custom clients
            long now = Calendar.getInstance().getTimeInMillis();

            //if allowed to join and login cooldown enabled
            if (ConfigLoader.config_spam_loginCooldownSeconds > 0 && event.getResult() == Result.ALLOWED && !player.hasPermission("griefprevention.spam")) {
                //determine how long since last login and cooldown remaining
                Date lastLoginThisSession = sessionManager.getSessionDate(player.getUniqueId());
                if (lastLoginThisSession != null) {
                    long millisecondsSinceLastLogin = now - lastLoginThisSession.getTime();
                    long secondsSinceLastLogin = millisecondsSinceLastLogin / 1000;
                    long cooldownRemaining = ConfigLoader.config_spam_loginCooldownSeconds - secondsSinceLastLogin;

                    //if cooldown remaining
                    if (cooldownRemaining > 0) {
                        //DAS BOOT!
                        event.setResult(Result.KICK_OTHER);
                        event.setKickMessage("You must wait " + cooldownRemaining + " seconds before logging-in again.");
                        event.disallow(event.getResult(), event.getKickMessage());
                        return;
                    }
                }
            }
        }

        //remember the player's ip address
        PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());
        playerData.ipAddress = event.getAddress();
    }

    //when a player spawns, conditionally apply temporary pvp protection
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        boolean inventoryEmpty = GriefPrevention.isInventoryEmpty(player);
        bukkitUtils.runTaskAsync(player, () -> {
            PlayerData playerData = playerDataRepository.getPlayerData(player.getUniqueId());
            playerData.lastSpawn = Calendar.getInstance().getTimeInMillis();
            playerData.lastPvpTimestamp = 0;  //no longer in pvp combat

            //also send him any messaged from grief prevention he would have received while dead
            if (playerData.messageOnRespawn != null) {
                MessageService.sendMessage(player, ChatColor.RESET /*color is alrady embedded in message in this case*/, playerData.messageOnRespawn, 40L);
                playerData.messageOnRespawn = null;
            }

            pvpProtectionService.checkPvpProtectionNeeded(player, inventoryEmpty);
        });
    }

    //when a player dies...
    private final HashMap<UUID, Long> deathTimestamps = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST)
    void onPlayerDeath(PlayerDeathEvent event) {
        //FEATURE: prevent death message spam by implementing a "cooldown period" for death messages
        Player player = event.getEntity();
        Long lastDeathTime = this.deathTimestamps.get(player.getUniqueId());
        long now = Calendar.getInstance().getTimeInMillis();
        if (lastDeathTime != null && now - lastDeathTime < ConfigLoader.config_spam_deathMessageCooldownSeconds * 1000) {
            player.sendMessage(event.getDeathMessage());  //let the player assume his death message was broadcasted to everyone
            event.setDeathMessage("");
        }

        this.deathTimestamps.put(player.getUniqueId(), now);

        //these are related to locking dropped items on death to prevent theft
        PlayerData playerData = playerDataRepository.getPlayerData(player.getUniqueId());
        playerData.dropsAreUnlocked = false;
        playerData.receivedDropUnlockAdvertisement = false;
    }

    //when a player gets kicked...
    @EventHandler(priority = EventPriority.HIGHEST)
    void onPlayerKicked(PlayerKickEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());
        playerData.wasKicked = true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerID = player.getUniqueId();
        PlayerData playerData = this.playerDataRepository.getPlayerData(playerID);
        boolean isBanned;

        //If player is not trapped in a portal and has a pending rescue task, remove the associated metadata
        //Why 9? No idea why, but this is decremented by 1 when the player disconnects.
        if (player.getPortalCooldown() < 9) {
            player.removeMetadata("GP_PORTALRESCUE", GriefPrevention.get());
        }

        if (playerData.wasKicked) {
            isBanned = player.isBanned();
        } else {
            isBanned = false;
        }

        //if banned, add IP to the temporary IP ban list
        if (isBanned && playerData.ipAddress != null) {
            long now = Calendar.getInstance().getTimeInMillis();
            this.tempBannedIps.add(new IpBanInfo(playerData.ipAddress, now + this.MILLISECONDS_IN_DAY, player.getName()));
        }

        //silence notifications when they're coming too fast
        if (event.getQuitMessage() != null && notificationService.shouldSilenceNotification()) {
            event.setQuitMessage(null);
        }

        //silence notifications when the player is banned
        if (isBanned && ConfigLoader.config_silenceBans) {
            event.setQuitMessage(null);
        }

        //make sure his data is all saved - he might have accrued some claim blocks while playing that were not saved immediately
        else {
            this.playerDataRepository.savePlayerData(player.getUniqueId(), playerData);
        }

        //FEATURE: players in pvp combat when they log out will die
        if (ConfigLoader.config_pvp_punishLogout && playerData.inPvpCombat()) {
            player.setHealth(0);
        }

        //FEATURE: during a siege, any player who logs out dies and forfeits the siege

        //if player was involved in a siege, he forfeits
        if (playerData.siegeData != null) {
            if (player.getHealth() > 0)
                player.setHealth(0);  //might already be zero from above, this avoids a double death message
        }

        //drop data about this player
        this.playerDataRepository.clearCachedPlayerData(playerID);

        //send quit message later, but only if the player stays offline
        if (ConfigLoader.config_spam_logoutMessageDelaySeconds > 0) {
            String quitMessage = event.getQuitMessage();
            if (quitMessage != null && !quitMessage.isEmpty()) {
                BroadcastMessageTask task = new BroadcastMessageTask(quitMessage);
                int taskID = Bukkit.getScheduler().scheduleSyncDelayedTask(GriefPrevention.get(), task, 20L * ConfigLoader.config_spam_logoutMessageDelaySeconds);
                this.logoutMessagesService.addMessage(playerID, taskID);
                event.setQuitMessage("");
            }
        }
    }

    //when a player drops an item
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        //in creative worlds, dropping items is blocked
        if (ConfigLoader.creativeRulesApply(player.getLocation())) {
            event.setCancelled(true);
            return;
        }

        PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());

        //FEATURE: players under siege or in PvP combat, can't throw items on the ground to hide
        //them or give them away to other players before they are defeated

        //if in combat, don't let him drop it
        if (!ConfigLoader.config_pvp_allowCombatItemDrop && playerData.inPvpCombat()) {
            MessageService.sendMessage(player, TextMode.Err, Messages.PvPNoDrop);
            event.setCancelled(true);
        }

        //if he's under siege, don't let him drop it
        else if (playerData.siegeData != null) {
            MessageService.sendMessage(player, TextMode.Err, Messages.SiegeNoDrop);
            event.setCancelled(true);
        }
    }

    //when a player teleports via a portal
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    void onPlayerPortal(PlayerPortalEvent event) {
        //if the player isn't going anywhere, take no action
        if (event.getTo() == null || event.getTo().getWorld() == null) return;

        Player player = event.getPlayer();
        if (event.getCause() == TeleportCause.NETHER_PORTAL) {
            //FEATURE: when players get trapped in a nether portal, send them back through to the other side
            GriefPrevention.get().startRescueTask(player, player.getLocation());

            //don't track in worlds where claims are not enabled
            if (!GriefPrevention.get().claimsEnabledForWorld(event.getTo().getWorld())) return;
        }
    }

    //when a player teleports
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());

        //FEATURE: prevent players from using ender pearls to gain access to secured claims
        TeleportCause cause = event.getCause();
        if (cause == TeleportCause.CHORUS_FRUIT || (cause == TeleportCause.ENDER_PEARL && ConfigLoader.config_claims_enderPearlsRequireAccessTrust)) {
            Claim toClaim = this.claimService.getClaimAt(event.getTo(), false, playerData.lastClaim);
            if (toClaim != null) {
                playerData.lastClaim = toClaim;
                Supplier<String> noAccessReason = claimService.checkPermission(toClaim, player, ClaimPermission.Access, event);
                if (noAccessReason != null) {
                    MessageService.sendMessage(player, TextMode.Err, noAccessReason.get());
                    event.setCancelled(true);
                    if (cause == TeleportCause.ENDER_PEARL)
                        player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL));
                }
            }
        }

        //FEATURE: prevent teleport abuse to win sieges

        //these rules only apply to siege worlds only
        if (!ConfigLoader.config_siege_enabledWorlds.contains(player.getWorld())) return;

        //these rules do not apply to admins
        if (player.hasPermission("griefprevention.siegeteleport")) return;

        //Ignore vanilla teleports (usually corrective teleports? See issue #210)
        if (event.getCause() == TeleportCause.UNKNOWN) return;

        Location source = event.getFrom();
        Claim sourceClaim = this.claimService.getClaimAt(source, false, playerData.lastClaim);
        if (sourceClaim != null && sourceClaim.siegeData != null) {
            MessageService.sendMessage(player, TextMode.Err, Messages.SiegeNoTeleport);
            event.setCancelled(true);
            return;
        }

        Location destination = event.getTo();
        Claim destinationClaim = this.claimService.getClaimAt(destination, false, null);
        if (destinationClaim != null && destinationClaim.siegeData != null) {
            MessageService.sendMessage(player, TextMode.Err, Messages.BesiegedNoTeleport);
            event.setCancelled(true);
            return;
        }
    }

    //when a player triggers a raid (in a claim)
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerTriggerRaid(RaidTriggerEvent event) {
        if (!ConfigLoader.config_claims_raidTriggersRequireBuildTrust)
            return;

        Player player = event.getPlayer();
        PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());

        Claim claim = this.claimService.getClaimAt(player.getLocation(), false, playerData.lastClaim);
        if (claim == null)
            return;

        playerData.lastClaim = claim;
        if (claimService.checkPermission(claim, player, ClaimPermission.Build, event) == null)
            return;

        event.setCancelled(true);
    }

    //when a player interacts with a specific part of entity...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        //treat it the same as interacting with an entity in general
        if (event.getRightClicked().getType() == EntityType.ARMOR_STAND) {
            this.onPlayerInteractEntity((PlayerInteractEntityEvent) event);
        }
    }

    //when a player interacts with an entity...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        if (!GriefPrevention.get().claimsEnabledForWorld(entity.getWorld())) return;

        //allow horse protection to be overridden to allow management from other plugins
        if (!ConfigLoader.config_claims_protectHorses && entity instanceof AbstractHorse) return;
        if (!ConfigLoader.config_claims_protectDonkeys && entity instanceof Donkey) return;
        if (!ConfigLoader.config_claims_protectDonkeys && entity instanceof Mule) return;
        if (!ConfigLoader.config_claims_protectLlamas && entity instanceof Llama) return;

        PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());

        //if entity is tameable and has an owner, apply special rules
        if (entity instanceof Tameable) {
            Tameable tameable = (Tameable) entity;
            if (tameable.isTamed()) {
                if (tameable.getOwner() != null) {
                    UUID ownerID = tameable.getOwner().getUniqueId();

                    //if the player interacting is the owner or an admin in ignore claims mode, always allow
                    if (player.getUniqueId().equals(ownerID) || playerData.ignoreClaims) {
                        //if giving away pet, do that instead
                        if (playerData.petGiveawayRecipient != null) {
                            tameable.setOwner(playerData.petGiveawayRecipient);
                            playerData.petGiveawayRecipient = null;
                            MessageService.sendMessage(player, TextMode.Success, Messages.PetGiveawayConfirmation);
                            event.setCancelled(true);
                        }

                        return;
                    }
                    if (!GriefPrevention.get().pvpRulesApply(entity.getLocation().getWorld()) || ConfigLoader.config_pvp_protectPets) {
                        //otherwise disallow
                        OfflinePlayer owner = GriefPrevention.get().getServer().getOfflinePlayer(ownerID);
                        String ownerName = owner.getName();
                        if (ownerName == null) ownerName = "someone";
                        String message = MessageService.getMessage(Messages.NotYourPet, ownerName);
                        if (player.hasPermission("griefprevention.ignoreclaims"))
                            message += "  " + MessageService.getMessage(Messages.IgnoreClaimsAdvertisement);
                        MessageService.sendMessage(player, TextMode.Err, message);
                        event.setCancelled(true);
                        return;
                    }
                }
            } else  //world repair code for a now-fixed GP bug //TODO: necessary anymore?
            {
                //ensure this entity can be tamed by players
                tameable.setOwner(null);
                if (tameable instanceof InventoryHolder) {
                    InventoryHolder holder = (InventoryHolder) tameable;
                    holder.getInventory().clear();
                }
            }
        }

        //don't allow interaction with item frames or armor stands in claimed areas without build permission
        if (entity.getType() == EntityType.ARMOR_STAND || entity instanceof Hanging) {
            String noBuildReason = claimService.allowBuild(player, entity.getLocation(), Material.ITEM_FRAME);
            if (noBuildReason != null) {
                MessageService.sendMessage(player, TextMode.Err, noBuildReason);
                event.setCancelled(true);
                return;
            }
        }

        //limit armor placements when entity count is too high
        if (entity.getType() == EntityType.ARMOR_STAND && ConfigLoader.creativeRulesApply(player.getLocation())) {
            if (playerData == null) playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());
            Claim claim = this.claimService.getClaimAt(entity.getLocation(), false, playerData.lastClaim);
            if (claim == null) return;

            String noEntitiesReason = claim.allowMoreEntities(false);
            if (noEntitiesReason != null) {
                MessageService.sendMessage(player, TextMode.Err, noEntitiesReason);
                event.setCancelled(true);
                return;
            }
        }

        //always allow interactions when player is in ignore claims mode
        if (playerData.ignoreClaims) return;

        //don't allow container access during pvp combat
        if ((entity instanceof StorageMinecart || entity instanceof PoweredMinecart)) {
            if (playerData.siegeData != null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.SiegeNoContainers);
                event.setCancelled(true);
                return;
            }

            if (playerData.inPvpCombat()) {
                MessageService.sendMessage(player, TextMode.Err, Messages.PvPNoContainers);
                event.setCancelled(true);
                return;
            }
        }

        //if the entity is a vehicle and we're preventing theft in claims
        if (ConfigLoader.config_claims_preventTheft && entity instanceof Vehicle) {
            //if the entity is in a claim
            Claim claim = this.claimService.getClaimAt(entity.getLocation(), false, null);
            if (claim != null) {
                //for storage entities, apply container rules (this is a potential theft)
                if (entity instanceof InventoryHolder) {
                    Supplier<String> noContainersReason = claimService.checkPermission(claim, player, ClaimPermission.Inventory, event);
                    if (noContainersReason != null) {
                        MessageService.sendMessage(player, TextMode.Err, noContainersReason.get());
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        //if the entity is an animal, apply container rules
        if ((ConfigLoader.config_claims_preventTheft && (entity instanceof Animals || entity instanceof Fish)) || (entity.getType() == EntityType.VILLAGER && ConfigLoader.config_claims_villagerTradingRequiresTrust)) {
            //if the entity is in a claim
            Claim claim = this.claimService.getClaimAt(entity.getLocation(), false, null);
            if (claim != null) {
                Supplier<String> override = () ->
                {
                    String message = MessageService.getMessage(Messages.NoDamageClaimedEntity, claim.getOwnerName());
                    if (player.hasPermission("griefprevention.ignoreclaims"))
                        message += "  " + MessageService.getMessage(Messages.IgnoreClaimsAdvertisement);

                    return message;
                };
                final Supplier<String> noContainersReason = claimService.checkPermission(claim, player, ClaimPermission.Inventory, event, override);
                if (noContainersReason != null) {
                    MessageService.sendMessage(player, TextMode.Err, noContainersReason.get());
                    event.setCancelled(true);
                    return;
                }
            }
        }

        ItemStack itemInHand = GriefPrevention.get().getItemInHand(player, event.getHand());

        //if preventing theft, prevent leashing claimed creatures
        if (ConfigLoader.config_claims_preventTheft && entity instanceof Creature && itemInHand.getType() == Material.LEAD) {
            Claim claim = this.claimService.getClaimAt(entity.getLocation(), false, playerData.lastClaim);
            if (claim != null) {
                Supplier<String> failureReason = claimService.checkPermission(claim, player, ClaimPermission.Inventory, event);
                if (failureReason != null) {
                    event.setCancelled(true);
                    MessageService.sendMessage(player, TextMode.Err, failureReason.get());
                    return;
                }
            }
        }

        // Name tags may only be used on entities that the player is allowed to kill.
        if (itemInHand.getType() == Material.NAME_TAG) {
            EntityDamageByEntityEvent damageEvent = new EntityDamageByEntityEvent(player, entity, EntityDamageEvent.DamageCause.CUSTOM, 0);
            entityEventHandler.onEntityDamage(damageEvent);
            if (damageEvent.isCancelled()) {
                event.setCancelled(true);
                // Don't print message - damage event handler should have handled it.
                return;
            }
        }
    }

    //when a player throws an egg
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerThrowEgg(PlayerEggThrowEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());
        Claim claim = this.claimService.getClaimAt(event.getEgg().getLocation(), false, playerData.lastClaim);

        //allow throw egg if player is in ignore claims mode
        if (playerData.ignoreClaims || claim == null) return;

        Supplier<String> failureReason = claimService.checkPermission(claim, player, ClaimPermission.Inventory, event);
        if (failureReason != null) {
            String reason = failureReason.get();
            if (player.hasPermission("griefprevention.ignoreclaims")) {
                reason += "  " + MessageService.getMessage(Messages.IgnoreClaimsAdvertisement);
            }

            MessageService.sendMessage(player, TextMode.Err, reason);

            //cancel the event by preventing hatching
            event.setHatching(false);

            //only give the egg back if player is in survival or adventure
            if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                player.getInventory().addItem(event.getEgg().getItem());
            }
        }
    }

    //when a player reels in his fishing rod
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerFish(PlayerFishEvent event) {
        Entity entity = event.getCaught();
        if (entity == null) return;  //if nothing pulled, uninteresting event

        //if should be protected from pulling in land claims without permission
        if (entity.getType() == EntityType.ARMOR_STAND || entity instanceof Animals) {
            Player player = event.getPlayer();
            PlayerData playerData = playerDataRepository.getPlayerData(player.getUniqueId());
            Claim claim = dataStore.getClaimAt(entity.getLocation(), false, playerData.lastClaim);
            if (claim != null) {
                //if no permission, cancel
                Supplier<String> errorMessage = claimService.checkPermission(claim, player, ClaimPermission.Inventory, event);
                if (errorMessage != null) {
                    event.setCancelled(true);
                    MessageService.sendMessage(player, TextMode.Err, Messages.NoDamageClaimedEntity, claim.getOwnerName());
                    return;
                }
            }
        }
    }

    //when a player picks up an item...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();

        //FEATURE: lock dropped items to player who dropped them

        //who owns this stack?
        Item item = event.getItem();
        List<MetadataValue> data = item.getMetadata("GP_ITEMOWNER");
        if (data != null && data.size() > 0) {
            UUID ownerID = (UUID) data.get(0).value();

            //has that player unlocked his drops?
            OfflinePlayer owner = GriefPrevention.get().getServer().getOfflinePlayer(ownerID);
            String ownerName = GriefPrevention.lookupPlayerName(ownerID);
            if (owner.isOnline() && !player.equals(owner)) {
                PlayerData playerData = this.playerDataRepository.getPlayerData(ownerID);

                //if locked, don't allow pickup
                if (!playerData.dropsAreUnlocked) {
                    event.setCancelled(true);

                    //if hasn't been instructed how to unlock, send explanatory messages
                    if (!playerData.receivedDropUnlockAdvertisement) {
                        MessageService.sendMessage(owner.getPlayer(), TextMode.Instr, Messages.DropUnlockAdvertisement);
                        MessageService.sendMessage(player, TextMode.Err, Messages.PickupBlockedExplanation, ownerName);
                        playerData.receivedDropUnlockAdvertisement = true;
                    }

                    return;
                }
            }
        }

        //the rest of this code is specific to pvp worlds
        if (!GriefPrevention.get().pvpRulesApply(player.getWorld())) return;

        //if we're preventing spawn camping and the player was previously empty handed...
        if (ConfigLoader.config_pvp_protectFreshSpawns && (GriefPrevention.get().getItemInHand(player, EquipmentSlot.HAND).getType() == Material.AIR)) {
            //if that player is currently immune to pvp
            PlayerData playerData = this.playerDataRepository.getPlayerData(event.getPlayer().getUniqueId());
            if (playerData.pvpImmune) {
                //if it's been less than 10 seconds since the last time he spawned, don't pick up the item
                long now = Calendar.getInstance().getTimeInMillis();
                long elapsedSinceLastSpawn = now - playerData.lastSpawn;
                if (elapsedSinceLastSpawn < 10000) {
                    event.setCancelled(true);
                    return;
                }

                //otherwise take away his immunity. he may be armed now.  at least, he's worth killing for some loot
                playerData.pvpImmune = false;
                MessageService.sendMessage(player, TextMode.Warn, Messages.PvPImmunityEnd);
            }
        }
    }

    //when a player switches in-hand items
    @EventHandler(ignoreCancelled = true)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();

        //if he's switching to the golden shovel
        int newSlot = event.getNewSlot();
        ItemStack newItemStack = player.getInventory().getItem(newSlot);
        if (newItemStack != null && newItemStack.getType() == ConfigLoader.config_claims_modificationTool) {
            //give the player his available claim blocks count and claiming instructions, but only if he keeps the shovel equipped for a minimum time, to avoid mouse wheel spam
            if (GriefPrevention.get().claimsEnabledForWorld(player.getWorld())) {
                EquipShovelProcessingTask task = new EquipShovelProcessingTask(player, playerDataRepository, claimService, claimBlockService);
                GriefPrevention.get().getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.get(), task, 15L);  //15L is approx. 3/4 of a second
            }
        }
    }

    //block use of buckets within other players' claims
    private final Set<Material> commonAdjacentBlocks_water = EnumSet.of(Material.WATER, Material.FARMLAND, Material.DIRT, Material.STONE);
    private final Set<Material> commonAdjacentBlocks_lava = EnumSet.of(Material.LAVA, Material.DIRT, Material.STONE);

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent bucketEvent) {
        if (!GriefPrevention.get().claimsEnabledForWorld(bucketEvent.getBlockClicked().getWorld())) return;

        Player player = bucketEvent.getPlayer();
        Block block = bucketEvent.getBlockClicked().getRelative(bucketEvent.getBlockFace());
        int minLavaDistance = 10;

        // Fixes #1155:
        // Prevents waterlogging blocks placed on a claim's edge.
        // Waterlogging a block affects the clicked block, and NOT the adjacent location relative to it.
        if (bucketEvent.getBucket() == Material.WATER_BUCKET
            && bucketEvent.getBlockClicked().getBlockData() instanceof Waterlogged) {
            block = bucketEvent.getBlockClicked();
        }

        //make sure the player is allowed to build at the location
        String noBuildReason = claimService.allowBuild(player, block.getLocation(), Material.WATER);
        if (noBuildReason != null) {
            MessageService.sendMessage(player, TextMode.Err, noBuildReason);
            bucketEvent.setCancelled(true);
            return;
        }

        //if the bucket is being used in a claim, allow for dumping lava closer to other players
        PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());
        Claim claim = this.claimService.getClaimAt(block.getLocation(), false, playerData.lastClaim);
        if (claim != null) {
            minLavaDistance = 3;
        }

        //otherwise no wilderness dumping in creative mode worlds
        else if (ConfigLoader.creativeRulesApply(block.getLocation())) {
            if (block.getY() >= GriefPrevention.get().getSeaLevel(block.getWorld()) - 5 && !player.hasPermission("griefprevention.lava")) {
                if (bucketEvent.getBucket() == Material.LAVA_BUCKET) {
                    MessageService.sendMessage(player, TextMode.Err, Messages.NoWildernessBuckets);
                    bucketEvent.setCancelled(true);
                    return;
                }
            }
        }

        //lava buckets can't be dumped near other players unless pvp is on
        if (!doesAllowLavaProximityInWorld(block.getWorld()) && !player.hasPermission("griefprevention.lava")) {
            if (bucketEvent.getBucket() == Material.LAVA_BUCKET) {
                List<Player> players = block.getWorld().getPlayers();
                for (Player otherPlayer : players) {
                    Location location = otherPlayer.getLocation();
                    if (!otherPlayer.equals(player) && otherPlayer.getGameMode() == GameMode.SURVIVAL && player.canSee(otherPlayer) && block.getY() >= location.getBlockY() - 1 && location.distanceSquared(block.getLocation()) < minLavaDistance * minLavaDistance) {
                        MessageService.sendMessage(player, TextMode.Err, Messages.NoLavaNearOtherPlayer, "another player");
                        bucketEvent.setCancelled(true);
                        return;
                    }
                }
            }
        }

        //log any suspicious placements (check sea level, world type, and adjacent blocks)
        if (block.getY() >= GriefPrevention.get().getSeaLevel(block.getWorld()) - 5 && !player.hasPermission("griefprevention.lava") && block.getWorld().getEnvironment() != Environment.NETHER) {
            //if certain blocks are nearby, it's less suspicious and not worth logging
            Set<Material> exclusionAdjacentTypes;
            if (bucketEvent.getBucket() == Material.WATER_BUCKET)
                exclusionAdjacentTypes = this.commonAdjacentBlocks_water;
            else
                exclusionAdjacentTypes = this.commonAdjacentBlocks_lava;

            boolean makeLogEntry = true;
            BlockFace[] adjacentDirections = new BlockFace[]{BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.DOWN};
            for (BlockFace direction : adjacentDirections) {
                Material adjacentBlockType = block.getRelative(direction).getType();
                if (exclusionAdjacentTypes.contains(adjacentBlockType)) {
                    makeLogEntry = false;
                    break;
                }
            }

            if (makeLogEntry) {
                GriefPrevention.AddLogEntry(player.getName() + " placed suspicious " + bucketEvent.getBucket().name() + " @ " + GriefPrevention.getfriendlyLocationString(block.getLocation()), CustomLogEntryTypes.SuspiciousActivity, true);
            }
        }
    }

    private boolean doesAllowLavaProximityInWorld(World world) {
        if (GriefPrevention.get().pvpRulesApply(world)) {
            return ConfigLoader.config_pvp_allowLavaNearPlayers;
        } else {
            return ConfigLoader.config_pvp_allowLavaNearPlayers_NonPvp;
        }
    }

    //see above
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerBucketFill(PlayerBucketFillEvent bucketEvent) {
        Player player = bucketEvent.getPlayer();
        Block block = bucketEvent.getBlockClicked();

        if (!GriefPrevention.get().claimsEnabledForWorld(block.getWorld())) return;

        //make sure the player is allowed to build at the location
        String noBuildReason = claimService.allowBuild(player, block.getLocation(), Material.AIR);
        if (noBuildReason != null) {
            //exemption for cow milking (permissions will be handled by player interact with entity event instead)
            Material blockType = block.getType();
            if (blockType == Material.AIR)
                return;
            if (blockType.isSolid()) {
                BlockData blockData = block.getBlockData();
                if (!(blockData instanceof Waterlogged) || !((Waterlogged) blockData).isWaterlogged())
                    return;
            }

            MessageService.sendMessage(player, TextMode.Err, noBuildReason);
            bucketEvent.setCancelled(true);
            return;
        }
    }

    // Stops an untrusted player from removing a book from a lectern
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    void onTakeBook(PlayerTakeLecternBookEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());
        Claim claim = this.claimService.getClaimAt(event.getLectern().getLocation(), false, playerData.lastClaim);
        if (claim != null) {
            playerData.lastClaim = claim;
            Supplier<String> noContainerReason = claimService.checkPermission(claim, player, ClaimPermission.Inventory, event);
            if (noContainerReason != null) {
                event.setCancelled(true);
                player.closeInventory();
                MessageService.sendMessage(player, TextMode.Err, noContainerReason.get());
            }
        }
    }


    private boolean isNewToServer(Player player) {
        if (player.getStatistic(Statistic.PICKUP, Material.OAK_LOG) > 0 ||
            player.getStatistic(Statistic.PICKUP, Material.SPRUCE_LOG) > 0 ||
            player.getStatistic(Statistic.PICKUP, Material.BIRCH_LOG) > 0 ||
            player.getStatistic(Statistic.PICKUP, Material.JUNGLE_LOG) > 0 ||
            player.getStatistic(Statistic.PICKUP, Material.ACACIA_LOG) > 0 ||
            player.getStatistic(Statistic.PICKUP, Material.DARK_OAK_LOG) > 0) return false;

        PlayerData playerData = playerDataRepository.getPlayerData(player.getUniqueId());
        if (claimService.getClaims(player.getUniqueId(), player.getName()).size() > 0) return false;

        return true;
    }
}

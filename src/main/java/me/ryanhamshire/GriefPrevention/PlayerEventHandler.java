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

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocListener;
import me.ryanhamshire.GriefPrevention.claims.ClaimBlockService;
import me.ryanhamshire.GriefPrevention.claims.ClaimRepository;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.claims.ResizeClaimService;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.events.ClaimInspectionEvent;
import me.ryanhamshire.GriefPrevention.events.VisualizationEvent;
import me.ryanhamshire.GriefPrevention.sessions.LogoutMessagesService;
import me.ryanhamshire.GriefPrevention.sessions.NotificationService;
import me.ryanhamshire.GriefPrevention.sessions.SessionManager;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import me.ryanhamshire.GriefPrevention.util.HelperUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
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
import org.bukkit.event.block.Action;
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
import org.bukkit.event.player.PlayerInteractEvent;
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
import org.bukkit.util.BlockIterator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
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

@IocBean
@IocListener
public class PlayerEventHandler implements Listener {
    private final DataStore dataStore;
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
    private final ClaimRepository claimRepository;
    private final SessionManager sessionManager;
    private final NotificationService notificationService;
    private final LogoutMessagesService logoutMessagesService;

    public PlayerEventHandler(DataStore dataStore, PvpProtectionService pvpProtectionService, BukkitUtils bukkitUtils, ClaimService claimService, ClaimBlockService claimBlockService, ResizeClaimService resizeClaimService, ClaimRepository claimRepository, SessionManager sessionManager, NotificationService notificationService, LogoutMessagesService logoutMessagesService) {
        this.dataStore = dataStore;
        bannedWordFinder = new WordFinder(dataStore.loadBannedWords());
        this.pvpProtectionService = pvpProtectionService;
        this.bukkitUtils = bukkitUtils;
        
        this.claimService = claimService;
        this.claimBlockService = claimBlockService;
        this.resizeClaimService = resizeClaimService;
        this.claimRepository = claimRepository;
        this.sessionManager = sessionManager;
        this.notificationService = notificationService;
        this.logoutMessagesService = logoutMessagesService;
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
        else if (this.dataStore.isSoftMuted(player.getUniqueId())) {
            String notificationMessage = "(Muted " + player.getName() + "): " + message;
            Set<Player> recipientsToKeep = new HashSet<>();
            for (Player recipient : recipients) {
                if (this.dataStore.isSoftMuted(recipient.getUniqueId())) {
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
                PlayerData playerData = dataStore.getPlayerData(player.getUniqueId());
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
                dataStore.toggleSoftMute(player.getUniqueId());
            }
        }

        //remaining messages
        else {
            //enter in abridged chat logs
            makeSocialLogEntry(player.getName(), message);

            //based on ignore lists, remove some of the audience
            if (!player.hasPermission("griefprevention.notignorable")) {
                Set<Player> recipientsToRemove = new HashSet<>();
                PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
                for (Player recipient : recipients) {
                    if (!recipient.hasPermission("griefprevention.notignorable")) {
                        if (playerData.ignoredPlayers.containsKey(recipient.getUniqueId())) {
                            recipientsToRemove.add(recipient);
                        } else {
                            PlayerData targetPlayerData = this.dataStore.getPlayerData(recipient.getUniqueId());
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
                MessageService.sendMessage(player, TextMode.Info, Messages.CreativeBasicsVideo2, 10L, DataStore.CREATIVE_VIDEO_URL);
            } else {
                MessageService.sendMessage(player, TextMode.Info, Messages.SurvivalBasicsVideo2, 10L, DataStore.SURVIVAL_VIDEO_URL);
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
        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
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

        //take action based on spam detector results
        if (result.shouldBanChatter) {
            if (ConfigLoader.config_spam_banOffenders) {
                //log entry
                GriefPrevention.AddLogEntry("Banning " + player.getName() + " for spam.", CustomLogEntryTypes.AdminActivity);

                //kick and ban
                PlayerKickBanTask task = new PlayerKickBanTask(player, ConfigLoader.config_spam_banMessage, "GriefPrevention Anti-Spam", true);
                GriefPrevention.get().getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.get(), task, 1L);
            } else {
                //log entry
                GriefPrevention.AddLogEntry("Kicking " + player.getName() + " for spam.", CustomLogEntryTypes.AdminActivity);

                //just kick
                PlayerKickBanTask task = new PlayerKickBanTask(player, "", "GriefPrevention Anti-Spam", false);
                GriefPrevention.get().getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.get(), task, 1L);
            }
        } else if (result.shouldWarnChatter) {
            //warn and log
            MessageService.sendMessage(player, TextMode.Warn, ConfigLoader.config_spam_warningMessage, 10L);
            GriefPrevention.AddLogEntry("Warned " + player.getName() + " about spam penalties.", CustomLogEntryTypes.Debug, true);
        }

        if (result.muteReason != null) {
            //mute and log
            GriefPrevention.AddLogEntry("Muted " + result.muteReason + ".");
            GriefPrevention.AddLogEntry("Muted " + player.getName() + " " + result.muteReason + ":" + message, CustomLogEntryTypes.Debug, true);

            return true;
        }

        return false;
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
            if (this.dataStore.isSoftMuted(player.getUniqueId()) && targetPlayer != null && !this.dataStore.isSoftMuted(targetPlayer.getUniqueId())) {
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
                playerData = this.dataStore.getPlayerData(player.getUniqueId());
                if (playerData.ignoredPlayers.containsKey(targetPlayer.getUniqueId()) && !targetPlayer.hasPermission("griefprevention.notignorable")) {
                    event.setCancelled(true);
                    MessageService.sendMessage(player, TextMode.Err, Messages.IsIgnoringYou);
                    return;
                }

                PlayerData targetPlayerData = this.dataStore.getPlayerData(targetPlayer.getUniqueId());
                if (targetPlayerData.ignoredPlayers.containsKey(player.getUniqueId()) && !player.hasPermission("griefprevention.notignorable")) {
                    event.setCancelled(true);
                    MessageService.sendMessage(player, TextMode.Err, Messages.IsIgnoringYou);
                    return;
                }
            }
        }

        //if in pvp, block any pvp-banned slash commands
        if (playerData == null) playerData = this.dataStore.getPlayerData(event.getPlayer().getUniqueId());

        if ((playerData.inPvpCombat() || playerData.siegeData != null) && ConfigLoader.config_pvp_blockedCommands.contains(command)) {
            event.setCancelled(true);
            MessageService.sendMessage(event.getPlayer(), TextMode.Err, Messages.CommandBannedInPvP);
            return;
        }

        //soft mute for chat slash commands
        if (category == CommandCategory.Chat && this.dataStore.isSoftMuted(player.getUniqueId())) {
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
        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
        playerData.ipAddress = event.getAddress();
    }

    //when a player spawns, conditionally apply temporary pvp protection
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        boolean inventoryEmpty = GriefPrevention.isInventoryEmpty(player);
        bukkitUtils.runTaskAsync(player, () -> {
            PlayerData playerData = dataStore.getPlayerData(player.getUniqueId());
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
        PlayerData playerData = dataStore.getPlayerData(player.getUniqueId());
        playerData.dropsAreUnlocked = false;
        playerData.receivedDropUnlockAdvertisement = false;
    }

    //when a player gets kicked...
    @EventHandler(priority = EventPriority.HIGHEST)
    void onPlayerKicked(PlayerKickEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
        playerData.wasKicked = true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerID = player.getUniqueId();
        PlayerData playerData = this.dataStore.getPlayerData(playerID);
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
            this.dataStore.savePlayerData(player.getUniqueId(), playerData);
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
        this.dataStore.clearCachedPlayerData(playerID);

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

        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());

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
        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());

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
        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());

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

        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());

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
            if (playerData == null) playerData = this.dataStore.getPlayerData(player.getUniqueId());
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
            GriefPrevention.get().entityEventHandler.onEntityDamage(damageEvent);
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
        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
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
            PlayerData playerData = dataStore.getPlayerData(player.getUniqueId());
            Claim claim = claimRepository.getClaimAt(entity.getLocation(), false, playerData.lastClaim);
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
                PlayerData playerData = this.dataStore.getPlayerData(ownerID);

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
            PlayerData playerData = this.dataStore.getPlayerData(event.getPlayer().getUniqueId());
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
                EquipShovelProcessingTask task = new EquipShovelProcessingTask(player, dataStore, claimService, claimBlockService);
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
        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
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

    //when a player interacts with the world
    @EventHandler(priority = EventPriority.LOWEST)
    void onPlayerInteract(PlayerInteractEvent event) {
        //not interested in left-click-on-air actions
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR) return;

        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock(); //null returned here means interacting with air

        Material clickedBlockType = null;
        if (clickedBlock != null) {
            clickedBlockType = clickedBlock.getType();
        } else {
            clickedBlockType = Material.AIR;
        }

        PlayerData playerData = null;

        //Turtle eggs
        if (action == Action.PHYSICAL) {
            if (clickedBlockType != Material.TURTLE_EGG)
                return;
            playerData = this.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = this.claimService.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
            if (claim != null) {
                playerData.lastClaim = claim;

                Supplier<String> noAccessReason = claimService.checkPermission(claim, player, ClaimPermission.Build, event);
                if (noAccessReason != null) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }

        //don't care about left-clicking on most blocks, this is probably a break action
        if (action == Action.LEFT_CLICK_BLOCK && clickedBlock != null) {
            if (clickedBlock.getY() < clickedBlock.getWorld().getMaxHeight() - 1 || event.getBlockFace() != BlockFace.UP) {
                Block adjacentBlock = clickedBlock.getRelative(event.getBlockFace());
                byte lightLevel = adjacentBlock.getLightFromBlocks();
                if (lightLevel == 15 && adjacentBlock.getType() == Material.FIRE) {
                    if (playerData == null) playerData = this.dataStore.getPlayerData(player.getUniqueId());
                    Claim claim = this.claimService.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
                    if (claim != null) {
                        playerData.lastClaim = claim;

                        Supplier<String> noBuildReason = claimService.checkPermission(claim, player, ClaimPermission.Build, event);
                        if (noBuildReason != null) {
                            event.setCancelled(true);
                            MessageService.sendMessage(player, TextMode.Err, noBuildReason.get());
                            player.sendBlockChange(adjacentBlock.getLocation(), adjacentBlock.getType(), adjacentBlock.getData());
                            return;
                        }
                    }
                }
            }

            //exception for blocks on a specific watch list
            if (!this.onLeftClickWatchList(clickedBlockType)) {
                return;
            }
        }

        //apply rules for containers and crafting blocks
        if (clickedBlock != null && ConfigLoader.config_claims_preventTheft && (
            event.getAction() == Action.RIGHT_CLICK_BLOCK && (
                (this.isInventoryHolder(clickedBlock) && clickedBlock.getType() != Material.LECTERN) ||
                    clickedBlockType == Material.ANVIL ||
                    clickedBlockType == Material.BEACON ||
                    clickedBlockType == Material.BEE_NEST ||
                    clickedBlockType == Material.BEEHIVE ||
                    clickedBlockType == Material.BELL ||
                    clickedBlockType == Material.CAKE ||
                    clickedBlockType == Material.CARTOGRAPHY_TABLE ||
                    clickedBlockType == Material.CAULDRON ||
                    clickedBlockType == Material.CAVE_VINES ||
                    clickedBlockType == Material.CAVE_VINES_PLANT ||
                    clickedBlockType == Material.CHIPPED_ANVIL ||
                    clickedBlockType == Material.DAMAGED_ANVIL ||
                    clickedBlockType == Material.GRINDSTONE ||
                    clickedBlockType == Material.JUKEBOX ||
                    clickedBlockType == Material.LOOM ||
                    clickedBlockType == Material.PUMPKIN ||
                    clickedBlockType == Material.RESPAWN_ANCHOR ||
                    clickedBlockType == Material.ROOTED_DIRT ||
                    clickedBlockType == Material.STONECUTTER ||
                    clickedBlockType == Material.SWEET_BERRY_BUSH ||
                    Tag.CANDLES.isTagged(clickedBlockType) ||
                    Tag.CANDLE_CAKES.isTagged(clickedBlockType)
            ))) {
            if (playerData == null) playerData = this.dataStore.getPlayerData(player.getUniqueId());

            //block container use while under siege, so players can't hide items from attackers
            if (playerData.siegeData != null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.SiegeNoContainers);
                event.setCancelled(true);
                return;
            }

            //block container use during pvp combat, same reason
            if (playerData.inPvpCombat()) {
                MessageService.sendMessage(player, TextMode.Err, Messages.PvPNoContainers);
                event.setCancelled(true);
                return;
            }

            //otherwise check permissions for the claim the player is in
            Claim claim = this.claimService.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
            if (claim != null) {
                playerData.lastClaim = claim;

                Supplier<String> noContainersReason = claimService.checkPermission(claim, player, ClaimPermission.Inventory, event);
                if (noContainersReason != null) {
                    event.setCancelled(true);
                    MessageService.sendMessage(player, TextMode.Err, noContainersReason.get());
                    return;
                }
            }

            //if the event hasn't been cancelled, then the player is allowed to use the container
            //so drop any pvp protection
            if (playerData.pvpImmune) {
                playerData.pvpImmune = false;
                MessageService.sendMessage(player, TextMode.Warn, Messages.PvPImmunityEnd);
            }
        }

        //otherwise apply rules for doors and beds, if configured that way
        else if (clickedBlock != null &&

            (ConfigLoader.config_claims_lockWoodenDoors && Tag.WOODEN_DOORS.isTagged(clickedBlockType) ||

                ConfigLoader.config_claims_preventButtonsSwitches && Tag.BEDS.isTagged(clickedBlockType) ||

                ConfigLoader.config_claims_lockTrapDoors && Tag.WOODEN_TRAPDOORS.isTagged(clickedBlockType) ||

                ConfigLoader.config_claims_lecternReadingRequiresAccessTrust && clickedBlockType == Material.LECTERN ||

                ConfigLoader.config_claims_lockFenceGates && Tag.FENCE_GATES.isTagged(clickedBlockType))) {
            if (playerData == null) playerData = this.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = this.claimService.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
            if (claim != null) {
                playerData.lastClaim = claim;

                Supplier<String> noAccessReason = claimService.checkPermission(claim, player, ClaimPermission.Access, event);
                if (noAccessReason != null) {
                    event.setCancelled(true);
                    MessageService.sendMessage(player, TextMode.Err, noAccessReason.get());
                    return;
                }
            }
        }

        //otherwise apply rules for buttons and switches
        else if (clickedBlock != null && ConfigLoader.config_claims_preventButtonsSwitches && (Tag.BUTTONS.isTagged(clickedBlockType) || clickedBlockType == Material.LEVER)) {
            if (playerData == null) playerData = this.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = this.claimService.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
            if (claim != null) {
                playerData.lastClaim = claim;

                Supplier<String> noAccessReason = claimService.checkPermission(claim, player, ClaimPermission.Access, event);
                if (noAccessReason != null) {
                    event.setCancelled(true);
                    MessageService.sendMessage(player, TextMode.Err, noAccessReason.get());
                    return;
                }
            }
        }

        //otherwise apply rule for cake
        else if (clickedBlock != null && ConfigLoader.config_claims_preventTheft && (clickedBlockType == Material.CAKE || Tag.CANDLE_CAKES.isTagged(clickedBlockType))) {
            if (playerData == null) playerData = this.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = this.claimService.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
            if (claim != null) {
                playerData.lastClaim = claim;

                Supplier<String> noContainerReason = claimService.checkPermission(claim, player, ClaimPermission.Access, event);
                if (noContainerReason != null) {
                    event.setCancelled(true);
                    MessageService.sendMessage(player, TextMode.Err, noContainerReason.get());
                    return;
                }
            }
        }

        //apply rule for note blocks and repeaters and daylight sensors //RoboMWM: Include flower pots
        else if (clickedBlock != null &&
            (
                clickedBlockType == Material.NOTE_BLOCK ||
                    clickedBlockType == Material.REPEATER ||
                    clickedBlockType == Material.DRAGON_EGG ||
                    clickedBlockType == Material.DAYLIGHT_DETECTOR ||
                    clickedBlockType == Material.COMPARATOR ||
                    clickedBlockType == Material.REDSTONE_WIRE ||
                    Tag.FLOWER_POTS.isTagged(clickedBlockType)
            )) {
            if (playerData == null) playerData = this.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = this.claimService.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
            if (claim != null) {
                Supplier<String> noBuildReason = claimService.checkPermission(claim, player, ClaimPermission.Build, event);
                if (noBuildReason != null) {
                    event.setCancelled(true);
                    MessageService.sendMessage(player, TextMode.Err, noBuildReason.get());
                    return;
                }
            }
        }

        //otherwise handle right click (shovel, string, bonemeal) //RoboMWM: flint and steel
        else {
            //ignore all actions except right-click on a block or in the air
            if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

            //what's the player holding?
            EquipmentSlot hand = event.getHand();
            ItemStack itemInHand = GriefPrevention.get().getItemInHand(player, hand);
            Material materialInHand = itemInHand.getType();

            Set<Material> spawn_eggs = new HashSet<>();
            Set<Material> dyes = new HashSet<>();

            for (Material material : Material.values()) {
                if (material.isLegacy()) continue;
                if (material.name().endsWith("_SPAWN_EGG"))
                    spawn_eggs.add(material);
                else if (material.name().endsWith("_DYE"))
                    dyes.add(material);
            }

            //if it's bonemeal, armor stand, spawn egg, etc - check for build permission //RoboMWM: also check flint and steel to stop TNT ignition
            //add glowing ink sac and ink sac, due to their usage on signs
            if (clickedBlock != null && (materialInHand == Material.BONE_MEAL
                || materialInHand == Material.ARMOR_STAND
                || (spawn_eggs.contains(materialInHand) && ConfigLoader.config_claims_preventGlobalMonsterEggs)
                || materialInHand == Material.END_CRYSTAL
                || materialInHand == Material.FLINT_AND_STEEL
                || materialInHand == Material.INK_SAC
                || materialInHand == Material.GLOW_INK_SAC
                || dyes.contains(materialInHand))) {
                String noBuildReason = claimService.allowBuild(player, clickedBlock
                            .getLocation(),
                        clickedBlockType);
                if (noBuildReason != null) {
                    MessageService.sendMessage(player, TextMode.Err, noBuildReason);
                    event.setCancelled(true);
                }

                return;
            } else if (clickedBlock != null && Tag.ITEMS_BOATS.isTagged(materialInHand)) {
                if (playerData == null) playerData = this.dataStore.getPlayerData(player.getUniqueId());
                Claim claim = this.claimService.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
                if (claim != null) {
                    Supplier<String> reason = claimService.checkPermission(claim, player, ClaimPermission.Inventory, event);
                    if (reason != null) {
                        MessageService.sendMessage(player, TextMode.Err, reason.get());
                        event.setCancelled(true);
                    }
                }

                return;
            }

            //survival world minecart placement requires container trust, which is the permission required to remove the minecart later
            else if (clickedBlock != null &&
                (materialInHand == Material.MINECART ||
                    materialInHand == Material.FURNACE_MINECART ||
                    materialInHand == Material.CHEST_MINECART ||
                    materialInHand == Material.TNT_MINECART ||
                    materialInHand == Material.HOPPER_MINECART) &&
                !ConfigLoader.creativeRulesApply(clickedBlock.getLocation())) {
                if (playerData == null) playerData = this.dataStore.getPlayerData(player.getUniqueId());
                Claim claim = this.claimService.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
                if (claim != null) {
                    Supplier<String> reason = claimService.checkPermission(claim, player, ClaimPermission.Inventory, event);
                    if (reason != null) {
                        MessageService.sendMessage(player, TextMode.Err, reason.get());
                        event.setCancelled(true);
                    }
                }

                return;
            }

            //if it's a spawn egg, minecart, or boat, and this is a creative world, apply special rules
            else if (clickedBlock != null && (materialInHand == Material.MINECART ||
                materialInHand == Material.FURNACE_MINECART ||
                materialInHand == Material.CHEST_MINECART ||
                materialInHand == Material.TNT_MINECART ||
                materialInHand == Material.ARMOR_STAND ||
                materialInHand == Material.ITEM_FRAME ||
                materialInHand == Material.GLOW_ITEM_FRAME ||
                spawn_eggs.contains(materialInHand) ||
                materialInHand == Material.INFESTED_STONE ||
                materialInHand == Material.INFESTED_COBBLESTONE ||
                materialInHand == Material.INFESTED_STONE_BRICKS ||
                materialInHand == Material.INFESTED_MOSSY_STONE_BRICKS ||
                materialInHand == Material.INFESTED_CRACKED_STONE_BRICKS ||
                materialInHand == Material.INFESTED_CHISELED_STONE_BRICKS ||
                materialInHand == Material.HOPPER_MINECART) &&
                ConfigLoader.creativeRulesApply(clickedBlock.getLocation())) {
                //player needs build permission at this location
                String noBuildReason = claimService.allowBuild(player, clickedBlock.getLocation(), Material.MINECART);
                if (noBuildReason != null) {
                    MessageService.sendMessage(player, TextMode.Err, noBuildReason);
                    event.setCancelled(true);
                    return;
                }

                //enforce limit on total number of entities in this claim
                if (playerData == null) playerData = this.dataStore.getPlayerData(player.getUniqueId());
                Claim claim = this.claimService.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
                if (claim == null) return;

                String noEntitiesReason = claim.allowMoreEntities(false);
                if (noEntitiesReason != null) {
                    MessageService.sendMessage(player, TextMode.Err, noEntitiesReason);
                    event.setCancelled(true);
                    return;
                }

                return;
            }

            //if he's investigating a claim
            else if (materialInHand == ConfigLoader.config_claims_investigationTool && hand == EquipmentSlot.HAND) {
                //if claims are disabled in this world, do nothing
                if (!GriefPrevention.get().claimsEnabledForWorld(player.getWorld())) return;

                //if holding shift (sneaking), show all claims in area
                if (player.isSneaking() && player.hasPermission("griefprevention.visualizenearbyclaims")) {
                    //find nearby claims
                    Set<Claim> claims = this.claimService.getNearbyClaims(player.getLocation());

                    // alert plugins of a claim inspection, return if cancelled
                    ClaimInspectionEvent inspectionEvent = new ClaimInspectionEvent(player, null, claims, true);
                    Bukkit.getPluginManager().callEvent(inspectionEvent);
                    if (inspectionEvent.isCancelled()) return;

                    //visualize boundaries
                    Visualization visualization = Visualization.fromClaims(claims, player.getEyeLocation().getBlockY(), VisualizationType.Claim, player.getLocation());

                    // alert plugins of a visualization
                    Bukkit.getPluginManager().callEvent(new VisualizationEvent(player, visualization, claims, true));

                    Visualization.Apply(player, playerData, visualization);

                    MessageService.sendMessage(player, TextMode.Info, Messages.ShowNearbyClaims, String.valueOf(claims.size()));

                    return;
                }

                //FEATURE: shovel and stick can be used from a distance away
                if (action == Action.RIGHT_CLICK_AIR) {
                    //try to find a far away non-air block along line of sight
                    clickedBlock = getTargetBlock(player, 100);
                    clickedBlockType = clickedBlock.getType();
                }

                //if no block, stop here
                if (clickedBlock == null) {
                    return;
                }

                //air indicates too far away
                if (clickedBlockType == Material.AIR) {
                    MessageService.sendMessage(player, TextMode.Err, Messages.TooFarAway);

                    // alert plugins of a visualization
                    Bukkit.getPluginManager().callEvent(new VisualizationEvent(player, null, Collections.<Claim>emptySet()));

                    Visualization.Revert(player, playerData);
                    return;
                }

                if (playerData == null) playerData = this.dataStore.getPlayerData(player.getUniqueId());
                Claim claim = this.claimService.getClaimAt(clickedBlock.getLocation(), false /*ignore height*/, playerData.lastClaim);

                //no claim case
                if (claim == null) {
                    // alert plugins of a claim inspection, return if cancelled
                    ClaimInspectionEvent inspectionEvent = new ClaimInspectionEvent(player, clickedBlock, null);
                    Bukkit.getPluginManager().callEvent(inspectionEvent);
                    if (inspectionEvent.isCancelled()) return;

                    MessageService.sendMessage(player, TextMode.Info, Messages.BlockNotClaimed);

                    // alert plugins of a visualization
                    Bukkit.getPluginManager().callEvent(new VisualizationEvent(player, null, Collections.<Claim>emptySet()));

                    Visualization.Revert(player, playerData);
                }

                //claim case
                else {
                    // alert plugins of a claim inspection, return if cancelled
                    ClaimInspectionEvent inspectionEvent = new ClaimInspectionEvent(player, clickedBlock, claim);
                    Bukkit.getPluginManager().callEvent(inspectionEvent);
                    if (inspectionEvent.isCancelled()) return;

                    playerData.lastClaim = claim;
                    MessageService.sendMessage(player, TextMode.Info, Messages.BlockClaimed, claim.getOwnerName());

                    //visualize boundary
                    Visualization visualization = Visualization.FromClaim(claim, player.getEyeLocation().getBlockY(), VisualizationType.Claim, player.getLocation());

                    // alert plugins of a visualization
                    Bukkit.getPluginManager().callEvent(new VisualizationEvent(player, visualization, claim));

                    Visualization.Apply(player, playerData, visualization);

                    if (player.hasPermission("griefprevention.seeclaimsize")) {
                        MessageService.sendMessage(player, TextMode.Info, "  " + claim.getWidth() + "x" + claim.getHeight() + "=" + claim.getArea());
                    }

                    //if permission, tell about the player's offline time
                    if (!claim.isAdminClaim() && (player.hasPermission("griefprevention.deleteclaims") || player.hasPermission("griefprevention.seeinactivity"))) {
                        if (claim.parent != null) {
                            claim = claim.parent;
                        }
                        Date lastLogin = new Date(Bukkit.getOfflinePlayer(claim.ownerID).getLastPlayed());
                        Date now = new Date();
                        long daysElapsed = (now.getTime() - lastLogin.getTime()) / (1000 * 60 * 60 * 24);

                        MessageService.sendMessage(player, TextMode.Info, Messages.PlayerOfflineTime, String.valueOf(daysElapsed));

                        //drop the data we just loaded, if the player isn't online
                        if (GriefPrevention.get().getServer().getPlayer(claim.ownerID) == null)
                            this.dataStore.clearCachedPlayerData(claim.ownerID);
                    }
                }

                return;
            }

            //if it's a golden shovel
            else if (materialInHand != ConfigLoader.config_claims_modificationTool || hand != EquipmentSlot.HAND)
                return;

            event.setCancelled(true);  //GriefPrevention exclusively reserves this tool  (e.g. no grass path creation for golden shovel)

            //disable golden shovel while under siege
            if (playerData == null) playerData = this.dataStore.getPlayerData(player.getUniqueId());
            if (playerData.siegeData != null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.SiegeNoShovel);
                event.setCancelled(true);
                return;
            }

            //FEATURE: shovel and stick can be used from a distance away
            if (action == Action.RIGHT_CLICK_AIR) {
                //try to find a far away non-air block along line of sight
                clickedBlock = getTargetBlock(player, 100);
                clickedBlockType = clickedBlock.getType();
            }

            //if no block, stop here
            if (clickedBlock == null) {
                return;
            }

            //can't use the shovel from too far away
            if (clickedBlockType == Material.AIR) {
                MessageService.sendMessage(player, TextMode.Err, Messages.TooFarAway);
                return;
            }

            //if the player is in restore nature mode, do only that
            UUID playerID = player.getUniqueId();
            playerData = this.dataStore.getPlayerData(player.getUniqueId());
            if (playerData.shovelMode == ShovelMode.RestoreNature || playerData.shovelMode == ShovelMode.RestoreNatureAggressive) {
                //if the clicked block is in a claim, visualize that claim and deliver an error message
                Claim claim = this.claimService.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
                if (claim != null) {
                    MessageService.sendMessage(player, TextMode.Err, Messages.BlockClaimed, claim.getOwnerName());
                    Visualization visualization = Visualization.FromClaim(claim, clickedBlock.getY(), VisualizationType.ErrorClaim, player.getLocation());

                    // alert plugins of a visualization
                    Bukkit.getPluginManager().callEvent(new VisualizationEvent(player, visualization, claim));

                    Visualization.Apply(player, playerData, visualization);

                    return;
                }

                //figure out which chunk to repair
                Chunk chunk = player.getWorld().getChunkAt(clickedBlock.getLocation());
                //start the repair process

                //set boundaries for processing
                int miny = clickedBlock.getY();

                //if not in aggressive mode, extend the selection down to a little below sea level
                if (!(playerData.shovelMode == ShovelMode.RestoreNatureAggressive)) {
                    if (miny > GriefPrevention.get().getSeaLevel(chunk.getWorld()) - 10) {
                        miny = GriefPrevention.get().getSeaLevel(chunk.getWorld()) - 10;
                    }
                }

                claimService.restoreChunk(chunk, miny, playerData.shovelMode == ShovelMode.RestoreNatureAggressive, 0, player);

                return;
            }

            //if in restore nature fill mode
            if (playerData.shovelMode == ShovelMode.RestoreNatureFill) {
                ArrayList<Material> allowedFillBlocks = new ArrayList<>();
                Environment environment = clickedBlock.getWorld().getEnvironment();
                if (environment == Environment.NETHER) {
                    allowedFillBlocks.add(Material.NETHERRACK);
                } else if (environment == Environment.THE_END) {
                    allowedFillBlocks.add(Material.END_STONE);
                } else {
                    allowedFillBlocks.add(Material.GRASS);
                    allowedFillBlocks.add(Material.DIRT);
                    allowedFillBlocks.add(Material.STONE);
                    allowedFillBlocks.add(Material.SAND);
                    allowedFillBlocks.add(Material.SANDSTONE);
                    allowedFillBlocks.add(Material.ICE);
                }

                Block centerBlock = clickedBlock;

                int maxHeight = centerBlock.getY();
                int minx = centerBlock.getX() - playerData.fillRadius;
                int maxx = centerBlock.getX() + playerData.fillRadius;
                int minz = centerBlock.getZ() - playerData.fillRadius;
                int maxz = centerBlock.getZ() + playerData.fillRadius;
                int minHeight = maxHeight - 10;
                minHeight = Math.max(minHeight, clickedBlock.getWorld().getMinHeight());

                Claim cachedClaim = null;
                for (int x = minx; x <= maxx; x++) {
                    for (int z = minz; z <= maxz; z++) {
                        //circular brush
                        Location location = new Location(centerBlock.getWorld(), x, centerBlock.getY(), z);
                        if (location.distance(centerBlock.getLocation()) > playerData.fillRadius) continue;

                        //default fill block is initially the first from the allowed fill blocks list above
                        Material defaultFiller = allowedFillBlocks.get(0);

                        //prefer to use the block the player clicked on, if it's an acceptable fill block
                        if (allowedFillBlocks.contains(centerBlock.getType())) {
                            defaultFiller = centerBlock.getType();
                        }

                        //if the player clicks on water, try to sink through the water to find something underneath that's useful for a filler
                        else if (centerBlock.getType() == Material.WATER) {
                            Block block = centerBlock.getWorld().getBlockAt(centerBlock.getLocation());
                            while (!allowedFillBlocks.contains(block.getType()) && block.getY() > centerBlock.getY() - 10) {
                                block = block.getRelative(BlockFace.DOWN);
                            }
                            if (allowedFillBlocks.contains(block.getType())) {
                                defaultFiller = block.getType();
                            }
                        }

                        //fill bottom to top
                        for (int y = minHeight; y <= maxHeight; y++) {
                            Block block = centerBlock.getWorld().getBlockAt(x, y, z);

                            //respect claims
                            Claim claim = this.claimService.getClaimAt(block.getLocation(), false, cachedClaim);
                            if (claim != null) {
                                cachedClaim = claim;
                                break;
                            }

                            //only replace air, spilling water, snow, long grass
                            if (block.getType() == Material.AIR || block.getType() == Material.SNOW || (block.getType() == Material.WATER && ((Levelled) block.getBlockData()).getLevel() != 0) || block.getType() == Material.GRASS) {
                                //if the top level, always use the default filler picked above
                                if (y == maxHeight) {
                                    block.setType(defaultFiller);
                                }

                                //otherwise look to neighbors for an appropriate fill block
                                else {
                                    Block eastBlock = block.getRelative(BlockFace.EAST);
                                    Block westBlock = block.getRelative(BlockFace.WEST);
                                    Block northBlock = block.getRelative(BlockFace.NORTH);
                                    Block southBlock = block.getRelative(BlockFace.SOUTH);

                                    //first, check lateral neighbors (ideally, want to keep natural layers)
                                    if (allowedFillBlocks.contains(eastBlock.getType())) {
                                        block.setType(eastBlock.getType());
                                    } else if (allowedFillBlocks.contains(westBlock.getType())) {
                                        block.setType(westBlock.getType());
                                    } else if (allowedFillBlocks.contains(northBlock.getType())) {
                                        block.setType(northBlock.getType());
                                    } else if (allowedFillBlocks.contains(southBlock.getType())) {
                                        block.setType(southBlock.getType());
                                    }

                                    //if all else fails, use the default filler selected above
                                    else {
                                        block.setType(defaultFiller);
                                    }
                                }
                            }
                        }
                    }
                }

                return;
            }

            //if the player doesn't have claims permission, don't do anything
            if (!player.hasPermission("griefprevention.createclaims")) {
                MessageService.sendMessage(player, TextMode.Err, Messages.NoCreateClaimPermission);
                return;
            }

            //if he's resizing a claim and that claim hasn't been deleted since he started resizing it
            if (playerData.claimResizing != null && playerData.claimResizing.inDataStore) {
                if (clickedBlock.getLocation().equals(playerData.lastShovelLocation)) return;

                //figure out what the coords of his new claim would be
                int newx1, newx2, newz1, newz2, newy1, newy2;
                if (playerData.lastShovelLocation.getBlockX() == playerData.claimResizing.getLesserBoundaryCorner().getBlockX()) {
                    newx1 = clickedBlock.getX();
                    newx2 = playerData.claimResizing.getGreaterBoundaryCorner().getBlockX();
                } else {
                    newx1 = playerData.claimResizing.getLesserBoundaryCorner().getBlockX();
                    newx2 = clickedBlock.getX();
                }

                if (playerData.lastShovelLocation.getBlockZ() == playerData.claimResizing.getLesserBoundaryCorner().getBlockZ()) {
                    newz1 = clickedBlock.getZ();
                    newz2 = playerData.claimResizing.getGreaterBoundaryCorner().getBlockZ();
                } else {
                    newz1 = playerData.claimResizing.getLesserBoundaryCorner().getBlockZ();
                    newz2 = clickedBlock.getZ();
                }

                newy1 = playerData.claimResizing.getLesserBoundaryCorner().getBlockY();
                newy2 = clickedBlock.getY() - ConfigLoader.config_claims_claimsExtendIntoGroundDistance;

                this.resizeClaimService.resizeClaimWithChecks(player, playerData, newx1, newx2, newy1, newy2, newz1, newz2);

                return;
            }

            //otherwise, since not currently resizing a claim, must be starting a resize, creating a new claim, or creating a subdivision
            Claim claim = this.claimService.getClaimAt(clickedBlock.getLocation(), true /*ignore height*/, playerData.lastClaim);

            //if within an existing claim, he's not creating a new one
            if (claim != null) {
                //if the player has permission to edit the claim or subdivision
                Supplier<String> noEditReason = claimService.checkPermission(claim, player, ClaimPermission.Edit, event, () -> MessageService.getMessage(Messages.CreateClaimFailOverlapOtherPlayer, claim.getOwnerName()));
                if (noEditReason == null) {
                    //if he clicked on a corner, start resizing it
                    if ((clickedBlock.getX() == claim.getLesserBoundaryCorner().getBlockX() || clickedBlock.getX() == claim.getGreaterBoundaryCorner().getBlockX()) && (clickedBlock.getZ() == claim.getLesserBoundaryCorner().getBlockZ() || clickedBlock.getZ() == claim.getGreaterBoundaryCorner().getBlockZ())) {
                        playerData.claimResizing = claim;
                        playerData.lastShovelLocation = clickedBlock.getLocation();
                        MessageService.sendMessage(player, TextMode.Instr, Messages.ResizeStart);
                    }

                    //if he didn't click on a corner and is in subdivision mode, he's creating a new subdivision
                    else if (playerData.shovelMode == ShovelMode.Subdivide) {
                        //if it's the first click, he's trying to start a new subdivision
                        if (playerData.lastShovelLocation == null) {
                            //if the clicked claim was a subdivision, tell him he can't start a new subdivision here
                            if (claim.parent != null) {
                                MessageService.sendMessage(player, TextMode.Err, Messages.ResizeFailOverlapSubdivision);
                            }

                            //otherwise start a new subdivision
                            else {
                                MessageService.sendMessage(player, TextMode.Instr, Messages.SubdivisionStart);
                                playerData.lastShovelLocation = clickedBlock.getLocation();
                                playerData.claimSubdividing = claim;
                            }
                        }

                        //otherwise, he's trying to finish creating a subdivision by setting the other boundary corner
                        else {
                            //if last shovel location was in a different world, assume the player is starting the create-claim workflow over
                            if (!playerData.lastShovelLocation.getWorld().equals(clickedBlock.getWorld())) {
                                playerData.lastShovelLocation = null;
                                this.onPlayerInteract(event);
                                return;
                            }

                            //try to create a new claim (will return null if this subdivision overlaps another)
                            CreateClaimResult result = this.claimService.createClaim(
                                player.getWorld(),
                                playerData.lastShovelLocation.getBlockX(), clickedBlock.getX(),
                                playerData.lastShovelLocation.getBlockY() - ConfigLoader.config_claims_claimsExtendIntoGroundDistance, clickedBlock.getY() - ConfigLoader.config_claims_claimsExtendIntoGroundDistance,
                                playerData.lastShovelLocation.getBlockZ(), clickedBlock.getZ(),
                                null,  //owner is not used for subdivisions
                                playerData.claimSubdividing,
                                null, player, false);

                            //if it didn't succeed, tell the player why
                            if (!result.succeeded) {
                                MessageService.sendMessage(player, TextMode.Err, Messages.CreateSubdivisionOverlap);

                                Visualization visualization = Visualization.FromClaim(result.claim, clickedBlock.getY(), VisualizationType.ErrorClaim, player.getLocation());

                                // alert plugins of a visualization
                                Bukkit.getPluginManager().callEvent(new VisualizationEvent(player, visualization, result.claim));

                                Visualization.Apply(player, playerData, visualization);

                                return;
                            }

                            //otherwise, advise him on the /trust command and show him his new subdivision
                            else {
                                MessageService.sendMessage(player, TextMode.Success, Messages.SubdivisionSuccess);
                                Visualization visualization = Visualization.FromClaim(result.claim, clickedBlock.getY(), VisualizationType.Claim, player.getLocation());

                                // alert plugins of a visualization
                                Bukkit.getPluginManager().callEvent(new VisualizationEvent(player, visualization, result.claim));

                                Visualization.Apply(player, playerData, visualization);
                                playerData.lastShovelLocation = null;
                                playerData.claimSubdividing = null;
                            }
                        }
                    }

                    //otherwise tell him he can't create a claim here, and show him the existing claim
                    //also advise him to consider /abandonclaim or resizing the existing claim
                    else {
                        MessageService.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlap);
                        Visualization visualization = Visualization.FromClaim(claim, clickedBlock.getY(), VisualizationType.Claim, player.getLocation());

                        // alert plugins of a visualization
                        Bukkit.getPluginManager().callEvent(new VisualizationEvent(player, visualization, claim));

                        Visualization.Apply(player, playerData, visualization);
                    }
                }

                //otherwise tell the player he can't claim here because it's someone else's claim, and show him the claim
                else {
                    MessageService.sendMessage(player, TextMode.Err, noEditReason.get());
                    Visualization visualization = Visualization.FromClaim(claim, clickedBlock.getY(), VisualizationType.ErrorClaim, player.getLocation());

                    // alert plugins of a visualization
                    Bukkit.getPluginManager().callEvent(new VisualizationEvent(player, visualization, claim));

                    Visualization.Apply(player, playerData, visualization);
                }

                return;
            }

            //otherwise, the player isn't in an existing claim!

            //if he hasn't already start a claim with a previous shovel action
            Location lastShovelLocation = playerData.lastShovelLocation;
            if (lastShovelLocation == null) {
                //if claims are not enabled in this world and it's not an administrative claim, display an error message and stop
                if (!GriefPrevention.get().claimsEnabledForWorld(player.getWorld())) {
                    MessageService.sendMessage(player, TextMode.Err, Messages.ClaimsDisabledWorld);
                    return;
                }

                //if he's at the claim count per player limit already and doesn't have permission to bypass, display an error message
                if (ConfigLoader.config_claims_maxClaimsPerPlayer > 0 &&
                    !player.hasPermission("griefprevention.overrideclaimcountlimit") &&
                    claimService.getClaims(player.getUniqueId(), player.getName()).size() >= ConfigLoader.config_claims_maxClaimsPerPlayer) {
                    MessageService.sendMessage(player, TextMode.Err, Messages.ClaimCreationFailedOverClaimCountLimit);
                    return;
                }

                //remember it, and start him on the new claim
                playerData.lastShovelLocation = clickedBlock.getLocation();
                MessageService.sendMessage(player, TextMode.Instr, Messages.ClaimStart);

                //show him where he's working
                Claim newClaim = new Claim(clickedBlock.getLocation(), clickedBlock.getLocation(), null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), null);
                Visualization visualization = Visualization.FromClaim(newClaim, clickedBlock.getY(), VisualizationType.RestoreNature, player.getLocation());

                // alert plugins of a visualization
                Bukkit.getPluginManager().callEvent(new VisualizationEvent(player, visualization, newClaim));

                Visualization.Apply(player, playerData, visualization);
            }

            //otherwise, he's trying to finish creating a claim by setting the other boundary corner
            else {
                //if last shovel location was in a different world, assume the player is starting the create-claim workflow over
                if (!lastShovelLocation.getWorld().equals(clickedBlock.getWorld())) {
                    playerData.lastShovelLocation = null;
                    this.onPlayerInteract(event);
                    return;
                }

                //apply pvp rule
                if (playerData.inPvpCombat()) {
                    MessageService.sendMessage(player, TextMode.Err, Messages.NoClaimDuringPvP);
                    return;
                }

                //apply minimum claim dimensions rule
                int newClaimWidth = Math.abs(playerData.lastShovelLocation.getBlockX() - clickedBlock.getX()) + 1;
                int newClaimHeight = Math.abs(playerData.lastShovelLocation.getBlockZ() - clickedBlock.getZ()) + 1;

                if (playerData.shovelMode != ShovelMode.Admin) {
                    if (newClaimWidth < ConfigLoader.config_claims_minWidth || newClaimHeight < ConfigLoader.config_claims_minWidth) {
                        //this IF block is a workaround for craftbukkit bug which fires two events for one interaction
                        if (newClaimWidth != 1 && newClaimHeight != 1) {
                            MessageService.sendMessage(player, TextMode.Err, Messages.NewClaimTooNarrow, String.valueOf(ConfigLoader.config_claims_minWidth));
                        }
                        return;
                    }

                    int newArea = newClaimWidth * newClaimHeight;
                    if (newArea < ConfigLoader.config_claims_minArea) {
                        if (newArea != 1) {
                            MessageService.sendMessage(player, TextMode.Err, Messages.ResizeClaimInsufficientArea, String.valueOf(ConfigLoader.config_claims_minArea));
                        }

                        return;
                    }
                }

                //if not an administrative claim, verify the player has enough claim blocks for this new claim
                if (playerData.shovelMode != ShovelMode.Admin) {
                    int newClaimArea = newClaimWidth * newClaimHeight;
                    int remainingBlocks = claimBlockService.getRemainingClaimBlocks(playerData, claimService.getClaims(player.getUniqueId(), player.getName()));
                    if (newClaimArea > remainingBlocks) {
                        MessageService.sendMessage(player, TextMode.Err, Messages.CreateClaimInsufficientBlocks, String.valueOf(newClaimArea - remainingBlocks));
                        HelperUtil.tryAdvertiseAdminAlternatives(player);
                        return;
                    }
                } else {
                    playerID = null;
                }

                //try to create a new claim
                CreateClaimResult result = this.claimService.createClaim(
                    player.getWorld(),
                    lastShovelLocation.getBlockX(), clickedBlock.getX(),
                    lastShovelLocation.getBlockY() - ConfigLoader.config_claims_claimsExtendIntoGroundDistance, clickedBlock.getY() - ConfigLoader.config_claims_claimsExtendIntoGroundDistance,
                    lastShovelLocation.getBlockZ(), clickedBlock.getZ(),
                    playerID,
                    null, null,
                    player,
                    false);

                //if it didn't succeed, tell the player why
                if (!result.succeeded) {
                    if (result.claim != null) {
                        MessageService.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapShort);

                        Visualization visualization = Visualization.FromClaim(result.claim, clickedBlock.getY(), VisualizationType.ErrorClaim, player.getLocation());

                        // alert plugins of a visualization
                        Bukkit.getPluginManager().callEvent(new VisualizationEvent(player, visualization, result.claim));

                        Visualization.Apply(player, playerData, visualization);
                    } else {
                        MessageService.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapRegion);
                    }

                    return;
                }

                //otherwise, advise him on the /trust command and show him his new claim
                else {
                    MessageService.sendMessage(player, TextMode.Success, Messages.CreateClaimSuccess);
                    Visualization visualization = Visualization.FromClaim(result.claim, clickedBlock.getY(), VisualizationType.Claim, player.getLocation());

                    // alert plugins of a visualization
                    Bukkit.getPluginManager().callEvent(new VisualizationEvent(player, visualization, result.claim));

                    Visualization.Apply(player, playerData, visualization);
                    playerData.lastShovelLocation = null;

                    //if it's a big claim, tell the player about subdivisions
                    if (!player.hasPermission("griefprevention.adminclaims") && result.claim.getArea() >= 1000) {
                        MessageService.sendMessage(player, TextMode.Info, Messages.BecomeMayor, 200L);
                        MessageService.sendMessage(player, TextMode.Instr, Messages.SubdivisionVideo2, 201L, DataStore.SUBDIVISION_VIDEO_URL);
                    }

                    autoExtendClaim(result.claim);
                }
            }
        }
    }

    // Stops an untrusted player from removing a book from a lectern
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    void onTakeBook(PlayerTakeLecternBookEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
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

    //determines whether a block type is an inventory holder.  uses a caching strategy to save cpu time
    private final ConcurrentHashMap<Material, Boolean> inventoryHolderCache = new ConcurrentHashMap<>();

    private boolean isInventoryHolder(Block clickedBlock) {

        Material cacheKey = clickedBlock.getType();
        Boolean cachedValue = this.inventoryHolderCache.get(cacheKey);
        if (cachedValue != null) {
            return cachedValue.booleanValue();
        } else {
            boolean isHolder = clickedBlock.getState() instanceof InventoryHolder;
            this.inventoryHolderCache.put(cacheKey, isHolder);
            return isHolder;
        }
    }

    private boolean onLeftClickWatchList(Material material) {
        switch (material) {
            case OAK_BUTTON:
            case SPRUCE_BUTTON:
            case BIRCH_BUTTON:
            case JUNGLE_BUTTON:
            case ACACIA_BUTTON:
            case DARK_OAK_BUTTON:
            case STONE_BUTTON:
            case LEVER:
            case REPEATER:
            case CAKE:
            case DRAGON_EGG:
                return true;
            default:
                return false;
        }
    }

    void autoExtendClaim(Claim newClaim) {
        //auto-extend it downward to cover anything already built underground
        Location lesserCorner = newClaim.getLesserBoundaryCorner();
        Location greaterCorner = newClaim.getGreaterBoundaryCorner();
        World world = lesserCorner.getWorld();
        ArrayList<ChunkSnapshot> snapshots = new ArrayList<>();
        for (int chunkx = lesserCorner.getBlockX() / 16; chunkx <= greaterCorner.getBlockX() / 16; chunkx++) {
            for (int chunkz = lesserCorner.getBlockZ() / 16; chunkz <= greaterCorner.getBlockZ() / 16; chunkz++) {
                if (world.isChunkLoaded(chunkx, chunkz)) {
                    snapshots.add(world.getChunkAt(chunkx, chunkz).getChunkSnapshot(true, true, false));
                }
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(GriefPrevention.instance, new AutoExtendClaimTask(newClaim, snapshots, world.getEnvironment(), resizeClaimService));
    }

    static Block getTargetBlock(Player player, int maxDistance) throws IllegalStateException {
        Location eye = player.getEyeLocation();
        Material eyeMaterial = eye.getBlock().getType();
        boolean passThroughWater = (eyeMaterial == Material.WATER);
        BlockIterator iterator = new BlockIterator(player.getLocation(), player.getEyeHeight(), maxDistance);
        Block result = player.getLocation().getBlock().getRelative(BlockFace.UP);
        while (iterator.hasNext()) {
            result = iterator.next();
            Material type = result.getType();
            if (type != Material.AIR &&
                (!passThroughWater || type != Material.WATER) &&
                type != Material.GRASS &&
                type != Material.SNOW) return result;
        }

        return result;
    }

    private boolean isNewToServer(Player player) {
        if (player.getStatistic(Statistic.PICKUP, Material.OAK_LOG) > 0 ||
            player.getStatistic(Statistic.PICKUP, Material.SPRUCE_LOG) > 0 ||
            player.getStatistic(Statistic.PICKUP, Material.BIRCH_LOG) > 0 ||
            player.getStatistic(Statistic.PICKUP, Material.JUNGLE_LOG) > 0 ||
            player.getStatistic(Statistic.PICKUP, Material.ACACIA_LOG) > 0 ||
            player.getStatistic(Statistic.PICKUP, Material.DARK_OAK_LOG) > 0) return false;

        PlayerData playerData = dataStore.getPlayerData(player.getUniqueId());
        if (claimService.getClaims(player.getUniqueId(), player.getName()).size() > 0) return false;

        return true;
    }
}

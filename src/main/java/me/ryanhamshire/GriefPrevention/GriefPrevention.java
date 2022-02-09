/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

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

import be.garagepoort.mcioc.TubingPlugin;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import org.bukkit.BanList;
import org.bukkit.BanList.Type;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GriefPrevention extends TubingPlugin {
    //for convenience, a reference to the instance of this plugin
    public static GriefPrevention instance;

    //for logging to the console and log file
    private static Logger log;

    // Event handlers with common functionality
    EntityEventHandler entityEventHandler;

    //this tracks item stacks expected to drop which will need protection
    public ArrayList<PendingItemProtection> pendingItemWatchList = new ArrayList<>();

    //log entry manager for GP's custom log files
    CustomLogger customLogger;

    //configuration variables, loaded/saved from a config.yml

    public static GriefPrevention get() {
        return instance;
    }

    //adds a server log entry
    public static synchronized void AddLogEntry(String entry, CustomLogEntryTypes customLogType, boolean excludeFromServerLogs) {
        if (customLogType != null && GriefPrevention.instance.customLogger != null) {
            GriefPrevention.instance.customLogger.AddEntry(entry, customLogType);
        }
        if (!excludeFromServerLogs) log.info(entry);
    }

    public static synchronized void AddLogEntry(String entry, CustomLogEntryTypes customLogType) {
        AddLogEntry(entry, customLogType, false);
    }

    public static synchronized void AddLogEntry(String entry) {
        AddLogEntry(entry, CustomLogEntryTypes.Debug);
    }

    //initializes well...   everything
    @Override
    public void enable() {
        instance = this;
        log = instance.getLogger();

        ConfigLoader.load();

        this.customLogger = new CustomLogger();

        AddLogEntry("Finished loading configuration.");

        //cache offline players
        OfflinePlayer[] offlinePlayers = this.getServer().getOfflinePlayers();
        CacheOfflinePlayerNamesThread namesThread = new CacheOfflinePlayerNamesThread(offlinePlayers, this.playerNameToIDMap);
        namesThread.setPriority(Thread.MIN_PRIORITY);
        namesThread.start();

        AddLogEntry("Boot finished.");
    }

    public enum IgnoreMode {None, StandardIgnore, AdminIgnore}


    public static String getfriendlyLocationString(Location location) {
        return location.getWorld().getName() + ": x" + location.getBlockX() + ", z" + location.getBlockZ();
    }

    //helper method to resolve a player by name
    ConcurrentHashMap<String, UUID> playerNameToIDMap = new ConcurrentHashMap<>();

    //thread to build the above cache
    private class CacheOfflinePlayerNamesThread extends Thread {
        private final OfflinePlayer[] offlinePlayers;
        private final ConcurrentHashMap<String, UUID> playerNameToIDMap;

        CacheOfflinePlayerNamesThread(OfflinePlayer[] offlinePlayers, ConcurrentHashMap<String, UUID> playerNameToIDMap) {
            this.offlinePlayers = offlinePlayers;
            this.playerNameToIDMap = playerNameToIDMap;
        }

        public void run() {
            long now = System.currentTimeMillis();
            final long millisecondsPerDay = 1000 * 60 * 60 * 24;
            for (OfflinePlayer player : offlinePlayers) {
                try {
                    UUID playerID = player.getUniqueId();
                    if (playerID == null) continue;
                    long lastSeen = player.getLastPlayed();

                    //if the player has been seen in the last 90 days, cache his name/UUID pair
                    long diff = now - lastSeen;
                    long daysDiff = diff / millisecondsPerDay;
                    if (daysDiff <= ConfigLoader.config_advanced_offlineplayer_cache_days) {
                        String playerName = player.getName();
                        if (playerName == null) continue;
                        this.playerNameToIDMap.put(playerName, playerID);
                        this.playerNameToIDMap.put(playerName.toLowerCase(), playerID);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public OfflinePlayer resolvePlayerByName(String name) {
        //try online players first
        Player targetPlayer = this.getServer().getPlayerExact(name);
        if (targetPlayer != null) return targetPlayer;

        UUID bestMatchID = null;

        //try exact match first
        bestMatchID = this.playerNameToIDMap.get(name);

        //if failed, try ignore case
        if (bestMatchID == null) {
            bestMatchID = this.playerNameToIDMap.get(name.toLowerCase());
        }
        if (bestMatchID == null) {
            return null;
        }

        return this.getServer().getOfflinePlayer(bestMatchID);
    }

    //helper method to resolve a player name from the player's UUID
    public static String lookupPlayerName(UUID playerID) {
        //parameter validation
        if (playerID == null) return "somebody";

        //check the cache
        OfflinePlayer player = GriefPrevention.instance.getServer().getOfflinePlayer(playerID);
        if (player.hasPlayedBefore() || player.isOnline()) {
            return player.getName();
        } else {
            return "someone(" + playerID.toString() + ")";
        }
    }

    //cache for player name lookups, to save searches of all offline players
    public static void cacheUUIDNamePair(UUID playerID, String playerName) {
        //store the reverse mapping
        GriefPrevention.instance.playerNameToIDMap.put(playerName, playerID);
        GriefPrevention.instance.playerNameToIDMap.put(playerName.toLowerCase(), playerID);
    }

    @Override
    public void disable() {
        //save data for any online players
        @SuppressWarnings("unchecked")
        Collection<Player> players = (Collection<Player>) this.getServer().getOnlinePlayers();
        DataStore dataStore = getIocContainer().get(DataStore.class);
        for (Player player : players) {
            UUID playerID = player.getUniqueId();
            PlayerData playerData = dataStore.getPlayerData(playerID);
            dataStore.savePlayerDataSync(playerID, playerData);
        }

        //dump any remaining unwritten log entries
        this.customLogger.WriteEntries();

        AddLogEntry("GriefPrevention disabled.");
    }

    public static boolean isInventoryEmpty(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] armorStacks = inventory.getArmorContents();

        //check armor slots, stop if any items are found
        for (ItemStack armorStack : armorStacks) {
            if (!(armorStack == null || armorStack.getType() == Material.AIR)) return false;
        }

        //check other slots, stop if any items are found
        ItemStack[] generalStacks = inventory.getContents();
        for (ItemStack generalStack : generalStacks) {
            if (!(generalStack == null || generalStack.getType() == Material.AIR)) return false;
        }

        return true;
    }

    //checks whether players can create claims in a world
    public boolean claimsEnabledForWorld(World world) {
        ClaimsMode mode = ConfigLoader.config_claims_worldModes.get(world);
        return mode != null && mode != ClaimsMode.Disabled;
    }

    public static int getSeaLevel(World world) {
        Integer overrideValue = ConfigLoader.config_seaLevelOverride.get(world.getName());
        if (overrideValue == null || overrideValue == -1) {
            return world.getSeaLevel();
        } else {
            return overrideValue;
        }
    }

    public boolean containsBlockedIP(String message) {
        message = message.replace("\r\n", "");
        Pattern ipAddressPattern = Pattern.compile("([0-9]{1,3}\\.){3}[0-9]{1,3}");
        Matcher matcher = ipAddressPattern.matcher(message);

        //if it looks like an IP address
        if (matcher.find()) {
            //and it's not in the list of allowed IP addresses
            if (!ConfigLoader.config_spam_allowedIpAddresses.contains(matcher.group())) {
                return true;
            }
        }

        return false;
    }

    public boolean pvpRulesApply(World world) {
        Boolean configSetting = ConfigLoader.config_pvp_specifiedWorlds.get(world);
        if (configSetting != null) return configSetting;
        return world.getPVP();
    }

    static void banPlayer(Player player, String reason, String source) {
        if (ConfigLoader.config_ban_useCommand) {
            Bukkit.getServer().dispatchCommand(
                Bukkit.getConsoleSender(),
                ConfigLoader.config_ban_commandFormat.replace("%name%", player.getName()).replace("%reason%", reason));
        } else {
            BanList bans = Bukkit.getServer().getBanList(Type.NAME);
            bans.addBan(player.getName(), reason, null, source);

            //kick
            if (player.isOnline()) {
                player.kickPlayer(reason);
            }
        }
    }

    public ItemStack getItemInHand(Player player, EquipmentSlot hand) {
        if (hand == EquipmentSlot.OFF_HAND) return player.getInventory().getItemInOffHand();
        return player.getInventory().getItemInMainHand();
    }

    public boolean claimIsPvPSafeZone(Claim claim) {
        if (claim.siegeData != null)
            return false;
        return claim.isAdminClaim() && claim.parent == null && ConfigLoader.config_pvp_noCombatInAdminLandClaims ||
            claim.isAdminClaim() && claim.parent != null && ConfigLoader.config_pvp_noCombatInAdminSubdivisions ||
            !claim.isAdminClaim() && ConfigLoader.config_pvp_noCombatInPlayerLandClaims;
    }


    //Track scheduled "rescues" so we can cancel them if the player happens to teleport elsewhere so we can cancel it.
    ConcurrentHashMap<UUID, BukkitTask> portalReturnTaskMap = new ConcurrentHashMap<>();

    public void startRescueTask(Player player, Location location) {
        //Schedule task to reset player's portal cooldown after 30 seconds (Maximum timeout time for client, in case their network is slow and taking forever to load chunks)
        BukkitTask task = new CheckForPortalTrapTask(player, this, location).runTaskLater(GriefPrevention.instance, 600L);

        //Cancel existing rescue task
        if (portalReturnTaskMap.containsKey(player.getUniqueId()))
            portalReturnTaskMap.put(player.getUniqueId(), task).cancel();
        else
            portalReturnTaskMap.put(player.getUniqueId(), task);
    }
}

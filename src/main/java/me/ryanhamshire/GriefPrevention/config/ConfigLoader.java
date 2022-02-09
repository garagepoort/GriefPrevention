package me.ryanhamshire.GriefPrevention.config;

import me.ryanhamshire.GriefPrevention.ClaimsMode;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PistonMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static me.ryanhamshire.GriefPrevention.GriefPrevention.AddLogEntry;
import static org.bukkit.Bukkit.getServer;

public class ConfigLoader {
    public DataStore dataStore;

    //configuration variables, loaded/saved from a config.yml

    //claim mode for each world
    public static ConcurrentHashMap<World, ClaimsMode> config_claims_worldModes;
    private static boolean config_creativeWorldsExist;                     //note on whether there are any creative mode worlds, to save cpu cycles on a common hash lookup

    public static boolean config_claims_preventGlobalMonsterEggs; //whether monster eggs can be placed regardless of trust.
    public static boolean config_claims_preventTheft;                        //whether containers and crafting blocks are protectable
    public static boolean config_claims_protectCreatures;                    //whether claimed animals may be injured by players without permission
    public static boolean config_claims_protectHorses;                        //whether horses on a claim should be protected by that claim's rules
    public static boolean config_claims_protectDonkeys;                    //whether donkeys on a claim should be protected by that claim's rules
    public static boolean config_claims_protectLlamas;                        //whether llamas on a claim should be protected by that claim's rules
    public static boolean config_claims_preventButtonsSwitches;            //whether buttons and switches are protectable
    public static boolean config_claims_lockWoodenDoors;                    //whether wooden doors should be locked by default (require /accesstrust)
    public static boolean config_claims_lockTrapDoors;                        //whether trap doors should be locked by default (require /accesstrust)
    public static boolean config_claims_lockFenceGates;                    //whether fence gates should be locked by default (require /accesstrust)
    public static boolean config_claims_enderPearlsRequireAccessTrust;        //whether teleporting into a claim with a pearl requires access trust
    public static boolean config_claims_raidTriggersRequireBuildTrust;      //whether raids are triggered by a player that doesn't have build permission in that claim
    public static int config_claims_maxClaimsPerPlayer;                    //maximum number of claims per player
    public static boolean config_claims_respectWorldGuard;                 //whether claim creations requires WG build permission in creation area
    public static boolean config_claims_villagerTradingRequiresTrust;      //whether trading with a claimed villager requires permission

    public static int config_claims_initialBlocks;                            //the number of claim blocks a new player starts with
    public static double config_claims_abandonReturnRatio;                 //the portion of claim blocks returned to a player when a claim is abandoned
    public static int config_claims_blocksAccruedPerHour_default;            //how many additional blocks players get each hour of play (can be zero) without any special permissions
    public static int config_claims_maxAccruedBlocks_default;                //the limit on accrued blocks (over time) for players without any special permissions.  doesn't limit purchased or admin-gifted blocks
    public static int config_claims_accruedIdleThreshold;                    //how far (in blocks) a player must move in order to not be considered afk/idle when determining accrued claim blocks
    public static int config_claims_accruedIdlePercent;                    //how much percentage of claim block accruals should idle players get
    public static int config_claims_maxDepth;                                //limit on how deep claims can go
    public static int config_claims_expirationDays;                        //how many days of inactivity before a player loses his claims
    public static int config_claims_expirationExemptionTotalBlocks;        //total claim blocks amount which will exempt a player from claim expiration
    public static int config_claims_expirationExemptionBonusBlocks;        //bonus claim blocks amount which will exempt a player from claim expiration

    public static int config_claims_automaticClaimsForNewPlayersRadius;    //how big automatic new player claims (when they place a chest) should be.  -1 to disable
    public static int config_claims_automaticClaimsForNewPlayersRadiusMin; //how big automatic new player claims must be. 0 to disable
    public static int config_claims_claimsExtendIntoGroundDistance;        //how far below the shoveled block a new claim will reach
    public static int config_claims_minWidth;                                //minimum width for non-admin claims
    public static int config_claims_minArea;                               //minimum area for non-admin claims

    public static int config_claims_chestClaimExpirationDays;                //number of days of inactivity before an automatic chest claim will be deleted
    public static int config_claims_unusedClaimExpirationDays;                //number of days of inactivity before an unused (nothing build) claim will be deleted
    public static boolean config_claims_survivalAutoNatureRestoration;        //whether survival claims will be automatically restored to nature when auto-deleted
    public static boolean config_claims_allowTrappedInAdminClaims;            //whether it should be allowed to use /trapped in adminclaims.

    public static Material config_claims_investigationTool;                //which material will be used to investigate claims with a right click
    public static Material config_claims_modificationTool;                    //which material will be used to create/resize claims with a right click

    public static ArrayList<String> config_claims_commandsRequiringAccessTrust; //the list of slash commands requiring access trust when in a claim
    public static boolean config_claims_supplyPlayerManual;                //whether to give new players a book with land claim help in it
    public static int config_claims_manualDeliveryDelaySeconds;            //how long to wait before giving a book to a new player

    public static boolean config_claims_firespreads;                        //whether fire will spread in claims
    public static boolean config_claims_firedamages;                        //whether fire will damage in claims

    public static boolean config_claims_lecternReadingRequiresAccessTrust;                    //reading lecterns requires access trust

    public static ArrayList<World> config_siege_enabledWorlds;                //whether or not /siege is enabled on this server
    public static Set<Material> config_siege_blocks;                    //which blocks will be breakable in siege mode
    public static int config_siege_doorsOpenSeconds;  // how before claim is re-secured after siege win
    public static int config_siege_cooldownEndInMinutes;
    public static boolean config_spam_enabled;                                //whether or not to monitor for spam
    public static int config_spam_loginCooldownSeconds;                    //how long players must wait between logins.  combats login spam.
    public static int config_spam_loginLogoutNotificationsPerMinute;        //how many login/logout notifications to show per minute (global, not per player)
    public static ArrayList<String> config_spam_monitorSlashCommands;    //the list of slash commands monitored for spam
    public static boolean config_spam_banOffenders;                        //whether or not to ban spammers automatically
    public static String config_spam_banMessage;                            //message to show an automatically banned player
    public static String config_spam_warningMessage;                        //message to show a player who is close to spam level
    public static String config_spam_allowedIpAddresses;                    //IP addresses which will not be censored
    public static int config_spam_deathMessageCooldownSeconds;                //cooldown period for death messages (per player) in seconds
    public static int config_spam_logoutMessageDelaySeconds;               //delay before a logout message will be shown (only if the player stays offline that long)

    public static HashMap<World, Boolean> config_pvp_specifiedWorlds;                //list of worlds where pvp anti-grief rules apply, according to the config file
    public static boolean config_pvp_protectFreshSpawns;                    //whether to make newly spawned players immune until they pick up an item
    public static boolean config_pvp_punishLogout;                            //whether to kill players who log out during PvP combat
    public static int config_pvp_combatTimeoutSeconds;                        //how long combat is considered to continue after the most recent damage
    public static boolean config_pvp_allowCombatItemDrop;                    //whether a player can drop items during combat to hide them
    public static ArrayList<String> config_pvp_blockedCommands;            //list of commands which may not be used during pvp combat
    public static boolean config_pvp_noCombatInPlayerLandClaims;            //whether players may fight in player-owned land claims
    public static boolean config_pvp_noCombatInAdminLandClaims;            //whether players may fight in admin-owned land claims
    public static boolean config_pvp_noCombatInAdminSubdivisions;          //whether players may fight in subdivisions of admin-owned land claims
    public static boolean config_pvp_allowLavaNearPlayers;                 //whether players may dump lava near other players in pvp worlds
    public static boolean config_pvp_allowLavaNearPlayers_NonPvp;            //whather this applies in non-PVP rules worlds <ArchdukeLiamus>
    public static boolean config_pvp_allowFireNearPlayers;                 //whether players may start flint/steel fires near other players in pvp worlds
    public static boolean config_pvp_allowFireNearPlayers_NonPvp;            //whether this applies in non-PVP rules worlds <ArchdukeLiamus>
    public static boolean config_pvp_protectPets;                          //whether players may damage pets outside of land claims in pvp worlds

    public static boolean config_lockDeathDropsInPvpWorlds;                //whether players' dropped on death items are protected in pvp worlds
    public static boolean config_lockDeathDropsInNonPvpWorlds;             //whether players' dropped on death items are protected in non-pvp worlds

    public static int config_economy_claimBlocksMaxBonus;                  //max "bonus" blocks a player can buy.  set to zero for no limit.
    public static double config_economy_claimBlocksPurchaseCost;            //cost to purchase a claim block.  set to zero to disable purchase.
    public static double config_economy_claimBlocksSellValue;                //return on a sold claim block.  set to zero to disable sale.

    public static boolean config_blockClaimExplosions;                     //whether explosions may destroy claimed blocks
    public static boolean config_blockSurfaceCreeperExplosions;            //whether creeper explosions near or above the surface destroy blocks
    public static boolean config_blockSurfaceOtherExplosions;                //whether non-creeper explosions near or above the surface destroy blocks
    public static boolean config_blockSkyTrees;                            //whether players can build trees on platforms in the sky

    public static boolean config_fireSpreads;                                //whether fire spreads outside of claims
    public static boolean config_fireDestroys;                                //whether fire destroys blocks outside of claims

    public static boolean config_whisperNotifications;                    //whether whispered messages will broadcast to administrators in game
    public static boolean config_signNotifications;                        //whether sign content will broadcast to administrators in game
    public static ArrayList<String> config_eavesdrop_whisperCommands;        //list of whisper commands to eavesdrop on

    public static boolean config_endermenMoveBlocks;                        //whether or not endermen may move blocks around
    public static boolean config_claims_ravagersBreakBlocks;                //whether or not ravagers may break blocks in claims
    public static boolean config_silverfishBreakBlocks;                    //whether silverfish may break blocks
    public static boolean config_creaturesTrampleCrops;                    //whether or not non-player entities may trample crops
    public static boolean config_rabbitsEatCrops;                          //whether or not rabbits may eat crops
    public static boolean config_zombiesBreakDoors;                        //whether or not hard-mode zombies may break down wooden doors

    public static int config_ipLimit;                                      //how many players can share an IP address

    public static boolean config_trollFilterEnabled;                       //whether to auto-mute new players who use banned words right after joining
    public static boolean config_silenceBans;                              //whether to remove quit messages on banned players

    public static HashMap<String, Integer> config_seaLevelOverride;        //override for sea level, because bukkit doesn't report the right value for all situations

    public static boolean config_limitTreeGrowth;                          //whether trees should be prevented from growing into a claim from outside
    public static PistonMode config_pistonMovement;                            //Setting for piston check options
    public static boolean config_pistonExplosionSound;                     //whether pistons make an explosion sound when they get removed

    public static boolean config_advanced_fixNegativeClaimblockAmounts;    //whether to attempt to fix negative claim block amounts (some addons cause/assume players can go into negative amounts)
    public static int config_advanced_claim_expiration_check_rate;            //How often GP should check for expired claims, amount in seconds
    public static int config_advanced_offlineplayer_cache_days;            //Cache players who have logged in within the last x number of days

    //custom log settings
    public static int config_logs_daysToKeep;
    public static boolean config_logs_socialEnabled;
    public static boolean config_logs_suspiciousEnabled;
    public static boolean config_logs_adminEnabled;
    public static boolean config_logs_debugEnabled;
    public static boolean config_logs_mutedChatEnabled;

    //ban management plugin interop settings
    public static boolean config_ban_useCommand;
    public static String config_ban_commandFormat;

    public static String databaseHost;
    public static String databasePort;
    public static String databaseName;
    public static String databaseUserName;
    public static String databasePassword;

    //how far away to search from a tree trunk for its branch blocks
    public static final int TREE_RADIUS = 5;

    //how long to wait before deciding a player is staying online or staying offline, for notication messages
    public static final int NOTIFICATION_SECONDS = 20;

    public static void load() {
        //load the config if it exists
        FileConfiguration config = YamlConfiguration.loadConfiguration(new File(DataStore.configFilePath));
        FileConfiguration outConfig = new YamlConfiguration();
        outConfig.options().header("Default values are perfect for most servers.  If you want to customize and have a question, look for the answer here first: http://dev.bukkit.org/bukkit-plugins/grief-prevention/pages/setup-and-configuration/");

        //read configuration settings (note defaults)

        //get (deprecated node) claims world names from the config file
        List<World> worlds = getServer().getWorlds();
        List<String> deprecated_claimsEnabledWorldNames = config.getStringList("GriefPrevention.Claims.Worlds");

        //validate that list
        for (int i = 0; i < deprecated_claimsEnabledWorldNames.size(); i++) {
            String worldName = deprecated_claimsEnabledWorldNames.get(i);
            World world = getServer().getWorld(worldName);
            if (world == null) {
                deprecated_claimsEnabledWorldNames.remove(i--);
            }
        }

        //get (deprecated node) creative world names from the config file
        List<String> deprecated_creativeClaimsEnabledWorldNames = config.getStringList("GriefPrevention.Claims.CreativeRulesWorlds");

        //validate that list
        for (int i = 0; i < deprecated_creativeClaimsEnabledWorldNames.size(); i++) {
            String worldName = deprecated_creativeClaimsEnabledWorldNames.get(i);
            World world = getServer().getWorld(worldName);
            if (world == null) {
                deprecated_claimsEnabledWorldNames.remove(i--);
            }
        }

        //get (deprecated) pvp fire placement proximity note and use it if it exists (in the new config format it will be overwritten later).
        config_pvp_allowFireNearPlayers = config.getBoolean("GriefPrevention.PvP.AllowFlintAndSteelNearOtherPlayers", false);
        //get (deprecated) pvp lava dump proximity note and use it if it exists (in the new config format it will be overwritten later).
        config_pvp_allowLavaNearPlayers = config.getBoolean("GriefPrevention.PvP.AllowLavaDumpingNearOtherPlayers", false);

        //decide claim mode for each world
        config_claims_worldModes = new ConcurrentHashMap<>();
        config_creativeWorldsExist = false;
        for (World world : worlds) {
            //is it specified in the config file?
            String configSetting = config.getString("GriefPrevention.Claims.Mode." + world.getName());
            if (configSetting != null) {
                ClaimsMode claimsMode = configStringToClaimsMode(configSetting);
                if (claimsMode != null) {
                    config_claims_worldModes.put(world, claimsMode);
                    if (claimsMode == ClaimsMode.Creative) config_creativeWorldsExist = true;
                    continue;
                } else {
                    AddLogEntry("Error: Invalid claim mode \"" + configSetting + "\".  Options are Survival, Creative, and Disabled.");
                    config_claims_worldModes.put(world, ClaimsMode.Creative);
                    config_creativeWorldsExist = true;
                }
            }

            //was it specified in a deprecated config node?
            if (deprecated_creativeClaimsEnabledWorldNames.contains(world.getName())) {
                config_claims_worldModes.put(world, ClaimsMode.Creative);
                config_creativeWorldsExist = true;
            } else if (deprecated_claimsEnabledWorldNames.contains(world.getName())) {
                config_claims_worldModes.put(world, ClaimsMode.Survival);
            }

            //does the world's name indicate its purpose?
            else if (world.getName().toLowerCase().contains("survival")) {
                config_claims_worldModes.put(world, ClaimsMode.Survival);
            } else if (world.getName().toLowerCase().contains("creative")) {
                config_claims_worldModes.put(world, ClaimsMode.Creative);
                config_creativeWorldsExist = true;
            }

            //decide a default based on server type and world type
            else if (getServer().getDefaultGameMode() == GameMode.CREATIVE) {
                config_claims_worldModes.put(world, ClaimsMode.Creative);
                config_creativeWorldsExist = true;
            } else if (world.getEnvironment() == World.Environment.NORMAL) {
                config_claims_worldModes.put(world, ClaimsMode.Survival);
            } else {
                config_claims_worldModes.put(world, ClaimsMode.Disabled);
            }

            //if the setting WOULD be disabled but this is a server upgrading from the old config format,
            //then default to survival mode for safety's sake (to protect any admin claims which may
            //have been created there)
            if (config_claims_worldModes.get(world) == ClaimsMode.Disabled &&
                deprecated_claimsEnabledWorldNames.size() > 0) {
                config_claims_worldModes.put(world, ClaimsMode.Survival);
            }
        }

        //pvp worlds list
        config_pvp_specifiedWorlds = new HashMap<>();
        for (World world : worlds) {
            boolean pvpWorld = config.getBoolean("GriefPrevention.PvP.RulesEnabledInWorld." + world.getName(), world.getPVP());
            config_pvp_specifiedWorlds.put(world, pvpWorld);
        }

        //sea level
        config_seaLevelOverride = new HashMap<>();
        for (World world : worlds) {
            int seaLevelOverride = config.getInt("GriefPrevention.SeaLevelOverrides." + world.getName(), -1);
            outConfig.set("GriefPrevention.SeaLevelOverrides." + world.getName(), seaLevelOverride);
            config_seaLevelOverride.put(world.getName(), seaLevelOverride);
        }

        config_claims_preventGlobalMonsterEggs = config.getBoolean("GriefPrevention.Claims.PreventGlobalMonsterEggs", true);
        config_claims_preventTheft = config.getBoolean("GriefPrevention.Claims.PreventTheft", true);
        config_claims_protectCreatures = config.getBoolean("GriefPrevention.Claims.ProtectCreatures", true);
        config_claims_protectHorses = config.getBoolean("GriefPrevention.Claims.ProtectHorses", true);
        config_claims_protectDonkeys = config.getBoolean("GriefPrevention.Claims.ProtectDonkeys", true);
        config_claims_protectLlamas = config.getBoolean("GriefPrevention.Claims.ProtectLlamas", true);
        config_claims_preventButtonsSwitches = config.getBoolean("GriefPrevention.Claims.PreventButtonsSwitches", true);
        config_claims_lockWoodenDoors = config.getBoolean("GriefPrevention.Claims.LockWoodenDoors", false);
        config_claims_lockTrapDoors = config.getBoolean("GriefPrevention.Claims.LockTrapDoors", false);
        config_claims_lockFenceGates = config.getBoolean("GriefPrevention.Claims.LockFenceGates", true);
        config_claims_enderPearlsRequireAccessTrust = config.getBoolean("GriefPrevention.Claims.EnderPearlsRequireAccessTrust", true);
        config_claims_raidTriggersRequireBuildTrust = config.getBoolean("GriefPrevention.Claims.RaidTriggersRequireBuildTrust", true);
        config_claims_initialBlocks = config.getInt("GriefPrevention.Claims.InitialBlocks", 100);
        config_claims_blocksAccruedPerHour_default = config.getInt("GriefPrevention.Claims.BlocksAccruedPerHour", 100);
        config_claims_blocksAccruedPerHour_default = config.getInt("GriefPrevention.Claims.Claim Blocks Accrued Per Hour.Default", config_claims_blocksAccruedPerHour_default);
        config_claims_maxAccruedBlocks_default = config.getInt("GriefPrevention.Claims.MaxAccruedBlocks", 80000);
        config_claims_maxAccruedBlocks_default = config.getInt("GriefPrevention.Claims.Max Accrued Claim Blocks.Default", config_claims_maxAccruedBlocks_default);
        config_claims_accruedIdleThreshold = config.getInt("GriefPrevention.Claims.AccruedIdleThreshold", 0);
        config_claims_accruedIdleThreshold = config.getInt("GriefPrevention.Claims.Accrued Idle Threshold", config_claims_accruedIdleThreshold);
        config_claims_accruedIdlePercent = config.getInt("GriefPrevention.Claims.AccruedIdlePercent", 0);
        config_claims_abandonReturnRatio = config.getDouble("GriefPrevention.Claims.AbandonReturnRatio", 1.0D);
        config_claims_automaticClaimsForNewPlayersRadius = config.getInt("GriefPrevention.Claims.AutomaticNewPlayerClaimsRadius", 4);
        config_claims_automaticClaimsForNewPlayersRadiusMin = Math.max(0, Math.min(config_claims_automaticClaimsForNewPlayersRadius,
            config.getInt("GriefPrevention.Claims.AutomaticNewPlayerClaimsRadiusMinimum", 0)));
        config_claims_claimsExtendIntoGroundDistance = Math.abs(config.getInt("GriefPrevention.Claims.ExtendIntoGroundDistance", 5));
        config_claims_minWidth = config.getInt("GriefPrevention.Claims.MinimumWidth", 5);
        config_claims_minArea = config.getInt("GriefPrevention.Claims.MinimumArea", 100);
        config_claims_maxDepth = config.getInt("GriefPrevention.Claims.MaximumDepth", Integer.MIN_VALUE);
        config_claims_chestClaimExpirationDays = config.getInt("GriefPrevention.Claims.Expiration.ChestClaimDays", 7);
        config_claims_unusedClaimExpirationDays = config.getInt("GriefPrevention.Claims.Expiration.UnusedClaimDays", 14);
        config_claims_expirationDays = config.getInt("GriefPrevention.Claims.Expiration.AllClaims.DaysInactive", 60);
        config_claims_expirationExemptionTotalBlocks = config.getInt("GriefPrevention.Claims.Expiration.AllClaims.ExceptWhenOwnerHasTotalClaimBlocks", 10000);
        config_claims_expirationExemptionBonusBlocks = config.getInt("GriefPrevention.Claims.Expiration.AllClaims.ExceptWhenOwnerHasBonusClaimBlocks", 5000);
        config_claims_survivalAutoNatureRestoration = config.getBoolean("GriefPrevention.Claims.Expiration.AutomaticNatureRestoration.SurvivalWorlds", false);
        config_claims_allowTrappedInAdminClaims = config.getBoolean("GriefPrevention.Claims.AllowTrappedInAdminClaims", false);

        config_claims_maxClaimsPerPlayer = config.getInt("GriefPrevention.Claims.MaximumNumberOfClaimsPerPlayer", 0);
        config_claims_respectWorldGuard = config.getBoolean("GriefPrevention.Claims.CreationRequiresWorldGuardBuildPermission", true);
        config_claims_villagerTradingRequiresTrust = config.getBoolean("GriefPrevention.Claims.VillagerTradingRequiresPermission", true);
        String accessTrustSlashCommands = config.getString("GriefPrevention.Claims.CommandsRequiringAccessTrust", "/sethome");
        config_claims_supplyPlayerManual = config.getBoolean("GriefPrevention.Claims.DeliverManuals", true);
        config_claims_manualDeliveryDelaySeconds = config.getInt("GriefPrevention.Claims.ManualDeliveryDelaySeconds", 30);
        config_claims_ravagersBreakBlocks = config.getBoolean("GriefPrevention.Claims.RavagersBreakBlocks", true);

        config_claims_firespreads = config.getBoolean("GriefPrevention.Claims.FireSpreadsInClaims", false);
        config_claims_firedamages = config.getBoolean("GriefPrevention.Claims.FireDamagesInClaims", false);
        config_claims_lecternReadingRequiresAccessTrust = config.getBoolean("GriefPrevention.Claims.LecternReadingRequiresAccessTrust", true);

        config_spam_enabled = config.getBoolean("GriefPrevention.Spam.Enabled", true);
        config_spam_loginCooldownSeconds = config.getInt("GriefPrevention.Spam.LoginCooldownSeconds", 60);
        config_spam_loginLogoutNotificationsPerMinute = config.getInt("GriefPrevention.Spam.LoginLogoutNotificationsPerMinute", 5);
        config_spam_warningMessage = config.getString("GriefPrevention.Spam.WarningMessage", "Please reduce your noise level.  Spammers will be banned.");
        config_spam_allowedIpAddresses = config.getString("GriefPrevention.Spam.AllowedIpAddresses", "1.2.3.4; 5.6.7.8");
        config_spam_banOffenders = config.getBoolean("GriefPrevention.Spam.BanOffenders", true);
        config_spam_banMessage = config.getString("GriefPrevention.Spam.BanMessage", "Banned for spam.");
        String slashCommandsToMonitor = config.getString("GriefPrevention.Spam.MonitorSlashCommands", "/me;/global;/local");
        slashCommandsToMonitor = config.getString("GriefPrevention.Spam.ChatSlashCommands", slashCommandsToMonitor);
        config_spam_deathMessageCooldownSeconds = config.getInt("GriefPrevention.Spam.DeathMessageCooldownSeconds", 120);
        config_spam_logoutMessageDelaySeconds = config.getInt("GriefPrevention.Spam.Logout Message Delay In Seconds", 0);

        config_pvp_protectFreshSpawns = config.getBoolean("GriefPrevention.PvP.ProtectFreshSpawns", true);
        config_pvp_punishLogout = config.getBoolean("GriefPrevention.PvP.PunishLogout", true);
        config_pvp_combatTimeoutSeconds = config.getInt("GriefPrevention.PvP.CombatTimeoutSeconds", 15);
        config_pvp_allowCombatItemDrop = config.getBoolean("GriefPrevention.PvP.AllowCombatItemDrop", false);
        String bannedPvPCommandsList = config.getString("GriefPrevention.PvP.BlockedSlashCommands", "/home;/vanish;/spawn;/tpa");

        config_economy_claimBlocksMaxBonus = config.getInt("GriefPrevention.Economy.ClaimBlocksMaxBonus", 0);
        config_economy_claimBlocksPurchaseCost = config.getDouble("GriefPrevention.Economy.ClaimBlocksPurchaseCost", 0);
        config_economy_claimBlocksSellValue = config.getDouble("GriefPrevention.Economy.ClaimBlocksSellValue", 0);

        config_lockDeathDropsInPvpWorlds = config.getBoolean("GriefPrevention.ProtectItemsDroppedOnDeath.PvPWorlds", false);
        config_lockDeathDropsInNonPvpWorlds = config.getBoolean("GriefPrevention.ProtectItemsDroppedOnDeath.NonPvPWorlds", true);

        config_blockClaimExplosions = config.getBoolean("GriefPrevention.BlockLandClaimExplosions", true);
        config_blockSurfaceCreeperExplosions = config.getBoolean("GriefPrevention.BlockSurfaceCreeperExplosions", true);
        config_blockSurfaceOtherExplosions = config.getBoolean("GriefPrevention.BlockSurfaceOtherExplosions", true);
        config_blockSkyTrees = config.getBoolean("GriefPrevention.LimitSkyTrees", true);
        config_limitTreeGrowth = config.getBoolean("GriefPrevention.LimitTreeGrowth", false);
        config_pistonExplosionSound = config.getBoolean("GriefPrevention.PistonExplosionSound", true);
        config_pistonMovement = PistonMode.of(config.getString("GriefPrevention.PistonMovement", "CLAIMS_ONLY"));
        if (config.isBoolean("GriefPrevention.LimitPistonsToLandClaims") && !config.getBoolean("GriefPrevention.LimitPistonsToLandClaims"))
            config_pistonMovement = PistonMode.EVERYWHERE_SIMPLE;
        if (config.isBoolean("GriefPrevention.CheckPistonMovement") && !config.getBoolean("GriefPrevention.CheckPistonMovement"))
            config_pistonMovement = PistonMode.IGNORED;

        config_fireSpreads = config.getBoolean("GriefPrevention.FireSpreads", false);
        config_fireDestroys = config.getBoolean("GriefPrevention.FireDestroys", false);

        config_whisperNotifications = config.getBoolean("GriefPrevention.AdminsGetWhispers", true);
        config_signNotifications = config.getBoolean("GriefPrevention.AdminsGetSignNotifications", true);
        String whisperCommandsToMonitor = config.getString("GriefPrevention.WhisperCommands", "/tell;/pm;/r;/whisper;/msg");
        whisperCommandsToMonitor = config.getString("GriefPrevention.Spam.WhisperSlashCommands", whisperCommandsToMonitor);

        config_trollFilterEnabled = config.getBoolean("GriefPrevention.Mute New Players Using Banned Words", true);
        config_ipLimit = config.getInt("GriefPrevention.MaxPlayersPerIpAddress", 3);
        config_silenceBans = config.getBoolean("GriefPrevention.SilenceBans", true);

        config_endermenMoveBlocks = config.getBoolean("GriefPrevention.EndermenMoveBlocks", false);
        config_silverfishBreakBlocks = config.getBoolean("GriefPrevention.SilverfishBreakBlocks", false);
        config_creaturesTrampleCrops = config.getBoolean("GriefPrevention.CreaturesTrampleCrops", false);
        config_rabbitsEatCrops = config.getBoolean("GriefPrevention.RabbitsEatCrops", true);
        config_zombiesBreakDoors = config.getBoolean("GriefPrevention.HardModeZombiesBreakDoors", false);
        config_ban_useCommand = config.getBoolean("GriefPrevention.UseBanCommand", false);
        config_ban_commandFormat = config.getString("GriefPrevention.BanCommandPattern", "ban %name% %reason%");

        //default for claim investigation tool
        String investigationToolMaterialName = Material.STICK.name();

        //get investigation tool from config
        investigationToolMaterialName = config.getString("GriefPrevention.Claims.InvestigationTool", investigationToolMaterialName);

        //validate investigation tool
        config_claims_investigationTool = Material.getMaterial(investigationToolMaterialName);
        if (config_claims_investigationTool == null) {
            AddLogEntry("ERROR: Material " + investigationToolMaterialName + " not found.  Defaulting to the stick.  Please update your config.yml.");
            config_claims_investigationTool = Material.STICK;
        }

        //default for claim creation/modification tool
        String modificationToolMaterialName = Material.GOLDEN_SHOVEL.name();

        //get modification tool from config
        modificationToolMaterialName = config.getString("GriefPrevention.Claims.ModificationTool", modificationToolMaterialName);

        //validate modification tool
        config_claims_modificationTool = Material.getMaterial(modificationToolMaterialName);
        if (config_claims_modificationTool == null) {
            AddLogEntry("ERROR: Material " + modificationToolMaterialName + " not found.  Defaulting to the golden shovel.  Please update your config.yml.");
            config_claims_modificationTool = Material.GOLDEN_SHOVEL;
        }

        //get siege world names from the config file
        List<String> siegeEnabledWorldNames = config.getStringList("GriefPrevention.Siege.Worlds");

        //validate that list
        config_siege_enabledWorlds = new ArrayList<>();
        for (String worldName : siegeEnabledWorldNames) {
            World world = getServer().getWorld(worldName);
            if (world == null) {
                AddLogEntry("Error: Siege Configuration: There's no world named \"" + worldName + "\".  Please update your config.yml.");
            } else {
                config_siege_enabledWorlds.add(world);
            }
        }

        //default siege blocks
        config_siege_blocks = EnumSet.noneOf(Material.class);
        config_siege_blocks.add(Material.DIRT);
        config_siege_blocks.add(Material.GRASS_BLOCK);
        config_siege_blocks.add(Material.GRASS);
        config_siege_blocks.add(Material.FERN);
        config_siege_blocks.add(Material.DEAD_BUSH);
        config_siege_blocks.add(Material.COBBLESTONE);
        config_siege_blocks.add(Material.GRAVEL);
        config_siege_blocks.add(Material.SAND);
        config_siege_blocks.add(Material.GLASS);
        config_siege_blocks.add(Material.GLASS_PANE);
        config_siege_blocks.add(Material.OAK_PLANKS);
        config_siege_blocks.add(Material.SPRUCE_PLANKS);
        config_siege_blocks.add(Material.BIRCH_PLANKS);
        config_siege_blocks.add(Material.JUNGLE_PLANKS);
        config_siege_blocks.add(Material.ACACIA_PLANKS);
        config_siege_blocks.add(Material.DARK_OAK_PLANKS);
        config_siege_blocks.add(Material.WHITE_WOOL);
        config_siege_blocks.add(Material.ORANGE_WOOL);
        config_siege_blocks.add(Material.MAGENTA_WOOL);
        config_siege_blocks.add(Material.LIGHT_BLUE_WOOL);
        config_siege_blocks.add(Material.YELLOW_WOOL);
        config_siege_blocks.add(Material.LIME_WOOL);
        config_siege_blocks.add(Material.PINK_WOOL);
        config_siege_blocks.add(Material.GRAY_WOOL);
        config_siege_blocks.add(Material.LIGHT_GRAY_WOOL);
        config_siege_blocks.add(Material.CYAN_WOOL);
        config_siege_blocks.add(Material.PURPLE_WOOL);
        config_siege_blocks.add(Material.BLUE_WOOL);
        config_siege_blocks.add(Material.BROWN_WOOL);
        config_siege_blocks.add(Material.GREEN_WOOL);
        config_siege_blocks.add(Material.RED_WOOL);
        config_siege_blocks.add(Material.BLACK_WOOL);
        config_siege_blocks.add(Material.SNOW);

        List<String> breakableBlocksList;

        //try to load the list from the config file
        if (config.isList("GriefPrevention.Siege.BreakableBlocks")) {
            breakableBlocksList = config.getStringList("GriefPrevention.Siege.BreakableBlocks");

            //load materials
            config_siege_blocks = parseMaterialListFromConfig(breakableBlocksList);
        }
        //if it fails, use default siege block list instead
        else {
            breakableBlocksList = config_siege_blocks.stream().map(Material::name).collect(Collectors.toList());
        }

        config_siege_doorsOpenSeconds = config.getInt("GriefPrevention.Siege.DoorsOpenDelayInSeconds", 5 * 60);
        config_siege_cooldownEndInMinutes = config.getInt("GriefPrevention.Siege.CooldownEndInMinutes", 60);
        config_pvp_noCombatInPlayerLandClaims = config.getBoolean("GriefPrevention.PvP.ProtectPlayersInLandClaims.PlayerOwnedClaims", config_siege_enabledWorlds.size() == 0);
        config_pvp_noCombatInAdminLandClaims = config.getBoolean("GriefPrevention.PvP.ProtectPlayersInLandClaims.AdministrativeClaims", config_siege_enabledWorlds.size() == 0);
        config_pvp_noCombatInAdminSubdivisions = config.getBoolean("GriefPrevention.PvP.ProtectPlayersInLandClaims.AdministrativeSubdivisions", config_siege_enabledWorlds.size() == 0);
        config_pvp_allowLavaNearPlayers = config.getBoolean("GriefPrevention.PvP.AllowLavaDumpingNearOtherPlayers.PvPWorlds", true);
        config_pvp_allowLavaNearPlayers_NonPvp = config.getBoolean("GriefPrevention.PvP.AllowLavaDumpingNearOtherPlayers.NonPvPWorlds", false);
        config_pvp_allowFireNearPlayers = config.getBoolean("GriefPrevention.PvP.AllowFlintAndSteelNearOtherPlayers.PvPWorlds", true);
        config_pvp_allowFireNearPlayers_NonPvp = config.getBoolean("GriefPrevention.PvP.AllowFlintAndSteelNearOtherPlayers.NonPvPWorlds", false);
        config_pvp_protectPets = config.getBoolean("GriefPrevention.PvP.ProtectPetsOutsideLandClaims", false);

        //optional database settings
        databaseName = config.getString("GriefPrevention.Database.Name", "");
        databaseHost = config.getString("GriefPrevention.Database.Host", "");
        databasePort = config.getString("GriefPrevention.Database.Port", "3306");
        databaseUserName = config.getString("GriefPrevention.Database.UserName", "");
        databasePassword = config.getString("GriefPrevention.Database.Password", "");

        config_advanced_fixNegativeClaimblockAmounts = config.getBoolean("GriefPrevention.Advanced.fixNegativeClaimblockAmounts", true);
        config_advanced_claim_expiration_check_rate = config.getInt("GriefPrevention.Advanced.ClaimExpirationCheckRate", 60);
        config_advanced_offlineplayer_cache_days = config.getInt("GriefPrevention.Advanced.OfflinePlayer_cache_days", 90);

        //custom logger settings
        config_logs_daysToKeep = config.getInt("GriefPrevention.Abridged Logs.Days To Keep", 7);
        config_logs_socialEnabled = config.getBoolean("GriefPrevention.Abridged Logs.Included Entry Types.Social Activity", true);
        config_logs_suspiciousEnabled = config.getBoolean("GriefPrevention.Abridged Logs.Included Entry Types.Suspicious Activity", true);
        config_logs_adminEnabled = config.getBoolean("GriefPrevention.Abridged Logs.Included Entry Types.Administrative Activity", false);
        config_logs_debugEnabled = config.getBoolean("GriefPrevention.Abridged Logs.Included Entry Types.Debug", false);
        config_logs_mutedChatEnabled = config.getBoolean("GriefPrevention.Abridged Logs.Included Entry Types.Muted Chat Messages", false);

        //claims mode by world
        for (World world : config_claims_worldModes.keySet()) {
            outConfig.set(
                "GriefPrevention.Claims.Mode." + world.getName(),
                config_claims_worldModes.get(world).name());
        }

        outConfig.set("GriefPrevention.Claims.PreventGlobalMonsterEggs", config_claims_preventGlobalMonsterEggs);
        outConfig.set("GriefPrevention.Claims.PreventTheft", config_claims_preventTheft);
        outConfig.set("GriefPrevention.Claims.ProtectCreatures", config_claims_protectCreatures);
        outConfig.set("GriefPrevention.Claims.PreventButtonsSwitches", config_claims_preventButtonsSwitches);
        outConfig.set("GriefPrevention.Claims.LockWoodenDoors", config_claims_lockWoodenDoors);
        outConfig.set("GriefPrevention.Claims.LockTrapDoors", config_claims_lockTrapDoors);
        outConfig.set("GriefPrevention.Claims.LockFenceGates", config_claims_lockFenceGates);
        outConfig.set("GriefPrevention.Claims.EnderPearlsRequireAccessTrust", config_claims_enderPearlsRequireAccessTrust);
        outConfig.set("GriefPrevention.Claims.RaidTriggersRequireBuildTrust", config_claims_raidTriggersRequireBuildTrust);
        outConfig.set("GriefPrevention.Claims.ProtectHorses", config_claims_protectHorses);
        outConfig.set("GriefPrevention.Claims.ProtectDonkeys", config_claims_protectDonkeys);
        outConfig.set("GriefPrevention.Claims.ProtectLlamas", config_claims_protectLlamas);
        outConfig.set("GriefPrevention.Claims.InitialBlocks", config_claims_initialBlocks);
        outConfig.set("GriefPrevention.Claims.Claim Blocks Accrued Per Hour.Default", config_claims_blocksAccruedPerHour_default);
        outConfig.set("GriefPrevention.Claims.Max Accrued Claim Blocks.Default", config_claims_maxAccruedBlocks_default);
        outConfig.set("GriefPrevention.Claims.Accrued Idle Threshold", config_claims_accruedIdleThreshold);
        outConfig.set("GriefPrevention.Claims.AccruedIdlePercent", config_claims_accruedIdlePercent);
        outConfig.set("GriefPrevention.Claims.AbandonReturnRatio", config_claims_abandonReturnRatio);
        outConfig.set("GriefPrevention.Claims.AutomaticNewPlayerClaimsRadius", config_claims_automaticClaimsForNewPlayersRadius);
        outConfig.set("GriefPrevention.Claims.AutomaticNewPlayerClaimsRadiusMinimum", config_claims_automaticClaimsForNewPlayersRadiusMin);
        outConfig.set("GriefPrevention.Claims.ExtendIntoGroundDistance", config_claims_claimsExtendIntoGroundDistance);
        outConfig.set("GriefPrevention.Claims.MinimumWidth", config_claims_minWidth);
        outConfig.set("GriefPrevention.Claims.MinimumArea", config_claims_minArea);
        outConfig.set("GriefPrevention.Claims.MaximumDepth", config_claims_maxDepth);
        outConfig.set("GriefPrevention.Claims.InvestigationTool", config_claims_investigationTool.name());
        outConfig.set("GriefPrevention.Claims.ModificationTool", config_claims_modificationTool.name());
        outConfig.set("GriefPrevention.Claims.Expiration.ChestClaimDays", config_claims_chestClaimExpirationDays);
        outConfig.set("GriefPrevention.Claims.Expiration.UnusedClaimDays", config_claims_unusedClaimExpirationDays);
        outConfig.set("GriefPrevention.Claims.Expiration.AllClaims.DaysInactive", config_claims_expirationDays);
        outConfig.set("GriefPrevention.Claims.Expiration.AllClaims.ExceptWhenOwnerHasTotalClaimBlocks", config_claims_expirationExemptionTotalBlocks);
        outConfig.set("GriefPrevention.Claims.Expiration.AllClaims.ExceptWhenOwnerHasBonusClaimBlocks", config_claims_expirationExemptionBonusBlocks);
        outConfig.set("GriefPrevention.Claims.Expiration.AutomaticNatureRestoration.SurvivalWorlds", config_claims_survivalAutoNatureRestoration);
        outConfig.set("GriefPrevention.Claims.AllowTrappedInAdminClaims", config_claims_allowTrappedInAdminClaims);
        outConfig.set("GriefPrevention.Claims.MaximumNumberOfClaimsPerPlayer", config_claims_maxClaimsPerPlayer);
        outConfig.set("GriefPrevention.Claims.CreationRequiresWorldGuardBuildPermission", config_claims_respectWorldGuard);
        outConfig.set("GriefPrevention.Claims.VillagerTradingRequiresPermission", config_claims_villagerTradingRequiresTrust);
        outConfig.set("GriefPrevention.Claims.CommandsRequiringAccessTrust", accessTrustSlashCommands);
        outConfig.set("GriefPrevention.Claims.DeliverManuals", config_claims_supplyPlayerManual);
        outConfig.set("GriefPrevention.Claims.ManualDeliveryDelaySeconds", config_claims_manualDeliveryDelaySeconds);
        outConfig.set("GriefPrevention.Claims.RavagersBreakBlocks", config_claims_ravagersBreakBlocks);

        outConfig.set("GriefPrevention.Claims.FireSpreadsInClaims", config_claims_firespreads);
        outConfig.set("GriefPrevention.Claims.FireDamagesInClaims", config_claims_firedamages);
        outConfig.set("GriefPrevention.Claims.LecternReadingRequiresAccessTrust", config_claims_lecternReadingRequiresAccessTrust);

        outConfig.set("GriefPrevention.Spam.Enabled", config_spam_enabled);
        outConfig.set("GriefPrevention.Spam.LoginCooldownSeconds", config_spam_loginCooldownSeconds);
        outConfig.set("GriefPrevention.Spam.LoginLogoutNotificationsPerMinute", config_spam_loginLogoutNotificationsPerMinute);
        outConfig.set("GriefPrevention.Spam.ChatSlashCommands", slashCommandsToMonitor);
        outConfig.set("GriefPrevention.Spam.WhisperSlashCommands", whisperCommandsToMonitor);
        outConfig.set("GriefPrevention.Spam.WarningMessage", config_spam_warningMessage);
        outConfig.set("GriefPrevention.Spam.BanOffenders", config_spam_banOffenders);
        outConfig.set("GriefPrevention.Spam.BanMessage", config_spam_banMessage);
        outConfig.set("GriefPrevention.Spam.AllowedIpAddresses", config_spam_allowedIpAddresses);
        outConfig.set("GriefPrevention.Spam.DeathMessageCooldownSeconds", config_spam_deathMessageCooldownSeconds);
        outConfig.set("GriefPrevention.Spam.Logout Message Delay In Seconds", config_spam_logoutMessageDelaySeconds);

        for (World world : worlds) {
            outConfig.set("GriefPrevention.PvP.RulesEnabledInWorld." + world.getName(), pvpRulesApply(world));
        }
        outConfig.set("GriefPrevention.PvP.ProtectFreshSpawns", config_pvp_protectFreshSpawns);
        outConfig.set("GriefPrevention.PvP.PunishLogout", config_pvp_punishLogout);
        outConfig.set("GriefPrevention.PvP.CombatTimeoutSeconds", config_pvp_combatTimeoutSeconds);
        outConfig.set("GriefPrevention.PvP.AllowCombatItemDrop", config_pvp_allowCombatItemDrop);
        outConfig.set("GriefPrevention.PvP.BlockedSlashCommands", bannedPvPCommandsList);
        outConfig.set("GriefPrevention.PvP.ProtectPlayersInLandClaims.PlayerOwnedClaims", config_pvp_noCombatInPlayerLandClaims);
        outConfig.set("GriefPrevention.PvP.ProtectPlayersInLandClaims.AdministrativeClaims", config_pvp_noCombatInAdminLandClaims);
        outConfig.set("GriefPrevention.PvP.ProtectPlayersInLandClaims.AdministrativeSubdivisions", config_pvp_noCombatInAdminSubdivisions);
        outConfig.set("GriefPrevention.PvP.AllowLavaDumpingNearOtherPlayers.PvPWorlds", config_pvp_allowLavaNearPlayers);
        outConfig.set("GriefPrevention.PvP.AllowLavaDumpingNearOtherPlayers.NonPvPWorlds", config_pvp_allowLavaNearPlayers_NonPvp);
        outConfig.set("GriefPrevention.PvP.AllowFlintAndSteelNearOtherPlayers.PvPWorlds", config_pvp_allowFireNearPlayers);
        outConfig.set("GriefPrevention.PvP.AllowFlintAndSteelNearOtherPlayers.NonPvPWorlds", config_pvp_allowFireNearPlayers_NonPvp);
        outConfig.set("GriefPrevention.PvP.ProtectPetsOutsideLandClaims", config_pvp_protectPets);

        outConfig.set("GriefPrevention.Economy.ClaimBlocksMaxBonus", config_economy_claimBlocksMaxBonus);
        outConfig.set("GriefPrevention.Economy.ClaimBlocksPurchaseCost", config_economy_claimBlocksPurchaseCost);
        outConfig.set("GriefPrevention.Economy.ClaimBlocksSellValue", config_economy_claimBlocksSellValue);

        outConfig.set("GriefPrevention.ProtectItemsDroppedOnDeath.PvPWorlds", config_lockDeathDropsInPvpWorlds);
        outConfig.set("GriefPrevention.ProtectItemsDroppedOnDeath.NonPvPWorlds", config_lockDeathDropsInNonPvpWorlds);

        outConfig.set("GriefPrevention.BlockLandClaimExplosions", config_blockClaimExplosions);
        outConfig.set("GriefPrevention.BlockSurfaceCreeperExplosions", config_blockSurfaceCreeperExplosions);
        outConfig.set("GriefPrevention.BlockSurfaceOtherExplosions", config_blockSurfaceOtherExplosions);
        outConfig.set("GriefPrevention.LimitSkyTrees", config_blockSkyTrees);
        outConfig.set("GriefPrevention.LimitTreeGrowth", config_limitTreeGrowth);
        outConfig.set("GriefPrevention.PistonMovement", config_pistonMovement.name());
        outConfig.set("GriefPrevention.CheckPistonMovement", null);
        outConfig.set("GriefPrevention.LimitPistonsToLandClaims", null);
        outConfig.set("GriefPrevention.PistonExplosionSound", config_pistonExplosionSound);

        outConfig.set("GriefPrevention.FireSpreads", config_fireSpreads);
        outConfig.set("GriefPrevention.FireDestroys", config_fireDestroys);

        outConfig.set("GriefPrevention.AdminsGetWhispers", config_whisperNotifications);
        outConfig.set("GriefPrevention.AdminsGetSignNotifications", config_signNotifications);

        outConfig.set("GriefPrevention.Mute New Players Using Banned Words", config_trollFilterEnabled);
        outConfig.set("GriefPrevention.MaxPlayersPerIpAddress", config_ipLimit);
        outConfig.set("GriefPrevention.SilenceBans", config_silenceBans);

        outConfig.set("GriefPrevention.Siege.Worlds", siegeEnabledWorldNames);
        outConfig.set("GriefPrevention.Siege.BreakableBlocks", breakableBlocksList);
        outConfig.set("GriefPrevention.Siege.DoorsOpenDelayInSeconds", config_siege_doorsOpenSeconds);
        outConfig.set("GriefPrevention.Siege.CooldownEndInMinutes", config_siege_cooldownEndInMinutes);
        outConfig.set("GriefPrevention.EndermenMoveBlocks", config_endermenMoveBlocks);
        outConfig.set("GriefPrevention.SilverfishBreakBlocks", config_silverfishBreakBlocks);
        outConfig.set("GriefPrevention.CreaturesTrampleCrops", config_creaturesTrampleCrops);
        outConfig.set("GriefPrevention.RabbitsEatCrops", config_rabbitsEatCrops);
        outConfig.set("GriefPrevention.HardModeZombiesBreakDoors", config_zombiesBreakDoors);

        outConfig.set("GriefPrevention.Database.Name", databaseName);
        outConfig.set("GriefPrevention.Database.Host", databaseHost);
        outConfig.set("GriefPrevention.Database.Port", databasePort);
        outConfig.set("GriefPrevention.Database.UserName", databaseUserName);
        outConfig.set("GriefPrevention.Database.Password", databasePassword);

        outConfig.set("GriefPrevention.UseBanCommand", config_ban_useCommand);
        outConfig.set("GriefPrevention.BanCommandPattern", config_ban_commandFormat);

        outConfig.set("GriefPrevention.Advanced.fixNegativeClaimblockAmounts", config_advanced_fixNegativeClaimblockAmounts);
        outConfig.set("GriefPrevention.Advanced.ClaimExpirationCheckRate", config_advanced_claim_expiration_check_rate);
        outConfig.set("GriefPrevention.Advanced.OfflinePlayer_cache_days", config_advanced_offlineplayer_cache_days);

        //custom logger settings
        outConfig.set("GriefPrevention.Abridged Logs.Days To Keep", config_logs_daysToKeep);
        outConfig.set("GriefPrevention.Abridged Logs.Included Entry Types.Social Activity", config_logs_socialEnabled);
        outConfig.set("GriefPrevention.Abridged Logs.Included Entry Types.Suspicious Activity", config_logs_suspiciousEnabled);
        outConfig.set("GriefPrevention.Abridged Logs.Included Entry Types.Administrative Activity", config_logs_adminEnabled);
        outConfig.set("GriefPrevention.Abridged Logs.Included Entry Types.Debug", config_logs_debugEnabled);
        outConfig.set("GriefPrevention.Abridged Logs.Included Entry Types.Muted Chat Messages", config_logs_mutedChatEnabled);

        try {
            outConfig.save(DataStore.configFilePath);
        } catch (IOException exception) {
            AddLogEntry("Unable to write to the configuration file at \"" + DataStore.configFilePath + "\"");
        }

        //try to parse the list of commands requiring access trust in land claims
        config_claims_commandsRequiringAccessTrust = new ArrayList<>();
        String[] commands = accessTrustSlashCommands.split(";");
        for (String command : commands) {
            if (!command.isEmpty()) {
                config_claims_commandsRequiringAccessTrust.add(command.trim().toLowerCase());
            }
        }

        //try to parse the list of commands which should be monitored for spam
        config_spam_monitorSlashCommands = new ArrayList<>();
        commands = slashCommandsToMonitor.split(";");
        for (String command : commands) {
            config_spam_monitorSlashCommands.add(command.trim().toLowerCase());
        }

        //try to parse the list of commands which should be included in eavesdropping
        config_eavesdrop_whisperCommands = new ArrayList<>();
        commands = whisperCommandsToMonitor.split(";");
        for (String command : commands) {
            config_eavesdrop_whisperCommands.add(command.trim().toLowerCase());
        }

        //try to parse the list of commands which should be banned during pvp combat
        config_pvp_blockedCommands = new ArrayList<>();
        commands = bannedPvPCommandsList.split(";");
        for (String command : commands) {
            config_pvp_blockedCommands.add(command.trim().toLowerCase());
        }
    }

    private static ClaimsMode configStringToClaimsMode(String configSetting) {
        if (configSetting.equalsIgnoreCase("Survival")) {
            return ClaimsMode.Survival;
        }
        if (configSetting.equalsIgnoreCase("Creative")) {
            return ClaimsMode.Creative;
        }
        if (configSetting.equalsIgnoreCase("Disabled")) {
            return ClaimsMode.Disabled;
        }
        if (configSetting.equalsIgnoreCase("SurvivalRequiringClaims")) {
            return ClaimsMode.SurvivalRequiringClaims;
        }
        return null;
    }

    private static Set<Material> parseMaterialListFromConfig(List<String> stringsToParse) {
        Set<Material> materials = EnumSet.noneOf(Material.class);

        for (int i = 0; i < stringsToParse.size(); i++) {
            String string = stringsToParse.get(i);
            if (string == null) continue;
            Material material = Material.getMaterial(string.toUpperCase());

            if (material == null) {
                //check if string has failed validity before
                if (!string.contains("can't")) {
                    stringsToParse.set(i, string + "     <-- can't understand this entry, see BukkitDev documentation");
                    GriefPrevention.AddLogEntry(String.format("ERROR: Invalid material %s.  Please update your config.yml.", string));
                }
                continue;
            }

            materials.add(material);
        }

        return materials;
    }

    //determines whether creative anti-grief rules apply at a location
    public static boolean creativeRulesApply(Location location)
    {
        if (!ConfigLoader.config_creativeWorldsExist) return false;

        return ConfigLoader.config_claims_worldModes.get((location.getWorld())) == ClaimsMode.Creative;
    }

    public static boolean pvpRulesApply(World world)
    {
        Boolean configSetting = config_pvp_specifiedWorlds.get(world);
        if (configSetting != null) return configSetting;
        return world.getPVP();
    }
}

package me.ryanhamshire.GriefPrevention.listeners;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocListener;
import me.ryanhamshire.GriefPrevention.AutoExtendClaimTask;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.ShovelMode;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.Visualization;
import me.ryanhamshire.GriefPrevention.VisualizationType;
import me.ryanhamshire.GriefPrevention.claims.ClaimBlockService;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.claims.ResizeClaimService;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.events.ClaimInspectionEvent;
import me.ryanhamshire.GriefPrevention.events.VisualizationEvent;
import me.ryanhamshire.GriefPrevention.util.HelperUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockIterator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@IocBean
@IocListener
public class PlayerInteractListener implements Listener {
    //determines whether a block type is an inventory holder.  uses a caching strategy to save cpu time
    private final ConcurrentHashMap<Material, Boolean> inventoryHolderCache = new ConcurrentHashMap<>();
    private final ClaimBlockService claimBlockService;
    private final ClaimService claimService;
    private final DataStore dataStore;
    private final ResizeClaimService resizeClaimService;

    public PlayerInteractListener(ClaimBlockService claimBlockService, ClaimService claimService, DataStore dataStore, ResizeClaimService resizeClaimService) {
        this.claimBlockService = claimBlockService;
        this.claimService = claimService;
        this.dataStore = dataStore;
        this.resizeClaimService = resizeClaimService;
    }


    private boolean isInventoryHolder(Block clickedBlock) {

        Material cacheKey = clickedBlock.getType();
        Boolean cachedValue = this.inventoryHolderCache.get(cacheKey);
        if (cachedValue != null) {
            return cachedValue;
        } else {
            boolean isHolder = clickedBlock.getState() instanceof InventoryHolder;
            this.inventoryHolderCache.put(cacheKey, isHolder);
            return isHolder;
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

        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());

        //Turtle eggs
        if (action == Action.PHYSICAL && clickedBlockType == Material.TURTLE_EGG) {
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
            event.getAction() == Action.RIGHT_CLICK_BLOCK &&
                ((this.isInventoryHolder(clickedBlock) && clickedBlock.getType() != Material.LECTERN) ||
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
                World.Environment environment = clickedBlock.getWorld().getEnvironment();
                if (environment == World.Environment.NETHER) {
                    allowedFillBlocks.add(Material.NETHERRACK);
                } else if (environment == World.Environment.THE_END) {
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
}

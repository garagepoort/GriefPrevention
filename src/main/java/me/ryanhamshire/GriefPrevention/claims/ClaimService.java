package me.ryanhamshire.GriefPrevention.claims;

import be.garagepoort.mcioc.IocBean;
import me.ryanhamshire.GriefPrevention.BlockSnapshot;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.ClaimsMode;
import me.ryanhamshire.GriefPrevention.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.NoTransferException;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.RestoreNatureProcessingTask;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.Visualization;
import me.ryanhamshire.GriefPrevention.WorldGuardWrapper;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimPermissionCheckEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimTransferEvent;
import me.ryanhamshire.GriefPrevention.events.PreventBlockBreakEvent;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import me.ryanhamshire.GriefPrevention.util.HelperUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static me.ryanhamshire.GriefPrevention.MessageService.sendMessage;
import static me.ryanhamshire.GriefPrevention.config.ConfigLoader.creativeRulesApply;

@IocBean
public class ClaimService {

    private final DataStore dataStore;
    private final BukkitUtils bukkitUtils;
    private final ClaimBlockService claimBlockService;
    private final ClaimFactory claimFactory;
    private final ClaimRepository claimRepository;
    //world guard reference, if available
    private WorldGuardWrapper worldGuard = null;

    public ClaimService(DataStore dataStore,
                        BukkitUtils bukkitUtils,
                        ClaimBlockService claimBlockService,
                        ClaimFactory claimFactory,
                        ClaimRepository claimRepository) {
        this.dataStore = dataStore;
        this.bukkitUtils = bukkitUtils;
        this.claimBlockService = claimBlockService;
        this.claimFactory = claimFactory;
        this.claimRepository = claimRepository;

        try {
            this.worldGuard = new WorldGuardWrapper();
            GriefPrevention.AddLogEntry("Successfully hooked into WorldGuard.");
        }
        //if failed, world guard compat features will just be disabled.
        catch (IllegalStateException | IllegalArgumentException | ClassCastException | NoClassDefFoundError ignored) {
        }
    }

    //players may only siege someone when he's not in an admin claim
    //and when he has some level of permission in the claim
    public boolean canSiege(Claim claim, Player defender) {
        if (claim.isAdminClaim()) return false;

        return this.checkPermission(claim, defender, ClaimPermission.Access, null) == null;
    }

    public boolean abandonClaim(Player player, boolean deleteTopLevelClaim) {
        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());

        //which claim is being abandoned?
        Claim claim = getClaimAt(player.getLocation(), true /*ignore height*/, null);
        if (claim == null) {
            sendMessage(player, TextMode.Instr, Messages.AbandonClaimMissing);
        }

        //verify ownership
        else if (checkPermission(claim, player, ClaimPermission.Edit, null) != null) {
            sendMessage(player, TextMode.Err, Messages.NotYourClaim);
        }

        //warn if has children and we're not explicitly deleting a top level claim
        else if (claim.children.size() > 0 && !deleteTopLevelClaim) {
            sendMessage(player, TextMode.Instr, Messages.DeleteTopLevelClaim);
            return true;
        } else {
            //delete it
            claim.removeSurfaceFluids(null);
            this.deleteClaim(claim, true, false);

            //if in a creative mode world, restore the claim area
            if (creativeRulesApply(claim.getLesserBoundaryCorner())) {
                GriefPrevention.AddLogEntry(player.getName() + " abandoned a claim @ " + GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()));
                sendMessage(player, TextMode.Warn, Messages.UnclaimCleanupWarning);
                restoreClaim(claim, 20L * 60 * 2);
            }

            //adjust claim blocks when abandoning a top level claim
            if (ConfigLoader.config_claims_abandonReturnRatio != 1.0D && claim.parent == null && claim.ownerID.equals(playerData.playerID)) {
                playerData.setAccruedClaimBlocks(claimBlockService.recalculateAccruedClaimBlocks(playerData) - (int) Math.ceil((claim.getArea() * (1 - ConfigLoader.config_claims_abandonReturnRatio))));
            }

            //tell the player how many claim blocks he has left
            int remainingBlocks = claimBlockService.getRemainingClaimBlocks(playerData, getClaims(player.getUniqueId(), player.getName()));
            sendMessage(player, TextMode.Success, Messages.AbandonSuccess, String.valueOf(remainingBlocks));

            bukkitUtils.runTaskLater(player, () -> Visualization.Revert(player, playerData));

            playerData.warnedAboutMajorDeletion = false;
        }

        return true;
    }

    public String allowBuild(Player player, Location location, Material material) {
        if (!GriefPrevention.instance.claimsEnabledForWorld(location.getWorld())) return null;

        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
        Claim claim = getClaimAt(location, false, playerData.lastClaim);

        //exception: administrators in ignore claims mode
        if (playerData.ignoreClaims) return null;

        //wilderness rules
        if (claim == null) {
            //no building in the wilderness in creative mode
            if (creativeRulesApply(location) || ConfigLoader.config_claims_worldModes.get(location.getWorld()) == ClaimsMode.SurvivalRequiringClaims) {
                //exception: when chest claims are enabled, players who have zero land claims and are placing a chest
                if (material != Material.CHEST || getClaims(player.getUniqueId(), player.getName()).size() > 0 || ConfigLoader.config_claims_automaticClaimsForNewPlayersRadius == -1) {
                    String reason = MessageService.getMessage(Messages.NoBuildOutsideClaims);
                    if (player.hasPermission("griefprevention.ignoreclaims"))
                        reason += "  " + MessageService.getMessage(Messages.IgnoreClaimsAdvertisement);
                    reason += "  " + MessageService.getMessage(Messages.CreativeBasicsVideo2, DataStore.CREATIVE_VIDEO_URL);
                    return reason;
                } else {
                    return null;
                }
            }

            //but it's fine in survival mode
            else {
                return null;
            }
        }

        //if not in the wilderness, then apply claim rules (permissions, etc)
        else {
            //cache the claim for later reference
            playerData.lastClaim = claim;
            Block block = location.getBlock();

            Supplier<String> supplier = checkPermission(claim, player, ClaimPermission.Build, new BlockPlaceEvent(block, block.getState(), block, new ItemStack(material), player, true, EquipmentSlot.HAND));

            if (supplier == null) return null;

            return supplier.get();
        }
    }

    public Supplier<String> checkPermission(Claim claim, Player player, ClaimPermission permission, Event event) {
        return checkPermission(claim, player, permission, event, null);
    }

    public Supplier<String> checkPermission(Claim claim, UUID uuid, ClaimPermission permission, Event event) {
        return callPermissionCheck(claim, new ClaimPermissionCheckEvent(uuid, claim, permission, event), null);
    }

    public Supplier<String> checkPermission(Claim claim, Player player, ClaimPermission permission, Event event, Supplier<String> denialOverride) {
        return callPermissionCheck(claim, new ClaimPermissionCheckEvent(player, claim, permission, event), denialOverride);
    }

    private Supplier<String> callPermissionCheck(Claim claim, ClaimPermissionCheckEvent event, Supplier<String> denialOverride) {
        // Set denial message (if any) using default behavior.
        Supplier<String> defaultDenial = getDefaultDenial(claim, event.getCheckedPlayer(), event.getCheckedUUID(),
            event.getRequiredPermission(), event.getTriggeringEvent());
        // If permission is denied and a clarifying override is provided, use override.
        if (defaultDenial != null && denialOverride != null) {
            defaultDenial = denialOverride;
        }

        event.setDenialReason(defaultDenial);

        BukkitUtils.sendEvent(event);

        return event.getDenialReason();
    }

    private Supplier<String> getDefaultDenial(Claim claim, Player player, UUID uuid, ClaimPermission permission, Event event) {
        if (player != null) {
            // Admin claims need adminclaims permission only.
            if (claim.isAdminClaim()) {
                if (player.hasPermission("griefprevention.adminclaims")) return null;
            }

            // Anyone with deleteclaims permission can edit non-admin claims at any time.
            else if (permission == ClaimPermission.Edit && player.hasPermission("griefprevention.deleteclaims"))
                return null;
        }

        // Claim owner and admins in ignoreclaims mode have access.
        if (uuid.equals(claim.getOwnerID()) || dataStore.getPlayerData(uuid).ignoreClaims)
            return null;

        // Look for explicit individual permission.
        if (player != null) {
            if (claim.hasExplicitPermission(player, permission)) return null;
        } else {
            if (claim.hasExplicitPermission(uuid, permission)) return null;
        }

        // Check for public permission.
        if (permission.isGrantedBy(claim.getPlayerIDToClaimPermissionMap().get("public"))) return null;

        // Special building-only rules.
        if (permission == ClaimPermission.Build) {
            // No building while in PVP.
            PlayerData playerData = dataStore.getPlayerData(uuid);
            if (playerData.inPvpCombat()) {
                return () -> MessageService.getMessage(Messages.NoBuildPvP);
            }

            // Allow farming crops with container trust.
            Material material = null;
            if (event instanceof BlockBreakEvent || event instanceof BlockPlaceEvent)
                material = ((BlockEvent) event).getBlock().getType();

            if (material != null && Claim.placeableForFarming(material)
                && getDefaultDenial(claim, player, uuid, ClaimPermission.Inventory, event) == null)
                return null;
        }

        // Permission inheritance for subdivisions.
        if (claim.parent != null) {
            if (!claim.isInheritNothing())
                return getDefaultDenial(claim.parent, player, uuid, permission, event);
        }

        // Catch-all error message for all other cases.
        return () ->
        {
            String reason = MessageService.getMessage(permission.getDenialMessage(), claim.getOwnerName());
            if (player != null && player.hasPermission("griefprevention.ignoreclaims"))
                reason += "  " + MessageService.getMessage(Messages.IgnoreClaimsAdvertisement);
            return reason;
        };
    }

    public String allowBreak(Player player, Block block, Location location) {
        return this.allowBreak(player, block, location, new BlockBreakEvent(block, player));
    }

    public String allowBreak(Player player, Block block, Location location, BlockBreakEvent breakEvent) {
        if (!GriefPrevention.instance.claimsEnabledForWorld(location.getWorld())) return null;

        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
        Claim claim = getClaimAt(location, false, playerData.lastClaim);

        //exception: administrators in ignore claims mode
        if (playerData.ignoreClaims) return null;

        //wilderness rules
        if (claim == null) {
            //no building in the wilderness in creative mode
            if (creativeRulesApply(location) || ConfigLoader.config_claims_worldModes.get(location.getWorld()) == ClaimsMode.SurvivalRequiringClaims) {
                String reason = MessageService.getMessage(Messages.NoBuildOutsideClaims);
                if (player.hasPermission("griefprevention.ignoreclaims"))
                    reason += "  " + MessageService.getMessage(Messages.IgnoreClaimsAdvertisement);
                reason += "  " + MessageService.getMessage(Messages.CreativeBasicsVideo2, DataStore.CREATIVE_VIDEO_URL);
                return reason;
            }

            //but it's fine in survival mode
            else {
                return null;
            }
        } else {
            //cache the claim for later reference
            playerData.lastClaim = claim;

            //if not in the wilderness, then apply claim rules (permissions, etc)
            Supplier<String> cancel = checkPermission(claim, player, ClaimPermission.Build, breakEvent);
            if (cancel != null && breakEvent != null) {
                PreventBlockBreakEvent preventionEvent = new PreventBlockBreakEvent(breakEvent);
                Bukkit.getPluginManager().callEvent(preventionEvent);
                if (preventionEvent.isCancelled()) {
                    cancel = null;
                }
            }

            if (cancel == null) return null;

            return cancel.get();
        }
    }

    public List<Claim> getClaims(UUID playerId, String playerName) {
        return claimRepository.getClaims().stream().filter(c -> Objects.equals(c.getOwnerID(), playerId)).collect(Collectors.toList());
//        PlayerData playerData = dataStore.getPlayerData(playerId);
//        if (playerData.claims == null) {
//            playerData.claims = new Vector<>();
//            int totalClaimsArea = 0;
//            ArrayList<Claim> claims = claimRepository.getClaims();
//            for (int i = 0; i < claims.size(); i++) {
//                Claim claim = claims.get(i);
//                if (!claim.inDataStore) {
//                    claims.remove(i--);
//                    continue;
//                }
//                if (playerId.equals(claim.ownerID)) {
//                    playerData.claims.add(claim);
//                    totalClaimsArea += claim.getArea();
//                }
//            }
//
//            //if total claimed area is more than total blocks available
//            int totalBlocks = playerData.accruedClaimBlocks + playerData.getBonusClaimBlocks() + groupBonusBlocksService.getGroupBonusBlocks(playerId);
//            if (ConfigLoader.config_advanced_fixNegativeClaimblockAmounts && totalBlocks < totalClaimsArea) {
//                GriefPrevention.AddLogEntry(playerName + " has more claimed land than blocks available.  Adding blocks to fix.", CustomLogEntryTypes.Debug, true);
//                GriefPrevention.AddLogEntry(playerName + " Accrued blocks: " + claimBlockService.recalculateAccruedClaimBlocks(playerData) + " Bonus blocks: " + playerData.getBonusClaimBlocks(), CustomLogEntryTypes.Debug, true);
//                GriefPrevention.AddLogEntry("Total blocks: " + totalBlocks + " Total claimed area: " + totalClaimsArea, CustomLogEntryTypes.Debug, true);
//                for (Claim claim : playerData.claims) {
//                    if (!claim.inDataStore) continue;
//                    GriefPrevention.AddLogEntry(
//                        GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()) + " // "
//                            + GriefPrevention.getfriendlyLocationString(claim.getGreaterBoundaryCorner()) + " = "
//                            + claim.getArea()
//                        , CustomLogEntryTypes.Debug, true);
//                }
//
//                //try to fix it by adding to accrued blocks
//                playerData.accruedClaimBlocks = totalClaimsArea; //Set accrued blocks to equal total claims
//                int accruedLimit = playerData.getAccruedClaimBlocksLimit();
//                playerData.accruedClaimBlocks = Math.min(accruedLimit, playerData.accruedClaimBlocks); //set accrued blocks to maximum limit, if it's smaller
//                GriefPrevention.AddLogEntry("New accrued blocks: " + playerData.accruedClaimBlocks, CustomLogEntryTypes.Debug, true);
//
//                //Recalculate total blocks (accrued + bonus + permission group bonus)
//                totalBlocks = playerData.accruedClaimBlocks + playerData.getBonusClaimBlocks() + groupBonusBlocksService.getGroupBonusBlocks(playerId);
//                GriefPrevention.AddLogEntry("New total blocks: " + totalBlocks, CustomLogEntryTypes.Debug, true);
//
//                //if that didn't fix it, then make up the difference with bonus blocks
//                if (totalBlocks < totalClaimsArea) {
//                    int bonusBlocksToAdd = totalClaimsArea - totalBlocks;
//                    playerData.bonusClaimBlocks += bonusBlocksToAdd;
//                    GriefPrevention.AddLogEntry("Accrued blocks weren't enough. Adding " + bonusBlocksToAdd + " bonus blocks.", CustomLogEntryTypes.Debug, true);
//                }
//                GriefPrevention.AddLogEntry(playerName + " Accrued blocks: " + claimBlockService.recalculateAccruedClaimBlocks(playerData) + " Bonus blocks: " + playerData.getBonusClaimBlocks() + " Group Bonus Blocks: " + groupBonusBlocksService.getGroupBonusBlocks(playerId), CustomLogEntryTypes.Debug, true);
//                //Recalculate total blocks (accrued + bonus + permission group bonus)
//                totalBlocks = playerData.accruedClaimBlocks + playerData.getBonusClaimBlocks() + groupBonusBlocksService.getGroupBonusBlocks(playerId);
//                GriefPrevention.AddLogEntry("Total blocks: " + totalBlocks + " Total claimed area: " + totalClaimsArea, CustomLogEntryTypes.Debug, true);
//                GriefPrevention.AddLogEntry("Remaining claim blocks to use: " + claimBlockService.getRemainingClaimBlocks(playerData, getClaims(playerId, playerName)) + " (should be 0)", CustomLogEntryTypes.Debug, true);
//            }
//        }
//
//        for (int i = 0; i < playerData.claims.size(); i++) {
//            if (!playerData.claims.get(i).inDataStore) {
//                playerData.claims.remove(i--);
//            }
//        }
//
//        return playerData.claims;
    }

    //restores nature in multiple chunks, as described by a claim instance
    //this restores all chunks which have ANY number of claim blocks from this claim in them
    //if the claim is still active (in the data store), then the claimed blocks will not be changed (only the area bordering the claim)
    public void restoreClaim(Claim claim, long delayInTicks) {
        //admin claims aren't automatically cleaned up when deleted or abandoned
        if (claim.isAdminClaim()) return;

        //it's too expensive to do this for huge claims
        if (claim.getArea() > 10000) return;

        ArrayList<Chunk> chunks = claim.getChunks();
        for (Chunk chunk : chunks) {
            this.restoreChunk(chunk, GriefPrevention.getSeaLevel(chunk.getWorld()) - 15, false, delayInTicks, null);
        }
    }

    public List<Claim> deleteClaimsForPlayer(@NotNull UUID playerID, boolean releasePets) {
        List<Claim> claimsToBeDeleted = claimRepository.getAllClaimsForPlayer(playerID);
        claimRepository.deleteAllClaimsForPlayer(playerID);
        claimsToBeDeleted.forEach(claim -> deleteClaim(claim, true, releasePets));
        return claimsToBeDeleted;
    }

    public void deleteAdminClaims(boolean releasePets) {
        List<Claim> claimsToBeDeleted = claimRepository.getAllAdminClaims();
        claimRepository.deleteAdminClaims();
        claimsToBeDeleted.forEach(claim -> deleteClaim(claim, true, releasePets));
    }

    public void deleteClaim(Claim claim, boolean fireEvent, boolean releasePets) {
        claimRepository.removeFromChunkClaimMap(claim);
        if (fireEvent) {
            ClaimDeletedEvent ev = new ClaimDeletedEvent(claim, releasePets);
            BukkitUtils.sendEvent(ev);
        }
    }

    public void restoreChunk(Chunk chunk, int miny, boolean aggressiveMode, long delayInTicks, Player playerReceivingVisualization) {
        //build a snapshot of this chunk, including 1 block boundary outside of the chunk all the way around
        int maxHeight = chunk.getWorld().getMaxHeight();
        BlockSnapshot[][][] snapshots = new BlockSnapshot[18][maxHeight][18];
        Block startBlock = chunk.getBlock(0, 0, 0);
        Location startLocation = new Location(chunk.getWorld(), startBlock.getX() - 1, 0, startBlock.getZ() - 1);
        for (int x = 0; x < snapshots.length; x++) {
            for (int z = 0; z < snapshots[0][0].length; z++) {
                for (int y = 0; y < snapshots[0].length; y++) {
                    Block block = chunk.getWorld().getBlockAt(startLocation.getBlockX() + x, startLocation.getBlockY() + y, startLocation.getBlockZ() + z);
                    snapshots[x][y][z] = new BlockSnapshot(block.getLocation(), block.getType(), block.getBlockData());
                }
            }
        }

        //create task to process those data in another thread
        Location lesserBoundaryCorner = chunk.getBlock(0, 0, 0).getLocation();
        Location greaterBoundaryCorner = chunk.getBlock(15, 0, 15).getLocation();

        //create task
        //when done processing, this task will create a main thread task to actually update the world with processing results
        RestoreNatureProcessingTask task = new RestoreNatureProcessingTask(snapshots, miny,
            chunk.getWorld().getEnvironment(),
            lesserBoundaryCorner.getBlock().getBiome(),
            lesserBoundaryCorner,
            greaterBoundaryCorner,
            GriefPrevention.getSeaLevel(chunk.getWorld()),
            aggressiveMode,
            ConfigLoader.creativeRulesApply(lesserBoundaryCorner),
            playerReceivingVisualization,
            dataStore,
            this);
        GriefPrevention.instance.getServer().getScheduler().runTaskLaterAsynchronously(GriefPrevention.instance, task, delayInTicks);
    }

    //deletes all the land claims in a specified world
    public void deleteClaimsInWorld(World world, boolean deleteAdminClaims) {
        // TODO David implement this
//        List<Claim> claimsToBeDeleted = claimRepository.getAllWorldClaims();
//        for (int i = 0; i < claims.size(); i++) {
//            Claim claim = claims.get(i);
//            if (claim.getLesserBoundaryCorner().getWorld().equals(world)) {
//                if (!deleteAdminClaims && claim.isAdminClaim()) continue;
//                this.deleteClaim(claim, false, false);
//                i--;
//            }
//        }
    }

    //returns a read-only access point for the list of all land claims
    //if you need to make changes, use provided methods like .deleteClaim() and .createClaim().
    //this will ensure primary memory (RAM) and secondary memory (disk, database) stay in sync
    public List<Claim> getClaims() {
        return Collections.unmodifiableList(this.claimRepository.getClaims());
    }

    public ConcurrentHashMap<Long, ArrayList<Claim>> getChunksToClaimsMap() {
        return claimRepository.getChunksToClaimsMap();
    }

    public CreateClaimResult createClaim(World world, int x1, int x2, int y1, int y2, int z1, int z2, UUID ownerID, Claim parent, Integer id, Player creatingPlayer, boolean dryRun) {
        Claim newClaim = claimFactory.create(world, x1, x2, y1, y2, z1, z2, ownerID, parent, id);
        CreateClaimResult result = new CreateClaimResult();
        //ensure this new claim won't overlap any existing claims
        List<Claim> claimsToCheck;
        if (newClaim.parent != null) {
            claimsToCheck = newClaim.parent.children;
        } else {
            claimsToCheck = this.claimRepository.getClaims();
        }

        for (Claim otherClaim : claimsToCheck) {
            //if we find an existing claim which will be overlapped
            if (!Objects.equals(otherClaim.id, newClaim.id) && otherClaim.inDataStore && otherClaim.overlaps(newClaim)) {
                //result = fail, return conflicting claim
                result.succeeded = false;
                result.claim = otherClaim;
                return result;
            }
        }

        //if worldguard is installed, also prevent claims from overlapping any worldguard regions
        if (ConfigLoader.config_claims_respectWorldGuard && this.worldGuard != null && creatingPlayer != null) {
            if (!this.worldGuard.canBuild(newClaim.lesserBoundaryCorner, newClaim.greaterBoundaryCorner, creatingPlayer)) {
                result.succeeded = false;
                result.claim = null;
                return result;
            }
        }
        if (dryRun) {
            // since this is a dry run, just return the unsaved claim as is.
            result.succeeded = true;
            result.claim = newClaim;
            return result;
        }
//        ClaimCreatedEvent event = new ClaimCreatedEvent(newClaim, creatingPlayer);
//        Bukkit.getPluginManager().callEvent(event);
//        if (event.isCancelled()) {
//            result.succeeded = false;
//            result.claim = null;
//            return result;
//        }
        //otherwise add this new claim to the data store to make it effective
        claimRepository.addClaim(newClaim, true);

        //then return success along with reference to new claim
        result.succeeded = true;
        result.claim = newClaim;
        return result;
    }

    public void changeClaimOwner(Claim claim, UUID newOwnerID) {
        //if it's a subdivision, throw an exception
        if (claim.parent != null) {
            throw new NoTransferException("Subdivisions can't be transferred.  Only top-level claims may change owners.");
        }

        PlayerData ownerData = null;
        if (!claim.isAdminClaim()) {
            ownerData = dataStore.getPlayerData(claim.ownerID);
        }

        //call event
        ClaimTransferEvent event = new ClaimTransferEvent(claim, newOwnerID);
        Bukkit.getPluginManager().callEvent(event);

        //return if event is cancelled
        if (event.isCancelled()) return;

        //determine new owner
        PlayerData newOwnerData = null;

        if (event.getNewOwner() != null) {
            newOwnerData = dataStore.getPlayerData(event.getNewOwner());
        }

        claim.ownerID = event.getNewOwner();
        claimRepository.saveClaim(claim);
    }

    //gets all the claims "near" a location
    public Set<Claim> getNearbyClaims(Location location) {
        Set<Claim> claims = new HashSet<>();

        Chunk lesserChunk = location.getWorld().getChunkAt(location.subtract(150, 0, 150));
        Chunk greaterChunk = location.getWorld().getChunkAt(location.add(300, 0, 300));

        for (int chunk_x = lesserChunk.getX(); chunk_x <= greaterChunk.getX(); chunk_x++) {
            for (int chunk_z = lesserChunk.getZ(); chunk_z <= greaterChunk.getZ(); chunk_z++) {
                Chunk chunk = location.getWorld().getChunkAt(chunk_x, chunk_z);
                Long chunkID = HelperUtil.getChunkHash(chunk.getBlock(0, 0, 0).getLocation());
                ArrayList<Claim> claimsInChunk = claimRepository.getChunksToClaimsMap().get(chunkID);
                if (claimsInChunk != null) {
                    for (Claim claim : claimsInChunk) {
                        if (claim.inDataStore && claim.getLesserBoundaryCorner().getWorld().equals(location.getWorld())) {
                            claims.add(claim);
                        }
                    }
                }
            }
        }

        return claims;
    }

    public Claim getClaimAt(Location location, boolean ignoreHeight, boolean ignoreSubclaims, Claim cachedClaim) {
        return claimRepository.getClaimAt(location, ignoreHeight, ignoreSubclaims, cachedClaim);
    }

    public Claim getClaimAt(Location location, boolean ignoreHeight, Claim cachedClaim) {
        return claimRepository.getClaimAt(location, ignoreHeight, false, cachedClaim);
    }
}

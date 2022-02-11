package me.ryanhamshire.GriefPrevention.claims;

import be.garagepoort.mcioc.IocBean;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.Visualization;
import me.ryanhamshire.GriefPrevention.VisualizationType;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.events.ClaimExtendEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimResizeEvent;
import me.ryanhamshire.GriefPrevention.util.HelperUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@IocBean
public class ResizeClaimService {

    private final ClaimBlockService claimBlockService;
    private final ClaimService claimService;
    private final DataStore dataStore;
    private final ClaimRepository claimRepository;

    public ResizeClaimService(ClaimBlockService claimBlockService, ClaimService claimService, DataStore dataStore, ClaimRepository claimRepository) {
        this.claimBlockService = claimBlockService;
        this.claimService = claimService;
        this.dataStore = dataStore;
        this.claimRepository = claimRepository;
    }

    public void resizeClaimWithChecks(Player player, PlayerData playerData, int newx1, int newx2, int newy1, int newy2, int newz1, int newz2) {
        //for top level claims, apply size rules and claim blocks requirement
        String ownerName = playerData.claimResizing.getOwnerName();
        if (playerData.claimResizing.parent == null) {
            //measure new claim, apply size rules
            int newWidth = (Math.abs(newx1 - newx2) + 1);
            int newHeight = (Math.abs(newz1 - newz2) + 1);
            boolean smaller = newWidth < playerData.claimResizing.getWidth() || newHeight < playerData.claimResizing.getHeight();

            if (!player.hasPermission("griefprevention.adminclaims") && !playerData.claimResizing.isAdminClaim() && smaller) {
                if (newWidth < ConfigLoader.config_claims_minWidth || newHeight < ConfigLoader.config_claims_minWidth) {
                    MessageService.sendMessage(player, TextMode.Err, Messages.ResizeClaimTooNarrow, String.valueOf(ConfigLoader.config_claims_minWidth));
                    return;
                }

                int newArea = newWidth * newHeight;
                if (newArea < ConfigLoader.config_claims_minArea) {
                    MessageService.sendMessage(player, TextMode.Err, Messages.ResizeClaimInsufficientArea, String.valueOf(ConfigLoader.config_claims_minArea));
                    return;
                }
            }

            //make sure player has enough blocks to make up the difference
            if (!playerData.claimResizing.isAdminClaim() && player.getName().equals(ownerName)) {
                List<Claim> claims = claimService.getClaims(player.getUniqueId(), player.getName());
                int newArea = newWidth * newHeight;
                int blocksRemainingAfter = claimBlockService.getRemainingClaimBlocks(playerData, claims) + playerData.claimResizing.getArea() - newArea;

                if (blocksRemainingAfter < 0) {
                    MessageService.sendMessage(player, TextMode.Err, Messages.ResizeNeedMoreBlocks, String.valueOf(Math.abs(blocksRemainingAfter)));
                    HelperUtil.tryAdvertiseAdminAlternatives(player);
                    return;
                }
            }
        }

        Claim oldClaim = playerData.claimResizing;
        Claim newClaim = new Claim(oldClaim);
        World world = newClaim.getLesserBoundaryCorner().getWorld();
        newClaim.lesserBoundaryCorner = new Location(world, newx1, newy1, newz1);
        newClaim.greaterBoundaryCorner = new Location(world, newx2, newy2, newz2);

        //call event here to check if it has been cancelled
        ClaimResizeEvent event = new ClaimResizeEvent(oldClaim, newClaim, player);
        Bukkit.getPluginManager().callEvent(event);

        //return here if event is cancelled
        if (event.isCancelled()) return;

        //special rule for making a top-level claim smaller.  to check this, verifying the old claim's corners are inside the new claim's boundaries.
        //rule: in any mode, shrinking a claim removes any surface fluids
        boolean smaller = false;
        if (oldClaim.parent == null) {
            //if the new claim is smaller
            if (!newClaim.contains(oldClaim.getLesserBoundaryCorner(), true, false) || !newClaim.contains(oldClaim.getGreaterBoundaryCorner(), true, false)) {
                smaller = true;

                //remove surface fluids about to be unclaimed
                oldClaim.removeSurfaceFluids(newClaim);
            }
        }

        //ask the datastore to try and resize the claim, this checks for conflicts with other claims
        CreateClaimResult result = resizeClaim(
            playerData.claimResizing,
            newClaim.getLesserBoundaryCorner().getBlockX(),
            newClaim.getGreaterBoundaryCorner().getBlockX(),
            newClaim.getLesserBoundaryCorner().getBlockY(),
            newClaim.getGreaterBoundaryCorner().getBlockY(),
            newClaim.getLesserBoundaryCorner().getBlockZ(),
            newClaim.getGreaterBoundaryCorner().getBlockZ(),
            player);

        if (result.succeeded) {
            //decide how many claim blocks are available for more resizing
            int claimBlocksRemaining = 0;
            if (!playerData.claimResizing.isAdminClaim()) {
                UUID ownerID = playerData.claimResizing.ownerID;
                if (playerData.claimResizing.parent != null) {
                    ownerID = playerData.claimResizing.parent.ownerID;
                }
                if (ownerID == player.getUniqueId()) {
                    claimBlocksRemaining = claimBlockService.getRemainingClaimBlocks(playerData, claimService.getClaims(player.getUniqueId(), player.getName()));
                } else {
                    PlayerData ownerData = dataStore.getPlayerData(ownerID);
                    claimBlocksRemaining = claimBlockService.getRemainingClaimBlocks(ownerData, claimService.getClaims(ownerID, ownerName));
                }
            }

            //inform about success, visualize, communicate remaining blocks available
            MessageService.sendMessage(player, TextMode.Success, Messages.ClaimResizeSuccess, String.valueOf(claimBlocksRemaining));
            Visualization visualization = Visualization.FromClaim(result.claim, player.getEyeLocation().getBlockY(), VisualizationType.Claim, player.getLocation());
            Visualization.Apply(player, playerData, visualization);

            //if resizing someone else's claim, make a log entry
            if (!player.getUniqueId().equals(playerData.claimResizing.ownerID) && playerData.claimResizing.parent == null) {
                GriefPrevention.AddLogEntry(player.getName() + " resized " + ownerName + "'s claim at " + GriefPrevention.getfriendlyLocationString(playerData.claimResizing.lesserBoundaryCorner) + ".");
            }

            //if increased to a sufficiently large size and no subdivisions yet, send subdivision instructions
            if (oldClaim.getArea() < 1000 && result.claim.getArea() >= 1000 && result.claim.children.size() == 0 && !player.hasPermission("griefprevention.adminclaims")) {
                MessageService.sendMessage(player, TextMode.Info, Messages.BecomeMayor, 200L);
                MessageService.sendMessage(player, TextMode.Instr, Messages.SubdivisionVideo2, 201L, DataStore.SUBDIVISION_VIDEO_URL);
            }

            //if in a creative mode world and shrinking an existing claim, restore any unclaimed area
            if (smaller && ConfigLoader.creativeRulesApply(oldClaim.getLesserBoundaryCorner())) {
                MessageService.sendMessage(player, TextMode.Warn, Messages.UnclaimCleanupWarning);
                claimService.restoreClaim(oldClaim, 20L * 60 * 2);  //2 minutes
                GriefPrevention.AddLogEntry(player.getName() + " shrank a claim @ " + GriefPrevention.getfriendlyLocationString(playerData.claimResizing.getLesserBoundaryCorner()));
            }

            //clean up
            playerData.claimResizing = null;
            playerData.lastShovelLocation = null;
        } else {
            if (result.claim != null) {
                //inform player
                MessageService.sendMessage(player, TextMode.Err, Messages.ResizeFailOverlap);

                //show the player the conflicting claim
                Visualization visualization = Visualization.FromClaim(result.claim, player.getEyeLocation().getBlockY(), VisualizationType.ErrorClaim, player.getLocation());
                Visualization.Apply(player, playerData, visualization);
            } else {
                MessageService.sendMessage(player, TextMode.Err, Messages.ResizeFailOverlapRegion);
            }
        }
    }
    //tries to resize a claim
    //see CreateClaim() for details on return value
    synchronized public CreateClaimResult resizeClaim(Claim claim, int newx1, int newx2, int newy1, int newy2, int newz1, int newz2, Player resizingPlayer) {
        //try to create this new claim, ignoring the original when checking for overlap
        CreateClaimResult result = claimService.createClaim(claim.getLesserBoundaryCorner().getWorld(), newx1, newx2, newy1, newy2, newz1, newz2, claim.ownerID, claim.parent, claim.id, resizingPlayer, true);

        //if succeeded
        if (result.succeeded) {
            claimRepository.removeFromChunkClaimMap(claim); // remove the old boundary from the chunk cache
            // copy the boundary from the claim created in the dry run of createClaim() to our existing claim
            claim.lesserBoundaryCorner = result.claim.lesserBoundaryCorner;
            claim.greaterBoundaryCorner = result.claim.greaterBoundaryCorner;
            // Sanitize claim depth, expanding parent down to the lowest subdivision and subdivisions down to parent.
            // Also saves affected claims.
            setNewDepth(claim, claim.getLesserBoundaryCorner().getBlockY());
            result.claim = claim;
            claimRepository.addToChunkClaimMap(claim); // add the new boundary to the chunk cache
        }

        return result;
    }

    //extends a claim to a new depth
    //respects the max depth config variable
    public void extendClaim(Claim claim, int newDepth) {
        if (claim.parent != null) claim = claim.parent;

        newDepth = sanitizeClaimDepth(claim, newDepth);

        //call event and return if event got cancelled
        ClaimExtendEvent event = new ClaimExtendEvent(claim, newDepth);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        //adjust to new depth
        setNewDepth(claim, event.getNewDepth());
    }
    /**
     * Helper method for sanitizing and setting claim depth. Saves affected claims.
     *
     * @param claim    the claim
     * @param newDepth the new depth
     */
    private void setNewDepth(Claim claim, int newDepth) {
        if (claim.parent != null) claim = claim.parent;

        final int depth = sanitizeClaimDepth(claim, newDepth);

        Stream.concat(Stream.of(claim), claim.children.stream()).forEach(localClaim -> {
            localClaim.lesserBoundaryCorner.setY(depth);
            localClaim.greaterBoundaryCorner.setY(Math.max(localClaim.greaterBoundaryCorner.getBlockY(), depth));
            claimRepository.saveClaim(localClaim);
        });
    }

    private int sanitizeClaimDepth(Claim claim, int newDepth) {
        if (claim.parent != null) claim = claim.parent;

        // Get the old depth including the depth of the lowest subdivision.
        int oldDepth = Math.min(
            claim.getLesserBoundaryCorner().getBlockY(),
            claim.children.stream().mapToInt(child -> child.getLesserBoundaryCorner().getBlockY())
                .min().orElse(Integer.MAX_VALUE));

        // Use the lowest of the old and new depths.
        newDepth = Math.min(newDepth, oldDepth);
        // Cap depth to maximum depth allowed by the configuration.
        newDepth = Math.max(newDepth, ConfigLoader.config_claims_maxDepth);
        // Cap the depth to the world's minimum height.
        World world = Objects.requireNonNull(claim.getLesserBoundaryCorner().getWorld());
        newDepth = Math.max(newDepth, world.getMinHeight());

        return newDepth;
    }
}

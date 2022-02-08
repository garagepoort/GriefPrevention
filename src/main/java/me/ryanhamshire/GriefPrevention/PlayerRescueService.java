package me.ryanhamshire.GriefPrevention;

import be.garagepoort.mcioc.IocBean;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

@IocBean
public class PlayerRescueService {

    private final ClaimService claimService;

    public PlayerRescueService(ClaimService claimService) {
        this.claimService = claimService;
    }

    //moves a player from the claim he's in to a nearby wilderness location
    public Location ejectPlayer(Player player) {
        //look for a suitable location
        Location candidateLocation = player.getLocation();
        while (true) {
            Claim claim = null;
            claim = claimService.getClaimAt(candidateLocation, false, null);

            //if there's a claim here, keep looking
            if (claim != null) {
                candidateLocation = new Location(claim.lesserBoundaryCorner.getWorld(), claim.lesserBoundaryCorner.getBlockX() - 1, claim.lesserBoundaryCorner.getBlockY(), claim.lesserBoundaryCorner.getBlockZ() - 1);
                continue;
            }

            //otherwise find a safe place to teleport the player
            else {
                //find a safe height, a couple of blocks above the surface
                guaranteeChunkLoaded(candidateLocation);
                Block highestBlock = candidateLocation.getWorld().getHighestBlockAt(candidateLocation.getBlockX(), candidateLocation.getBlockZ());
                Location destination = new Location(highestBlock.getWorld(), highestBlock.getX(), highestBlock.getY() + 2, highestBlock.getZ());
                player.teleport(destination);
                return destination;
            }
        }
    }

    //ensures a piece of the managed world is loaded into server memory
    //(generates the chunk if necessary)
    private static void guaranteeChunkLoaded(Location location) {
        Chunk chunk = location.getChunk();
        while (!chunk.isLoaded() || !chunk.load(true)) ;
    }
}

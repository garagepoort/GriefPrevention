package me.ryanhamshire.GriefPrevention.claims;

import be.garagepoort.mcioc.IocBean;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimsMode;
import me.ryanhamshire.GriefPrevention.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.cmd.BusinessException;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

@IocBean
public class ClaimFactory {

    public Claim create(World world, int x1, int x2, int y1, int y2, int z1, int z2, UUID ownerID, Claim parent, Integer id) {
        CreateClaimResult result = new CreateClaimResult();

        int smallx, bigx, smally, bigy, smallz, bigz;

        int worldMinY = world.getMinHeight();
        y1 = Math.max(worldMinY, Math.max(ConfigLoader.config_claims_maxDepth, y1));
        y2 = Math.max(worldMinY, Math.max(ConfigLoader.config_claims_maxDepth, y2));

        if (x1 < x2) {
            smallx = x1;
            bigx = x2;
        } else {
            smallx = x2;
            bigx = x1;
        }

        if (y1 < y2) {
            smally = y1;
            bigy = y2;
        } else {
            smally = y2;
            bigy = y1;
        }

        if (z1 < z2) {
            smallz = z1;
            bigz = z2;
        } else {
            smallz = z2;
            bigz = z1;
        }

        if (parent != null) {
            Location lesser = parent.getLesserBoundaryCorner();
            Location greater = parent.getGreaterBoundaryCorner();
            if (smallx < lesser.getX() || smallz < lesser.getZ() || bigx > greater.getX() || bigz > greater.getZ()) {
                result.succeeded = false;
                result.claim = parent;
                throw new BusinessException("Invalid claim size. Cannot create");
            }
            smally = sanitizeClaimDepth(parent, smally);
        }

        //creative mode claims always go to bedrock
        if (ConfigLoader.config_claims_worldModes.get(world) == ClaimsMode.Creative) {
            smally = world.getMinHeight();
        }

        //create a new claim instance (but don't save it, yet)
        Claim newClaim = new Claim(
            new Location(world, smallx, smally, smallz),
            new Location(world, bigx, bigy, bigz),
            ownerID,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            id);

        newClaim.parent = parent;
        return newClaim;


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

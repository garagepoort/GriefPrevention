package me.ryanhamshire.GriefPrevention.claims;

import be.garagepoort.mcioc.IocBean;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.UUIDFetcher;
import me.ryanhamshire.GriefPrevention.database.DatabaseException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@IocBean
public class ClaimRowMapper {
    private final String locationStringDelimiter = ";";

    public Optional<Claim> map(ResultSet results) throws SQLException {
        List<World> validWorlds = Bukkit.getServer().getWorlds();

        long parentId = results.getLong("parentid");
        Integer claimID = results.getInt("id");
        boolean inheritNothing = results.getBoolean("inheritNothing");
        Location lesserBoundaryCorner = null;
        Location greaterBoundaryCorner = null;
        String lesserCornerString = "(location not available)";
        try {
            lesserCornerString = results.getString("lessercorner");
            lesserBoundaryCorner = this.locationFromString(lesserCornerString, validWorlds);
            String greaterCornerString = results.getString("greatercorner");
            greaterBoundaryCorner = this.locationFromString(greaterCornerString, validWorlds);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("World not found")) {
                GriefPrevention.AddLogEntry("Failed to load a claim (ID:" + claimID.toString() + ") because its world isn't loaded (yet?).  Please delete the claim or contact the GriefPrevention developer with information about which plugin(s) you're using to load or create worlds.  " + lesserCornerString);
                return Optional.empty();
            } else {
                throw new DatabaseException(e.getCause());
            }
        }

        String ownerName = results.getString("owner");
        UUID ownerID = null;
        if (!ownerName.isEmpty() && !ownerName.startsWith("--")) {
            try {
                ownerID = UUID.fromString(ownerName);
            } catch (Exception ex) {
                GriefPrevention.AddLogEntry("This owner entry is not a UUID: " + ownerName + ".");
                GriefPrevention.AddLogEntry("  Converted land claim to administrative @ " + lesserBoundaryCorner.toString());
            }
        }
        String buildersString = results.getString("builders");
        List<String> builderNames = Arrays.asList(buildersString.split(";"));
        builderNames = this.convertNameListToUUIDList(builderNames);

        String containersString = results.getString("containers");
        List<String> containerNames = Arrays.asList(containersString.split(";"));
        containerNames = this.convertNameListToUUIDList(containerNames);

        String accessorsString = results.getString("accessors");
        List<String> accessorNames = Arrays.asList(accessorsString.split(";"));
        accessorNames = this.convertNameListToUUIDList(accessorNames);

        String managersString = results.getString("managers");
        List<String> managerNames = Arrays.asList(managersString.split(";"));
        managerNames = this.convertNameListToUUIDList(managerNames);
        Claim claim = new Claim(lesserBoundaryCorner, greaterBoundaryCorner, ownerID, builderNames, containerNames, accessorNames, managerNames, inheritNothing, claimID);
        claim.parentId = parentId;
        return Optional.of(claim);
    }

    private Location locationFromString(String string, List<World> validWorlds) throws Exception {
        //split the input string on the space
        String[] elements = string.split(locationStringDelimiter);

        //expect four elements - world name, X, Y, and Z, respectively
        if (elements.length < 4) {
            throw new Exception("Expected four distinct parts to the location string: \"" + string + "\"");
        }

        String worldName = elements[0];
        String xString = elements[1];
        String yString = elements[2];
        String zString = elements[3];

        //identify world the claim is in
        World world = null;
        for (World w : validWorlds) {
            if (w.getName().equalsIgnoreCase(worldName)) {
                world = w;
                break;
            }
        }

        if (world == null) {
            throw new Exception("World not found: \"" + worldName + "\"");
        }

        //convert those numerical strings to integer values
        int x = Integer.parseInt(xString);
        int y = Integer.parseInt(yString);
        int z = Integer.parseInt(zString);

        return new Location(world, x, y, z);
    }


    protected List<String> convertNameListToUUIDList(List<String> names) {
        //list to build results
        List<String> resultNames = new ArrayList<>();

        for (String name : names) {
            if (name.startsWith("[") || name.equals("public")) {
                resultNames.add(name);
                continue;
            }
            UUID playerID = null;
            try {
                playerID = UUIDFetcher.getUUIDOf(name);
            } catch (Exception ex) {
            }

            //if successful, replace player name with corresponding UUID
            if (playerID != null) {
                resultNames.add(playerID.toString());
            }
        }

        return resultNames;
    }
}

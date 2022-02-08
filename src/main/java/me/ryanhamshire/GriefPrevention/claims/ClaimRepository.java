package me.ryanhamshire.GriefPrevention.claims;

import be.garagepoort.mcioc.IocBean;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.database.DatabaseException;
import me.ryanhamshire.GriefPrevention.database.SqlConnectionProvider;
import me.ryanhamshire.GriefPrevention.util.HelperUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@IocBean
public class ClaimRepository {

    private static final String SQL_SET_NEXT_CLAIM_ID = "INSERT INTO griefprevention_nextclaimid VALUES (?)";
    private static final String SQL_DELETE_NEXT_CLAIM_ID = "DELETE FROM griefprevention_nextclaimid";
    private static final String SQL_INSERT_CLAIM = "INSERT INTO griefprevention_claimdata (id, owner, lessercorner, greatercorner, builders, containers, accessors, managers, inheritnothing, parentid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_DELETE_CLAIM = "DELETE FROM griefprevention_claimdata WHERE id = ?";
    private final String locationStringDelimiter = ";";

    //next claim ID
    Long nextClaimID = (long) 0;

    private final ArrayList<Claim> claims = new ArrayList<>();
    private final ConcurrentHashMap<Long, ArrayList<Claim>> chunksToClaimsMap = new ConcurrentHashMap<>();

    private final DataStore dataStore;
    private final SqlConnectionProvider sqlConnectionProvider;
    private final ClaimRowMapper claimRowMapper;

    public ClaimRepository(DataStore dataStore, SqlConnectionProvider sqlConnectionProvider, ClaimRowMapper claimRowMapper) {
        this.dataStore = dataStore;
        this.sqlConnectionProvider = sqlConnectionProvider;
        this.claimRowMapper = claimRowMapper;

        GriefPrevention.AddLogEntry(this.claims.size() + " total claims loaded.");

        loadNextClaimId(sqlConnectionProvider);
    }

    private void loadClaimData() {
        try (Statement statement = sqlConnectionProvider.getConnection().createStatement()) {
            ResultSet results = statement.executeQuery("SELECT * FROM griefprevention_claimdata");

            ArrayList<Claim> claimsToRemove = new ArrayList<>();
            ArrayList<Claim> subdivisionsToLoad = new ArrayList<>();
            List<World> validWorlds = Bukkit.getServer().getWorlds();

            while (results.next()) {
                try {
                    long parentId = results.getLong("parentid");
                    Optional<Claim> claim = claimRowMapper.map(results);
                    if(claim.isEmpty()) {
                        continue;
                    }

                    if (parentId == -1) {
                        this.addClaim(claim.get(), false);
                    } else {
                        subdivisionsToLoad.add(claim.get());
                    }
                } catch (SQLException e) {
                    GriefPrevention.AddLogEntry("Unable to load a claim.  Details: " + e.getMessage() + " ... " + results.toString());
                    throw new DatabaseException(e.getCause());
                }
            }

            //add subdivisions to their parent claims
            for (Claim childClaim : subdivisionsToLoad) {
                //find top level claim parent
                Claim topLevelClaim = this.getClaimAt(childClaim.getLesserBoundaryCorner(), true, null);

                if (topLevelClaim == null) {
                    claimsToRemove.add(childClaim);
                    GriefPrevention.AddLogEntry("Removing orphaned claim subdivision: " + childClaim.getLesserBoundaryCorner().toString());
                    continue;
                }

                //add this claim to the list of children of the current top level claim
                childClaim.parent = topLevelClaim;
                topLevelClaim.children.add(childClaim);
                childClaim.inDataStore = true;
            }

            for (Claim claim : claimsToRemove) {
                this.deleteClaimFromSecondaryStorage(claim);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadNextClaimId(SqlConnectionProvider sqlConnectionProvider) {
        try (Statement statement = sqlConnectionProvider.getConnection().createStatement()) {
            //load next claim number into memory
            ResultSet results = statement.executeQuery("SELECT * FROM griefprevention_nextclaimid");
            //if there's nothing yet, add it
            if (!results.next()) {
                statement.execute("INSERT INTO griefprevention_nextclaimid VALUES (0)");
                this.nextClaimID = (long) 0;
            } else {
                this.nextClaimID = results.getLong("nextid");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        //RoboMWM: ensure the nextClaimID is greater than any other claim ID. If not, data corruption occurred (out of storage space, usually).
        for (Claim claim : this.claims) {
            if (claim.id >= nextClaimID) {
                GriefPrevention.instance.getLogger().severe("nextClaimID was lesser or equal to an already-existing claim ID!\n" +
                    "This usually happens if you ran out of storage space.");
                GriefPrevention.AddLogEntry("Changing nextClaimID from " + nextClaimID + " to " + claim.id, CustomLogEntryTypes.Debug, false);
                nextClaimID = claim.id + 1;
            }
        }
    }

    //adds a claim to the datastore, making it an effective claim
    public void addClaim(Claim newClaim, boolean writeToStorage) {
        //subdivisions are added under their parent, not directly to the hash map for direct search
        if (newClaim.parent != null) {
            if (!newClaim.parent.children.contains(newClaim)) {
                newClaim.parent.children.add(newClaim);
            }
            newClaim.inDataStore = true;
            if (writeToStorage) {
                this.saveClaim(newClaim);
            }
            return;
        }

        //add it and mark it as added
        this.claims.add(newClaim);
        addToChunkClaimMap(newClaim);

        newClaim.inDataStore = true;

        //except for administrative claims (which have no owner), update the owner's playerData with the new claim
        if (!newClaim.isAdminClaim() && writeToStorage) {
            PlayerData ownerData = dataStore.getPlayerData(newClaim.ownerID);
            ownerData.addClaim(newClaim);
        }

        //make sure the claim is saved to disk
        if (writeToStorage) {
            this.saveClaim(newClaim);
        }
    }

    public void addToChunkClaimMap(Claim claim) {
        if (claim.parent != null) return;

        ArrayList<Long> chunkHashes = claim.getChunkHashes();
        for (Long chunkHash : chunkHashes) {
            ArrayList<Claim> claimsInChunk = this.chunksToClaimsMap.computeIfAbsent(chunkHash, k -> new ArrayList<>());
            claimsInChunk.add(claim);
        }
    }

    public void saveClaim(Claim claim) {
        assignClaimID(claim);

        this.writeClaimToStorage(claim);
    }

    synchronized void writeClaimToStorage(Claim claim)  //see datastore.cs.  this will ALWAYS be a top level claim
    {
        try {
            //wipe out any existing data about this claim
            this.deleteClaimFromSecondaryStorage(claim);

            //write claim data to the database
            this.writeClaimData(claim);
        } catch (SQLException e) {
            GriefPrevention.AddLogEntry("Unable to save data for claim at " + this.locationToString(claim.lesserBoundaryCorner) + ".  Details:");
            GriefPrevention.AddLogEntry(e.getMessage());
            throw new DatabaseException(e.getCause());
        }
    }

    //actually writes claim data to the database
    synchronized private void writeClaimData(Claim claim) throws SQLException {
        String lesserCornerString = this.locationToString(claim.getLesserBoundaryCorner());
        String greaterCornerString = this.locationToString(claim.getGreaterBoundaryCorner());
        String owner = "";
        if (claim.ownerID != null) owner = claim.ownerID.toString();

        ArrayList<String> builders = new ArrayList<>();
        ArrayList<String> containers = new ArrayList<>();
        ArrayList<String> accessors = new ArrayList<>();
        ArrayList<String> managers = new ArrayList<>();

        claim.getPermissions(builders, containers, accessors, managers);

        String buildersString = this.storageStringBuilder(builders);
        String containersString = this.storageStringBuilder(containers);
        String accessorsString = this.storageStringBuilder(accessors);
        String managersString = this.storageStringBuilder(managers);
        boolean inheritNothing = claim.getSubclaimRestrictions();
        long parentId = claim.parent == null ? -1 : claim.parent.id;

        try (PreparedStatement insertStmt = sqlConnectionProvider.getConnection().prepareStatement(SQL_INSERT_CLAIM)) {

            insertStmt.setLong(1, claim.id);
            insertStmt.setString(2, owner);
            insertStmt.setString(3, lesserCornerString);
            insertStmt.setString(4, greaterCornerString);
            insertStmt.setString(5, buildersString);
            insertStmt.setString(6, containersString);
            insertStmt.setString(7, accessorsString);
            insertStmt.setString(8, managersString);
            insertStmt.setBoolean(9, inheritNothing);
            insertStmt.setLong(10, parentId);
            insertStmt.executeUpdate();
        } catch (SQLException e) {
            GriefPrevention.AddLogEntry("Unable to save data for claim at " + this.locationToString(claim.lesserBoundaryCorner) + ".  Details:");
            GriefPrevention.AddLogEntry(e.getMessage());
            throw new DatabaseException(e.getCause());
        }
    }

    private String locationToString(Location location) {
        StringBuilder stringBuilder = new StringBuilder(location.getWorld().getName());
        stringBuilder.append(locationStringDelimiter);
        stringBuilder.append(location.getBlockX());
        stringBuilder.append(locationStringDelimiter);
        stringBuilder.append(location.getBlockY());
        stringBuilder.append(locationStringDelimiter);
        stringBuilder.append(location.getBlockZ());

        return stringBuilder.toString();
    }

    public synchronized void deleteClaimFromSecondaryStorage(Claim claim) {
        try (PreparedStatement deleteStmnt = sqlConnectionProvider.getConnection().prepareStatement(SQL_DELETE_CLAIM)) {
            deleteStmnt.setLong(1, claim.id);
            deleteStmnt.executeUpdate();
        } catch (SQLException e) {
            GriefPrevention.AddLogEntry("Unable to delete data for claim " + claim.id + ".  Details:");
            GriefPrevention.AddLogEntry(e.getMessage());
            throw new DatabaseException(e.getCause());
        }
    }

    public synchronized void assignClaimID(Claim claim) {
        //ensure a unique identifier for the claim which will be used to name the file on disk
        if (claim.id == null || claim.id == -1) {
            claim.id = this.nextClaimID;
            this.incrementNextClaimID();
        }
    }

    synchronized void incrementNextClaimID() {
        this.setNextClaimID(this.nextClaimID + 1);
    }

    //sets the next claim ID.  used by incrementNextClaimID() above, and also while migrating data from a flat file data store
    synchronized void setNextClaimID(long nextID) {
        this.nextClaimID = nextID;

        try (Connection databaseConnection = sqlConnectionProvider.getConnection();
             PreparedStatement deleteStmnt = databaseConnection.prepareStatement(SQL_DELETE_NEXT_CLAIM_ID);
             PreparedStatement insertStmnt = databaseConnection.prepareStatement(SQL_SET_NEXT_CLAIM_ID)) {
            deleteStmnt.execute();
            insertStmnt.setLong(1, nextID);
            insertStmnt.executeUpdate();
        } catch (SQLException e) {
            GriefPrevention.AddLogEntry("Unable to set next claim ID to " + nextID + ".  Details:");
            GriefPrevention.AddLogEntry(e.getMessage());
            throw new DatabaseException(e.getCause());
        }
    }

    private String storageStringBuilder(ArrayList<String> input) {
        StringBuilder output = new StringBuilder();
        for (String string : input) {
            output.append(string).append(";");
        }
        return output.toString();
    }

    //gets the claim at a specific location
    //ignoreHeight = TRUE means that a location UNDER an existing claim will return the claim
    //cachedClaim can be NULL, but will help performance if you have a reasonable guess about which claim the location is in
    synchronized public Claim getClaimAt(Location location, boolean ignoreHeight, Claim cachedClaim) {
        return getClaimAt(location, ignoreHeight, false, cachedClaim);
    }

    /**
     * Get the claim at a specific location.
     *
     * <p>The cached claim may be null, but will increase performance if you have a reasonable idea
     * of which claim is correct.
     *
     * @param location        the location
     * @param ignoreHeight    whether or not to check containment vertically
     * @param ignoreSubclaims whether or not subclaims should be returned over claims
     * @param cachedClaim     the cached claim, if any
     * @return the claim containing the location or null if no claim exists there
     */
    synchronized public Claim getClaimAt(Location location, boolean ignoreHeight, boolean ignoreSubclaims, Claim cachedClaim) {
        //check cachedClaim guess first.  if it's in the datastore and the location is inside it, we're done
        if (cachedClaim != null && cachedClaim.inDataStore && cachedClaim.contains(location, ignoreHeight, !ignoreSubclaims))
            return cachedClaim;

        //find a top level claim
        Long chunkID = HelperUtil.getChunkHash(location);
        ArrayList<Claim> claimsInChunk = this.chunksToClaimsMap.get(chunkID);
        if (claimsInChunk == null) return null;

        for (Claim claim : claimsInChunk) {
            if (claim.inDataStore && claim.contains(location, ignoreHeight, false)) {
                // If ignoring subclaims, claim is a match.
                if (ignoreSubclaims) return claim;

                //when we find a top level claim, if the location is in one of its subdivisions,
                //return the SUBDIVISION, not the top level claim
                for (int j = 0; j < claim.children.size(); j++) {
                    Claim subdivision = claim.children.get(j);
                    if (subdivision.inDataStore && subdivision.contains(location, ignoreHeight, false))
                        return subdivision;
                }

                return claim;
            }
        }

        //if no claim found, return null
        return null;
    }

}

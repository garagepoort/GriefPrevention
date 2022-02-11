package me.ryanhamshire.GriefPrevention.claims;

import be.garagepoort.mcioc.IocBean;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.database.DatabaseException;
import me.ryanhamshire.GriefPrevention.database.SqlConnectionProvider;
import me.ryanhamshire.GriefPrevention.util.HelperUtil;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@IocBean
public class ClaimRepository {

    private static final String SQL_INSERT_CLAIM = "INSERT INTO griefprevention_claimdata (owner, lessercorner, greatercorner, builders, containers, accessors, managers, inheritnothing, parentid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_DELETE_CLAIM = "DELETE FROM griefprevention_claimdata WHERE id = ?";

    private final List<Claim> claims;
    private final ConcurrentHashMap<Long, ArrayList<Claim>> chunksToClaimsMap = new ConcurrentHashMap<>();

    private final SqlConnectionProvider sqlConnectionProvider;
    private final ClaimRowMapper claimRowMapper;

    public ClaimRepository(SqlConnectionProvider sqlConnectionProvider, ClaimRowMapper claimRowMapper) {
        this.sqlConnectionProvider = sqlConnectionProvider;
        this.claimRowMapper = claimRowMapper;
        this.claims = getAllClaims();
        GriefPrevention.AddLogEntry(this.claims.size() + " total claims loaded.");
    }

    //adds a claim to the datastore, making it an effective claim
    public void addClaim(Claim newClaim, boolean writeToStorage) {
        //subdivisions are added under their parent, not directly to the hash map for direct search
        addClaimToCache(newClaim);

        if (writeToStorage) {
            this.writeClaimToStorage(newClaim);
        }
    }

    public ConcurrentHashMap<Long, ArrayList<Claim>> getChunksToClaimsMap() {
        return chunksToClaimsMap;
    }

    private void addClaimToCache(Claim newClaim) {
        if (newClaim.parent != null) {
            if (!newClaim.parent.children.contains(newClaim)) {
                newClaim.parent.children.add(newClaim);
            }
        } else {
            //add it and mark it as added
            if(!claims.contains(newClaim)) {
                this.claims.add(newClaim);
            }
            addToChunkClaimMap(newClaim);
        }
    }

    public void removeFromChunkClaimMap(Claim claim) {
        ArrayList<Long> chunkHashes = claim.getChunkHashes();
        for (Long chunkHash : chunkHashes) {
            ArrayList<Claim> claimsInChunk = this.chunksToClaimsMap.get(chunkHash);
            if (claimsInChunk != null) {
                for (Iterator<Claim> it = claimsInChunk.iterator(); it.hasNext(); ) {
                    Claim c = it.next();
                    if (c.id.equals(claim.id)) {
                        it.remove();
                        break;
                    }
                }
                if (claimsInChunk.isEmpty()) { // if nothing's left, remove this chunk's cache
                    this.chunksToClaimsMap.remove(chunkHash);
                }
            }
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
        this.writeClaimToStorage(claim);
        addClaimToCache(claim);
    }

    synchronized void writeClaimToStorage(Claim claim)  //see datastore.cs.  this will ALWAYS be a top level claim
    {
        try {
            //wipe out any existing data about this
            if (claim.id != null) {
                this.deleteClaimFromSecondaryStorage(claim);
            }

            //write claim data to the database
            this.writeClaimData(claim);
            claim.inDataStore = true;
        } catch (SQLException e) {
            GriefPrevention.AddLogEntry("Unable to save data for claim at " + this.locationToString(claim.lesserBoundaryCorner) + ".  Details:");
            GriefPrevention.AddLogEntry(e.getMessage());
            throw new DatabaseException(e.getCause());
        }
    }

    //actually writes claim data to the database
    private void writeClaimData(Claim claim) throws SQLException {
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

        try (Connection connection = sqlConnectionProvider.getConnection();
             PreparedStatement insertStmt = connection.prepareStatement(SQL_INSERT_CLAIM, Statement.RETURN_GENERATED_KEYS)) {

            insertStmt.setString(1, owner);
            insertStmt.setString(2, lesserCornerString);
            insertStmt.setString(3, greaterCornerString);
            insertStmt.setString(4, buildersString);
            insertStmt.setString(5, containersString);
            insertStmt.setString(6, accessorsString);
            insertStmt.setString(7, managersString);
            insertStmt.setBoolean(8, inheritNothing);
            insertStmt.setLong(9, parentId);
            insertStmt.executeUpdate();

            ResultSet generatedKeys = insertStmt.getGeneratedKeys();
            int generatedKey = -1;
            if (generatedKeys.next()) {
                generatedKey = generatedKeys.getInt(1);
            }
            claim.id = generatedKey;
        } catch (SQLException e) {
            GriefPrevention.AddLogEntry("Unable to save data for claim at " + this.locationToString(claim.lesserBoundaryCorner) + ".  Details:");
            GriefPrevention.AddLogEntry(e.getMessage());
            throw new DatabaseException(e.getCause());
        }
    }

    private String locationToString(Location location) {
        StringBuilder stringBuilder = new StringBuilder(location.getWorld().getName());
        String locationStringDelimiter = ";";
        stringBuilder.append(locationStringDelimiter);
        stringBuilder.append(location.getBlockX());
        stringBuilder.append(locationStringDelimiter);
        stringBuilder.append(location.getBlockY());
        stringBuilder.append(locationStringDelimiter);
        stringBuilder.append(location.getBlockZ());

        return stringBuilder.toString();
    }

    public synchronized void deleteClaimFromSecondaryStorage(Claim claim) {
        try (Connection connection = sqlConnectionProvider.getConnection();
             PreparedStatement deleteStmnt = connection.prepareStatement(SQL_DELETE_CLAIM)) {
            deleteStmnt.setInt(1, claim.id);
            deleteStmnt.executeUpdate();
        } catch (SQLException e) {
            GriefPrevention.AddLogEntry("Unable to delete data for claim " + claim.id + ".  Details:");
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

    synchronized public Claim getClaimAt(Location location, boolean ignoreHeight, boolean ignoreSubclaims, Claim cachedClaim) {
//        //check cachedClaim guess first.  if it's in the datastore and the location is inside it, we're done
//        if (cachedClaim != null && cachedClaim.inDataStore && cachedClaim.contains(location, ignoreHeight, !ignoreSubclaims))
//            return cachedClaim;

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

    public List<Claim> getAllClaimsForPlayer(UUID playerId) {
        try (Connection sql = sqlConnectionProvider.getConnection();
             PreparedStatement selectParentClaims = sql.prepareStatement("SELECT * FROM griefprevention_claimdata WHERE owner = ? and parentid = ?");
             PreparedStatement selectSubdivisions = sql.prepareStatement("SELECT * FROM griefprevention_claimdata WHERE owner = ? and parentid != ?")) {
            selectParentClaims.setString(1, playerId.toString());
            selectParentClaims.setInt(2, -1);
            selectSubdivisions.setString(1, playerId.toString());
            selectSubdivisions.setInt(2, -1);

            return loadClaims(selectParentClaims, selectSubdivisions);
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    public List<Claim> getAllAdminClaims() {
        try (Connection sql = sqlConnectionProvider.getConnection();
             PreparedStatement selectParentClaims = sql.prepareStatement("SELECT * FROM griefprevention_claimdata WHERE owner = ? and parentid = ?");
             PreparedStatement selectSubdivisions = sql.prepareStatement("SELECT * FROM griefprevention_claimdata WHERE owner = ? and parentid != ?")) {
            selectParentClaims.setNull(1, Types.VARCHAR);
            selectParentClaims.setInt(2, -1);
            selectSubdivisions.setNull(1, Types.VARCHAR);
            selectSubdivisions.setInt(2, -1);

            return loadClaims(selectParentClaims, selectSubdivisions);
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    public List<Claim> getAllClaims() {
        try (Connection sql = sqlConnectionProvider.getConnection();
             PreparedStatement selectParentClaims = sql.prepareStatement("SELECT * FROM griefprevention_claimdata WHERE parentid = ?");
             PreparedStatement selectSubdivisions = sql.prepareStatement("SELECT * FROM griefprevention_claimdata WHERE parentid != ?")) {
            selectParentClaims.setInt(1, -1);
            selectSubdivisions.setInt(1, -1);

            return loadClaims(selectParentClaims, selectSubdivisions);
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    private List<Claim> loadClaims(PreparedStatement selectParentClaims, PreparedStatement selectSubdivisions) throws SQLException {
        List<Claim> parentClaims = new ArrayList<>();
        try (ResultSet rs = selectParentClaims.executeQuery()) {
            while (rs.next()) {
                claimRowMapper.map(rs).ifPresent(parentClaims::add);
            }
        }

        List<Claim> subDivisions = new ArrayList<>();
        try (ResultSet rs = selectSubdivisions.executeQuery()) {
            while (rs.next()) {
                claimRowMapper.map(rs).ifPresent(subDivisions::add);
            }
        }

        for (Claim parentClaim : parentClaims) {
            parentClaim.setChildren(subDivisions.stream().filter(s -> s.parentId == parentClaim.id).collect(Collectors.toList()));
        }
        return parentClaims;
    }

    public void deleteAllClaimsForPlayer(UUID playerID) {
        try (Connection sql = sqlConnectionProvider.getConnection();
             PreparedStatement insert = sql.prepareStatement("DELETE FROM griefprevention_claimdata WHERE owner = ?");) {
            insert.setString(1, playerID.toString());
            insert.executeUpdate();
            claims.removeIf(c -> Objects.equals(playerID, c.ownerID));
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    public void deleteAdminClaims() {
        try (Connection sql = sqlConnectionProvider.getConnection();
             PreparedStatement insert = sql.prepareStatement("DELETE FROM griefprevention_claimdata WHERE owner = ?");) {
            insert.setNull(1, Types.VARCHAR);
            insert.executeUpdate();
            claims.removeIf(c -> c.ownerID == null);
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    public List<Claim> getClaims() {
        return claims;
    }
}

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

import me.ryanhamshire.GriefPrevention.database.DatabaseException;
import me.ryanhamshire.GriefPrevention.database.SqlConnectionProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//manages data stored in the file system
public class DatabaseDataStore extends DataStore {

    private static final String SQL_UPDATE_NAME =
        "UPDATE griefprevention_playerdata SET name = ? WHERE name = ?";
    private static final String SQL_SELECT_PLAYER_DATA =
        "SELECT * FROM griefprevention_playerdata WHERE name = ?";
    private static final String SQL_DELETE_PLAYER_DATA =
        "DELETE FROM griefprevention_playerdata WHERE name = ?";
    private static final String SQL_INSERT_PLAYER_DATA =
        "INSERT INTO griefprevention_playerdata (name, lastlogin, accruedblocks, bonusblocks) VALUES (?, ?, ?, ?)";
    private static final String SQL_DELETE_GROUP_DATA =
        "DELETE FROM griefprevention_playerdata WHERE name = ?";
    private static final String SQL_INSERT_SCHEMA_VERSION =
        "INSERT INTO griefprevention_schemaversion VALUES (?)";
    private static final String SQL_DELETE_SCHEMA_VERSION =
        "DELETE FROM griefprevention_schemaversion";
    private static final String SQL_SELECT_SCHEMA_VERSION =
        "SELECT * FROM griefprevention_schemaversion";

    private final SqlConnectionProvider sqlConnectionProvider;

    public DatabaseDataStore(SqlConnectionProvider sqlConnectionProvider) throws Exception {
        super();
        this.sqlConnectionProvider = sqlConnectionProvider;

        this.initialize();
        for (Player player : Bukkit.getOnlinePlayers()) {
            new IgnoreLoaderThread(player.getUniqueId(), getPlayerData(player.getUniqueId()).ignoredPlayers).start();
        }
    }

    @Override
    void initialize() throws Exception {
        try (Connection connection = sqlConnectionProvider.getConnection()) {

            try (Statement statement = connection.createStatement()) {
                //ensure the data tables exist
                statement.execute("CREATE TABLE IF NOT EXISTS griefprevention_claimdata (id INTEGER AUTO_INCREMENT, owner VARCHAR(50), lessercorner VARCHAR(100), greatercorner VARCHAR(100), builders TEXT, containers TEXT, accessors TEXT, managers TEXT, inheritnothing BOOLEAN, parentid INTEGER, PRIMARY KEY (ID))");
                statement.execute("CREATE TABLE IF NOT EXISTS griefprevention_playerdata (name VARCHAR(50), lastlogin DATETIME, accruedblocks INTEGER, bonusblocks INTEGER, PRIMARY KEY (name))");
                statement.execute("CREATE TABLE IF NOT EXISTS griefprevention_schemaversion (version INTEGER)");

                statement.execute("ALTER TABLE griefprevention_claimdata MODIFY builders TEXT");
                statement.execute("ALTER TABLE griefprevention_claimdata MODIFY containers TEXT");
                statement.execute("ALTER TABLE griefprevention_claimdata MODIFY accessors TEXT");
                statement.execute("ALTER TABLE griefprevention_claimdata MODIFY managers TEXT");

            } catch (Exception e3) {
                GriefPrevention.AddLogEntry("ERROR: Unable to create the necessary database table.  Details:");
                GriefPrevention.AddLogEntry(e3.getMessage());
                e3.printStackTrace();
                throw e3;
            }

            super.initialize();
        } catch (Exception e2) {
            GriefPrevention.AddLogEntry("ERROR: Unable to connect to database.  Check your config file settings.");
            throw e2;
        }
    }

    @Override
    Optional<PlayerData> getPlayerDataFromStorage(UUID playerID) {

        try (Connection connection = sqlConnectionProvider.getConnection();
             PreparedStatement selectStmnt = connection.prepareStatement(SQL_SELECT_PLAYER_DATA)) {
            selectStmnt.setString(1, playerID.toString());
            try (ResultSet results = selectStmnt.executeQuery()) {
                //if data for this player exists, use it
                if (results.next()) {
                    PlayerData playerData = new PlayerData(playerID);
                    playerData.setAccruedClaimBlocks(results.getInt("accruedblocks"));
                    playerData.setBonusClaimBlocks(results.getInt("bonusblocks"));
                    return Optional.of(playerData);
                }
            }
        } catch (SQLException e) {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            GriefPrevention.AddLogEntry(playerID + " " + errors.toString(), CustomLogEntryTypes.Exception);
            throw new DatabaseException(e.getCause());
        }

        return Optional.empty();
    }

    //saves changes to player data.  MUST be called after you're done making changes, otherwise a reload will lose them
    @Override
    public void overrideSavePlayerData(UUID playerID, PlayerData playerData) {
        //never save data for the "administrative" account.  an empty string for player name indicates administrative account
        if (playerID == null) return;

        this.savePlayerData(playerID.toString(), playerData);
    }

    @Override
    public ConcurrentHashMap<String, Integer> getGroupBonusBlocks() {
        ConcurrentHashMap<String, Integer> permissionBonusBlocks = new ConcurrentHashMap<>();

        try (Connection connection = sqlConnectionProvider.getConnection();
             Statement statement = connection.createStatement()) {
            try (ResultSet results = statement.executeQuery("SELECT * FROM griefprevention_playerdata")) {
                while (results.next()) {
                    String name = results.getString("name");
                    if (!name.startsWith("$")) continue;
                    String groupName = name.substring(1);
                    if (groupName.isEmpty()) continue;
                    int groupBonusBlocks = results.getInt("bonusblocks");
                    permissionBonusBlocks.put(groupName, groupBonusBlocks);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException(e.getCause());
        }
        return permissionBonusBlocks;
    }

    private void savePlayerData(String playerID, PlayerData playerData) {
        try (Connection databaseConnection = sqlConnectionProvider.getConnection();
             PreparedStatement deleteStmnt = databaseConnection.prepareStatement(SQL_DELETE_PLAYER_DATA);
             PreparedStatement insertStmnt = databaseConnection.prepareStatement(SQL_INSERT_PLAYER_DATA)) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(playerID));

            SimpleDateFormat sqlFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String dateString = sqlFormat.format(new Date(player.getLastPlayed()));
            deleteStmnt.setString(1, playerID);
            deleteStmnt.executeUpdate();

            insertStmnt.setString(1, playerID);
            insertStmnt.setString(2, dateString);
            insertStmnt.setInt(3, playerData.accruedClaimBlocks);
            insertStmnt.setInt(4, playerData.getBonusClaimBlocks());
            insertStmnt.executeUpdate();
        } catch (SQLException e) {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            GriefPrevention.AddLogEntry(playerID + " " + errors.toString(), CustomLogEntryTypes.Exception);
            throw new DatabaseException(e.getCause());
        }
    }

    //updates the database with a group's bonus blocks
    @Override
    synchronized void saveGroupBonusBlocks(String groupName, int currentValue) {
        //group bonus blocks are stored in the player data table, with player name = $groupName
        try (Connection databaseConnection = sqlConnectionProvider.getConnection();
             PreparedStatement deleteStmnt = databaseConnection.prepareStatement(SQL_DELETE_GROUP_DATA);
             PreparedStatement insertStmnt = databaseConnection.prepareStatement(SQL_INSERT_PLAYER_DATA)) {
            SimpleDateFormat sqlFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String dateString = sqlFormat.format(new Date());
            deleteStmnt.setString(1, '$' + groupName);
            deleteStmnt.executeUpdate();

            insertStmnt.setString(1, '$' + groupName);
            insertStmnt.setString(2, dateString);
            insertStmnt.setInt(3, 0);
            insertStmnt.setInt(4, currentValue);
            insertStmnt.executeUpdate();
        } catch (SQLException e) {
            GriefPrevention.AddLogEntry("Unable to save data for group " + groupName + ".  Details:");
            GriefPrevention.AddLogEntry(e.getMessage());
            throw new DatabaseException(e.getCause());
        }
    }

    @Override
    protected int getSchemaVersionFromStorage() {
        try (Connection connection = sqlConnectionProvider.getConnection();
             PreparedStatement selectStmnt = connection.prepareStatement(SQL_SELECT_SCHEMA_VERSION)) {
            try (ResultSet results = selectStmnt.executeQuery()) {
                //if there's nothing yet, assume 0 and add it
                if (!results.next()) {
                    this.setSchemaVersion(0);
                    return 0;
                }
                //otherwise return the value that's in the table
                else {
                    return results.getInt("version");
                }
            }
        } catch (SQLException e) {
            GriefPrevention.AddLogEntry("Unable to retrieve schema version from database.  Details:");
            GriefPrevention.AddLogEntry(e.getMessage());
            throw new DatabaseException(e.getCause());
        }
    }

    @Override
    protected void updateSchemaVersionInStorage(int versionToSet) {
        try (Connection databaseConnection = sqlConnectionProvider.getConnection();
             PreparedStatement deleteStmnt = databaseConnection.prepareStatement(SQL_DELETE_SCHEMA_VERSION);
             PreparedStatement insertStmnt = databaseConnection.prepareStatement(SQL_INSERT_SCHEMA_VERSION)) {
            deleteStmnt.execute();

            insertStmnt.setInt(1, versionToSet);
            insertStmnt.executeUpdate();
        } catch (SQLException e) {
            GriefPrevention.AddLogEntry("Unable to set next schema version to " + versionToSet + ".  Details:");
            GriefPrevention.AddLogEntry(e.getMessage());
            throw new DatabaseException(e.getCause());
        }
    }
}

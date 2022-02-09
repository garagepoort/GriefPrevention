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

import com.google.common.io.Files;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//singleton class which manages all GriefPrevention data (except for config options)
public abstract class DataStore {

    //in-memory cache for player data
    protected ConcurrentHashMap<UUID, PlayerData> playerNameToPlayerDataMap = new ConcurrentHashMap<>();

    //path information, for where stuff stored on disk is well...  stored
    protected final static String dataLayerFolderPath = "plugins" + File.separator + "GriefPreventionData";
    final static String playerDataFolderPath = dataLayerFolderPath + File.separator + "PlayerData";
    public final static String configFilePath = dataLayerFolderPath + File.separator + "config.yml";
    final static String softMuteFilePath = dataLayerFolderPath + File.separator + "softMute.txt";
    final static String bannedWordsFilePath = dataLayerFolderPath + File.separator + "bannedWords.txt";

    //the latest version of the data schema implemented here
    protected static final int latestSchemaVersion = 3;

    //reading and writing the schema version to the data store
    abstract int getSchemaVersionFromStorage();

    abstract void updateSchemaVersionInStorage(int versionToSet);

    //current version of the schema of data in secondary storage
    private int currentSchemaVersion = -1;  //-1 means not determined yet

    //video links
    public static final String SURVIVAL_VIDEO_URL = "" + ChatColor.DARK_AQUA + ChatColor.UNDERLINE + "bit.ly/mcgpuser" + ChatColor.RESET;
    public static final String CREATIVE_VIDEO_URL = "" + ChatColor.DARK_AQUA + ChatColor.UNDERLINE + "bit.ly/mcgpcrea" + ChatColor.RESET;
    public static final String SUBDIVISION_VIDEO_URL = "" + ChatColor.DARK_AQUA + ChatColor.UNDERLINE + "bit.ly/mcgpsub" + ChatColor.RESET;

    //list of UUIDs which are soft-muted
    ConcurrentHashMap<UUID, Boolean> softMuteMap = new ConcurrentHashMap<>();

    protected int getSchemaVersion() {
        if (this.currentSchemaVersion >= 0) {
            return this.currentSchemaVersion;
        } else {
            this.currentSchemaVersion = this.getSchemaVersionFromStorage();
            return this.currentSchemaVersion;
        }
    }

    protected void setSchemaVersion(int versionToSet) {
        this.currentSchemaVersion = versionToSet;
        this.updateSchemaVersionInStorage(versionToSet);
    }

    //initialization!
    void initialize() throws Exception {
        //ensure data folders exist
        File playerDataFolder = new File(playerDataFolderPath);
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }

        //load list of soft mutes
        this.loadSoftMutes();
    }

    private void loadSoftMutes() {
        File softMuteFile = new File(softMuteFilePath);
        if (softMuteFile.exists()) {
            BufferedReader inStream = null;
            try {
                //open the file
                inStream = new BufferedReader(new FileReader(softMuteFile.getAbsolutePath()));

                //while there are lines left
                String nextID = inStream.readLine();
                while (nextID != null) {
                    //parse line into a UUID
                    UUID playerID;
                    try {
                        playerID = UUID.fromString(nextID);
                    } catch (Exception e) {
                        playerID = null;
                        GriefPrevention.AddLogEntry("Failed to parse soft mute entry as a UUID: " + nextID);
                    }

                    //push it into the map
                    if (playerID != null) {
                        this.softMuteMap.put(playerID, true);
                    }

                    //move to the next
                    nextID = inStream.readLine();
                }
            } catch (Exception e) {
                GriefPrevention.AddLogEntry("Failed to read from the soft mute data file: " + e.toString());
                e.printStackTrace();
            }

            try {
                if (inStream != null) inStream.close();
            } catch (IOException exception) {
            }
        }
    }

    public List<String> loadBannedWords() {
        try {
            File bannedWordsFile = new File(bannedWordsFilePath);
            if (!bannedWordsFile.exists()) {
                Files.touch(bannedWordsFile);
                String defaultWords =
                    "nigger\nniggers\nniger\nnigga\nnigers\nniggas\n" +
                        "fag\nfags\nfaggot\nfaggots\nfeggit\nfeggits\nfaggit\nfaggits\n" +
                        "cunt\ncunts\nwhore\nwhores\nslut\nsluts\n";
                Files.append(defaultWords, bannedWordsFile, Charset.forName("UTF-8"));
            }

            return Files.readLines(bannedWordsFile, Charset.forName("UTF-8"));
        } catch (Exception e) {
            GriefPrevention.AddLogEntry("Failed to read from the banned words data file: " + e.toString());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    //updates soft mute map and data file
    public boolean toggleSoftMute(UUID playerID) {
        boolean newValue = !this.isSoftMuted(playerID);

        this.softMuteMap.put(playerID, newValue);
        this.saveSoftMutes();

        return newValue;
    }

    public boolean isSoftMuted(UUID playerID) {
        Boolean mapEntry = this.softMuteMap.get(playerID);
        if (mapEntry == null || mapEntry == Boolean.FALSE) {
            return false;
        }

        return true;
    }

    private void saveSoftMutes() {
        BufferedWriter outStream = null;

        try {
            //open the file and write the new value
            File softMuteFile = new File(softMuteFilePath);
            softMuteFile.createNewFile();
            outStream = new BufferedWriter(new FileWriter(softMuteFile));

            for (Map.Entry<UUID, Boolean> entry : softMuteMap.entrySet()) {
                if (entry.getValue().booleanValue()) {
                    outStream.write(entry.getKey().toString());
                    outStream.newLine();
                }
            }
        }

        //if any problem, log it
        catch (Exception e) {
            GriefPrevention.AddLogEntry("Unexpected exception saving soft mute data: " + e.getMessage());
            e.printStackTrace();
        }

        //close the file
        try {
            if (outStream != null) outStream.close();
        } catch (IOException exception) {
        }
    }

    //removes cached player data from memory
    public synchronized void clearCachedPlayerData(UUID playerID) {
        this.playerNameToPlayerDataMap.remove(playerID);
    }

    abstract void saveGroupBonusBlocks(String groupName, int amount);

    public PlayerData getPlayerData(UUID playerID) {
        if (this.playerNameToPlayerDataMap.containsKey(playerID)) {
            return playerNameToPlayerDataMap.get(playerID);
        }
        PlayerData playerData = getPlayerDataFromStorage(playerID).orElseGet(() -> new PlayerData(playerID));
        this.playerNameToPlayerDataMap.put(playerID, playerData);
        return playerData;
    }

    abstract Optional<PlayerData> getPlayerDataFromStorage(UUID playerID);

    //saves changes to player data to secondary storage.  MUST be called after you're done making changes, otherwise a reload will lose them
    public void savePlayerDataSync(UUID playerID, PlayerData playerData) {
        this.asyncSavePlayerData(playerID, playerData);
    }

    //saves changes to player data to secondary storage.  MUST be called after you're done making changes, otherwise a reload will lose them
    public void savePlayerData(UUID playerID, PlayerData playerData) {
        new SavePlayerDataThread(playerID, playerData).start();
    }

    public void asyncSavePlayerData(UUID playerID, PlayerData playerData) {
        //save everything except the ignore list
        this.overrideSavePlayerData(playerID, playerData);

        //save the ignore list
        if (playerData.ignoreListChanged) {
            StringBuilder fileContent = new StringBuilder();
            try {
                for (UUID uuidKey : playerData.ignoredPlayers.keySet()) {
                    Boolean value = playerData.ignoredPlayers.get(uuidKey);
                    if (value == null) continue;

                    //admin-enforced ignores begin with an asterisk
                    if (value) {
                        fileContent.append("*");
                    }

                    fileContent.append(uuidKey);
                    fileContent.append("\n");
                }

                //write data to file
                File playerDataFile = new File(playerDataFolderPath + File.separator + playerID + ".ignore");
                Files.write(fileContent.toString().trim().getBytes("UTF-8"), playerDataFile);
            }

            //if any problem, log it
            catch (Exception e) {
                GriefPrevention.AddLogEntry("GriefPrevention: Unexpected exception saving data for player \"" + playerID.toString() + "\": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    abstract void overrideSavePlayerData(UUID playerID, PlayerData playerData);

    //extend a siege, if it's possible to do so
    synchronized void tryExtendSiege(Player player, Claim claim) {
        PlayerData playerData = this.getPlayerData(player.getUniqueId());

        //player must be sieged
        if (playerData.siegeData == null) return;

        //claim isn't already under the same siege
        if (playerData.siegeData.claims.contains(claim)) return;

        //admin claims can't be sieged
        if (claim.isAdminClaim()) return;

        //player must have some level of permission to be sieged in a claim
        Claim currentClaim = claim;
        while (!currentClaim.hasExplicitPermission(player, ClaimPermission.Access)) {
            if (currentClaim.parent == null) return;
            currentClaim = currentClaim.parent;
        }

        //otherwise extend the siege
        playerData.siegeData.claims.add(claim);
        claim.siegeData = playerData.siegeData;
    }

    private class SavePlayerDataThread extends Thread {
        private final UUID playerID;
        private final PlayerData playerData;

        SavePlayerDataThread(UUID playerID, PlayerData playerData) {
            this.playerID = playerID;
            this.playerData = playerData;
        }

        public void run() {
            //ensure player data is already read from file before trying to save
            playerData.getAccruedClaimBlocks();
            playerData.getClaims();
            asyncSavePlayerData(this.playerID, this.playerData);
        }
    }

    public abstract ConcurrentHashMap<String, Integer> getGroupBonusBlocks();
}

package me.ryanhamshire.GriefPrevention;

import be.garagepoort.mcioc.IocBean;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@IocBean
public class GroupBonusBlocksService {

    private final DataStore dataStore;


    //in-memory cache for group (permission-based) data
    protected ConcurrentHashMap<String, Integer> permissionToBonusBlocksMap;

    public GroupBonusBlocksService(DataStore dataStore) {
        this.dataStore = dataStore;
        permissionToBonusBlocksMap = dataStore.getGroupBonusBlocks();
    }

    //gets the number of bonus blocks a player has from his permissions
    //Bukkit doesn't allow for checking permissions of an offline player.
    //this will return 0 when he's offline, and the correct number when online.
    public int getGroupBonusBlocks(UUID playerID) {
        Player player = GriefPrevention.instance.getServer().getPlayer(playerID);

        if (player == null) return 0;

        int bonusBlocks = 0;

        for (Map.Entry<String, Integer> groupEntry : this.permissionToBonusBlocksMap.entrySet()) {
            if (player.hasPermission(groupEntry.getKey())) {
                bonusBlocks += groupEntry.getValue();
            }
        }

        return bonusBlocks;
    }

    public int adjustGroupBonusBlocks(String groupName, int amount) {
        Integer currentValue = this.permissionToBonusBlocksMap.get(groupName);
        if (currentValue == null) currentValue = 0;

        currentValue += amount;
        this.permissionToBonusBlocksMap.put(groupName, currentValue);

        //write changes to storage to ensure they don't get lost
        dataStore.saveGroupBonusBlocks(groupName, currentValue);

        return currentValue;
    }

}

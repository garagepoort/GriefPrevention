package me.ryanhamshire.GriefPrevention.util;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;

public class HelperUtil {


    //educates a player about /adminclaims and /acb, if he can use them
    public static void tryAdvertiseAdminAlternatives(Player player) {
        if (player.hasPermission("griefprevention.adminclaims") && player.hasPermission("griefprevention.adjustclaimblocks")) {
            MessageService.sendMessage(player, TextMode.Info, Messages.AdvertiseACandACB);
        } else if (player.hasPermission("griefprevention.adminclaims")) {
            MessageService.sendMessage(player, TextMode.Info, Messages.AdvertiseAdminClaims);
        } else if (player.hasPermission("griefprevention.adjustclaimblocks")) {
            MessageService.sendMessage(player, TextMode.Info, Messages.AdvertiseACB);
        }
    }

    //gets an almost-unique, persistent identifier for a chunk
    public static Long getChunkHash(long chunkx, long chunkz) {
        return (chunkz ^ (chunkx << 32));
    }

    //gets an almost-unique, persistent identifier for a chunk
    public static Long getChunkHash(Location location) {
        return getChunkHash(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public static ArrayList<Long> getChunkHashes(Claim claim) {
        return getChunkHashes(claim.getLesserBoundaryCorner(), claim.getGreaterBoundaryCorner());
    }

    public static ArrayList<Long> getChunkHashes(Location min, Location max) {
        ArrayList<Long> hashes = new ArrayList<>();
        int smallX = min.getBlockX() >> 4;
        int smallZ = min.getBlockZ() >> 4;
        int largeX = max.getBlockX() >> 4;
        int largeZ = max.getBlockZ() >> 4;

        for (int x = smallX; x <= largeX; x++) {
            for (int z = smallZ; z <= largeZ; z++) {
                hashes.add(getChunkHash(x, z));
            }
        }

        return hashes;
    }
}

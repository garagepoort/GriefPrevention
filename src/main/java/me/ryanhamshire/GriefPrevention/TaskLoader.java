package me.ryanhamshire.GriefPrevention;

import be.garagepoort.mcioc.AfterIocLoad;
import be.garagepoort.mcioc.TubingConfiguration;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@TubingConfiguration
public class TaskLoader {

    @AfterIocLoad
    public static void loadDeliverClaimBlocksTask(PlayerDataRepository playerDataRepository) {
        if (ConfigLoader.config_claims_blocksAccruedPerHour_default <= 0) {
            return;
        }

        long i = 0;
        for (Player onlinePlayer : Bukkit.getServer().getOnlinePlayers()) {
            DeliverClaimBlocksTask newTask = new DeliverClaimBlocksTask(onlinePlayer, playerDataRepository);
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.get(), newTask, i++);
        }
    }

    @AfterIocLoad
    public static void loadEntityCleanupTask(PlayerDataRepository playerDataRepository, ClaimService claimService) {
        //start the recurring cleanup event for entities in creative worlds
        EntityCleanupTask task = new EntityCleanupTask(0, playerDataRepository, claimService);
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.instance, task, 20L * 60 * 2);

    }
}

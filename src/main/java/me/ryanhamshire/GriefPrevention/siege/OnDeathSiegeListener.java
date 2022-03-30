package me.ryanhamshire.GriefPrevention.siege;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocListener;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.SiegeService;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

@IocListener
public class OnDeathSiegeListener implements Listener {
    private final PlayerDataRepository playerDataRepository;
    private final SiegeService siegeService;
    private final BukkitUtils bukkitUtils;

    public OnDeathSiegeListener(PlayerDataRepository playerDataRepository, SiegeService siegeService, BukkitUtils bukkitUtils) {
        this.playerDataRepository = playerDataRepository;
        this.siegeService = siegeService;
        this.bukkitUtils = bukkitUtils;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!GriefPrevention.get().claimsEnabledForWorld(entity.getWorld())) return;
        if (entity.getType() != EntityType.PLAYER) return;

        Player player = (Player) entity;
        bukkitUtils.runTaskAsync(() -> {
            PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());
            if (playerData.siegeData != null) {
                this.siegeService.endSiege(playerData.siegeData, null, player.getName(), event.getDrops());
            }
        });
    }
}

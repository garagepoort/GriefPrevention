package me.ryanhamshire.GriefPrevention.listeners;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocListener;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PendingItemProtection;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.claims.ClaimRepository;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.events.ProtectDeathDropsEvent;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

@IocBean
@IocListener
public class OnDeath implements Listener {
    private final DataStore dataStore;
    private final ClaimRepository claimService;
    private final BukkitUtils bukkitUtils;

    public OnDeath(DataStore dataStore, ClaimRepository claimService, BukkitUtils bukkitUtils) {
        this.dataStore = dataStore;
        this.claimService = claimService;
        this.bukkitUtils = bukkitUtils;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity.getType() != EntityType.PLAYER) return;
        if (!GriefPrevention.get().claimsEnabledForWorld(entity.getWorld())) return;

        if (ConfigLoader.creativeRulesApply(entity.getLocation())) {
            event.setDroppedExp(0);
            event.getDrops().clear();
        }

        Player player = (Player) entity;
        World world = entity.getWorld();

        bukkitUtils.runTaskAsync(() -> {
            PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
            boolean isPvPWorld = GriefPrevention.get().pvpRulesApply(world);
            if ((isPvPWorld && ConfigLoader.config_lockDeathDropsInPvpWorlds) ||
                (!isPvPWorld && ConfigLoader.config_lockDeathDropsInNonPvpWorlds)) {
                Claim claim = this.claimService.getClaimAt(player.getLocation(), false, playerData.lastClaim);
                ProtectDeathDropsEvent protectionEvent = new ProtectDeathDropsEvent(claim);
                BukkitUtils.sendEvent(protectionEvent);
                if (!protectionEvent.isCancelled()) {
                    //remember information about these drops so that they can be marked when they spawn as items
                    long expirationTime = System.currentTimeMillis() + 3000;  //now + 3 seconds
                    Location deathLocation = player.getLocation();
                    UUID playerID = player.getUniqueId();
                    List<ItemStack> drops = event.getDrops();
                    for (ItemStack stack : drops) {
                        GriefPrevention.get().pendingItemWatchList.add(
                            new PendingItemProtection(deathLocation, playerID, expirationTime, stack));
                    }

                    //allow the player to receive a message about how to unlock any drops
                    playerData.dropsAreUnlocked = false;
                    playerData.receivedDropUnlockAdvertisement = false;
                }
            }
        });
    }
}

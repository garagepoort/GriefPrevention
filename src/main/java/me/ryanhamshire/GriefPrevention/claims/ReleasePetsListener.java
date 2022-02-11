package me.ryanhamshire.GriefPrevention.claims;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocListener;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent;
import org.bukkit.Chunk;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.InventoryHolder;

import java.util.Arrays;
import java.util.UUID;

@IocBean
@IocListener
public class ReleasePetsListener implements Listener {

    @EventHandler
    public void onClaimDeletion(ClaimDeletedEvent event) {
        Claim claim = event.getClaim();
        if (event.isReleasePets() && claim.ownerID != null && claim.parent == null) {
            for (Chunk chunk : claim.getChunks()) {
                Entity[] entities = chunk.getEntities();
                Arrays.stream(entities)
                    .filter(entity -> entity instanceof Tameable)
                    .map(entity -> (Tameable) entity)
                    .filter(Tameable::isTamed).forEach(pet -> {
                        AnimalTamer owner = pet.getOwner();
                        if (owner != null) {
                            UUID ownerID = owner.getUniqueId();
                            if (ownerID.equals(claim.ownerID)) {
                                pet.setTamed(false);
                                pet.setOwner(null);
                                if (pet instanceof InventoryHolder holder) {
                                    holder.getInventory().clear();
                                }
                            }
                        }
                    });
            }
        }
    }
}

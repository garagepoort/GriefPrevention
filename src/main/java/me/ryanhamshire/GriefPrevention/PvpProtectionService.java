package me.ryanhamshire.GriefPrevention;

import be.garagepoort.mcioc.IocBean;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import static me.ryanhamshire.GriefPrevention.config.ConfigLoader.pvpRulesApply;

@IocBean
public class PvpProtectionService {
    private final DataStore dataStore;
    private final MessageService messageService;

    public PvpProtectionService(DataStore dataStore, MessageService messageService) {
        this.dataStore = dataStore;
        this.messageService = messageService;
    }

    //called when a player spawns, applies protection for that player if necessary
    public void checkPvpProtectionNeeded(Player player, boolean hasEmptyInventory) {
        //if anti spawn camping feature is not enabled, do nothing
        if (!ConfigLoader.config_pvp_protectFreshSpawns) return;

        //if pvp is disabled, do nothing
        if (!pvpRulesApply(player.getWorld())) return;

        //if player is in creative mode, do nothing
        if (player.getGameMode() == GameMode.CREATIVE) return;

        //if the player has the damage any player permission enabled, do nothing
        if (player.hasPermission("griefprevention.nopvpimmunity")) return;

        //check inventory for well, anything
        if (hasEmptyInventory) {
            //if empty, apply immunity
            PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
            playerData.pvpImmune = true;

            //inform the player after he finishes respawning
            messageService.sendMessage(player, TextMode.Success, Messages.PvPImmunityStart, 5L);

            //start a task to re-check this player's inventory every minute until his immunity is gone
            PvPImmunityValidationTask task = new PvPImmunityValidationTask(player, dataStore);
            GriefPrevention.get().getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.get(), task, 1200L);
        }
    }
}

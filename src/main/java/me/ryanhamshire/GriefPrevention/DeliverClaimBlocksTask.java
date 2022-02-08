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

import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.events.AccrueClaimBlocksEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class DeliverClaimBlocksTask extends BukkitRunnable {
    private final Player player;
    private final DataStore dataStore;
    private final int idleThresholdSquared;

    public DeliverClaimBlocksTask(Player player, DataStore dataStore) {
        this.player = player;
        this.dataStore = dataStore;
        this.idleThresholdSquared = ConfigLoader.config_claims_accruedIdleThreshold * ConfigLoader.config_claims_accruedIdleThreshold;

        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(GriefPrevention.get(), this, 20L * 60 * 10, 20L * 60 * 10);
    }

    @Override
    public void run() {

        //deliver claim blocks to the specified player
        if (!this.player.isOnline()) {
            return; //player is not online to receive claim blocks
        }

        PlayerData playerData = dataStore.getPlayerData(player.getUniqueId());

        // check if player is idle. considered idle if
        //    in vehicle or is in water (pushed by water)
        //    or has not moved at least defined blocks since last check
        boolean isIdle = false;
        try {
            isIdle = player.isInsideVehicle() || player.getLocation().getBlock().isLiquid() ||
                !(playerData.lastAfkCheckLocation == null || playerData.lastAfkCheckLocation.distanceSquared(player.getLocation()) > idleThresholdSquared);
        } catch (IllegalArgumentException ignore) //can't measure distance when to/from are different worlds
        {
        }

        //remember current location for next time
        playerData.lastAfkCheckLocation = player.getLocation();

        try {
            //determine how fast blocks accrue for this player //RoboMWM: addons determine this instead
            int accrualRate = ConfigLoader.config_claims_blocksAccruedPerHour_default;

            //determine idle accrual rate when idle
            if (isIdle) {
                if (ConfigLoader.config_claims_accruedIdlePercent <= 0) {
                    GriefPrevention.AddLogEntry(player.getName() + " wasn't active enough to accrue claim blocks this round.", CustomLogEntryTypes.Debug, true);
                    return; //idle accrual percentage is disabled
                }

                accrualRate = (int) (accrualRate * (ConfigLoader.config_claims_accruedIdlePercent / 100.0D));
            }

            //fire event for addons
            AccrueClaimBlocksEvent event = new AccrueClaimBlocksEvent(player, accrualRate, isIdle);
            Bukkit.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                GriefPrevention.AddLogEntry(player.getName() + " claim block delivery was canceled by another plugin.", CustomLogEntryTypes.Debug, true);
                return; //event was cancelled
            }

            //set actual accrual
            accrualRate = event.getBlocksToAccrue();
            if (accrualRate < 0) accrualRate = 0;
            playerData.accrueBlocks(accrualRate);
            GriefPrevention.AddLogEntry("Delivering " + event.getBlocksToAccrue() + " blocks to " + player.getName(), CustomLogEntryTypes.Debug, true);

            //intentionally NOT saving data here to reduce overall secondary storage access frequency
            //many other operations will cause this player's data to save, including his eventual logout
            //dataStore.savePlayerData(player.getUniqueIdentifier(), playerData);
        } catch (Exception e) {
            GriefPrevention.AddLogEntry("Problem delivering claim blocks to player " + player.getName() + ":");
            e.printStackTrace();
        }
    }
}

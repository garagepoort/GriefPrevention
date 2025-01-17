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

import be.garagepoort.mcioc.IocBean;
import me.ryanhamshire.GriefPrevention.claims.ClaimBlockService;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

//FEATURE: automatically remove claims owned by inactive players which:
//...aren't protecting much OR
//...are a free new player claim (and the player has no other claims) OR
//...because the player has been gone a REALLY long time, and that expiration has been configured in config.yml

//runs every 1 minute in the main thread
@IocBean
public class FindUnusedClaimsTask extends BukkitRunnable
{
    private List<UUID> claimOwnerUUIDs;
    private Iterator<UUID> claimOwnerIterator;
    private final PlayerDataRepository playerDataRepository;
    private final ClaimBlockService claimBlockService;
    private final ClaimService claimService;

    public FindUnusedClaimsTask(PlayerDataRepository playerDataRepository, ClaimBlockService claimBlockService, ClaimService claimService)
    {
        this.playerDataRepository = playerDataRepository;
        this.claimBlockService = claimBlockService;
        this.claimService = claimService;
        refreshUUIDs();
        runTaskTimer(GriefPrevention.get(), 20L * 60, 20L * ConfigLoader.config_advanced_claim_expiration_check_rate);

    }

    @Override
    public void run()
    {
        //don't do anything when there are no claims
        if (claimOwnerUUIDs.isEmpty()) return;

        //wrap search around to beginning
        if (!claimOwnerIterator.hasNext())
        {
            refreshUUIDs();
            return;
        }

        GriefPrevention.instance.getServer().getScheduler().runTaskAsynchronously(GriefPrevention.instance,
            new CleanupUnusedClaimPreTask(claimOwnerIterator.next(), playerDataRepository, claimBlockService, claimService));
    }

    public void refreshUUIDs()
    {
        // Fetch owner UUIDs from list of claims
        claimOwnerUUIDs = claimService.getClaims().stream().map(claim -> claim.ownerID)
                .distinct().filter(Objects::nonNull).collect(Collectors.toList());

        if (!claimOwnerUUIDs.isEmpty())
        {
            // Randomize order
            Collections.shuffle(claimOwnerUUIDs);
        }

        GriefPrevention.AddLogEntry("The following UUIDs own a claim and will be checked for inactivity in the following order:", CustomLogEntryTypes.Debug, true);

        for (UUID uuid : claimOwnerUUIDs)
            GriefPrevention.AddLogEntry(uuid.toString(), CustomLogEntryTypes.Debug, true);

        claimOwnerIterator = claimOwnerUUIDs.iterator();
    }
}

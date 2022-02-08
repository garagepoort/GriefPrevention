/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2011 Ryan Hamshire

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
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;

import java.net.InetAddress;
import java.util.Calendar;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

//holds all of GriefPrevention's player-tied data
public class PlayerData {
    public UUID playerID;

    public Vector<Claim> claims = null;
    public Integer accruedClaimBlocks = null;
    public int newlyAccruedClaimBlocks = 0;
    public Location lastAfkCheckLocation = null;
    public Integer bonusClaimBlocks = null;
    public ShovelMode shovelMode = ShovelMode.Basic;
    public int fillRadius = 0;
    public Location lastShovelLocation = null;
    public Claim claimResizing = null;
    public Claim claimSubdividing = null;
    public boolean pendingTrapped = false;
    boolean warnedAboutBuildingOutsideClaims = false;
    long lastSiegeEndTimeStamp = 0;
    boolean wasKicked = false;
    public Visualization currentVisualization = null;
    public boolean pvpImmune = false;
    public long lastSpawn = 0;
    public boolean ignoreClaims = false;
    public Claim lastClaim = null;
    public SiegeData siegeData = null;

    public long lastPvpTimestamp = 0;
    public String lastPvpPlayer = "";

    public boolean warnedAboutMajorDeletion = false;

    public InetAddress ipAddress;

    private int AccruedClaimBlocksLimit = -1;

    boolean receivedDropUnlockAdvertisement = false;

    //whether or not this player's dropped items (on death) are unlocked for other players to pick up
    public boolean dropsAreUnlocked = false;

    //message to send to player after he respawns
    String messageOnRespawn = null;

    //player which a pet will be given to when it's right-clicked
    public OfflinePlayer petGiveawayRecipient = null;

    //timestamp for last "you're building outside your land claims" message
    Long buildWarningTimestamp = null;

    //spot where a player can't talk, used to mute new players until they've moved a little
    //this is an anti-bot strategy.
    Location noChatLocation = null;

    //ignore list
    //true means invisible (admin-forced ignore), false means player-created ignore
    public ConcurrentHashMap<UUID, Boolean> ignoredPlayers = new ConcurrentHashMap<>();
    public boolean ignoreListChanged = false;

    //profanity warning, once per play session
    boolean profanityWarned = false;

    //whether or not this player is "in" pvp combat
    public boolean inPvpCombat() {
        if (this.lastPvpTimestamp == 0) return false;

        long now = Calendar.getInstance().getTimeInMillis();

        long elapsed = now - this.lastPvpTimestamp;

        if (elapsed > ConfigLoader.config_pvp_combatTimeoutSeconds * 1000) //X seconds
        {
            this.lastPvpTimestamp = 0;
            return false;
        }

        return true;
    }

    public void setAccruedClaimBlocks(Integer accruedClaimBlocks) {
        this.accruedClaimBlocks = accruedClaimBlocks;
        this.newlyAccruedClaimBlocks = 0;
    }

    public int getBonusClaimBlocks() {
        return bonusClaimBlocks;
    }

    public void setBonusClaimBlocks(int bonusClaimBlocks) {
        this.bonusClaimBlocks = bonusClaimBlocks;
    }

    //Limit can be changed by addons
    public int getAccruedClaimBlocksLimit() {
        if (this.AccruedClaimBlocksLimit < 0)
            return ConfigLoader.config_claims_maxAccruedBlocks_default;
        return this.AccruedClaimBlocksLimit;
    }

    public Integer getAccruedClaimBlocks() {
        return accruedClaimBlocks;
    }

    public void setAccruedClaimBlocksLimit(int limit) {
        this.AccruedClaimBlocksLimit = limit;
    }

    public void accrueBlocks(int howMany) {
        this.newlyAccruedClaimBlocks += howMany;
    }

    public void addClaim(Claim claim) {
        this.claims.add(claim);
    }

    public void removeClaim(Claim claim) {
        this.claims.remove(claim);
    }

    public Vector<Claim> getClaims() {
        return claims;
    }
}

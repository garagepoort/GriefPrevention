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
public class PlayerData
{
    //the player's ID
    public UUID playerID;

    //the player's claims
    public Vector<Claim> claims = null;

    //how many claim blocks the player has earned via play time
    public Integer accruedClaimBlocks = null;

    //temporary holding area to avoid opening data files too early
    public int newlyAccruedClaimBlocks = 0;

    //where this player was the last time we checked on him for earning claim blocks
    public Location lastAfkCheckLocation = null;

    //how many claim blocks the player has been gifted by admins, or purchased via economy integration
    public Integer bonusClaimBlocks = null;

    //what "mode" the shovel is in determines what it will do when it's used
    public ShovelMode shovelMode = ShovelMode.Basic;

    //radius for restore nature fill mode
    public int fillRadius = 0;

    //last place the player used the shovel, useful in creating and resizing claims,
    //because the player must use the shovel twice in those instances
    public Location lastShovelLocation = null;

    //the claim this player is currently resizing
    public Claim claimResizing = null;

    //the claim this player is currently subdividing
    public Claim claimSubdividing = null;

    //whether or not the player has a pending /trapped rescue
    public boolean pendingTrapped = false;

    //whether this player was recently warned about building outside land claims
    boolean warnedAboutBuildingOutsideClaims = false;

    //timestamp when last siege ended (where this player was the defender)
    long lastSiegeEndTimeStamp = 0;

    //whether the player was kicked (set and used during logout)
    boolean wasKicked = false;

    //visualization
    public Visualization currentVisualization = null;

    //anti-camping pvp protection
    public boolean pvpImmune = false;
    public long lastSpawn = 0;

    //ignore claims mode
    public boolean ignoreClaims = false;

    //the last claim this player was in, that we know of
    public Claim lastClaim = null;

    //siege
    public SiegeData siegeData = null;

    //pvp
    public long lastPvpTimestamp = 0;
    public String lastPvpPlayer = "";

    //safety confirmation for deleting multi-subdivision claims
    public boolean warnedAboutMajorDeletion = false;

    public InetAddress ipAddress;

    //for addons to set per-player claim limits. Any negative value will use config's value
    private int AccruedClaimBlocksLimit = -1;

    //whether or not this player has received a message about unlocking death drops since his last death
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
    public boolean inPvpCombat()
    {
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

    public void setAccruedClaimBlocks(Integer accruedClaimBlocks)
    {
        this.accruedClaimBlocks = accruedClaimBlocks;
        this.newlyAccruedClaimBlocks = 0;
    }

    public int getBonusClaimBlocks()
    {
        if (this.bonusClaimBlocks == null) this.loadDataFromSecondaryStorage();
        return bonusClaimBlocks;
    }

    public void setBonusClaimBlocks(Integer bonusClaimBlocks)
    {
        this.bonusClaimBlocks = bonusClaimBlocks;
    }

    private void loadDataFromSecondaryStorage()
    {
        //reach out to secondary storage to get any data there
        PlayerData storageData = GriefPrevention.instance.dataStore.getPlayerDataFromStorage(this.playerID);

        if (this.accruedClaimBlocks == null)
        {
            if (storageData.accruedClaimBlocks != null)
            {
                this.accruedClaimBlocks = storageData.accruedClaimBlocks;

                //ensure at least minimum accrued are accrued (in case of settings changes to increase initial amount)
                if (ConfigLoader.config_advanced_fixNegativeClaimblockAmounts && (this.accruedClaimBlocks < ConfigLoader.config_claims_initialBlocks))
                {
                    this.accruedClaimBlocks = ConfigLoader.config_claims_initialBlocks;
                }

            }
            else
            {
                this.accruedClaimBlocks = ConfigLoader.config_claims_initialBlocks;
            }
        }

        if (this.bonusClaimBlocks == null)
        {
            if (storageData.bonusClaimBlocks != null)
            {
                this.bonusClaimBlocks = storageData.bonusClaimBlocks;
            }
            else
            {
                this.bonusClaimBlocks = 0;
            }
        }
    }

    //Limit can be changed by addons
    public int getAccruedClaimBlocksLimit()
    {
        if (this.AccruedClaimBlocksLimit < 0)
            return ConfigLoader.config_claims_maxAccruedBlocks_default;
        return this.AccruedClaimBlocksLimit;
    }

    public void setAccruedClaimBlocksLimit(int limit)
    {
        this.AccruedClaimBlocksLimit = limit;
    }

    public void accrueBlocks(int howMany)
    {
        this.newlyAccruedClaimBlocks += howMany;
    }
}

package me.ryanhamshire.GriefPrevention;

import be.garagepoort.mcioc.IocBean;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@IocBean
public class SiegeService {

    private final PlayerDataRepository playerDataRepository;
    private final ClaimService claimService;

    //timestamp for each siege cooldown to end
    private final HashMap<String, Long> siegeCooldownRemaining = new HashMap<>();

    public SiegeService(PlayerDataRepository playerDataRepository, ClaimService claimService) {
        this.playerDataRepository = playerDataRepository;
        this.claimService = claimService;
    }

    public void startSiege(Player attacker, Player defender, Claim defenderClaim) {
        //fill-in the necessary SiegeData instance
        SiegeData siegeData = new SiegeData(attacker, defender, defenderClaim);
        PlayerData attackerData = playerDataRepository.getPlayerData(attacker.getUniqueId());
        PlayerData defenderData = playerDataRepository.getPlayerData(defender.getUniqueId());
        attackerData.siegeData = siegeData;
        defenderData.siegeData = siegeData;
        defenderClaim.siegeData = siegeData;

        //start a task to monitor the siege
        //why isn't this a "repeating" task?
        //because depending on the status of the siege at the time the task runs, there may or may not be a reason to run the task again
        SiegeCheckupTask task = new SiegeCheckupTask(siegeData, claimService, playerDataRepository, this);
        siegeData.checkupTaskID = GriefPrevention.instance.getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.instance, task, 20L * 30);
    }

    public boolean onCooldown(Player attacker, Player defender, Claim defenderClaim) {
        Long cooldownEnd = null;
        if (this.siegeCooldownRemaining.get(attacker.getName() + "_" + defender.getName()) != null) {
            cooldownEnd = this.siegeCooldownRemaining.get(attacker.getName() + "_" + defender.getName());

            if (Calendar.getInstance().getTimeInMillis() < cooldownEnd) {
                return true;
            }
            this.siegeCooldownRemaining.remove(attacker.getName() + "_" + defender.getName());
        }

        PlayerData defenderData = playerDataRepository.getPlayerData(defender.getUniqueId());
        if (defenderData.lastSiegeEndTimeStamp > 0) {
            long now = System.currentTimeMillis();
            if (now - defenderData.lastSiegeEndTimeStamp > 1000 * 60 * 15) //15 minutes in milliseconds
            {
                return true;
            }
        }

        if (cooldownEnd == null && this.siegeCooldownRemaining.get(attacker.getName() + "_" + defenderClaim.getOwnerName()) != null) {
            cooldownEnd = this.siegeCooldownRemaining.get(attacker.getName() + "_" + defenderClaim.getOwnerName());
            if (Calendar.getInstance().getTimeInMillis() < cooldownEnd) {
                return true;
            }
            this.siegeCooldownRemaining.remove(attacker.getName() + "_" + defenderClaim.getOwnerName());
        }

        return false;
    }

    public void endSiege(SiegeData siegeData, String winnerName, String loserName, List<ItemStack> drops) {
        boolean grantAccess = false;

        //determine winner and loser
        if (winnerName == null && loserName != null) {
            if (siegeData.attacker.getName().equals(loserName)) {
                winnerName = siegeData.defender.getName();
            } else {
                winnerName = siegeData.attacker.getName();
            }
        } else if (winnerName != null && loserName == null) {
            if (siegeData.attacker.getName().equals(winnerName)) {
                loserName = siegeData.defender.getName();
            } else {
                loserName = siegeData.attacker.getName();
            }
        }

        //if the attacker won, plan to open the doors for looting
        if (siegeData.attacker.getName().equals(winnerName)) {
            grantAccess = true;
        }

        PlayerData attackerData = playerDataRepository.getPlayerData(siegeData.attacker.getUniqueId());
        attackerData.siegeData = null;

        PlayerData defenderData = playerDataRepository.getPlayerData(siegeData.defender.getUniqueId());
        defenderData.siegeData = null;
        defenderData.lastSiegeEndTimeStamp = System.currentTimeMillis();

        //start a cooldown for this attacker/defender pair
        long now = Calendar.getInstance().getTimeInMillis();
        Long cooldownEnd = now + 1000L * 60 * ConfigLoader.config_siege_cooldownEndInMinutes;  //one hour from now
        this.siegeCooldownRemaining.put(siegeData.attacker.getName() + "_" + siegeData.defender.getName(), cooldownEnd);

        //start cooldowns for every attacker/involved claim pair
        for (int i = 0; i < siegeData.claims.size(); i++) {
            Claim claim = siegeData.claims.get(i);
            claim.siegeData = null;
            this.siegeCooldownRemaining.put(siegeData.attacker.getName() + "_" + claim.getOwnerName(), cooldownEnd);

            //if doors should be opened for looting, do that now
            if (grantAccess) {
                claim.doorsOpen = true;
            }
        }

        //cancel the siege checkup task
        GriefPrevention.instance.getServer().getScheduler().cancelTask(siegeData.checkupTaskID);

        //notify everyone who won and lost
        if (winnerName != null) {
            GriefPrevention.instance.getServer().broadcastMessage(winnerName + " defeated " + loserName + " in siege warfare!");
        }

        //if the claim should be opened to looting
        if (grantAccess) {

            Player winner = GriefPrevention.instance.getServer().getPlayer(winnerName);
            if (winner != null) {
                //notify the winner
                MessageService.sendMessage(winner, TextMode.Success, Messages.SiegeWinDoorsOpen);

                //schedule a task to secure the claims in about 5 minutes
                SecureClaimTask task = new SecureClaimTask(siegeData, claimService);

                GriefPrevention.instance.getServer().getScheduler().scheduleSyncDelayedTask(
                    GriefPrevention.instance, task, 20L * ConfigLoader.config_siege_doorsOpenSeconds
                );
            }
        }

        //if the siege ended due to death, transfer inventory to winner
        if (drops != null) {

            Player winner = GriefPrevention.instance.getServer().getPlayer(winnerName);

            Player loser = GriefPrevention.instance.getServer().getPlayer(loserName);
            if (winner != null && loser != null) {
                for (ItemStack stack : drops) {
                    if (stack == null || stack.getType() == Material.AIR || stack.getAmount() == 0) continue;
                    HashMap<Integer, ItemStack> wontFitItems = winner.getInventory().addItem(stack);
                    Location winnerLocation = winner.getLocation();
                    for (Map.Entry<Integer, ItemStack> wontFitItem : wontFitItems.entrySet()) {
                        winner.getWorld().dropItemNaturally(winnerLocation, wontFitItem.getValue());
                    }
                }

                drops.clear();
            }
        }
    }
}

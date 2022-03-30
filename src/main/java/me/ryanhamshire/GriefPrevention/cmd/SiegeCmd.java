package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.SiegeService;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocCommandHandler("siege")
public class SiegeCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final BukkitUtils bukkitUtils;
    private final ClaimService claimService;
    private final SiegeService siegeService;
    private final DataStore dataStore;

    public SiegeCmd(PlayerDataRepository playerDataRepository, BukkitUtils bukkitUtils, ClaimService claimService, SiegeService siegeService, DataStore dataStore) {
        this.playerDataRepository = playerDataRepository;
        this.bukkitUtils = bukkitUtils;
        this.claimService = claimService;
        this.siegeService = siegeService;
        this.dataStore = dataStore;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        if (!this.siegeEnabledForWorld(player.getWorld())) {
            MessageService.sendMessage(player, TextMode.Err, Messages.NonSiegeWorld);
            return true;
        }

        if (args.length > 1) {
            return false;
        }

        bukkitUtils.runTaskAsync(sender, () -> {

            //can't start a siege when you're already involved in one
            Player attacker = player;
            PlayerData attackerData = this.playerDataRepository.getPlayerData(attacker.getUniqueId());
            if (attackerData.siegeData != null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.AlreadySieging);
                return;
            }

            //can't start a siege when you're protected from pvp combat
            if (attackerData.pvpImmune) {
                MessageService.sendMessage(player, TextMode.Err, Messages.CantFightWhileImmune);
                return;
            }

            //if a player name was specified, use that
            Player defender = null;
            if (args.length >= 1) {
                defender = GriefPrevention.get().getServer().getPlayer(args[0]);
                if (defender == null) {
                    MessageService.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                    return;
                }
            }

            //otherwise use the last player this player was in pvp combat with
            else if (attackerData.lastPvpPlayer.length() > 0) {
                defender = GriefPrevention.get().getServer().getPlayer(attackerData.lastPvpPlayer);
                if (defender == null) {
                    return;
                }
            } else {
                return;
            }

            // First off, you cannot siege yourself, that's just
            // silly:
            if (attacker.getName().equals(defender.getName())) {
                MessageService.sendMessage(player, TextMode.Err, Messages.NoSiegeYourself);
                return;
            }

            //victim must not have the permission which makes him immune to siege
            if (defender.hasPermission("griefprevention.siegeimmune")) {
                MessageService.sendMessage(player, TextMode.Err, Messages.SiegeImmune);
                return;
            }

            //victim must not be under siege already
            PlayerData defenderData = this.playerDataRepository.getPlayerData(defender.getUniqueId());
            if (defenderData.siegeData != null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.AlreadyUnderSiegePlayer);
                return;
            }

            //victim must not be pvp immune
            if (defenderData.pvpImmune) {
                MessageService.sendMessage(player, TextMode.Err, Messages.NoSiegeDefenseless);
                return;
            }

            Claim defenderClaim = this.claimService.getClaimAt(defender.getLocation(), false, null);

            //defender must have some level of permission there to be protected
            if (defenderClaim == null || claimService.checkPermission(defenderClaim, defender, ClaimPermission.Access, null) != null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.NotSiegableThere);
                return;
            }

            //attacker must be close to the claim he wants to siege
            if (!defenderClaim.isNear(attacker.getLocation(), 25)) {
                MessageService.sendMessage(player, TextMode.Err, Messages.SiegeTooFarAway);
                return;
            }

            //claim can't be under siege already
            if (defenderClaim.siegeData != null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.AlreadyUnderSiegeArea);
                return;
            }

            //can't siege admin claims
            if (defenderClaim.isAdminClaim()) {
                MessageService.sendMessage(player, TextMode.Err, Messages.NoSiegeAdminClaim);
                return;
            }

            //can't be on cooldown
            if (siegeService.onCooldown(attacker, defender, defenderClaim)) {
                MessageService.sendMessage(player, TextMode.Err, Messages.SiegeOnCooldown);
                return;
            }

            //start the siege
            siegeService.startSiege(attacker, defender, defenderClaim);

            //confirmation message for attacker, warning message for defender
            MessageService.sendMessage(defender, TextMode.Warn, Messages.SiegeAlert, attacker.getName());
            MessageService.sendMessage(player, TextMode.Success, Messages.SiegeConfirmed, defender.getName());
        });

        return true;
    }

    private boolean siegeEnabledForWorld(World world) {
        return ConfigLoader.config_siege_enabledWorlds.contains(world);
    }
}

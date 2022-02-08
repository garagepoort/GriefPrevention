package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("siege")
public class SiegeCmd extends AbstractCmd {
    private final DataStore dataStore;
    private final BukkitUtils bukkitUtils;

    public SiegeCmd(DataStore dataStore, BukkitUtils bukkitUtils) {
        this.dataStore = dataStore;
        this.bukkitUtils = bukkitUtils;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        if (!this.siegeEnabledForWorld(player.getWorld())) {
            messageService.sendMessage(player, TextMode.Err, Messages.NonSiegeWorld);
            return true;
        }

        if (args.length > 1) {
            return false;
        }

        bukkitUtils.runTaskAsync(sender, () -> {

            //can't start a siege when you're already involved in one
            Player attacker = player;
            PlayerData attackerData = this.dataStore.getPlayerData(attacker.getUniqueId());
            if (attackerData.siegeData != null) {
                messageService.sendMessage(player, TextMode.Err, Messages.AlreadySieging);
                return;
            }

            //can't start a siege when you're protected from pvp combat
            if (attackerData.pvpImmune) {
                messageService.sendMessage(player, TextMode.Err, Messages.CantFightWhileImmune);
                return;
            }

            //if a player name was specified, use that
            Player defender = null;
            if (args.length >= 1) {
                defender = GriefPrevention.get().getServer().getPlayer(args[0]);
                if (defender == null) {
                    messageService.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
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
                messageService.sendMessage(player, TextMode.Err, Messages.NoSiegeYourself);
                return;
            }

            //victim must not have the permission which makes him immune to siege
            if (defender.hasPermission("griefprevention.siegeimmune")) {
                messageService.sendMessage(player, TextMode.Err, Messages.SiegeImmune);
                return;
            }

            //victim must not be under siege already
            PlayerData defenderData = this.dataStore.getPlayerData(defender.getUniqueId());
            if (defenderData.siegeData != null) {
                messageService.sendMessage(player, TextMode.Err, Messages.AlreadyUnderSiegePlayer);
                return;
            }

            //victim must not be pvp immune
            if (defenderData.pvpImmune) {
                messageService.sendMessage(player, TextMode.Err, Messages.NoSiegeDefenseless);
                return;
            }

            Claim defenderClaim = this.dataStore.getClaimAt(defender.getLocation(), false, null);

            //defender must have some level of permission there to be protected
            if (defenderClaim == null || defenderClaim.checkPermission(defender, ClaimPermission.Access, null) != null) {
                messageService.sendMessage(player, TextMode.Err, Messages.NotSiegableThere);
                return;
            }

            //attacker must be close to the claim he wants to siege
            if (!defenderClaim.isNear(attacker.getLocation(), 25)) {
                messageService.sendMessage(player, TextMode.Err, Messages.SiegeTooFarAway);
                return;
            }

            //claim can't be under siege already
            if (defenderClaim.siegeData != null) {
                messageService.sendMessage(player, TextMode.Err, Messages.AlreadyUnderSiegeArea);
                return;
            }

            //can't siege admin claims
            if (defenderClaim.isAdminClaim()) {
                messageService.sendMessage(player, TextMode.Err, Messages.NoSiegeAdminClaim);
                return;
            }

            //can't be on cooldown
            if (dataStore.onCooldown(attacker, defender, defenderClaim)) {
                messageService.sendMessage(player, TextMode.Err, Messages.SiegeOnCooldown);
                return;
            }

            //start the siege
            dataStore.startSiege(attacker, defender, defenderClaim);

            //confirmation message for attacker, warning message for defender
            messageService.sendMessage(defender, TextMode.Warn, Messages.SiegeAlert, attacker.getName());
            messageService.sendMessage(player, TextMode.Success, Messages.SiegeConfirmed, defender.getName());
        });

        return true;
    }

    private boolean siegeEnabledForWorld(World world) {
        return ConfigLoader.config_siege_enabledWorlds.contains(world);
    }
}

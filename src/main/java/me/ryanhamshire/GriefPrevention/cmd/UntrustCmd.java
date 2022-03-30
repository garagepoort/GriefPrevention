package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.events.TrustChangedEvent;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocCommandHandler("untrust")
public class UntrustCmd extends AbstractCmd {
    private final ClaimService claimService;
    private final DataStore dataStore;

    public UntrustCmd(ClaimService claimService, DataStore dataStore) {
        this.claimService = claimService;
        this.dataStore = dataStore;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        //requires exactly one parameter, the other player's name
        if (args.length != 1) return false;

        Claim claim = this.claimService.getClaimAt(player.getLocation(), true /*ignore height*/, null);

        //determine whether a single player or clearing permissions entirely
        boolean clearPermissions = false;
        OfflinePlayer otherPlayer = null;
        if (args[0].equals("all")) {
            if (claim == null || claimService.checkPermission(claim, player, ClaimPermission.Edit, null) == null) {
                clearPermissions = true;
            } else {
                MessageService.sendMessage(player, TextMode.Err, Messages.ClearPermsOwnerOnly);
                return true;
            }
        } else {
            //validate player argument or group argument
            if (!args[0].startsWith("[") || !args[0].endsWith("]")) {
                otherPlayer = GriefPrevention.get().resolvePlayerByName(args[0]);
                if (otherPlayer == null && !args[0].equals("public")) {
                    //bracket any permissions - at this point it must be a permission without brackets
                    if (args[0].contains(".")) {
                        args[0] = "[" + args[0] + "]";
                    } else {
                        MessageService.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                        return true;
                    }
                }

                //correct to proper casing
                if (otherPlayer != null)
                    args[0] = otherPlayer.getName();
            }
        }

        //if no claim here, apply changes to all his claims
        if (claim == null) {
            String idToDrop = args[0];
            if (otherPlayer != null) {
                idToDrop = otherPlayer.getUniqueId().toString();
            }

            //calling event
            TrustChangedEvent event = new TrustChangedEvent(player, claimService.getClaims(player.getUniqueId(), player.getName()), null, false, idToDrop);
            BukkitUtils.sendEventOnThisTick(event);

            if (event.isCancelled()) {
                return true;
            }

            //dropping permissions
            for (Claim targetClaim : event.getClaims()) {
                claim = targetClaim;

                //if untrusting "all" drop all permissions
                if (clearPermissions) {
                    claim.clearPermissions();
                }

                //otherwise drop individual permissions
                else {
                    claim.dropPermission(idToDrop);
                    claim.managers.remove(idToDrop);
                }

                //save changes
                this.dataStore.saveClaim(claim);
            }

            //beautify for output
            if (args[0].equals("public")) {
                args[0] = "the public";
            }

            //confirmation message
            if (!clearPermissions) {
                MessageService.sendMessage(player, TextMode.Success, Messages.UntrustIndividualAllClaims, args[0]);
            } else {
                MessageService.sendMessage(player, TextMode.Success, Messages.UntrustEveryoneAllClaims);
            }
        }

        //otherwise, apply changes to only this claim
        else if (claimService.checkPermission(claim, player, ClaimPermission.Manage, null) != null) {
            MessageService.sendMessage(player, TextMode.Err, Messages.NoPermissionTrust, claim.getOwnerName());
        } else {
            //if clearing all
            if (clearPermissions) {
                //requires owner
                if (claimService.checkPermission(claim, player, ClaimPermission.Edit, null) != null) {
                    MessageService.sendMessage(player, TextMode.Err, Messages.UntrustAllOwnerOnly);
                    return true;
                }

                //calling the event
                TrustChangedEvent event = new TrustChangedEvent(player, claim, null, false, args[0]);
                BukkitUtils.sendEventOnThisTick(event);

                if (event.isCancelled()) {
                    return true;
                }

                event.getClaims().forEach(Claim::clearPermissions);
                MessageService.sendMessage(player, TextMode.Success, Messages.ClearPermissionsOneClaim);
            }

            //otherwise individual permission drop
            else {
                String idToDrop = args[0];
                if (otherPlayer != null) {
                    idToDrop = otherPlayer.getUniqueId().toString();
                }
                boolean targetIsManager = claim.managers.contains(idToDrop);
                if (targetIsManager && claimService.checkPermission(claim, player, ClaimPermission.Edit, null) != null)  //only claim owners can untrust managers
                {
                    MessageService.sendMessage(player, TextMode.Err, Messages.ManagersDontUntrustManagers, claim.getOwnerName());
                    return true;
                } else {
                    //calling the event
                    TrustChangedEvent event = new TrustChangedEvent(player, claim, null, false, idToDrop);
                    BukkitUtils.sendEventOnThisTick(event);

                    if (event.isCancelled()) {
                        return true;
                    }

                    event.getClaims().forEach(targetClaim -> targetClaim.dropPermission(event.getIdentifier()));

                    //beautify for output
                    if (args[0].equals("public")) {
                        args[0] = "the public";
                    }

                    MessageService.sendMessage(player, TextMode.Success, Messages.UntrustIndividualSingleClaim, args[0]);
                }
            }

            this.dataStore.saveClaim(claim);
        }

        return true;
    }
}

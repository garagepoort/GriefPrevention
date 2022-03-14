package me.ryanhamshire.GriefPrevention;

import be.garagepoort.mcioc.IocBean;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.events.TrustChangedEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Supplier;

@IocBean
public class TrustService {

    private final ClaimService claimService;
    private final DataStore dataStore;

    public TrustService(ClaimService claimService, DataStore dataStore) {
        this.claimService = claimService;
        this.dataStore = dataStore;
    }

    public void handleTrust(Player player, ClaimPermission permissionLevel, String recipientName) {
        //determine which claim the player is standing in
        Claim claim = this.claimService.getClaimAt(player.getLocation(), true /*ignore height*/, null);

        //validate player or group argument
        String permission = null;
        OfflinePlayer otherPlayer = null;
        UUID recipientID = null;
        if (recipientName.startsWith("[") && recipientName.endsWith("]")) {
            permission = recipientName.substring(1, recipientName.length() - 1);
            if (permission.isEmpty()) {
                MessageService.sendMessage(player, TextMode.Err, Messages.InvalidPermissionID);
                return;
            }
        } else {
            otherPlayer = GriefPrevention.get().resolvePlayerByName(recipientName);
            boolean isPermissionFormat = recipientName.contains(".");
            if (otherPlayer == null && !recipientName.equals("public") && !recipientName.equals("all") && !isPermissionFormat) {
                MessageService.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return;
            }

            if (otherPlayer == null && isPermissionFormat) {
                //player does not exist and argument has a period so this is a permission instead
                permission = recipientName;
            } else if (otherPlayer != null) {
                recipientName = otherPlayer.getName();
                recipientID = otherPlayer.getUniqueId();
            } else {
                recipientName = "public";
            }
        }

        //determine which claims should be modified
        ArrayList<Claim> targetClaims = new ArrayList<>();
        if (claim == null) {
            targetClaims.addAll(claimService.getClaims(player.getUniqueId(), player.getName()));
        } else {
            //check permission here
            if (claimService.checkPermission(claim, player, ClaimPermission.Manage, null) != null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.NoPermissionTrust, claim.getOwnerName());
                return;
            }

            //see if the player has the level of permission he's trying to grant
            Supplier<String> errorMessage;

            //permission level null indicates granting permission trust
            if (permissionLevel == null) {
                errorMessage = claimService.checkPermission(claim, player, ClaimPermission.Edit, null);
                if (errorMessage != null) {
                    errorMessage = () -> "Only " + claim.getOwnerName() + " can grant /PermissionTrust here.";
                }
            }

            //otherwise just use the ClaimPermission enum values
            else {
                errorMessage = claimService.checkPermission(claim, player, permissionLevel, null);
            }

            //error message for trying to grant a permission the player doesn't have
            if (errorMessage != null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.CantGrantThatPermission);
                return;
            }

            targetClaims.add(claim);
        }

        //if we didn't determine which claims to modify, tell the player to be specific
        if (targetClaims.size() == 0) {
            MessageService.sendMessage(player, TextMode.Err, Messages.GrantPermissionNoClaim);
            return;
        }

        String identifierToAdd = recipientName;
        if (permission != null) {
            identifierToAdd = "[" + permission + "]";
            //replace recipientName as well so the success message clearly signals a permission
            recipientName = identifierToAdd;
        } else if (recipientID != null) {
            identifierToAdd = recipientID.toString();
        }

        //calling the event
        TrustChangedEvent event = new TrustChangedEvent(player, targetClaims, permissionLevel, true, identifierToAdd);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        //apply changes
        for (Claim currentClaim : event.getClaims()) {
            if (permissionLevel == null) {
                if (!currentClaim.managers.contains(identifierToAdd)) {
                    currentClaim.managers.add(identifierToAdd);
                }
            } else {
                currentClaim.setPermission(identifierToAdd, permissionLevel);
            }
            this.dataStore.saveClaim(currentClaim);
        }

        //notify player
        if (recipientName.equals("public")) recipientName = MessageService.getMessage(Messages.CollectivePublic);
        String permissionDescription;
        if (permissionLevel == null) {
            permissionDescription = MessageService.getMessage(Messages.PermissionsPermission);
        } else if (permissionLevel == ClaimPermission.Build) {
            permissionDescription = MessageService.getMessage(Messages.BuildPermission);
        } else if (permissionLevel == ClaimPermission.Access) {
            permissionDescription = MessageService.getMessage(Messages.AccessPermission);
        } else //ClaimPermission.Inventory
        {
            permissionDescription = MessageService.getMessage(Messages.ContainersPermission);
        }

        String location;
        if (claim == null) {
            location = MessageService.getMessage(Messages.LocationAllClaims);
        } else {
            location = MessageService.getMessage(Messages.LocationCurrentClaim);
        }

        MessageService.sendMessage(player, TextMode.Success, Messages.GrantPermissionConfirmation, recipientName, permissionDescription, location);
    }
}

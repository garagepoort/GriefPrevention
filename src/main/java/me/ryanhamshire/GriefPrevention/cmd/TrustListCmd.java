package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Supplier;

@IocCommandHandler("trustlist")
public class TrustListCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final BukkitUtils bukkitUtils;
    private final ClaimService claimService;

    public TrustListCmd(PlayerDataRepository playerDataRepository, BukkitUtils bukkitUtils, ClaimService claimService) {
        this.playerDataRepository = playerDataRepository;
        this.bukkitUtils = bukkitUtils;
        this.claimService = claimService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        bukkitUtils.runTaskAsync(sender, () -> {

            Claim claim = this.claimService.getClaimAt(player.getLocation(), true, null);

            //if no claim here, error message
            if (claim == null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.TrustListNoClaim);
                return;
            }

            //if no permission to manage permissions, error message
            Supplier<String> errorMessage = claimService.checkPermission(claim, player, ClaimPermission.Manage, null);
            if (errorMessage != null) {
                MessageService.sendMessage(player, TextMode.Err, errorMessage.get());
                return;
            }

            //otherwise build a list of explicit permissions by permission level
            //and send that to the player
            ArrayList<String> builders = new ArrayList<>();
            ArrayList<String> containers = new ArrayList<>();
            ArrayList<String> accessors = new ArrayList<>();
            ArrayList<String> managers = new ArrayList<>();
            claim.getPermissions(builders, containers, accessors, managers);

            MessageService.sendMessage(player, TextMode.Info, Messages.TrustListHeader);

            StringBuilder permissions = new StringBuilder();
            permissions.append(ChatColor.GOLD).append('>');

            if (managers.size() > 0) {
                for (String manager : managers)
                    permissions.append(this.trustEntryToPlayerName(manager)).append(' ');
            }

            player.sendMessage(permissions.toString());
            permissions = new StringBuilder();
            permissions.append(ChatColor.YELLOW).append('>');

            if (builders.size() > 0) {
                for (String builder : builders)
                    permissions.append(this.trustEntryToPlayerName(builder)).append(' ');
            }

            player.sendMessage(permissions.toString());
            permissions = new StringBuilder();
            permissions.append(ChatColor.GREEN).append('>');

            if (containers.size() > 0) {
                for (String container : containers)
                    permissions.append(this.trustEntryToPlayerName(container)).append(' ');
            }

            player.sendMessage(permissions.toString());
            permissions = new StringBuilder();
            permissions.append(ChatColor.BLUE).append('>');

            if (accessors.size() > 0) {
                for (String accessor : accessors)
                    permissions.append(this.trustEntryToPlayerName(accessor)).append(' ');
            }

            player.sendMessage(permissions.toString());

            player.sendMessage(
                ChatColor.GOLD + MessageService.getMessage(Messages.Manage) + " " +
                    ChatColor.YELLOW + MessageService.getMessage(Messages.Build) + " " +
                    ChatColor.GREEN + MessageService.getMessage(Messages.Containers) + " " +
                    ChatColor.BLUE + MessageService.getMessage(Messages.Access));

            if (claim.getSubclaimRestrictions()) {
                MessageService.sendMessage(player, TextMode.Err, Messages.HasSubclaimRestriction);
            }
        });

        return true;
    }

    private String trustEntryToPlayerName(String entry) {
        if (entry.startsWith("[") || entry.equals("public")) {
            return entry;
        } else {
            return lookupPlayerName(entry);
        }
    }

    //string overload for above helper
    private String lookupPlayerName(String playerID) {
        UUID id;
        try {
            id = UUID.fromString(playerID);
        } catch (IllegalArgumentException ex) {
            GriefPrevention.AddLogEntry("Error: Tried to look up a local player name for invalid UUID: " + playerID);
            return "someone";
        }

        return GriefPrevention.get().lookupPlayerName(id);
    }
}

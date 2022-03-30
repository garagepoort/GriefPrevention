package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.NoTransferException;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

@IocCommandHandler("transferclaim")
public class TransferClaimCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final BukkitUtils bukkitUtils;
    private final ClaimService claimService;

    public TransferClaimCmd(PlayerDataRepository playerDataRepository, BukkitUtils bukkitUtils, ClaimService claimService) {
        this.playerDataRepository = playerDataRepository;
        this.bukkitUtils = bukkitUtils;
        this.claimService = claimService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        bukkitUtils.runTaskAsync(sender, () -> {

            //which claim is the user in?
            Claim claim = this.claimService.getClaimAt(player.getLocation(), true, null);
            if (claim == null) {
                MessageService.sendMessage(player, TextMode.Instr, Messages.TransferClaimMissing);
                return;
            }

            //check additional permission for admin claims
            if (claim.isAdminClaim() && !player.hasPermission("griefprevention.adminclaims")) {
                MessageService.sendMessage(player, TextMode.Err, Messages.TransferClaimPermission);
                return;
            }

            UUID newOwnerID = null;  //no argument = make an admin claim
            String ownerName = "admin";

            if (args.length > 0) {
                OfflinePlayer targetPlayer = GriefPrevention.get().resolvePlayerByName(args[0]);
                if (targetPlayer == null) {
                    MessageService.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                    return;
                }
                newOwnerID = targetPlayer.getUniqueId();
                ownerName = targetPlayer.getName();
            }

            //change ownerhsip
            try {
                this.claimService.changeClaimOwner(claim, newOwnerID);
            } catch (NoTransferException e) {
                MessageService.sendMessage(player, TextMode.Instr, Messages.TransferTopLevel);
                return;
            }

            //confirm
            MessageService.sendMessage(player, TextMode.Success, Messages.TransferSuccess);
            GriefPrevention.AddLogEntry(player.getName() + " transferred a claim at " + GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()) + " to " + ownerName + ".", CustomLogEntryTypes.AdminActivity);
        });

        return true;
    }
}

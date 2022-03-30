package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.Visualization;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocCommandHandler("deleteclaim")
public class DeleteClaimCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final BukkitUtils bukkitUtils;
    private final ClaimService claimService;

    public DeleteClaimCmd(PlayerDataRepository playerDataRepository, BukkitUtils bukkitUtils, ClaimService claimService) {
        this.playerDataRepository = playerDataRepository;
        this.bukkitUtils = bukkitUtils;
        this.claimService = claimService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        bukkitUtils.runTaskAsync(sender, () -> {

            //determine which claim the player is standing in
            Claim claim = this.claimService.getClaimAt(player.getLocation(), true /*ignore height*/, null);

            if (claim == null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.DeleteClaimMissing);
            } else {
                //deleting an admin claim additionally requires the adminclaims permission
                if (!claim.isAdminClaim() || player.hasPermission("griefprevention.adminclaims")) {
                    PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());
                    if (claim.children.size() > 0 && !playerData.warnedAboutMajorDeletion) {
                        MessageService.sendMessage(player, TextMode.Warn, Messages.DeletionSubdivisionWarning);
                        playerData.warnedAboutMajorDeletion = true;
                    } else {
                        claim.removeSurfaceFluids(null);
                        this.claimService.deleteClaim(claim, true, true);

                        //if in a creative mode world, /restorenature the claim
                        if (ConfigLoader.creativeRulesApply(claim.getLesserBoundaryCorner()) || ConfigLoader.config_claims_survivalAutoNatureRestoration) {
                            claimService.restoreClaim(claim, 0);
                        }

                        MessageService.sendMessage(player, TextMode.Success, Messages.DeleteSuccess);
                        GriefPrevention.AddLogEntry(player.getName() + " deleted " + claim.getOwnerName() + "'s claim at " + GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()), CustomLogEntryTypes.AdminActivity);

                        bukkitUtils.runTaskLater(player, () -> Visualization.Revert(player, playerData));

                        playerData.warnedAboutMajorDeletion = false;
                    }
                } else {
                    MessageService.sendMessage(player, TextMode.Err, Messages.CantDeleteAdminClaim);
                }
            }
        });

        return true;
    }
}

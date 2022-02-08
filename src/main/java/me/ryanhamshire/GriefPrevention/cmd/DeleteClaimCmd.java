package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.Visualization;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("deleteclaim")
public class DeleteClaimCmd extends AbstractCmd {
    private final DataStore dataStore;
    private final BukkitUtils bukkitUtils;

    public DeleteClaimCmd(DataStore dataStore, BukkitUtils bukkitUtils) {
        this.dataStore = dataStore;
        this.bukkitUtils = bukkitUtils;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        bukkitUtils.runTaskAsync(sender, () -> {

            //determine which claim the player is standing in
            Claim claim = this.dataStore.getClaimAt(player.getLocation(), true /*ignore height*/, null);

            if (claim == null) {
                messageService.sendMessage(player, TextMode.Err, Messages.DeleteClaimMissing);
            } else {
                //deleting an admin claim additionally requires the adminclaims permission
                if (!claim.isAdminClaim() || player.hasPermission("griefprevention.adminclaims")) {
                    PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
                    if (claim.children.size() > 0 && !playerData.warnedAboutMajorDeletion) {
                        messageService.sendMessage(player, TextMode.Warn, Messages.DeletionSubdivisionWarning);
                        playerData.warnedAboutMajorDeletion = true;
                    } else {
                        claim.removeSurfaceFluids(null);
                        this.dataStore.deleteClaim(claim, true, true);

                        //if in a creative mode world, /restorenature the claim
                        if (ConfigLoader.creativeRulesApply(claim.getLesserBoundaryCorner()) || ConfigLoader.config_claims_survivalAutoNatureRestoration) {
                            GriefPrevention.instance.restoreClaim(claim, 0);
                        }

                        messageService.sendMessage(player, TextMode.Success, Messages.DeleteSuccess);
                        GriefPrevention.AddLogEntry(player.getName() + " deleted " + claim.getOwnerName() + "'s claim at " + GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()), CustomLogEntryTypes.AdminActivity);

                        bukkitUtils.runTaskLater(player, () -> Visualization.Revert(player));

                        playerData.warnedAboutMajorDeletion = false;
                    }
                } else {
                    messageService.sendMessage(player, TextMode.Err, Messages.CantDeleteAdminClaim);
                }
            }
        });

        return true;
    }
}

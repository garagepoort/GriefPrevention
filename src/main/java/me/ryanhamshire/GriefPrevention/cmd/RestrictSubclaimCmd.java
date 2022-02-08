package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.claims.ClaimRepository;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("restrictsubclaim")
public class RestrictSubclaimCmd extends AbstractCmd {
    private final DataStore dataStore;
    private final BukkitUtils bukkitUtils;
    private final ClaimRepository claimRepository;

    public RestrictSubclaimCmd(DataStore dataStore, BukkitUtils bukkitUtils, ClaimRepository claimRepository) {
        this.dataStore = dataStore;
        this.bukkitUtils = bukkitUtils;
        this.claimRepository = claimRepository;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        bukkitUtils.runTaskAsync(sender, () -> {

            PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = this.claimRepository.getClaimAt(player.getLocation(), true, playerData.lastClaim);
            if (claim == null || claim.parent == null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.StandInSubclaim);
                return;
            }

            // If player has /ignoreclaims on, continue
            // If admin claim, fail if this user is not an admin
            // If not an admin claim, fail if this user is not the owner
            if (!playerData.ignoreClaims && (claim.isAdminClaim() ? !player.hasPermission("griefprevention.adminclaims") : !player.getUniqueId().equals(claim.parent.ownerID))) {
                MessageService.sendMessage(player, TextMode.Err, Messages.OnlyOwnersModifyClaims, claim.getOwnerName());
                return;
            }

            if (claim.getSubclaimRestrictions()) {
                claim.setSubclaimRestrictions(false);
                MessageService.sendMessage(player, TextMode.Success, Messages.SubclaimUnrestricted);
            } else {
                claim.setSubclaimRestrictions(true);
                MessageService.sendMessage(player, TextMode.Success, Messages.SubclaimRestricted);
            }
            this.claimRepository.saveClaim(claim);
        });

        return true;
    }
}

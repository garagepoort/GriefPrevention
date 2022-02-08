package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.Visualization;
import me.ryanhamshire.GriefPrevention.claims.ClaimBlockService;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Vector;

@IocBean
@IocCommandHandler("abandonallclaims")
public class AbandonAllClaimsCmd extends AbstractCmd {
    private final DataStore dataStore;
    private final BukkitUtils bukkitUtils;
    private final MessageService messageService;
    private final ClaimService claimService;
    private final ClaimBlockService claimBlockService;

    public AbandonAllClaimsCmd(DataStore dataStore, BukkitUtils bukkitUtils, MessageService messageService, ClaimService claimService, ClaimBlockService claimBlockService) {
        this.dataStore = dataStore;
        this.bukkitUtils = bukkitUtils;
        this.messageService = messageService;
        this.claimService = claimService;
        this.claimBlockService = claimBlockService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;

        if (args.length > 1) return false;

        if (args.length != 1 || !"confirm".equalsIgnoreCase(args[0])) {
            messageService.sendMessage(player, TextMode.Err, Messages.ConfirmAbandonAllClaims);
            return false;
        }

        bukkitUtils.runTaskAsync(sender, () -> {
            PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());

            Vector<Claim> claims = claimService.getClaims(player);
            int originalClaimCount = claims.size();

            //check count
            if (originalClaimCount == 0) {
                messageService.sendMessage(player, TextMode.Err, Messages.YouHaveNoClaims);
                return;
            }

            if (ConfigLoader.config_claims_abandonReturnRatio != 1.0D) {
                claims.forEach(claim -> playerData.setAccruedClaimBlocks(claimBlockService.recalculateAccruedClaimBlocks(playerData) - (int) Math.ceil((claim.getArea() * (1 - ConfigLoader.config_claims_abandonReturnRatio)))));
            }

            this.dataStore.deleteClaimsForPlayer(player.getUniqueId(), false);
            int remainingBlocks = claimBlockService.getRemainingClaimBlocks(playerData, claims);
            messageService.sendMessage(player, TextMode.Success, Messages.SuccessfulAbandon, String.valueOf(remainingBlocks));
            bukkitUtils.runTaskLater(player, () -> Visualization.Revert(player, playerData));
        });

        return true;
    }
}

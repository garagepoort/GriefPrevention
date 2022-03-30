package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.Visualization;
import me.ryanhamshire.GriefPrevention.claims.ClaimBlockService;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@IocCommandHandler("abandonallclaims")
public class AbandonAllClaimsCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final BukkitUtils bukkitUtils;
    private final ClaimService claimService;
    private final ClaimBlockService claimBlockService;

    public AbandonAllClaimsCmd(PlayerDataRepository playerDataRepository, BukkitUtils bukkitUtils, ClaimService claimService, ClaimBlockService claimBlockService) {
        this.playerDataRepository = playerDataRepository;
        this.bukkitUtils = bukkitUtils;
        this.claimService = claimService;
        this.claimBlockService = claimBlockService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;

        if (args.length > 1) return false;

        if (args.length != 1 || !"confirm".equalsIgnoreCase(args[0])) {
            MessageService.sendMessage(player, TextMode.Err, Messages.ConfirmAbandonAllClaims);
            return false;
        }

        bukkitUtils.runTaskAsync(sender, () -> {
            PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());

            List<Claim> claims = claimService.getClaims(player.getUniqueId(), player.getName());
            int originalClaimCount = claims.size();

            //check count
            if (originalClaimCount == 0) {
                MessageService.sendMessage(player, TextMode.Err, Messages.YouHaveNoClaims);
                return;
            }

            if (ConfigLoader.config_claims_abandonReturnRatio != 1.0D) {
                claims.forEach(claim -> playerData.setAccruedClaimBlocks(claimBlockService.recalculateAccruedClaimBlocks(playerData) - (int) Math.ceil((claim.getArea() * (1 - ConfigLoader.config_claims_abandonReturnRatio)))));
            }

            List<Claim> claimsDeleted = claimService.deleteClaimsForPlayer(player.getUniqueId(), false);
            int remainingBlocks = claimBlockService.getRemainingClaimBlocks(playerData, claims);
            MessageService.sendMessage(player, TextMode.Success, Messages.SuccessfulAbandon, String.valueOf(remainingBlocks));

            bukkitUtils.runTaskLater(player, () -> {
                for (Claim claim : claimsDeleted) {
                    claim.removeSurfaceFluids(null);
                    if (ConfigLoader.creativeRulesApply(claim.getLesserBoundaryCorner())) {
                        claimService.restoreClaim(claim, 0);
                    }
                }
                Visualization.Revert(player, playerData);
            });
        });

        return true;
    }
}

package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.claims.ResizeClaimService;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

@IocBean
@IocCommandHandler("extendclaim")
public class ExtendClaimCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final ClaimService claimService;
    private final ResizeClaimService resizeClaimService;

    public ExtendClaimCmd(PlayerDataRepository playerDataRepository, ClaimService claimService, ResizeClaimService resizeClaimService) {
        this.playerDataRepository = playerDataRepository;
        this.claimService = claimService;
        this.resizeClaimService = resizeClaimService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;

        if (args.length < 1) {
            return sendVideoLink(player);
        }

        int amount;
        try {
            amount = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            return sendVideoLink(player);
        }

        //requires claim modification tool in hand
        if (player.getGameMode() != GameMode.CREATIVE && player.getItemInHand().getType() != ConfigLoader.config_claims_modificationTool) {
            MessageService.sendMessage(player, TextMode.Err, Messages.MustHoldModificationToolForThat);
            return true;
        }

        //must be standing in a land claim
        PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());
        Claim claim = this.claimService.getClaimAt(player.getLocation(), true, playerData.lastClaim);
        if (claim == null) {
            MessageService.sendMessage(player, TextMode.Err, Messages.StandInClaimToResize);
            return true;
        }

        //must have permission to edit the land claim you're in
        Supplier<String> errorMessage = claimService.checkPermission(claim, player, ClaimPermission.Edit, null);
        if (errorMessage != null) {
            MessageService.sendMessage(player, TextMode.Err, Messages.NotYourClaim);
            return true;
        }

        //determine new corner coordinates
        org.bukkit.util.Vector direction = player.getLocation().getDirection();
        if (direction.getY() > .75) {
            MessageService.sendMessage(player, TextMode.Info, Messages.ClaimsExtendToSky);
            return true;
        }

        if (direction.getY() < -.75) {
            MessageService.sendMessage(player, TextMode.Info, Messages.ClaimsAutoExtendDownward);
            return true;
        }

        Location lc = claim.getLesserBoundaryCorner();
        Location gc = claim.getGreaterBoundaryCorner();
        int newx1 = lc.getBlockX();
        int newx2 = gc.getBlockX();
        int newy1 = lc.getBlockY();
        int newy2 = gc.getBlockY();
        int newz1 = lc.getBlockZ();
        int newz2 = gc.getBlockZ();

        //if changing Z only
        if (Math.abs(direction.getX()) < .3) {
            if (direction.getZ() > 0) {
                newz2 += amount;  //north
            } else {
                newz1 -= amount;  //south
            }
        }

        //if changing X only
        else if (Math.abs(direction.getZ()) < .3) {
            if (direction.getX() > 0) {
                newx2 += amount;  //east
            } else {
                newx1 -= amount;  //west
            }
        }

        //diagonals
        else {
            if (direction.getX() > 0) {
                newx2 += amount;
            } else {
                newx1 -= amount;
            }

            if (direction.getZ() > 0) {
                newz2 += amount;
            } else {
                newz1 -= amount;
            }
        }

        //attempt resize
        playerData.claimResizing = claim;
        this.resizeClaimService.resizeClaimWithChecks(player, playerData, newx1, newx2, newy1, newy2, newz1, newz2);
        playerData.claimResizing = null;

        return true;
    }

    private boolean sendVideoLink(Player player) {
        if (ConfigLoader.creativeRulesApply(player.getLocation())) {
            MessageService.sendMessage(player, TextMode.Instr, Messages.CreativeBasicsVideo2, PlayerDataRepository.CREATIVE_VIDEO_URL);
        } else if (GriefPrevention.instance.claimsEnabledForWorld(player.getLocation().getWorld())) {
            MessageService.sendMessage(player, TextMode.Instr, Messages.SurvivalBasicsVideo2, PlayerDataRepository.SURVIVAL_VIDEO_URL);
        }
        return false;
    }
}

package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.GroupBonusBlocksService;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.claims.ClaimBlockService;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@IocBean
@IocCommandHandler("claimslist")
public class ClaimsListCmd extends AbstractCmd {
    private final DataStore dataStore;
    private final BukkitUtils bukkitUtils;
    
    private final ClaimService claimService;
    private final ClaimBlockService claimBlockService;
    private final GroupBonusBlocksService groupBonusBlocksService;

    public ClaimsListCmd(DataStore dataStore, BukkitUtils bukkitUtils, ClaimService claimService, ClaimBlockService claimBlockService, GroupBonusBlocksService groupBonusBlocksService) {
        this.dataStore = dataStore;
        this.bukkitUtils = bukkitUtils;
        
        this.claimService = claimService;
        this.claimBlockService = claimBlockService;
        this.groupBonusBlocksService = groupBonusBlocksService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        //at most one parameter
        if (args.length > 1) return false;
        //player whose claims will be listed
        OfflinePlayer otherPlayer;

        //if another player isn't specified, assume current player
        if (args.length < 1) {
            if (player != null)
                otherPlayer = player;
            else
                return false;
        } else if (player != null && !player.hasPermission("griefprevention.claimslistother")) {
            MessageService.sendMessage(player, TextMode.Err, Messages.ClaimsListNoPermission);
            return true;
        } else {
            otherPlayer = GriefPrevention.get().resolvePlayerByName(args[0]);
            if (otherPlayer == null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }
        }

        bukkitUtils.runTaskAsync(sender, () -> {
            PlayerData playerData = this.dataStore.getPlayerData(otherPlayer.getUniqueId());
            List<Claim> claims = claimService.getClaims(otherPlayer.getUniqueId(), otherPlayer.getName());
            MessageService.sendMessage(player, TextMode.Instr, Messages.StartBlockMath,
                String.valueOf(claimBlockService.recalculateAccruedClaimBlocks(playerData)),
                String.valueOf((playerData.getBonusClaimBlocks() + groupBonusBlocksService.getGroupBonusBlocks(otherPlayer.getUniqueId()))),
                String.valueOf((claimBlockService.recalculateAccruedClaimBlocks(playerData) + playerData.getBonusClaimBlocks() + groupBonusBlocksService.getGroupBonusBlocks(otherPlayer.getUniqueId()))));
            if (claims.size() > 0) {
                MessageService.sendMessage(player, TextMode.Instr, Messages.ClaimsListHeader);
                for (Claim claim : claims) {
                    MessageService.sendMessage(player, TextMode.Instr, GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()) + MessageService.getMessage(Messages.ContinueBlockMath, String.valueOf(claim.getArea())));
                }

                MessageService.sendMessage(player, TextMode.Instr, Messages.EndBlockMath, String.valueOf(claimBlockService.getRemainingClaimBlocks(playerData, claims)));
            }

            //drop the data we just loaded, if the player isn't online
            if (!otherPlayer.isOnline())
                this.dataStore.clearCachedPlayerData(otherPlayer.getUniqueId());
        });

        return true;
    }
}

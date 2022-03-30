package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.GroupBonusBlocksService;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.UUID;

@IocCommandHandler("adjustbonusclaimblocks")
public class AdjustBonusClaimsCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final BukkitUtils bukkitUtils;
    private final GroupBonusBlocksService groupBonusBlocksService;

    public AdjustBonusClaimsCmd(PlayerDataRepository playerDataRepository, BukkitUtils bukkitUtils, GroupBonusBlocksService groupBonusBlocksService) {
        this.playerDataRepository = playerDataRepository;
        this.bukkitUtils = bukkitUtils;
        this.groupBonusBlocksService = groupBonusBlocksService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        //requires exactly two parameters, the other player or group's name and the adjustment
        if (args.length != 2) return false;

        //parse the adjustment amount
        int adjustment;
        try {
            adjustment = Integer.parseInt(args[1]);
        } catch (NumberFormatException numberFormatException) {
            return false;  //causes usage to be displayed
        }

        bukkitUtils.runTaskAsync(sender, () -> {

            //if granting blocks to all players with a specific permission
            if (args[0].startsWith("[") && args[0].endsWith("]")) {
                String permissionIdentifier = args[0].substring(1, args[0].length() - 1);
                int newTotal = groupBonusBlocksService.adjustGroupBonusBlocks(permissionIdentifier, adjustment);

                MessageService.sendMessage(sender, TextMode.Success, Messages.AdjustGroupBlocksSuccess, permissionIdentifier, String.valueOf(adjustment), String.valueOf(newTotal));
                if (sender != null)
                    GriefPrevention.AddLogEntry(sender.getName() + " adjusted " + permissionIdentifier + "'s bonus claim blocks by " + adjustment + ".");
                return;
            }

            //otherwise, find the specified player
            OfflinePlayer targetPlayer;
            try {
                UUID playerID = UUID.fromString(args[0]);
                targetPlayer = GriefPrevention.get().getServer().getOfflinePlayer(playerID);
            } catch (IllegalArgumentException e) {
                targetPlayer = GriefPrevention.get().resolvePlayerByName(args[0]);
            }

            if (targetPlayer == null) {
                MessageService.sendMessage(sender, TextMode.Err, Messages.PlayerNotFound2);
                return;
            }

            //give blocks to player
            PlayerData playerData = this.playerDataRepository.getPlayerData(targetPlayer.getUniqueId());
            playerData.setBonusClaimBlocks(playerData.getBonusClaimBlocks() + adjustment);
            this.playerDataRepository.savePlayerData(targetPlayer.getUniqueId(), playerData);

            MessageService.sendMessage(sender, TextMode.Success, Messages.AdjustBlocksSuccess, targetPlayer.getName(), String.valueOf(adjustment), String.valueOf(playerData.getBonusClaimBlocks()));
            if (sender != null)
                GriefPrevention.AddLogEntry(sender.getName() + " adjusted " + targetPlayer.getName() + "'s bonus claim blocks by " + adjustment + ".", CustomLogEntryTypes.AdminActivity);
        });

        return true;
    }
}

package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

@IocCommandHandler("adjustbonusclaimblocksall")
public class AdjustBonusClaimBlocksAllCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final BukkitUtils bukkitUtils;

    public AdjustBonusClaimBlocksAllCmd(PlayerDataRepository playerDataRepository, BukkitUtils bukkitUtils) {
        this.playerDataRepository = playerDataRepository;
        this.bukkitUtils = bukkitUtils;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        //requires exactly one parameter, the amount of adjustment
        if (args.length != 1) return false;

        //parse the adjustment amount
        int adjustment;
        try {
            adjustment = Integer.parseInt(args[0]);
        } catch (NumberFormatException numberFormatException) {
            return false;  //causes usage to be displayed
        }
        Collection<Player> players = (Collection<Player>) GriefPrevention.get().getServer().getOnlinePlayers();
        bukkitUtils.runTaskAsync(sender, () -> {
            StringBuilder builder = new StringBuilder();
            for (Player onlinePlayer : players) {
                UUID playerID = onlinePlayer.getUniqueId();
                PlayerData playerData = this.playerDataRepository.getPlayerData(playerID);
                playerData.setBonusClaimBlocks(playerData.getBonusClaimBlocks() + adjustment);
                this.playerDataRepository.savePlayerData(playerID, playerData);
                builder.append(onlinePlayer.getName()).append(' ');
            }

            MessageService.sendMessage(player, TextMode.Success, Messages.AdjustBlocksAllSuccess, String.valueOf(adjustment));
            GriefPrevention.AddLogEntry("Adjusted all " + players.size() + "players' bonus claim blocks by " + adjustment + ".  " + builder.toString(), CustomLogEntryTypes.AdminActivity);
        });

        return true;
    }
}

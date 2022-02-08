package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("setaccruedclaimblocks")
public class SetAccruedClaimBlocksCmd extends AbstractCmd {
    private final DataStore dataStore;
    private final BukkitUtils bukkitUtils;
    

    public SetAccruedClaimBlocksCmd(DataStore dataStore, BukkitUtils bukkitUtils) {
        this.dataStore = dataStore;
        this.bukkitUtils = bukkitUtils;
        
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        //requires exactly two parameters, the other player's name and the new amount
        if (args.length != 2) return false;

        //parse the adjustment amount
        int newAmount;
        try {
            newAmount = Integer.parseInt(args[1]);
        } catch (NumberFormatException numberFormatException) {
            return false;  //causes usage to be displayed
        }

        //find the specified player
        OfflinePlayer targetPlayer = GriefPrevention.get().resolvePlayerByName(args[0]);
        if (targetPlayer == null) {
            MessageService.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
            return true;
        }

        bukkitUtils.runTaskAsync(sender, () -> {
            PlayerData playerData = this.dataStore.getPlayerData(targetPlayer.getUniqueId());
            playerData.setAccruedClaimBlocks(newAmount);
            this.dataStore.savePlayerData(targetPlayer.getUniqueId(), playerData);

            MessageService.sendMessage(player, TextMode.Success, Messages.SetClaimBlocksSuccess);
            if (player != null)
                GriefPrevention.AddLogEntry(player.getName() + " set " + targetPlayer.getName() + "'s accrued claim blocks to " + newAmount + ".", CustomLogEntryTypes.AdminActivity);

        });

        return true;
    }
}

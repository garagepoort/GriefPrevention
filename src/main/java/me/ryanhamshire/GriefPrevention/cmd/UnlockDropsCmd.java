package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("unlockdrops")
public class UnlockDropsCmd extends AbstractCmd {
    private final DataStore dataStore;
    private final BukkitUtils bukkitUtils;
    

    public UnlockDropsCmd(DataStore dataStore, BukkitUtils bukkitUtils) {
        this.dataStore = dataStore;
        this.bukkitUtils = bukkitUtils;
        
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        bukkitUtils.runTaskAsync(sender, () -> {
            PlayerData playerData;

            if (player.hasPermission("griefprevention.unlockothersdrops") && args.length == 1) {
                Player otherPlayer = Bukkit.getPlayer(args[0]);
                if (otherPlayer == null) {
                    MessageService.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                    return;
                }

                playerData = this.dataStore.getPlayerData(otherPlayer.getUniqueId());
                MessageService.sendMessage(player, TextMode.Success, Messages.DropUnlockOthersConfirmation, otherPlayer.getName());
            } else {
                playerData = this.dataStore.getPlayerData(player.getUniqueId());
                MessageService.sendMessage(player, TextMode.Success, Messages.DropUnlockConfirmation);
            }

            playerData.dropsAreUnlocked = true;
        });

        return true;
    }
}

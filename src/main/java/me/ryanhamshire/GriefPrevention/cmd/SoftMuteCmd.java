package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("softmute")
public class SoftMuteCmd extends AbstractCmd {
    private final DataStore dataStore;
    private final BukkitUtils bukkitUtils;
    

    public SoftMuteCmd(DataStore dataStore, BukkitUtils bukkitUtils) {
        this.dataStore = dataStore;
        this.bukkitUtils = bukkitUtils;
        
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        //requires one parameter
        if (args.length != 1) return false;

        //find the specified player
        OfflinePlayer targetPlayer = GriefPrevention.get().resolvePlayerByName(args[0]);
        if (targetPlayer == null) {
            MessageService.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
            return true;
        }
        bukkitUtils.runTaskAsync(sender, () -> {
            boolean isMuted = this.dataStore.toggleSoftMute(targetPlayer.getUniqueId());
            if (isMuted) {
                MessageService.sendMessage(player, TextMode.Success, Messages.SoftMuted, targetPlayer.getName());
                String executorName = "console";
                if (player != null) {
                    executorName = player.getName();
                }

                GriefPrevention.AddLogEntry(executorName + " muted " + targetPlayer.getName() + ".", CustomLogEntryTypes.AdminActivity, true);
            } else {
                MessageService.sendMessage(player, TextMode.Success, Messages.UnSoftMuted, targetPlayer.getName());
            }
        });

        return true;
    }
}

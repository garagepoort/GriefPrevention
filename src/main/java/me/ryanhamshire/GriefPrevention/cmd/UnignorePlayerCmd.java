package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocCommandHandler("unignoreplayer")
public class UnignorePlayerCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final BukkitUtils bukkitUtils;
    

    public UnignorePlayerCmd(PlayerDataRepository playerDataRepository, BukkitUtils bukkitUtils) {
        this.playerDataRepository = playerDataRepository;
        this.bukkitUtils = bukkitUtils;
        
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        if (args.length < 1) return false;
        //validate target player
        OfflinePlayer targetPlayer = GriefPrevention.get().resolvePlayerByName(args[0]);
        if (targetPlayer == null) {
            MessageService.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
            return true;
        }
        bukkitUtils.runTaskAsync(sender, () -> {//requires target player name
            PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());
            Boolean ignoreStatus = playerData.ignoredPlayers.get(targetPlayer.getUniqueId());
            if (ignoreStatus == null || ignoreStatus == true) {
                MessageService.sendMessage(player, TextMode.Err, Messages.NotIgnoringPlayer);
                return;
            }

            this.setIgnoreStatus(player, targetPlayer, GriefPrevention.IgnoreMode.None);

            MessageService.sendMessage(player, TextMode.Success, Messages.UnIgnoreConfirmation);

        });

        return true;
    }


    private void setIgnoreStatus(OfflinePlayer ignorer, OfflinePlayer ignoree, GriefPrevention.IgnoreMode mode) {
        PlayerData playerData = this.playerDataRepository.getPlayerData(ignorer.getUniqueId());
        if (mode == GriefPrevention.IgnoreMode.None) {
            playerData.ignoredPlayers.remove(ignoree.getUniqueId());
        } else {
            playerData.ignoredPlayers.put(ignoree.getUniqueId(), mode == GriefPrevention.IgnoreMode.StandardIgnore ? false : true);
        }

        playerData.ignoreListChanged = true;
        if (!ignorer.isOnline()) {
            this.playerDataRepository.savePlayerData(ignorer.getUniqueId(), playerData);
            this.playerDataRepository.clearCachedPlayerData(ignorer.getUniqueId());
        }
    }
}

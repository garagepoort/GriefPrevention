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

@IocCommandHandler("ignoreplayer")
public class IgnorePlayerCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final BukkitUtils bukkitUtils;
    

    public IgnorePlayerCmd(PlayerDataRepository playerDataRepository, BukkitUtils bukkitUtils) {
        this.playerDataRepository = playerDataRepository;
        this.bukkitUtils = bukkitUtils;
        
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        if (args.length < 1) return false;

        OfflinePlayer targetPlayer = GriefPrevention.get().resolvePlayerByName(args[0]);
        if (targetPlayer == null) {
            MessageService.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
            return true;
        }
        
        bukkitUtils.runTaskAsync(sender, () -> {
            this.setIgnoreStatus(player, targetPlayer, GriefPrevention.IgnoreMode.StandardIgnore);
            MessageService.sendMessage(player, TextMode.Success, Messages.IgnoreConfirmation);
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

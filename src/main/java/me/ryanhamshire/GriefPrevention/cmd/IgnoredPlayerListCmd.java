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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

@IocCommandHandler("ignoredplayerlist")
public class IgnoredPlayerListCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final BukkitUtils bukkitUtils;
    

    public IgnoredPlayerListCmd(PlayerDataRepository playerDataRepository, BukkitUtils bukkitUtils) {
        this.playerDataRepository = playerDataRepository;
        this.bukkitUtils = bukkitUtils;
        
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        bukkitUtils.runTaskAsync(sender, () -> {
            PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<UUID, Boolean> entry : playerData.ignoredPlayers.entrySet()) {
                if (entry.getValue() != null) {
                    //if not an admin ignore, add it to the list
                    if (!entry.getValue()) {
                        builder.append(GriefPrevention.lookupPlayerName(entry.getKey()));
                        builder.append(" ");
                    }
                }
            }

            String list = builder.toString().trim();
            if (list.isEmpty()) {
                MessageService.sendMessage(player, TextMode.Info, Messages.NotIgnoringAnyone);
            } else {
                MessageService.sendMessage(player, TextMode.Info, list);
            }
        });

        return true;
    }
}

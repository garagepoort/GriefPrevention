package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.ShovelMode;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocCommandHandler("restorenaturefill")
public class RestoreNatureFillCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final BukkitUtils bukkitUtils;
    

    public RestoreNatureFillCmd(PlayerDataRepository playerDataRepository, BukkitUtils bukkitUtils) {
        this.playerDataRepository = playerDataRepository;
        this.bukkitUtils = bukkitUtils;
        
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        bukkitUtils.runTaskAsync(sender, () -> {
            //change shovel mode
            PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());
            playerData.shovelMode = ShovelMode.RestoreNatureFill;

            //set radius based on arguments
            playerData.fillRadius = 2;
            if (args.length > 0) {
                try {
                    playerData.fillRadius = Integer.parseInt(args[0]);
                } catch (Exception exception) {
                }
            }

            if (playerData.fillRadius < 0) playerData.fillRadius = 2;
            MessageService.sendMessage(player, TextMode.Success, Messages.FillModeActive, String.valueOf(playerData.fillRadius));
        });

        return true;
    }
}

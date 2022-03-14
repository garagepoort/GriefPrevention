package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.ShovelMode;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("basicclaims")
public class BasicClaimsCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final BukkitUtils bukkitUtils;

    public BasicClaimsCmd(PlayerDataRepository playerDataRepository, BukkitUtils bukkitUtils) {
        this.playerDataRepository = playerDataRepository;
        this.bukkitUtils = bukkitUtils;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        bukkitUtils.runTaskAsync(sender, () -> {
            PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());
            playerData.shovelMode = ShovelMode.Basic;
            playerData.claimSubdividing = null;
            MessageService.sendMessage(player, TextMode.Success, Messages.BasicClaimsMode);
        });

        return true;
    }
}

package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.WelcomeTask;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("claimbook")
public class ClaimBookCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final BukkitUtils bukkitUtils;
    

    public ClaimBookCmd(PlayerDataRepository playerDataRepository, BukkitUtils bukkitUtils) {
        this.playerDataRepository = playerDataRepository;
        this.bukkitUtils = bukkitUtils;
        
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        if (args.length != 1) return false;
        Player otherPlayer = GriefPrevention.get().getServer().getPlayer(args[0]);
        if (otherPlayer == null) {
            MessageService.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
            return true;
        } else {
            WelcomeTask task = new WelcomeTask(otherPlayer);
            bukkitUtils.runTaskAsync(sender, task);
            return true;
        }
    }
}

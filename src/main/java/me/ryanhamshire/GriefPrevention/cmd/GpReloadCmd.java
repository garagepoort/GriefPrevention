package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocCommandHandler("gpreload")
public class GpReloadCmd extends AbstractCmd {

    

    public GpReloadCmd(MessageService messageService) {
        
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        ConfigLoader.load();
        if (sender instanceof Player) {
            MessageService.sendMessage(sender, TextMode.Success, "Configuration updated.  If you have updated your Grief Prevention JAR, you still need to /reload or reboot your server.");
        } else {
            GriefPrevention.AddLogEntry("Configuration updated.  If you have updated your Grief Prevention JAR, you still need to /reload or reboot your server.");
        }

        return true;
    }
}

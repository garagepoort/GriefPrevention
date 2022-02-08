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
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("deleteuserclaimsinworld")
public class DeleteUserClaimsInWorldCmd extends AbstractCmd {
    private final DataStore dataStore;
    private final BukkitUtils bukkitUtils;
    private final MessageService messageService;

    public DeleteUserClaimsInWorldCmd(DataStore dataStore, BukkitUtils bukkitUtils, MessageService messageService) {
        this.dataStore = dataStore;
        this.bukkitUtils = bukkitUtils;
        this.messageService = messageService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        if (sender instanceof Player) {
            messageService.sendMessage(sender, TextMode.Err, Messages.ConsoleOnlyCommand);
            return true;
        }

        //requires exactly one parameter, the world name
        if (args.length != 1) return false;

        bukkitUtils.runTaskAsync(sender, () -> {
            World world = Bukkit.getServer().getWorld(args[0]);
            if (world == null) {
                messageService.sendMessage(sender, TextMode.Err, Messages.WorldNotFound);
                return;
            }

            this.dataStore.deleteClaimsInWorld(world, false);
            GriefPrevention.AddLogEntry("Deleted all user claims in world: " + world.getName() + ".", CustomLogEntryTypes.AdminActivity);
        });

        return true;
    }
}

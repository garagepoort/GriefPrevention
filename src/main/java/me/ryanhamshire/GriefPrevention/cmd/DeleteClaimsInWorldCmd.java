package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("deleteclaimsinworld")
public class DeleteClaimsInWorldCmd extends AbstractCmd {
    private final DataStore dataStore;
    private final BukkitUtils bukkitUtils;

    public DeleteClaimsInWorldCmd(DataStore dataStore, BukkitUtils bukkitUtils) {
        this.dataStore = dataStore;
        this.bukkitUtils = bukkitUtils;
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

            this.dataStore.deleteClaimsInWorld(world, true);
            GriefPrevention.AddLogEntry("Deleted all claims in world: " + world.getName() + ".", CustomLogEntryTypes.AdminActivity);
        });

        return true;
    }
}

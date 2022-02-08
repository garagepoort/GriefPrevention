package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("deleteclaimsinworld")
public class DeleteClaimsInWorldCmd extends AbstractCmd {
    private final BukkitUtils bukkitUtils;
    private final ClaimService claimService;

    public DeleteClaimsInWorldCmd(BukkitUtils bukkitUtils, ClaimService claimService) {
        this.bukkitUtils = bukkitUtils;
        this.claimService = claimService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        if (sender instanceof Player) {
            MessageService.sendMessage(sender, TextMode.Err, Messages.ConsoleOnlyCommand);
            return true;
        }

        //requires exactly one parameter, the world name
        if (args.length != 1) return false;

        bukkitUtils.runTaskAsync(sender, () -> {
            World world = Bukkit.getServer().getWorld(args[0]);
            if (world == null) {
                MessageService.sendMessage(sender, TextMode.Err, Messages.WorldNotFound);
                return;
            }

            this.claimService.deleteClaimsInWorld(world, true);
            GriefPrevention.AddLogEntry("Deleted all claims in world: " + world.getName() + ".", CustomLogEntryTypes.AdminActivity);
        });

        return true;
    }
}

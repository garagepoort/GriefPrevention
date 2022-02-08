package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.TrustService;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("accesstrust")
public class AccessTrustCmd extends AbstractCmd {
    private final TrustService trustService;
    private final BukkitUtils bukkitUtils;

    public AccessTrustCmd(TrustService trustService, BukkitUtils bukkitUtils) {
        this.trustService = trustService;
        this.bukkitUtils = bukkitUtils;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        if (args.length != 1) return false;
        bukkitUtils.runTaskAsync(sender, () -> trustService.handleTrust(player, ClaimPermission.Access, args[0]));

        return true;
    }
}

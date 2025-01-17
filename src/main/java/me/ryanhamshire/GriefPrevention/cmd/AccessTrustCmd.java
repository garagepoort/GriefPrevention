package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.TrustService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocCommandHandler("accesstrust")
public class AccessTrustCmd extends AbstractCmd {
    private final TrustService trustService;

    public AccessTrustCmd(TrustService trustService) {
        this.trustService = trustService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        if (args.length != 1) return false;
        trustService.handleTrust(player, ClaimPermission.Access, args[0]);

        return true;
    }
}

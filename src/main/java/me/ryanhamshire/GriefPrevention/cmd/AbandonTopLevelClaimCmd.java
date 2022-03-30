package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocCommandHandler("gpreload")
public class AbandonTopLevelClaimCmd extends AbstractCmd {
    private final BukkitUtils bukkitUtils;
    private final ClaimService claimService;

    public AbandonTopLevelClaimCmd(BukkitUtils bukkitUtils, ClaimService claimService) {
        this.bukkitUtils = bukkitUtils;
        this.claimService = claimService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        bukkitUtils.runTaskAsync(sender, () -> claimService.abandonClaim(player, true));

        return true;
    }
}

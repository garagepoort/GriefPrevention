package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Vector;

import static me.ryanhamshire.GriefPrevention.GriefPrevention.getfriendlyLocationString;

@IocCommandHandler("adminclaimslist")
public class AdminClaimsListCmd extends AbstractCmd {
    private final BukkitUtils bukkitUtils;
    private final ClaimService claimService;

    public AdminClaimsListCmd(BukkitUtils bukkitUtils, ClaimService claimService) {
        this.bukkitUtils = bukkitUtils;
        this.claimService = claimService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        bukkitUtils.runTaskAsync(sender, () -> {
            //find admin claims
            Vector<Claim> claims = new Vector<>();
            for (Claim claim : this.claimService.getClaims()) {
                if (claim.ownerID == null)  //admin claim
                {
                    claims.add(claim);
                }
            }
            if (claims.size() > 0) {
                MessageService.sendMessage(player, TextMode.Instr, Messages.ClaimsListHeader);
                for (Claim claim : claims) {
                    MessageService.sendMessage(player, TextMode.Instr, getfriendlyLocationString(claim.getLesserBoundaryCorner()));
                }
            }
        });

        return true;
    }
}

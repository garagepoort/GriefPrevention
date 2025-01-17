package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

@IocCommandHandler("claimexplosions")
public class ClaimExplosionsCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final BukkitUtils bukkitUtils;
    private final ClaimService claimService;

    public ClaimExplosionsCmd(PlayerDataRepository playerDataRepository, BukkitUtils bukkitUtils, ClaimService claimService) {
        this.playerDataRepository = playerDataRepository;
        this.bukkitUtils = bukkitUtils;
        this.claimService = claimService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        bukkitUtils.runTaskAsync(sender, () -> {

            //determine which claim the player is standing in
            Claim claim = this.claimService.getClaimAt(player.getLocation(), true /*ignore height*/, null);

            if (claim == null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.DeleteClaimMissing);
            } else {
                Supplier<String> noBuildReason = claimService.checkPermission(claim, player, ClaimPermission.Build, null);
                if (noBuildReason != null) {
                    MessageService.sendMessage(player, TextMode.Err, noBuildReason.get());
                    return;
                }

                if (claim.areExplosivesAllowed) {
                    claim.areExplosivesAllowed = false;
                    MessageService.sendMessage(player, TextMode.Success, Messages.ExplosivesDisabled);
                } else {
                    claim.areExplosivesAllowed = true;
                    MessageService.sendMessage(player, TextMode.Success, Messages.ExplosivesEnabled);
                }
            }
        });

        return true;
    }
}

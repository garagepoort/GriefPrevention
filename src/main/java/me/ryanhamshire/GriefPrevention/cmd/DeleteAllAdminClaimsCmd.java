package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.Visualization;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("deletealladminclaims")
public class DeleteAllAdminClaimsCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final BukkitUtils bukkitUtils;
    private final ClaimService claimService;

    public DeleteAllAdminClaimsCmd(PlayerDataRepository playerDataRepository, BukkitUtils bukkitUtils, ClaimService claimService) {
        this.playerDataRepository = playerDataRepository;
        this.bukkitUtils = bukkitUtils;
        this.claimService = claimService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        if (!player.hasPermission("griefprevention.deleteclaims")) {
            MessageService.sendMessage(player, TextMode.Err, Messages.NoDeletePermission);
            return true;
        }

        bukkitUtils.runTaskAsync(sender, () -> {
            PlayerData playerData = playerDataRepository.getPlayerData(player.getUniqueId());
            //delete all admin claims
            claimService.deleteAdminClaims( true);  //null for owner id indicates an administrative claim

            MessageService.sendMessage(player, TextMode.Success, Messages.AllAdminDeleted);
            GriefPrevention.AddLogEntry(player.getName() + " deleted all administrative claims.", CustomLogEntryTypes.AdminActivity);

            bukkitUtils.runTaskLater(player, () -> Visualization.Revert(player, playerData));
        });

        return true;
    }
}

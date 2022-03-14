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
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("deleteallclaims")
public class DeleteAllClaimsCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final BukkitUtils bukkitUtils;
    private final ClaimService claimService;


    public DeleteAllClaimsCmd(PlayerDataRepository playerDataRepository, BukkitUtils bukkitUtils, ClaimService claimService) {
        this.playerDataRepository = playerDataRepository;
        this.bukkitUtils = bukkitUtils;
        this.claimService = claimService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        //requires exactly one parameter, the other player's name
        if (args.length != 1) return false;
        bukkitUtils.runTaskAsync(sender, () -> {

            //try to find that player
            OfflinePlayer otherPlayer = GriefPrevention.get().resolvePlayerByName(args[0]);
            if (otherPlayer == null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return;
            }

            //delete all that player's claims
            this.claimService.deleteClaimsForPlayer(otherPlayer.getUniqueId(), true);

            MessageService.sendMessage(player, TextMode.Success, Messages.DeleteAllSuccess, otherPlayer.getName());
            if (player != null) {
                GriefPrevention.AddLogEntry(player.getName() + " deleted all claims belonging to " + otherPlayer.getName() + ".", CustomLogEntryTypes.AdminActivity);
                PlayerData playerData = playerDataRepository.getPlayerData(player.getUniqueId());
                bukkitUtils.runTaskLater(player, () -> Visualization.Revert(player, playerData));
            }
        });

        return true;
    }
}

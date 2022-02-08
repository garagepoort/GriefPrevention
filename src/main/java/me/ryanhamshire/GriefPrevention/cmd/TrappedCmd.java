package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.PlayerRescueService;
import me.ryanhamshire.GriefPrevention.PlayerRescueTask;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.events.SaveTrappedPlayerEvent;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("trapped")
public class TrappedCmd extends AbstractCmd {
    private final DataStore dataStore;
    private final BukkitUtils bukkitUtils;
    private final PlayerRescueService playerRescueService;
    private final ClaimService claimService;


    public TrappedCmd(DataStore dataStore, BukkitUtils bukkitUtils, PlayerRescueService playerRescueService, ClaimService claimService) {
        this.dataStore = dataStore;
        this.bukkitUtils = bukkitUtils;
        this.playerRescueService = playerRescueService;

        this.claimService = claimService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        bukkitUtils.runTaskAsync(sender, () -> {
            //FEATURE: empower players who get "stuck" in an area where they don't have permission to build to save themselves

            PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = this.claimService.getClaimAt(player.getLocation(), false, playerData.lastClaim);

            //if another /trapped is pending, ignore this slash command
            if (playerData.pendingTrapped) {
                return;
            }

            //if the player isn't in a claim or has permission to build, tell him to man up
            if (claim == null || claimService.checkPermission(claim, player, ClaimPermission.Build, null) == null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.NotTrappedHere);
                return;
            }

            //rescue destination may be set by GPFlags or other plugin, ask to find out
            SaveTrappedPlayerEvent event = new SaveTrappedPlayerEvent(claim);
            Bukkit.getPluginManager().callEvent(event);

            //if the player is in the nether or end, he's screwed (there's no way to programmatically find a safe place for him)
            if (player.getWorld().getEnvironment() != World.Environment.NORMAL && event.getDestination() == null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.TrappedWontWorkHere);
                return;
            }

            //if the player is in an administrative claim and AllowTrappedInAdminClaims is false, he should contact an admin
            if (!ConfigLoader.config_claims_allowTrappedInAdminClaims && claim.isAdminClaim() && event.getDestination() == null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.TrappedWontWorkHere);
                return;
            }
            //send instructions
            MessageService.sendMessage(player, TextMode.Instr, Messages.RescuePending);

            //create a task to rescue this player in a little while
            PlayerRescueTask task = new PlayerRescueTask(player, player.getLocation(), event.getDestination(), dataStore, playerRescueService);
            Bukkit.getScheduler().scheduleSyncDelayedTask(GriefPrevention.get(), task, 200L);  //20L ~ 1 second
        });

        return true;
    }
}

package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.AutoExtendClaimTask;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.Visualization;
import me.ryanhamshire.GriefPrevention.VisualizationType;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Objects;

@IocBean
@IocCommandHandler("claim")
public class ClaimCmd extends AbstractCmd {
    private final DataStore dataStore;
    private final BukkitUtils bukkitUtils;

    public ClaimCmd(DataStore dataStore, BukkitUtils bukkitUtils) {
        this.dataStore = dataStore;
        this.bukkitUtils = bukkitUtils;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;

        if (!GriefPrevention.instance.claimsEnabledForWorld(player.getWorld())) {
            messageService.sendMessage(player, TextMode.Err, Messages.ClaimsDisabledWorld);
            return true;
        }

        bukkitUtils.runTaskAsync(sender, () -> {
            PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());

            //if he's at the claim count per player limit already and doesn't have permission to bypass, display an error message
            if (ConfigLoader.config_claims_maxClaimsPerPlayer > 0 &&
                !player.hasPermission("griefprevention.overrideclaimcountlimit") &&
                playerData.getClaims().size() >= ConfigLoader.config_claims_maxClaimsPerPlayer) {
                messageService.sendMessage(player, TextMode.Err, Messages.ClaimCreationFailedOverClaimCountLimit);
                return;
            }

            //default is chest claim radius, unless -1
            int radius = ConfigLoader.config_claims_automaticClaimsForNewPlayersRadius;
            if (radius < 0) radius = (int) Math.ceil(Math.sqrt(ConfigLoader.config_claims_minArea) / 2);

            //if player has any claims, respect claim minimum size setting
            if (playerData.getClaims().size() > 0) {
                //if player has exactly one land claim, this requires the claim modification tool to be in hand (or creative mode player)
                if (playerData.getClaims().size() == 1 && player.getGameMode() != GameMode.CREATIVE && player.getItemInHand().getType() != ConfigLoader.config_claims_modificationTool) {
                    messageService.sendMessage(player, TextMode.Err, Messages.MustHoldModificationToolForThat);
                    return;
                }

                radius = (int) Math.ceil(Math.sqrt(ConfigLoader.config_claims_minArea) / 2);
            }

            //allow for specifying the radius
            if (args.length > 0) {
                if (playerData.getClaims().size() < 2 && player.getGameMode() != GameMode.CREATIVE && player.getItemInHand().getType() != ConfigLoader.config_claims_modificationTool) {
                    messageService.sendMessage(player, TextMode.Err, Messages.RadiusRequiresGoldenShovel);
                    return;
                }

                int specifiedRadius;
                try {
                    specifiedRadius = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    messageService.sendMessage(player, TextMode.Err, Messages.MinimumRadius, String.valueOf(radius));
                    return;
                }

                if (specifiedRadius < radius) {
                    messageService.sendMessage(player, TextMode.Err, Messages.MinimumRadius, String.valueOf(radius));
                    return;
                }
                radius = specifiedRadius;
            }

            if (radius < 0) radius = 0;

            Location lc = player.getLocation().add(-radius, 0, -radius);
            Location gc = player.getLocation().add(radius, 0, radius);

            //player must have sufficient unused claim blocks
            int area = Math.abs((gc.getBlockX() - lc.getBlockX() + 1) * (gc.getBlockZ() - lc.getBlockZ() + 1));
            int remaining = playerData.getRemainingClaimBlocks();
            if (remaining < area) {
                messageService.sendMessage(player, TextMode.Err, Messages.CreateClaimInsufficientBlocks, String.valueOf(area - remaining));
                dataStore.tryAdvertiseAdminAlternatives(player);
                return;
            }

            CreateClaimResult result = this.dataStore.createClaim(lc.getWorld(),
                lc.getBlockX(), gc.getBlockX(),
                lc.getBlockY() - ConfigLoader.config_claims_claimsExtendIntoGroundDistance - 1,
                gc.getWorld().getHighestBlockYAt(gc) - ConfigLoader.config_claims_claimsExtendIntoGroundDistance - 1,
                lc.getBlockZ(), gc.getBlockZ(),
                player.getUniqueId(), null, null, player);
            if (!result.succeeded) {
                if (result.claim != null) {
                    messageService.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapShort);

                    Visualization visualization = Visualization.FromClaim(result.claim, player.getEyeLocation().getBlockY(), VisualizationType.ErrorClaim, player.getLocation());
                    Visualization.Apply(player, visualization);
                } else {
                    messageService.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapRegion);
                }
            } else {
                messageService.sendMessage(player, TextMode.Success, Messages.CreateClaimSuccess);

                //link to a video demo of land claiming, based on world type
                if (ConfigLoader.creativeRulesApply(player.getLocation())) {
                    messageService.sendMessage(player, TextMode.Instr, Messages.CreativeBasicsVideo2, DataStore.CREATIVE_VIDEO_URL);
                } else if (GriefPrevention.instance.claimsEnabledForWorld(player.getLocation().getWorld())) {
                    messageService.sendMessage(player, TextMode.Instr, Messages.SurvivalBasicsVideo2, DataStore.SURVIVAL_VIDEO_URL);
                }
                Visualization visualization = Visualization.FromClaim(result.claim, player.getEyeLocation().getBlockY(), VisualizationType.Claim, player.getLocation());
                Visualization.Apply(player, visualization);
                playerData.claimResizing = null;
                playerData.lastShovelLocation = null;

                bukkitUtils.runTaskLater(sender, () -> this.autoExtendClaim(result.claim));
            }
        });

        return true;
    }

    private void autoExtendClaim(Claim newClaim) {
        Location lesserCorner = newClaim.getLesserBoundaryCorner();
        Location greaterCorner = newClaim.getGreaterBoundaryCorner();
        World world = lesserCorner.getWorld();
        ArrayList<ChunkSnapshot> snapshots = new ArrayList<>();
        for (int chunkx = lesserCorner.getBlockX() / 16; chunkx <= greaterCorner.getBlockX() / 16; chunkx++) {
            for (int chunkz = lesserCorner.getBlockZ() / 16; chunkz <= greaterCorner.getBlockZ() / 16; chunkz++) {
                if (Objects.requireNonNull(world).isChunkLoaded(chunkx, chunkz)) {
                    snapshots.add(world.getChunkAt(chunkx, chunkz).getChunkSnapshot(true, true, false));
                }
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(GriefPrevention.instance, new AutoExtendClaimTask(newClaim, snapshots, world.getEnvironment(), dataStore));
    }

}

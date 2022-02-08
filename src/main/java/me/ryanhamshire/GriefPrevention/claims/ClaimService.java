package me.ryanhamshire.GriefPrevention.claims;

import be.garagepoort.mcioc.IocBean;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.ClaimsMode;
import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.GroupBonusBlocksService;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.Visualization;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.events.PreventBlockBreakEvent;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Vector;
import java.util.function.Supplier;

import static me.ryanhamshire.GriefPrevention.config.ConfigLoader.creativeRulesApply;

@IocBean
public class ClaimService {

    private final DataStore dataStore;
    private final MessageService messageService;
    private final BukkitUtils bukkitUtils;
    private final GroupBonusBlocksService groupBonusBlocksService;
    private final ClaimBlockService claimBlockService;

    public ClaimService(DataStore dataStore, MessageService messageService, BukkitUtils bukkitUtils, GroupBonusBlocksService groupBonusBlocksService, ClaimBlockService claimBlockService) {
        this.dataStore = dataStore;
        this.messageService = messageService;
        this.bukkitUtils = bukkitUtils;
        this.groupBonusBlocksService = groupBonusBlocksService;
        this.claimBlockService = claimBlockService;
    }

    public boolean abandonClaim(Player player, boolean deleteTopLevelClaim) {
        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());

        //which claim is being abandoned?
        Claim claim = this.dataStore.getClaimAt(player.getLocation(), true /*ignore height*/, null);
        if (claim == null) {
            messageService.sendMessage(player, TextMode.Instr, Messages.AbandonClaimMissing);
        }

        //verify ownership
        else if (claim.checkPermission(player, ClaimPermission.Edit, null) != null) {
            messageService.sendMessage(player, TextMode.Err, Messages.NotYourClaim);
        }

        //warn if has children and we're not explicitly deleting a top level claim
        else if (claim.children.size() > 0 && !deleteTopLevelClaim) {
            messageService.sendMessage(player, TextMode.Instr, Messages.DeleteTopLevelClaim);
            return true;
        } else {
            //delete it
            claim.removeSurfaceFluids(null);
            this.dataStore.deleteClaim(claim, true, false);

            //if in a creative mode world, restore the claim area
            if (creativeRulesApply(claim.getLesserBoundaryCorner())) {
                GriefPrevention.AddLogEntry(player.getName() + " abandoned a claim @ " + GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()));
                messageService.sendMessage(player, TextMode.Warn, Messages.UnclaimCleanupWarning);
                GriefPrevention.instance.restoreClaim(claim, 20L * 60 * 2);
            }

            //adjust claim blocks when abandoning a top level claim
            if (ConfigLoader.config_claims_abandonReturnRatio != 1.0D && claim.parent == null && claim.ownerID.equals(playerData.playerID)) {
                playerData.setAccruedClaimBlocks(claimBlockService.recalculateAccruedClaimBlocks(playerData) - (int) Math.ceil((claim.getArea() * (1 - ConfigLoader.config_claims_abandonReturnRatio))));
            }

            //tell the player how many claim blocks he has left
            int remainingBlocks = claimBlockService.getRemainingClaimBlocks(playerData, getClaims(player));
            messageService.sendMessage(player, TextMode.Success, Messages.AbandonSuccess, String.valueOf(remainingBlocks));

            bukkitUtils.runTaskLater(player, () -> Visualization.Revert(player, playerData));

            playerData.warnedAboutMajorDeletion = false;
        }

        return true;
    }

    public String allowBuild(Player player, Location location, Material material) {
        if (!GriefPrevention.instance.claimsEnabledForWorld(location.getWorld())) return null;

        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
        Claim claim = this.dataStore.getClaimAt(location, false, playerData.lastClaim);

        //exception: administrators in ignore claims mode
        if (playerData.ignoreClaims) return null;

        //wilderness rules
        if (claim == null) {
            //no building in the wilderness in creative mode
            if (creativeRulesApply(location) || ConfigLoader.config_claims_worldModes.get(location.getWorld()) == ClaimsMode.SurvivalRequiringClaims) {
                //exception: when chest claims are enabled, players who have zero land claims and are placing a chest
                if (material != Material.CHEST || getClaims(player).size() > 0 || ConfigLoader.config_claims_automaticClaimsForNewPlayersRadius == -1) {
                    String reason = MessageService.getMessage(Messages.NoBuildOutsideClaims);
                    if (player.hasPermission("griefprevention.ignoreclaims"))
                        reason += "  " + MessageService.getMessage(Messages.IgnoreClaimsAdvertisement);
                    reason += "  " + MessageService.getMessage(Messages.CreativeBasicsVideo2, DataStore.CREATIVE_VIDEO_URL);
                    return reason;
                } else {
                    return null;
                }
            }

            //but it's fine in survival mode
            else {
                return null;
            }
        }

        //if not in the wilderness, then apply claim rules (permissions, etc)
        else {
            //cache the claim for later reference
            playerData.lastClaim = claim;
            Block block = location.getBlock();

            Supplier<String> supplier = claim.checkPermission(player, ClaimPermission.Build, new BlockPlaceEvent(block, block.getState(), block, new ItemStack(material), player, true, EquipmentSlot.HAND));

            if (supplier == null) return null;

            return supplier.get();
        }
    }

    public String allowBreak(Player player, Block block, Location location) {
        return this.allowBreak(player, block, location, new BlockBreakEvent(block, player));
    }

    public String allowBreak(Player player, Material material, Location location, BlockBreakEvent breakEvent) {
        return this.allowBreak(player, location.getBlock(), location, breakEvent);
    }

    public String allowBreak(Player player, Block block, Location location, BlockBreakEvent breakEvent) {
        if (!GriefPrevention.instance.claimsEnabledForWorld(location.getWorld())) return null;

        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
        Claim claim = this.dataStore.getClaimAt(location, false, playerData.lastClaim);

        //exception: administrators in ignore claims mode
        if (playerData.ignoreClaims) return null;

        //wilderness rules
        if (claim == null) {
            //no building in the wilderness in creative mode
            if (creativeRulesApply(location) || ConfigLoader.config_claims_worldModes.get(location.getWorld()) == ClaimsMode.SurvivalRequiringClaims) {
                String reason = MessageService.getMessage(Messages.NoBuildOutsideClaims);
                if (player.hasPermission("griefprevention.ignoreclaims"))
                    reason += "  " + MessageService.getMessage(Messages.IgnoreClaimsAdvertisement);
                reason += "  " + MessageService.getMessage(Messages.CreativeBasicsVideo2, DataStore.CREATIVE_VIDEO_URL);
                return reason;
            }

            //but it's fine in survival mode
            else {
                return null;
            }
        } else {
            //cache the claim for later reference
            playerData.lastClaim = claim;

            //if not in the wilderness, then apply claim rules (permissions, etc)
            Supplier<String> cancel = claim.checkPermission(player, ClaimPermission.Build, breakEvent);
            if (cancel != null && breakEvent != null) {
                PreventBlockBreakEvent preventionEvent = new PreventBlockBreakEvent(breakEvent);
                Bukkit.getPluginManager().callEvent(preventionEvent);
                if (preventionEvent.isCancelled()) {
                    cancel = null;
                }
            }

            if (cancel == null) return null;

            return cancel.get();
        }
    }

    public Vector<Claim> getClaims(OfflinePlayer player) {
        PlayerData playerData = dataStore.getPlayerData(player.getUniqueId());
        if (playerData.claims == null) {
            playerData.claims = new Vector<>();
            int totalClaimsArea = 0;
            for (int i = 0; i < dataStore.claims.size(); i++) {
                Claim claim = dataStore.claims.get(i);
                if (!claim.inDataStore) {
                    dataStore.claims.remove(i--);
                    continue;
                }
                if (player.getUniqueId().equals(claim.ownerID)) {
                    playerData.claims.add(claim);
                    totalClaimsArea += claim.getArea();
                }
            }

            //if total claimed area is more than total blocks available
            int totalBlocks = playerData.accruedClaimBlocks + playerData.getBonusClaimBlocks() + groupBonusBlocksService.getGroupBonusBlocks(player.getUniqueId());
            if (ConfigLoader.config_advanced_fixNegativeClaimblockAmounts && totalBlocks < totalClaimsArea) {
                GriefPrevention.AddLogEntry(player.getName() + " has more claimed land than blocks available.  Adding blocks to fix.", CustomLogEntryTypes.Debug, true);
                GriefPrevention.AddLogEntry(player.getName() + " Accrued blocks: " + claimBlockService.recalculateAccruedClaimBlocks(playerData) + " Bonus blocks: " + playerData.getBonusClaimBlocks(), CustomLogEntryTypes.Debug, true);
                GriefPrevention.AddLogEntry("Total blocks: " + totalBlocks + " Total claimed area: " + totalClaimsArea, CustomLogEntryTypes.Debug, true);
                for (Claim claim : playerData.claims) {
                    if (!claim.inDataStore) continue;
                    GriefPrevention.AddLogEntry(
                        GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()) + " // "
                            + GriefPrevention.getfriendlyLocationString(claim.getGreaterBoundaryCorner()) + " = "
                            + claim.getArea()
                        , CustomLogEntryTypes.Debug, true);
                }

                //try to fix it by adding to accrued blocks
                playerData.accruedClaimBlocks = totalClaimsArea; //Set accrued blocks to equal total claims
                int accruedLimit = playerData.getAccruedClaimBlocksLimit();
                playerData.accruedClaimBlocks = Math.min(accruedLimit, playerData.accruedClaimBlocks); //set accrued blocks to maximum limit, if it's smaller
                GriefPrevention.AddLogEntry("New accrued blocks: " + playerData.accruedClaimBlocks, CustomLogEntryTypes.Debug, true);

                //Recalculate total blocks (accrued + bonus + permission group bonus)
                totalBlocks = playerData.accruedClaimBlocks + playerData.getBonusClaimBlocks() + groupBonusBlocksService.getGroupBonusBlocks(player.getUniqueId());
                GriefPrevention.AddLogEntry("New total blocks: " + totalBlocks, CustomLogEntryTypes.Debug, true);

                //if that didn't fix it, then make up the difference with bonus blocks
                if (totalBlocks < totalClaimsArea) {
                    int bonusBlocksToAdd = totalClaimsArea - totalBlocks;
                    playerData.bonusClaimBlocks += bonusBlocksToAdd;
                    GriefPrevention.AddLogEntry("Accrued blocks weren't enough. Adding " + bonusBlocksToAdd + " bonus blocks.", CustomLogEntryTypes.Debug, true);
                }
                GriefPrevention.AddLogEntry(player.getName() + " Accrued blocks: " + claimBlockService.recalculateAccruedClaimBlocks(playerData) + " Bonus blocks: " + playerData.getBonusClaimBlocks() + " Group Bonus Blocks: " + groupBonusBlocksService.getGroupBonusBlocks(player.getUniqueId()), CustomLogEntryTypes.Debug, true);
                //Recalculate total blocks (accrued + bonus + permission group bonus)
                totalBlocks = playerData.accruedClaimBlocks + playerData.getBonusClaimBlocks() + groupBonusBlocksService.getGroupBonusBlocks(player.getUniqueId());
                GriefPrevention.AddLogEntry("Total blocks: " + totalBlocks + " Total claimed area: " + totalClaimsArea, CustomLogEntryTypes.Debug, true);
                GriefPrevention.AddLogEntry("Remaining claim blocks to use: " + claimBlockService.getRemainingClaimBlocks(playerData, getClaims(player)) + " (should be 0)", CustomLogEntryTypes.Debug, true);
            }
        }

        for (int i = 0; i < playerData.claims.size(); i++) {
            if (!playerData.claims.get(i).inDataStore) {
                playerData.claims.remove(i--);
            }
        }

        return playerData.claims;
    }
}

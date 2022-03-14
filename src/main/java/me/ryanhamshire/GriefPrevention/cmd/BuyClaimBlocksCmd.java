package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.EconomyHandler;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.claims.ClaimBlockService;
import me.ryanhamshire.GriefPrevention.claims.ClaimService;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("buyclaimblocks")
public class BuyClaimBlocksCmd extends AbstractCmd {
    private final PlayerDataRepository playerDataRepository;
    private final BukkitUtils bukkitUtils;
    private final EconomyHandler economyHandler;
    
    private final ClaimBlockService claimBlockService;
    private final ClaimService claimService;

    public BuyClaimBlocksCmd(PlayerDataRepository playerDataRepository, BukkitUtils bukkitUtils, EconomyHandler economyHandler, ClaimBlockService claimBlockService, ClaimService claimService) {
        this.playerDataRepository = playerDataRepository;
        this.bukkitUtils = bukkitUtils;
        this.economyHandler = economyHandler;
        
        this.claimBlockService = claimBlockService;
        this.claimService = claimService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        bukkitUtils.runTaskAsync(sender, () -> {

            //if economy is disabled, don't do anything
            EconomyHandler.EconomyWrapper economyWrapper = economyHandler.getWrapper();
            if (economyWrapper == null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.BuySellNotConfigured);
                return;
            }

            if (!player.hasPermission("griefprevention.buysellclaimblocks")) {
                MessageService.sendMessage(player, TextMode.Err, Messages.NoPermissionForCommand);
                return;
            }

            //if purchase disabled, send error message
            if (ConfigLoader.config_economy_claimBlocksPurchaseCost == 0) {
                MessageService.sendMessage(player, TextMode.Err, Messages.OnlySellBlocks);
                return;
            }

            Economy economy = economyWrapper.getEconomy();

            //if no parameter, just tell player cost per block and balance
            if (args.length != 1) {
                MessageService.sendMessage(player, TextMode.Info, Messages.BlockPurchaseCost, String.valueOf(ConfigLoader.config_economy_claimBlocksPurchaseCost), String.valueOf(economy.getBalance(player)));
            } else {
                PlayerData playerData = this.playerDataRepository.getPlayerData(player.getUniqueId());

                //try to parse number of blocks
                int blockCount;
                try {
                    blockCount = Integer.parseInt(args[0]);
                } catch (NumberFormatException numberFormatException) {
                    return;  //causes usage to be displayed
                }

                if (blockCount <= 0) {
                    return;
                }

                //if the player can't afford his purchase, send error message
                double balance = economy.getBalance(player);
                double totalCost = blockCount * ConfigLoader.config_economy_claimBlocksPurchaseCost;
                if (totalCost > balance) {
                    MessageService.sendMessage(player, TextMode.Err, Messages.InsufficientFunds, String.valueOf(totalCost), String.valueOf(balance));
                }

                //otherwise carry out transaction
                else {
                    int newBonusClaimBlocks = playerData.getBonusClaimBlocks() + blockCount;

                    //if the player is going to reach max bonus limit, send error message
                    int bonusBlocksLimit = ConfigLoader.config_economy_claimBlocksMaxBonus;
                    if (bonusBlocksLimit != 0 && newBonusClaimBlocks > bonusBlocksLimit) {
                        MessageService.sendMessage(player, TextMode.Err, Messages.MaxBonusReached, String.valueOf(blockCount), String.valueOf(bonusBlocksLimit));
                        return;
                    }

                    //withdraw cost
                    economy.withdrawPlayer(player, totalCost);

                    //add blocks
                    playerData.setBonusClaimBlocks(playerData.getBonusClaimBlocks() + blockCount);
                    this.playerDataRepository.savePlayerData(player.getUniqueId(), playerData);

                    //inform player
                    MessageService.sendMessage(player, TextMode.Success, Messages.PurchaseConfirmation, String.valueOf(totalCost), String.valueOf(claimBlockService.getRemainingClaimBlocks(playerData, claimService.getClaims(player.getUniqueId(), player.getName()))));
                }
            }
        });

        return true;
    }
}

package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.EconomyHandler;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("sellclaimblocks")
public class SellClaimBlocksCmd extends AbstractCmd {
    private final DataStore dataStore;
    private final BukkitUtils bukkitUtils;
    private final EconomyHandler economyHandler;
    private final MessageService messageService;

    public SellClaimBlocksCmd(DataStore dataStore, BukkitUtils bukkitUtils, EconomyHandler economyHandler, MessageService messageService) {
        this.dataStore = dataStore;
        this.bukkitUtils = bukkitUtils;
        this.economyHandler = economyHandler;
        this.messageService = messageService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        bukkitUtils.runTaskAsync(sender, () -> {

            //if economy is disabled, don't do anything
            EconomyHandler.EconomyWrapper economyWrapper = economyHandler.getWrapper();
            if (economyWrapper == null) {
                messageService.sendMessage(player, TextMode.Err, Messages.BuySellNotConfigured);
                return;
            }

            if (!player.hasPermission("griefprevention.buysellclaimblocks")) {
                messageService.sendMessage(player, TextMode.Err, Messages.NoPermissionForCommand);
                return;
            }

            //if disabled, error message
            if (ConfigLoader.config_economy_claimBlocksSellValue == 0) {
                messageService.sendMessage(player, TextMode.Err, Messages.OnlyPurchaseBlocks);
                return;
            }

            //load player data
            PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
            int availableBlocks = playerData.getRemainingClaimBlocks();

            //if no amount provided, just tell player value per block sold, and how many he can sell
            if (args.length != 1) {
                messageService.sendMessage(player, TextMode.Info, Messages.BlockSaleValue, String.valueOf(ConfigLoader.config_economy_claimBlocksSellValue), String.valueOf(availableBlocks));
                return;
            }

            //parse number of blocks
            int blockCount;
            try {
                blockCount = Integer.parseInt(args[0]);
            } catch (NumberFormatException numberFormatException) {
                return;  //causes usage to be displayed
            }

            if (blockCount <= 0) {
                return;
            }

            //if he doesn't have enough blocks, tell him so
            if (blockCount > availableBlocks) {
                messageService.sendMessage(player, TextMode.Err, Messages.NotEnoughBlocksForSale);
            }

            //otherwise carry out the transaction
            else {
                //compute value and deposit it
                double totalValue = blockCount * ConfigLoader.config_economy_claimBlocksSellValue;
                economyWrapper.getEconomy().depositPlayer(player, totalValue);

                //subtract blocks
                playerData.setBonusClaimBlocks(playerData.getBonusClaimBlocks() - blockCount);
                this.dataStore.savePlayerData(player.getUniqueId(), playerData);

                //inform player
                messageService.sendMessage(player, TextMode.Success, Messages.BlockSaleConfirmation, String.valueOf(totalValue), String.valueOf(playerData.getRemainingClaimBlocks()));
            }
        });

        return true;
    }
}

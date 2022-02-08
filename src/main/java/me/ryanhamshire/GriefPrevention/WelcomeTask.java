package me.ryanhamshire.GriefPrevention;

import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

public class WelcomeTask implements Runnable
{
    private final Player player;
    private final DataStore datastore;

    public WelcomeTask(Player player, DataStore dataStore)
    {
        this.player = player;
        this.datastore = dataStore;
    }

    @Override
    public void run()
    {
        //abort if player has logged out since this task was scheduled
        if (!this.player.isOnline()) return;

        //offer advice and a helpful link
        messageService.sendMessage(player, TextMode.Instr, Messages.AvoidGriefClaimLand);
        messageService.sendMessage(player, TextMode.Instr, Messages.SurvivalBasicsVideo2, DataStore.SURVIVAL_VIDEO_URL);

        //give the player a reference book for later
        if (ConfigLoader.config_claims_supplyPlayerManual)
        {
            ItemFactory factory = Bukkit.getItemFactory();
            BookMeta meta = (BookMeta) factory.getItemMeta(Material.WRITTEN_BOOK);

            meta.setAuthor(MessageService.getMessage(Messages.BookAuthor));
            meta.setTitle(MessageService.getMessage(Messages.BookTitle));

            StringBuilder page1 = new StringBuilder();
            String URL = MessageService.getMessage(Messages.BookLink, DataStore.SURVIVAL_VIDEO_URL);
            String intro = MessageService.getMessage(Messages.BookIntro);

            page1.append(URL).append("\n\n");
            page1.append(intro).append("\n\n");
            String editToolName = ConfigLoader.config_claims_modificationTool.name().replace('_', ' ').toLowerCase();
            String infoToolName = ConfigLoader.config_claims_investigationTool.name().replace('_', ' ').toLowerCase();
            String configClaimTools = MessageService.getMessage(Messages.BookTools, editToolName, infoToolName);
            page1.append(configClaimTools);
            if (ConfigLoader.config_claims_automaticClaimsForNewPlayersRadius < 0)
            {
                page1.append(MessageService.getMessage(Messages.BookDisabledChestClaims));
            }

            StringBuilder page2 = new StringBuilder(MessageService.getMessage(Messages.BookUsefulCommands)).append("\n\n");
            page2.append("/Trust /UnTrust /TrustList\n");
            page2.append("/ClaimsList\n");
            page2.append("/AbandonClaim\n\n");
            page2.append("/Claim /ExtendClaim\n");

            page2.append("/IgnorePlayer\n\n");

            page2.append("/SubdivideClaims\n");
            page2.append("/AccessTrust\n");
            page2.append("/ContainerTrust\n");
            page2.append("/PermissionTrust");

            meta.setPages(page1.toString(), page2.toString());

            ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
            item.setItemMeta(meta);
            player.getInventory().addItem(item);
        }

    }


}

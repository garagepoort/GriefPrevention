package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.MessageService;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.util.BukkitUtils;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@IocCommandHandler("givepet")
public class GivePetCmd extends AbstractCmd {
    private final DataStore dataStore;
    private final BukkitUtils bukkitUtils;
    

    public GivePetCmd(DataStore dataStore, BukkitUtils bukkitUtils) {
        this.dataStore = dataStore;
        this.bukkitUtils = bukkitUtils;
        
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        //requires one parameter
        if (args.length < 1) return false;
        bukkitUtils.runTaskAsync(sender, () -> {
            PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());

            //special case: cancellation
            if (args[0].equalsIgnoreCase("cancel")) {
                playerData.petGiveawayRecipient = null;
                MessageService.sendMessage(player, TextMode.Success, Messages.PetTransferCancellation);
                return;
            }

            //find the specified player
            OfflinePlayer targetPlayer = GriefPrevention.get().resolvePlayerByName(args[0]);
            if (targetPlayer == null) {
                MessageService.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return;
            }

            //remember the player's ID for later pet transfer
            playerData.petGiveawayRecipient = targetPlayer;

            //send instructions
            MessageService.sendMessage(player, TextMode.Instr, Messages.ReadyToTransferPet);
        });

        return true;
    }
}

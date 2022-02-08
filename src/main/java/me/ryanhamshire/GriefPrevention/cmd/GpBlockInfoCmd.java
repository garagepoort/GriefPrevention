package me.ryanhamshire.GriefPrevention.cmd;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.IocCommandHandler;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@IocBean
@IocCommandHandler("gpblockinfo")
public class GpBlockInfoCmd extends AbstractCmd {

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        validateIsPlayer(sender);
        Player player = (Player) sender;
        ItemStack inHand = player.getInventory().getItemInMainHand();
        player.sendMessage("In Hand: " + inHand.getType().name());

        Block inWorld = player.getTargetBlockExact(300, FluidCollisionMode.ALWAYS);
        if (inWorld == null) inWorld = player.getEyeLocation().getBlock();
        player.sendMessage("In World: " + inWorld.getType().name());
        return true;
    }
}

package me.ryanhamshire.GriefPrevention.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static org.bukkit.ChatColor.translateAlternateColorCodes;

public abstract class AbstractCmd implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        try {
            return executeCmd(sender, alias, args);
        } catch (BusinessException e) {
            sender.sendMessage(translateAlternateColorCodes('&', "&6[GriefPrevention] &C" + e.getMessage()));
            return false;
        }
    }

    protected void validateIsPlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            throw new BusinessException("Only players can execute this command");
        }
    }

    protected abstract boolean executeCmd(CommandSender sender, String alias, String[] args);


}

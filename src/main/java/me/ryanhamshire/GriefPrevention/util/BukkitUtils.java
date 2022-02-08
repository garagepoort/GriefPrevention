package me.ryanhamshire.GriefPrevention.util;

import be.garagepoort.mcioc.IocBean;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.cmd.BusinessException;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import static org.bukkit.Bukkit.getScheduler;
import static org.bukkit.ChatColor.translateAlternateColorCodes;

@IocBean
public class BukkitUtils {

    public static void sendEvent(Event event) {
        if (GriefPrevention.get().isEnabled()) {
            getScheduler().runTask(GriefPrevention.get(), () -> Bukkit.getPluginManager().callEvent(event));
        }
    }

    public static void sendEventOnThisTick(Event event) {
        if (GriefPrevention.get().isEnabled()) {
            Bukkit.getPluginManager().callEvent(event);
        }
    }

    public static void sendEventAsync(Event event) {
        getScheduler().runTaskAsynchronously(GriefPrevention.get(), () -> Bukkit.getPluginManager().callEvent(event));
    }

    public void runTaskAsync(CommandSender sender, Runnable runnable) {
        getScheduler().runTaskAsynchronously(GriefPrevention.get(), () -> {
            try {
                runnable.run();
            } catch (BusinessException e) {
                sender.sendMessage(translateAlternateColorCodes('&', "&6[GriefPrevention] &C" + e.getMessage()));
            }
        });
    }

    public void runTaskLater(CommandSender sender, Runnable runnable) {
        getScheduler().runTaskLater(GriefPrevention.get(), () -> {
            try {
                runnable.run();
            } catch (BusinessException e) {
                sender.sendMessage(translateAlternateColorCodes('&', "&6[GriefPrevention] &C" + e.getMessage()));
            }
        }, 1);
    }

    public void runTaskLater(Runnable runnable) {
        this.runTaskLater(Bukkit.getConsoleSender(), runnable);
    }

    public void runTaskAsync(Runnable runnable) {
        this.runTaskAsync(Bukkit.getConsoleSender(), runnable);
    }

    public static int getInventorySize(int amountOfItems) {
        int division = amountOfItems / 9;
        int rest = amountOfItems % 9;
        if (rest != 0) {
            division++;
            return division * 9;
        }
        return amountOfItems;
    }

    public static String getIpFromPlayer(Player player) {
        return player.getAddress().getAddress().getHostAddress().replace("/", "");
    }

}

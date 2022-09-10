package com.slprojects.slcraftplugin.utils;

import com.slprojects.slcraftplugin.Main;
import org.bukkit.ChatColor;

import static org.bukkit.Bukkit.getServer;

public class ConsoleLog {
    public static void info(String message) {
        getServer().getConsoleSender().sendMessage("[" + Main.pluginName + "] " + message);
    }

    public static void warning(String message) {
        getServer().getConsoleSender().sendMessage(ChatColor.GOLD + "[" + Main.pluginName + "] " + message);
    }

    public static void danger(String message) {
        getServer().getConsoleSender().sendMessage(ChatColor.RED + "[" + Main.pluginName + "] " + message);
    }

    public static void success(String message) {
        getServer().getConsoleSender().sendMessage(ChatColor.GREEN + "[" + Main.pluginName + "] " + message);
    }
}

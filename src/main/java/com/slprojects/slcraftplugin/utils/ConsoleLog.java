package com.slprojects.slcraftplugin.utils;

import com.slprojects.slcraftplugin.Main;
import org.bukkit.ChatColor;

import static org.bukkit.Bukkit.getServer;

public class ConsoleLog {
    public static void info(String message) {
ConsoleLog.info("[" + Main.pluginName + "] " + message);
    }

    public static void warning(String message) {
ConsoleLog.warning("[" + Main.pluginName + "] " + message);
    }

    public static void danger(String message) {
ConsoleLog.danger("[" + Main.pluginName + "] " + message);
    }

    public static void success(String message) {
ConsoleLog.success("[" + Main.pluginName + "] " + message);
    }
}

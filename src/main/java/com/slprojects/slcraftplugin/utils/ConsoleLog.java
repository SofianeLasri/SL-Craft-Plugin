package com.slprojects.slcraftplugin.utils;

import com.slprojects.slcraftplugin.Main;
import org.bukkit.ChatColor;

import static org.bukkit.Bukkit.getServer;

public class ConsoleLog {
    /**
     * Envoyer un message d'information dans la console.
     *
     * @param message Message à envoyer
     */
    public static void info(String message) {
        getServer().getConsoleSender().sendMessage("[" + Main.pluginName + "] " + message);
    }

    /**
     * Envoyer un message d'avertissement dans la console.
     *
     * @param message Message à envoyer
     */
    public static void warning(String message) {
        getServer().getConsoleSender().sendMessage(ChatColor.GOLD + "[" + Main.pluginName + "] " + message);
    }

    /**
     * Envoyer un message d'erreur dans la console.
     *
     * @param message Message à envoyer
     */
    public static void danger(String message) {
        getServer().getConsoleSender().sendMessage(ChatColor.RED + "[" + Main.pluginName + "] " + message);
    }

    /**
     * Envoyer un message de succès dans la console.
     *
     * @param message Message à envoyer
     */
    public static void success(String message) {
        getServer().getConsoleSender().sendMessage(ChatColor.GREEN + "[" + Main.pluginName + "] " + message);
    }
}

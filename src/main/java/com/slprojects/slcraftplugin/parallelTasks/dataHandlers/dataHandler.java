package com.slprojects.slcraftplugin.parallelTasks.dataHandlers;

import org.bukkit.entity.Player;

public interface dataHandler {
    void joinEvent(Player player);

    void quitEvent(Player player);
}

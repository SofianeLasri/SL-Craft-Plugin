package com.slprojects.slcraftplugin.parallelTasks.events;

import com.slprojects.slcraftplugin.Main;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class PeriodicEvent {
    private final Main plugin;
    private boolean doesTheEventIsCurrentlyRunning = false;
    private final int periodicEventCallTime;

    public PeriodicEvent(Main plugin) {
        this.plugin = plugin;
        startPeriodicEvent();
        periodicEventCallTime = plugin.getConfig().getInt("periodic-event-call-time") * plugin.getConfig().getInt("ticks-per-seconds");
    }

    public void startPeriodicEvent() {
        if (doesTheEventIsCurrentlyRunning)
            throw new RuntimeException("L'exécution de l'évènement périodique est déjà enclanchée.");

        doesTheEventIsCurrentlyRunning = true;

        new BukkitRunnable() {
            @Override
            public void run() {
                periodicEvent();
            }
        }.runTaskLater(plugin, (periodicEventCallTime));
    }

    private void periodicEvent() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            plugin.playerDataHandler.playedTimeHandler.savePlayedTime(player);
        }
        //ConsoleLog.warning("[SL-Craft] Évènement périodique éxecuté.");

        // S'exécute à la fin
        doesTheEventIsCurrentlyRunning = false;
        startPeriodicEvent();
    }
}

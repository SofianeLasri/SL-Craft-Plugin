package com.slprojects.slcraftplugin.parallelTasks;

import com.slprojects.slcraftplugin.Main;
import org.bukkit.scheduler.BukkitRunnable;

public class PeriodicEvent {
    private final Main plugin;
    private boolean doesTheEventHasBeenCalled = false;
    private final int periodicEventCallTime;

    public PeriodicEvent(Main plugin){
        this.plugin = plugin;
        startPeriodicEvent();
        periodicEventCallTime = plugin.getConfig().getInt("periodicEventCallTime");
    }

    public void startPeriodicEvent(){
        if(doesTheEventHasBeenCalled) throw new RuntimeException("L'exécution de l'évènement périodique est déjà enclanchée.");

        doesTheEventHasBeenCalled = true;
        new BukkitRunnable(){
            @Override
            public void run(){
                periodicEvent();
            }
        }.runTaskLater(plugin, (long) periodicEventCallTime * Main.ticksPerSeconds);
    }

    private void periodicEvent(){
        // S'exécute à la fin
        startPeriodicEvent();
    }
}

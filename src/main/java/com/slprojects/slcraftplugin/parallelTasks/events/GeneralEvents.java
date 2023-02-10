package com.slprojects.slcraftplugin.parallelTasks.events;

import com.slprojects.slcraftplugin.Main;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class GeneralEvents {
    /**
     * Joue un son de feu d'artifice
     *
     * @param player Joueur
     * @param plugin Plugin
     */
    public static void playFireworkSoundEffect(Player player, Main plugin) {
        int min = 1;
        int max = 25;
        Random random = new Random();

        for (int i = 0; i < 6; i++) {
            int delay = random.nextInt(max - min + 1) + min;
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 100, 2);
                }
            }.runTaskLater(plugin, delay);

            new BukkitRunnable() {
                @Override
                public void run() {
                    player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 100, 2);
                }
            }.runTaskLater(plugin, delay + 40);
        }
    }
}

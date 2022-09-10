package com.slprojects.slcraftplugin.parallelTasks.dataHandlers;

import com.slprojects.slcraftplugin.Main;
import com.slprojects.slcraftplugin.utils.Database;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayedTimeHandler implements dataHandler {
    private final Main plugin;
    private final List<UUID> usersIndexes;
    private final List<LocalDateTime> userSessionJoinDateTime;
    private final List<Long> userStoredPlayedTimeBeforeJoining;

    public PlayedTimeHandler(Main plugin) {
        this.plugin = plugin;
        usersIndexes = new ArrayList<>();
        userSessionJoinDateTime = new ArrayList<>();
        userStoredPlayedTimeBeforeJoining = new ArrayList<>();
    }

    @Override
    public void joinEvent(Player player) {
        usersIndexes.add(player.getUniqueId());
        userSessionJoinDateTime.add(LocalDateTime.now());

        if (plugin.playerDataHandler.playerAlreadyJoined(player)) {
            userStoredPlayedTimeBeforeJoining.add(Long.valueOf(Database.getUserSetting(player.getUniqueId().toString(), "playedTime")));
        } else {
            userStoredPlayedTimeBeforeJoining.add(0L);
        }
    }

    @Override
    public void quitEvent(Player player) {
        savePlayedTime(player); // On actualise le temps de jeu du joueur
    }

    public void savePlayedTime(Player player) {
        // On va calculer le temps de jeu du joueur
        UUID playerUuid = player.getUniqueId();
        LocalDateTime timeNow = LocalDateTime.now();
        Duration duration = Duration.between(timeNow, userSessionJoinDateTime.get(usersIndexes.indexOf(playerUuid)));
        long playedTimeInSeconds = Math.abs(duration.toSeconds());
        long actualPlayedTime = userStoredPlayedTimeBeforeJoining.get(usersIndexes.indexOf(playerUuid)) + playedTimeInSeconds;

        Database.setUserSetting(playerUuid.toString(), "playedTime", String.valueOf(actualPlayedTime));
    }
}

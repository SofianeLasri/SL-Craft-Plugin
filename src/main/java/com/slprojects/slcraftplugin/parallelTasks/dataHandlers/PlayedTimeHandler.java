package com.slprojects.slcraftplugin.parallelTasks.dataHandlers;

import com.slprojects.slcraftplugin.Main;
import com.slprojects.slcraftplugin.parallelTasks.events.GeneralEvents;
import com.slprojects.slcraftplugin.utils.Database;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

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
    private final int requiredPlayedTimeForUpgradingPlayersAccount;
    private final Group playersAccountUpgradeGroup;

    public PlayedTimeHandler(Main plugin) {
        this.plugin = plugin;
        usersIndexes = new ArrayList<>();
        userSessionJoinDateTime = new ArrayList<>();
        userStoredPlayedTimeBeforeJoining = new ArrayList<>();
        requiredPlayedTimeForUpgradingPlayersAccount = plugin.getConfig().getInt("stats.required-played-time-for-upgrading-players-account");
        playersAccountUpgradeGroup = plugin.luckPermsApi.getGroupManager().getGroup(plugin.getConfig().getString("stats.players-account-upgrade-role"));
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

        // Vérification pour avoir le rôle habitué
        // TODO: Ne sauvegarde pas. :/
        if(actualPlayedTime >= requiredPlayedTimeForUpgradingPlayersAccount){
            String playerGroupName = plugin.luckPermsApi.getPlayerAdapter(Player.class).getMetaData(player).getPrimaryGroup();
            if(playerGroupName != playersAccountUpgradeGroup.getName()){
                player.sendMessage("Ton rôle: " + playerGroupName + " - Groupe visé: " + playersAccountUpgradeGroup.getName());

                Group playerGroup = plugin.luckPermsApi.getGroupManager().getGroup(playerGroupName);
                if(playerGroup.getWeight().getAsInt() < playersAccountUpgradeGroup.getWeight().getAsInt()){
                    User playerLuckPerms = plugin.luckPermsApi.getUserManager().getUser(player.getUniqueId());
                    playerLuckPerms.setPrimaryGroup(playersAccountUpgradeGroup.getName());
                    plugin.luckPermsApi.getUserManager().saveUser(playerLuckPerms);

                    int requiredPlayedTimeInHours = requiredPlayedTimeForUpgradingPlayersAccount / 60 / 60;
                    player.sendMessage(ChatColor.GREEN + "Bravo tu as passé plus de WOAW, " + ChatColor.GOLD + requiredPlayedTimeInHours + "H " + ChatColor.GREEN + "sur le serveur !!!");
                    player.sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.GREEN + "Bravo " + ChatColor.GOLD + player.getName() + ChatColor.GREEN + ",  tu est maintenant un joueur habitué!"));

                    // Feux d'artifices
                    GeneralEvents.fireworkSoundEffect(player, plugin);
                }
            }
        }
    }
}

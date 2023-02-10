package com.slprojects.slcraftplugin.parallelTasks.dataHandlers;

import com.slprojects.slcraftplugin.Main;
import com.slprojects.slcraftplugin.parallelTasks.events.GeneralEvents;
import com.slprojects.slcraftplugin.utils.ConsoleLog;
import com.slprojects.slcraftplugin.utils.Database;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class PlayedTimeHandler implements dataHandler {
    private final Main plugin;
    private final List<UUID> usersIndexes;
    private final List<LocalDateTime> userSessionJoinDateTime;
    private final List<Long> userStoredPlayedTimeBeforeJoining;
    private final int requiredPlayedTimeForUpgradingPlayersAccount;
    private final Group playersAccountUpgradeGroup;

    /**
     * Gère le temps de jeu des joueurs
     *
     * @param plugin Plugin
     */
    public PlayedTimeHandler(Main plugin) {
        this.plugin = plugin;
        String playersAccountUpgradeRole = plugin.getConfig().getString("stats.players-account-upgrade-role");

        if (playersAccountUpgradeRole != null) {
            usersIndexes = new ArrayList<>();
            userSessionJoinDateTime = new ArrayList<>();
            userStoredPlayedTimeBeforeJoining = new ArrayList<>();
            requiredPlayedTimeForUpgradingPlayersAccount = plugin.getConfig().getInt("stats.required-played-time-for-upgrading-players-account");
            playersAccountUpgradeGroup = Main.luckPermsApi.getGroupManager().getGroup(playersAccountUpgradeRole);

            if (playersAccountUpgradeGroup == null) {
                throw new RuntimeException("Le groupe " + playersAccountUpgradeRole + " n'existe pas !");
            }
        } else {
            throw new RuntimeException("La configuration stats.required-played-time-for-upgrading-players-account n'existe pas !");
        }
    }

    /**
     * Gestion de l'arrivée d'un joueur
     *
     * @param player Joueur
     */
    @Override
    public void joinEvent(Player player) {
        usersIndexes.add(player.getUniqueId());
        userSessionJoinDateTime.add(LocalDateTime.now());

        if (plugin.playerDataHandler.playerAlreadyJoined(player)) {
            userStoredPlayedTimeBeforeJoining.add(Long.valueOf(Database.getUserSetting(player.getUniqueId().toString(), "playedTime")));
            // Delay sinon le joueur ne voit pas
            new BukkitRunnable() {
                @Override
                public void run() {
                    checkPlayerTime(player);
                }
            }.runTaskLater(plugin, 20);
        } else {
            userStoredPlayedTimeBeforeJoining.add(0L);
        }
    }

    /**
     * Gestion de la déconnexion d'un joueur
     *
     * @param player Joueur
     */
    @Override
    public void quitEvent(Player player) {
        savePlayedTime(player); // On actualise le temps de jeu du joueur
    }

    /**
     * Sauvegarde le temps de jeu du joueur
     *
     * @param player Joueur
     */
    public void savePlayedTime(Player player) {
        // On va calculer le temps de jeu du joueur
        UUID playerUuid = player.getUniqueId();
        LocalDateTime timeNow = LocalDateTime.now();
        Duration duration = Duration.between(timeNow, userSessionJoinDateTime.get(usersIndexes.indexOf(playerUuid)));
        long playedTimeInSeconds = Math.abs(duration.toSeconds());
        long actualPlayedTime = userStoredPlayedTimeBeforeJoining.get(usersIndexes.indexOf(playerUuid)) + playedTimeInSeconds;

        Database.setUserSetting(playerUuid.toString(), "playedTime", String.valueOf(actualPlayedTime));

        // Vérification pour avoir le rôle habitué
        checkPlayerTime(player);
    }

    /**
     * Vérifie si le joueur a débloqué le rôle habitué
     *
     * @param player Joueur
     */
    public void checkPlayerTime(Player player) {
        // On va calculer le temps de jeu du joueur
        UUID playerUuid = player.getUniqueId();
        LocalDateTime timeNow = LocalDateTime.now();
        Duration duration = Duration.between(timeNow, userSessionJoinDateTime.get(usersIndexes.indexOf(playerUuid)));
        long playedTimeInSeconds = Math.abs(duration.toSeconds());
        long actualPlayedTime = userStoredPlayedTimeBeforeJoining.get(usersIndexes.indexOf(playerUuid)) + playedTimeInSeconds;

        if (actualPlayedTime >= requiredPlayedTimeForUpgradingPlayersAccount) {
            String playerGroupName = Main.luckPermsApi.getPlayerAdapter(Player.class).getMetaData(player).getPrimaryGroup();

            if (playerGroupName != null && !Objects.equals(playerGroupName, playersAccountUpgradeGroup.getName())) {
                Group playerGroup = Main.luckPermsApi.getGroupManager().getGroup(playerGroupName);
                if (playerGroup == null) {
                    throw new RuntimeException("Le groupe " + playerGroupName + " n'existe pas !");
                }

                if (playerGroup.getWeight().isPresent() && playersAccountUpgradeGroup.getWeight().isPresent()) {
                    if (playerGroup.getWeight().getAsInt() < playersAccountUpgradeGroup.getWeight().getAsInt()) {
                        ConsoleLog.info(ChatColor.GREEN + player.getName() + ChatColor.LIGHT_PURPLE + " a débloqué le rôle des joueurs " + ChatColor.GOLD + "habitués" + ChatColor.LIGHT_PURPLE + "!");
                        User playerLuckPerms = Main.luckPermsApi.getUserManager().getUser(player.getUniqueId());
                        if (playerLuckPerms == null) {
                            throw new RuntimeException("LuckPerms ne semble pas disposer de donnée sur le joueur " + player.getName() + " UUID:" + player.getUniqueId());
                        }

                        // https://www.spigotmc.org/threads/how-can-i-set-a-players-group-with-luckperms-api.489404/#post-4084060
                        InheritanceNode node = InheritanceNode.builder(playersAccountUpgradeGroup).value(true).build();
                        playerLuckPerms.data().add(node);
                        Main.luckPermsApi.getUserManager().saveUser(playerLuckPerms);

                        int requiredPlayedTimeInHours = requiredPlayedTimeForUpgradingPlayersAccount / 60 / 60;
                        player.sendMessage(ChatColor.GREEN + "Bravo et un grand merci à toi " + ChatColor.YELLOW + player.getName() + ChatColor.GREEN + "!");
                        player.sendMessage(ChatColor.GREEN + "Tu as joué pendant plus de" + ChatColor.GOLD + requiredPlayedTimeInHours + "H " + ChatColor.GREEN + "sur le serveur !!!");
                        player.sendMessage("Pour te récompenser, nous te donnons le rôle des joueurs " + ChatColor.GOLD + "habitués" + ChatColor.RESET + "!");
                        player.sendMessage(ChatColor.GREEN + "Ce rôle te donne accès à un plus grand nombre de homes et à une plus grande surface utilisable pour protéger tes constructions avec RedProtect.");

                        for (Player connectedPlayer : plugin.getServer().getOnlinePlayers()) {
                            if (connectedPlayer != player) {
                                connectedPlayer.sendMessage(ChatColor.GREEN + player.getName() + ChatColor.LIGHT_PURPLE + " a débloqué le rôle des joueurs " + ChatColor.GOLD + "habitués" + ChatColor.LIGHT_PURPLE + "!");
                            }
                            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 100, 2);
                        }

                        plugin.sendMessageToDiscord("**" + player.getName() + "** a débloqué le rôle des joueurs **habitués**! \uD83E\uDD73");
                        plugin.sendMessageToDiscord("Un grand merci à toi qui a passé plus de 20H de jeu sur le serveur!  ❤");

                        // Feux d'artifices
                        GeneralEvents.playFireworkSoundEffect(player, plugin);
                    }
                } else {
                    ConsoleLog.danger("Impossible de vérifier la priorité du ou des rôles suivants : " + playerGroupName + " & " + playersAccountUpgradeGroup.getName());
                }
            }
        }
    }
}

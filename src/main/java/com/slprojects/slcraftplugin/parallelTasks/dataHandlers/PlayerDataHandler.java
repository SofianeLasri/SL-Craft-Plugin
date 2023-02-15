package com.slprojects.slcraftplugin.parallelTasks.dataHandlers;

import com.slprojects.slcraftplugin.Main;
import com.slprojects.slcraftplugin.utils.ConsoleLog;
import com.slprojects.slcraftplugin.utils.Database;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerDataHandler implements dataHandler {
    private final Main plugin;
    private Connection con;
    // Playtime
    public final PlayedTimeHandler playedTimeHandler;
    private final List<UUID> playerIndexes;
    private final List<Boolean> playerAlreadyJoined;

    public PlayerDataHandler(Main plugin) {
        this.plugin = plugin;
        this.playedTimeHandler = new PlayedTimeHandler(plugin);
        playerIndexes = new ArrayList<>();
        playerAlreadyJoined = new ArrayList<>();
    }

    @Override
    public void joinEvent(Player player) {
        // On ouvre la bdd
        con = plugin.bddOpenConn();

        playerIndexes.add(player.getUniqueId());
        playerAlreadyJoined.add(insertPlayerName(player)); // On check si le nom du joueur est déjà enregistré
        statsPlayerEntryExit(player, true); // On ajoute son entée
        checkPlayerJoinedDate(player); // On check si on dipose de sa date de rejoint
        setPlayerJoinCount(player); // On set le nombre de fois qu'il a rejoint
        plugin.wildCommand.setPlayerStats(player, getPlayerWildCmdStats(player));

        playedTimeHandler.joinEvent(player);

        // On ferme la bdd
        try {
            con.close();
        } catch (SQLException e) {
            ConsoleLog.warning("Impossible de fermer la connexion à la bdd. Func savePlayerData::saveOnJoin(Player player)");
            e.printStackTrace();
        }
    }

    @Override
    public void quitEvent(Player player) {
        // On ouvre la bdd
        con = plugin.bddOpenConn();

        playedTimeHandler.quitEvent(player);
        statsPlayerEntryExit(player, false); // On ajoute son sortie
        savePlayerWildCmdStats(player, plugin.wildCommand.getPlayerStats(player));

        // On ferme la bdd
        try {
            con.close();
        } catch (SQLException e) {
            ConsoleLog.warning("Impossible de fermer la connexion à la bdd. Func savePlayerData::saveOnQuit(Player player)");
            e.printStackTrace();
        }
    }

    // Fonctions
    private boolean insertPlayerName(Player player) {
        String savedPlayerName = Database.getUserSetting(player.getUniqueId().toString(), "playerName");

        if (savedPlayerName != null) {
            // On a déjà renseigné le nom du joueur on va donc vérifier s'il a besoin d'être mis à jour
            if (!savedPlayerName.equals(player.getName())) {
                Database.setUserSetting(player.getUniqueId().toString(), "playerName", player.getName());
            }
            return true;
        } else {
            // On peut insérer le nom du joueur
            Database.setUserSetting(player.getUniqueId().toString(), "playerName", player.getName());
            return false;
        }
    }

    private void statsPlayerEntryExit(Player player, boolean isEnter) {
        try {
            PreparedStatement insertPlayerEntryOrExit = con.prepareStatement("INSERT INTO site_playerEntries (uuid, isJoin, date) VALUES (?, ?, ?)");
            insertPlayerEntryOrExit.setString(1, player.getUniqueId().toString());
            insertPlayerEntryOrExit.setBoolean(2, isEnter);
            insertPlayerEntryOrExit.setString(3, Timestamp.valueOf(java.time.LocalDateTime.now()).toString());
            insertPlayerEntryOrExit.executeQuery();
        } catch (SQLException e) {
            ConsoleLog.warning("Func savePlayerData::playerAddPlayerEntryOrExit(Player player, boolean isEnter)");
            e.printStackTrace();
        }
    }

    private void checkPlayerJoinedDate(Player player) {
        try {
            // On va vérifier si on l'a déjà renseigné par le passé
            String joinedDate = Database.getUserSetting(player.getUniqueId().toString(), "joinedDate");

            if (joinedDate == null) {
                // On n'a pas renseigné la date de création du joueur
                if (player.hasPlayedBefore()) {
                    // On va piocher la date d'inscription chez CoreProtect (si elle existe)
                    // On la prend chez CoreProtect car le plugin a été installé dans les premières semaines du serveur. Il a donc bcp plus de données que nous concernant les anciens joueurs.
                    PreparedStatement rechercheDateInscription = con.prepareStatement("SELECT time FROM co_user WHERE uuid = ?");
                    rechercheDateInscription.setString(1, player.getUniqueId().toString());
                    ResultSet resultat = rechercheDateInscription.executeQuery();

                    if (resultat.next()) {
                        // On insère la date d'inscription
                        Database.setUserSetting(player.getUniqueId().toString(),
                                "joinedDate",
                                java.sql.Timestamp.valueOf(
                                        LocalDateTime.ofEpochSecond(
                                                Long.parseLong(resultat.getString("time")),
                                                0,
                                                ZoneOffset.UTC
                                        )
                                ).toString()
                        );

                        // On va précisier que la date d'inscription a été trouvée chez CoreProtect
                        ConsoleLog.info("Le joueur " + ChatColor.GOLD + player.getName() + ChatColor.RESET + " n'avait pas de données sur sa date d'inscription dans dans la table des paramètres utilisateurs. On lui a donc attribué comme date de création du compte, celle que détenait CoreProtect.");
                    } else {
                        // On insère la date d'inscription (du coup on considère que Le joueur n'a pas joué avant, malgré la condition)
                        Database.setUserSetting(
                                player.getUniqueId().toString(),
                                "joinedDate",
                                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()).toString()
                        );

                        // On va préciser que la date d'inscription n'a pas été trouvée chez CoreProtect
                        Database.setUserSetting(player.getUniqueId().toString(),
                                "inaccurrateJoinedDate",
                                "true"
                        );

                        // On est daccord que ceci n'est pas censé arriver, cela ne concerne que mes potes n'étant venus que durant les premières semaines du serveur.
                        ConsoleLog.info("Le joueur " + ChatColor.GOLD + player.getName() + ChatColor.RESET + " n'avait pas de données sur sa date d'inscription dans dans la table des paramètres utilisateurs, ni dans la table des utilisateurs de CoreProtect. On lui a donc attribué comme date de création du compte, la date du début de sa partie.");
                    }
                } else {
                    // Le joueur est nouveau, on insère la date d'inscription
                    Database.setUserSetting(
                            player.getUniqueId().toString(),
                            "joinedDate",
                            java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()).toString()
                    );
                }
            }
        } catch (SQLException e) {
            ConsoleLog.warning("Func savePlayerData::checkJoinedDate(Player player)");
            e.printStackTrace();
        }
    }

    void setPlayerJoinCount(Player player) {
        Database.setUserSetting(player.getUniqueId().toString(), "joins", String.valueOf(player.getStatistic(Statistic.LEAVE_GAME) + 1));
    }

    private List<Object> getPlayerWildCmdStats(Player player) {
        // Indexes:
        // - 0: Nombre d'utilisation du jour
        // - 1: Date de la dernière commande

        String playerLastUsed = Database.getUserSetting(player.getUniqueId().toString(), "wildCmdLastUsed");

        if (playerLastUsed != null) {
            LocalDateTime lastUsed = Timestamp.valueOf(playerLastUsed).toLocalDateTime();
            if (ChronoUnit.HOURS.between(lastUsed, LocalDateTime.now()) > 24) {
                return new ArrayList<>() {
                    {
                        add(0);
                        add(lastUsed);
                    }
                };
            } else {
                String playerAskNum = Database.getUserSetting(player.getUniqueId().toString(), "wildCmdAskNum");

                if (playerAskNum != null) {
                    return new ArrayList<>() {
                        {
                            add(Integer.valueOf(playerAskNum));
                            add(lastUsed);
                        }
                    };
                } else {
                    ConsoleLog.warning("Func savePlayerData::getPlayerWildCmdStats(Player player)");
                    ConsoleLog.warning("Fonctionnement anormal! On dispose de la date de 'wildCmdLastUsed' mais pas de 'wildCmdAskNum' pour le joueur " + player.getName() + " UUID: " + player.getUniqueId());
                    ConsoleLog.warning("Passage de 'wildCmdAskNum' à 0.");
                    return new ArrayList<>() {
                        {
                            add(0);
                            add(lastUsed);
                        }
                    };
                }
            }
        } else {
            ConsoleLog.info("Mise à jour du joueur " + player.getName() + " UUID: " + player.getUniqueId());
            ConsoleLog.info("Création des champs 'wildCmdLastUsed' et 'wildCmdAskNum'");

            // On va insérer une date bidon pour éviter un potentiel cooldown
            LocalDateTime dateBidon = LocalDateTime.parse("2001-12-11 12:30", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            Database.setUserSetting(player.getUniqueId().toString(), "wildCmdLastUsed", Timestamp.valueOf(dateBidon).toString());
            Database.setUserSetting(player.getUniqueId().toString(), "wildCmdAskNum", "0");
            return new ArrayList<>() {
                {
                    add(0);
                    add(dateBidon);
                }
            };
        }
    }

    public void savePlayerWildCmdStats(Player player, List<Object> stats) {
        // Indexes:
        // - 0: Nombre d'utilisation du jour
        // - 1: Date de la dernière commande

        // On va zapper la vérification de présence car on suppose que la commande getWildCmdStats avait réussie
        Database.setUserSetting(player.getUniqueId().toString(), "wildCmdAskNum", String.valueOf(stats.get(0)));
        Database.setUserSetting(player.getUniqueId().toString(), "wildCmdLastUsed", Timestamp.valueOf((LocalDateTime) stats.get(1)).toString());
    }

    public boolean playerAlreadyJoined(Player player) {
        return playerAlreadyJoined.get(playerIndexes.indexOf(player.getUniqueId()));
    }
}

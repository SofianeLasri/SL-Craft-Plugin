package com.slprojects.slcraftplugin.parallelTasks;

import com.slprojects.slcraftplugin.Main;
import com.slprojects.slcraftplugin.utils.ConsoleLog;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerDataHandler {
    private final Main plugin;
    private Connection con;
    // Playtime
    private final List<UUID> playTimeUsersIndexes;
    private final List<LocalDateTime> playTimeUsersDate;

    public PlayerDataHandler(Main plugin) {
        this.plugin = plugin;
        playTimeUsersIndexes = new ArrayList<>();
        playTimeUsersDate = new ArrayList<>();
    }

    public void joinEvent(Player player) {
        // On ouvre la bdd
        con = plugin.bddOpenConn();

        playTimeUsersIndexes.add(player.getUniqueId());
        playTimeUsersDate.add(LocalDateTime.now());

        insertPlayerName(player); // On check si le nom du joueur est déjà enregistré
        statsPlayerEntryExit(player, true); // On ajoute son entée
        checkPlayerJoinedDate(player); // On check si on dipose de sa date de rejoint
        setPlayerJoinCount(player); // On set le nombre de fois qu'il a rejoint
        plugin.wildCommand.setPlayerStats(player, getPlayerWildCmdStats(player));

        // On ferme la bdd
        try {
            con.close();
        } catch (SQLException e) {
            ConsoleLog.warning("Impossible de fermer la connexion à la bdd. Func savePlayerData::saveOnJoin(Player player)");
            e.printStackTrace();
        }
    }

    public void quitEvent(Player player) {
        // On ouvre la bdd
        con = plugin.bddOpenConn();

        calculatePlayerPlayTime(player); // On actualise le temps de jeu du joueur
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
    private void insertPlayerName(Player player) {
        try {
            // On va d'abord regarder si on a déjà renseigné le nom du joueur
            PreparedStatement rechercheUtilisateur = con.prepareStatement("SELECT * FROM site_userSetting WHERE uuid = ? AND name = 'playerName' AND value = ?");
            rechercheUtilisateur.setString(1, player.getUniqueId().toString());
            rechercheUtilisateur.setString(2, player.getName());
            ResultSet resultat = rechercheUtilisateur.executeQuery();

            if (resultat.next()) {
                // On a déjà renseigné le nom du joueur on va donc vérifier s'il a besoin d'être mis à jour
                if (!resultat.getString("value").equals(player.getName())) {
                    // On va mettre à jour le nom du joueur
                    PreparedStatement updateUtilisateur = con.prepareStatement("UPDATE site_userSetting SET value = ? WHERE uuid = ? AND name = 'playerName'");
                    updateUtilisateur.setString(1, player.getName());
                    updateUtilisateur.setString(2, player.getUniqueId().toString());
                    updateUtilisateur.executeUpdate();
                }
            } else {
                // On peut insérer le nom du joueur
                PreparedStatement insertUtilisateur = con.prepareStatement("INSERT INTO site_userSetting (uuid, name, value) VALUES (?, 'playerName', ?)");
                insertUtilisateur.setString(1, player.getUniqueId().toString());
                insertUtilisateur.setString(2, player.getName());
                insertUtilisateur.executeQuery();
            }
        } catch (SQLException e) {
            ConsoleLog.warning("Func savePlayerData::insertPlayerName(Player player)");
            e.printStackTrace();
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
            PreparedStatement rechercheUtilisateur = con.prepareStatement("SELECT * FROM site_userSetting WHERE uuid = ? AND name = 'joinedDate'");
            rechercheUtilisateur.setString(1, player.getUniqueId().toString());
            ResultSet resultat = rechercheUtilisateur.executeQuery();

            if (!resultat.next()) {
                // On n'a pas renseigné la date de création du joueur
                if (player.hasPlayedBefore()) {
                    // On va piocher la date d'inscription chez CoreProtect (si elle existe)
                    // On la prend chez CoreProtect car le plugin a été installé dans les premières semaines du serveur. Il a donc bcp plus de données que nous concernant les anciens joueurs.
                    PreparedStatement rechercheDateInscription = con.prepareStatement("SELECT time FROM co_user WHERE uuid = ?");
                    rechercheDateInscription.setString(1, player.getUniqueId().toString());
                    resultat = rechercheDateInscription.executeQuery();

                    if (resultat.next()) {
                        // On insère la date d'inscription
                        PreparedStatement insertionDateInscription = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'joinedDate',?)");
                        insertionDateInscription.setString(1, player.getUniqueId().toString());
                        insertionDateInscription.setString(2, java.sql.Timestamp.valueOf(LocalDateTime.ofEpochSecond(Long.parseLong(resultat.getString("time")), 0, ZoneOffset.UTC)).toString()); // Il faut convertir le timestamp (epoch second) en date
                        insertionDateInscription.executeQuery();

                        // On va précisier que la date d'inscription a été trouvée chez CoreProtect
                        ConsoleLog.info("Le joueur " + ChatColor.GOLD + player.getName() + ChatColor.RESET + " n'avait pas de données sur sa date d'inscription dans dans la table des paramètres utilisateurs. On lui a donc attribué comme date de création du compte, celle que détenait CoreProtect.");
                    } else {
                        // On insère la date d'inscription (du coup on considère que Le joueur n'a pas joué avant, malgré la condition)
                        PreparedStatement insertionDateInscription = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'joinedDate',?)");
                        insertionDateInscription.setString(1, player.getUniqueId().toString());
                        insertionDateInscription.setString(2, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()).toString());
                        insertionDateInscription.executeQuery();

                        // On va préciser que la date d'inscription n'a pas été trouvée chez CoreProtect
                        PreparedStatement insertionInaccurrateJoinedDate = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'inaccurrateJoinedDate',?)");
                        insertionInaccurrateJoinedDate.setString(1, player.getUniqueId().toString());
                        insertionInaccurrateJoinedDate.setString(2, "true");
                        insertionInaccurrateJoinedDate.executeQuery();

                        // On est daccord que ceci n'est pas censé arriver, cela ne concerne que mes potes n'étant venus que durant les premières semaines du serveur.

                        ConsoleLog.info("Le joueur " + ChatColor.GOLD + player.getName() + ChatColor.RESET + " n'avait pas de données sur sa date d'inscription dans dans la table des paramètres utilisateurs, ni dans la table des utilisateurs de CoreProtect. On lui a donc attribué comme date de création du compte, la date du début de sa partie.");
                    }
                } else {
                    // Le joueur est nouveau, on insère la date d'inscription
                    PreparedStatement insertionDateInscription = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'joinedDate',?)");
                    insertionDateInscription.setString(1, player.getUniqueId().toString());
                    insertionDateInscription.setString(2, Timestamp.valueOf(java.time.LocalDateTime.now()).toString());
                    insertionDateInscription.executeQuery();
                }
            }
        } catch (SQLException e) {
            ConsoleLog.warning("Func savePlayerData::checkJoinedDate(Player player)");
            e.printStackTrace();
        }
    }

    void setPlayerJoinCount(Player player) {
        try {
            // On va vérifier si on a déjà renseigné le nombre de fois que le joueur a rejoint le serveur par le passé
            PreparedStatement rechercheJoinCount = con.prepareStatement("SELECT * FROM site_userSetting WHERE uuid = ? AND name = 'joins'");
            rechercheJoinCount.setString(1, player.getUniqueId().toString());
            ResultSet resultat = rechercheJoinCount.executeQuery();

            if (resultat.next()) {
                // On a déjà renseigné ça par le passé, on va donc faire un update
                PreparedStatement updateJoinCount = con.prepareStatement("UPDATE site_userSetting SET value = ? WHERE uuid = ? AND name = 'joins'");
                updateJoinCount.setString(1, String.valueOf(player.getStatistic(Statistic.LEAVE_GAME) + 1));
                updateJoinCount.setString(2, player.getUniqueId().toString());
                updateJoinCount.executeQuery();
            } else {
                // On n'a pas encore renseigné le nombre de fois que le joueur a rejoint le serveur, on va donc faire un insert
                PreparedStatement insertionJoinCount = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'joins',?)");
                insertionJoinCount.setString(1, player.getUniqueId().toString());
                insertionJoinCount.setString(2, String.valueOf(player.getStatistic(Statistic.LEAVE_GAME) + 1));
                insertionJoinCount.executeQuery();
            }

        } catch (SQLException e) {
            ConsoleLog.warning("Func savePlayerData::setPlayerJoinCount(Player player)");
            e.printStackTrace();
        }
    }

    private void calculatePlayerPlayTime(Player player) {
        // On va calculer le temps de jeu du joueur
        LocalDateTime timeNow = LocalDateTime.now();
        Duration duration = Duration.between(timeNow, playTimeUsersDate.get(playTimeUsersIndexes.indexOf(player.getUniqueId())));
        long playedTimeInSeconds = Math.abs(duration.toSeconds());

        try {
            // On va vérifier si on a déjà renseigné le temps de jeu du joueur par le passé
            PreparedStatement recherchePlayTime = con.prepareStatement("SELECT * FROM site_userSetting WHERE uuid = ? AND name = 'playedTime'");
            recherchePlayTime.setString(1, player.getUniqueId().toString());
            ResultSet resultat = recherchePlayTime.executeQuery();

            if (resultat.next()) {
                // On a déjà renseigné ça par le passé, on va donc faire un update
                PreparedStatement updatePlayTime = con.prepareStatement("UPDATE site_userSetting SET value = ? WHERE uuid = ? AND name = 'playedTime'");
                updatePlayTime.setString(1, String.valueOf(Long.parseLong(resultat.getString("value")) + playedTimeInSeconds));
                updatePlayTime.setString(2, player.getUniqueId().toString());
                updatePlayTime.executeQuery();
            } else {
                // On n'a pas encore renseigné le temps de jeu du joueur, on va donc faire un insert
                PreparedStatement insertionPlayTime = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'playedTime',?)");
                insertionPlayTime.setString(1, player.getUniqueId().toString());
                insertionPlayTime.setString(2, String.valueOf(playedTimeInSeconds));
                insertionPlayTime.executeQuery();
            }

        } catch (SQLException e) {
            ConsoleLog.warning("Func savePlayerData::increasePlayerPlayTime(Player player)");
            e.printStackTrace();
        }
    }

    private List<Object> getPlayerWildCmdStats(Player player) {
        // Indexes:
        // - 0: Nombre d'utilisation du jour
        // - 1: Date de la dernière commande

        try {
            PreparedStatement playerLastUsed = con.prepareStatement("SELECT * FROM site_userSetting WHERE uuid = ? AND name = 'wildCmdLastUsed'");
            playerLastUsed.setString(1, player.getUniqueId().toString());
            ResultSet lastUsedResult = playerLastUsed.executeQuery();

            if (lastUsedResult.next()) {
                LocalDateTime lastUsed = Timestamp.valueOf(lastUsedResult.getString("value")).toLocalDateTime();
                if (ChronoUnit.HOURS.between(lastUsed, LocalDateTime.now()) > 24) {
                    return new ArrayList<Object>() {
                        {
                            add(0);
                            add(lastUsed);
                        }
                    };
                } else {
                    PreparedStatement playerAskNum = con.prepareStatement("SELECT * FROM site_userSetting WHERE uuid = ? AND name = 'wildCmdAskNum'");
                    playerAskNum.setString(1, player.getUniqueId().toString());
                    ResultSet askNumResult = playerAskNum.executeQuery();

                    if (askNumResult.next()) {
                        return new ArrayList<Object>() {
                            {
                                add(Integer.valueOf(askNumResult.getString("value")));
                                add(lastUsed);
                            }
                        };
                    } else {
                        ConsoleLog.warning("Func savePlayerData::getPlayerWildCmdStats(Player player)");
                        ConsoleLog.warning("Fonctionnement anormal! On dispose de la date de 'wildCmdLastUsed' mais pas de 'wildCmdAskNum' pour le joueur " + player.getName() + " UUID: " + player.getUniqueId());
                        ConsoleLog.warning("Passage de 'wildCmdAskNum' à 0.");
                        return new ArrayList<Object>() {
                            {
                                add(0);
                                add(lastUsed);
                            }
                        };
                    }
                }
            } else {
                plugin.getLogger().info("Mise à jour du joueur " + player.getName() + " UUID: " + player.getUniqueId());
                ConsoleLog.info("Création des champs 'wildCmdLastUsed' et 'wildCmdAskNum'");

                // On va insérer une date bidon pour éviter un potentiel cooldown
                LocalDateTime dateBidon = LocalDateTime.parse("2001-12-11 12:30", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                PreparedStatement insertWildCmdLastUsed = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'wildCmdLastUsed',?)");
                insertWildCmdLastUsed.setString(1, player.getUniqueId().toString());
                insertWildCmdLastUsed.setString(2, Timestamp.valueOf(dateBidon).toString());
                insertWildCmdLastUsed.executeQuery();

                PreparedStatement insertWildCmdAskNum = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'wildCmdAskNum',?)");
                insertWildCmdAskNum.setString(1, player.getUniqueId().toString());
                insertWildCmdAskNum.setString(2, "0");
                insertWildCmdAskNum.executeQuery();

                return new ArrayList<Object>() {
                    {
                        add(0);
                        add(dateBidon);
                    }
                };
            }

        } catch (SQLException e) {
            ConsoleLog.warning("Func savePlayerData::getPlayerWildCmdStats(Player player)");
            e.printStackTrace();
        }

        ConsoleLog.warning("Func savePlayerData::getPlayerWildCmdStats(Player player)");
        ConsoleLog.warning("Fonctionnement anormal! La recherche dans la bdd a échouée pour le joueur " + player.getName() + " UUID: " + player.getUniqueId());
        ConsoleLog.warning("Passage de 'wildCmdLastUsed' au 11 décembre 2001 et 'wildCmdAskNum' à 0");

        return new ArrayList<Object>() {
            {
                add(0);
                add(LocalDateTime.parse("2001-12-11 12:30", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            }
        };
    }

    public void savePlayerWildCmdStats(Player player, List<Object> stats) {
        // Indexes:
        // - 0: Nombre d'utilisation du jour
        // - 1: Date de la dernière commande

        try {
            // On va zapper la vérification de présence car on suppose que la commande getWildCmdStats avait réussie
            PreparedStatement updateWildCmdAskNum = con.prepareStatement("UPDATE site_userSetting SET value = ? WHERE uuid = ? AND name = 'wildCmdAskNum'");
            updateWildCmdAskNum.setString(1, String.valueOf(stats.get(0)));
            updateWildCmdAskNum.setString(2, player.getUniqueId().toString());
            updateWildCmdAskNum.executeUpdate();

            PreparedStatement updateWildCmdLastUsed = con.prepareStatement("UPDATE site_userSetting SET value = ? WHERE uuid = ? AND name = 'wildCmdLastUsed'");
            updateWildCmdLastUsed.setString(1, Timestamp.valueOf((LocalDateTime) stats.get(1)).toString());
            updateWildCmdLastUsed.setString(2, player.getUniqueId().toString());
            updateWildCmdLastUsed.executeUpdate();

        } catch (SQLException e) {
            ConsoleLog.warning("Func savePlayerData::getPlayerWildCmdStats(Player player)");
            e.printStackTrace();
        }
    }
}

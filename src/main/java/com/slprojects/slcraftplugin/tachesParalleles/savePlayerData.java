package com.slprojects.slcraftplugin.tachesParalleles;

import com.slprojects.slcraftplugin.Main;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class savePlayerData {
    private final Main plugin;
    private Connection con;
    // Playtime
    private final List<UUID> playTimeUsersIndexes;
    private final List<LocalDateTime> playTimeUsersDate;

    public savePlayerData(Main plugin){
        this.plugin = plugin;
        playTimeUsersIndexes = new ArrayList<>();
        playTimeUsersDate = new ArrayList<>();
    }

    public void saveOnJoin(Player player) {
        // On ouvre la bdd
        con = plugin.bddOpenConn();

        playTimeUsersIndexes.add(player.getUniqueId());
        playTimeUsersDate.add(LocalDateTime.now());

        insertPlayerName(player); // On check si le nom du joueur est déjà enregistré
        playerAddPlayerEntryOrExit(player, true); // On ajoute son entée
        checkJoinedDate(player); // On check si on dipose de sa date de rejoint
        setPlayerJoinCount(player); // On set le nombre de fois qu'il a rejoint

        // On ferme la bdd
        try {
            con.close();
        } catch (SQLException e) {
            plugin.getLogger().warning(ChatColor.RED + "Impossible de fermer la connexion à la bdd. Func savePlayerData::saveOnJoin(Player player)");
            e.printStackTrace();
        }
    }

    public void saveOnQuit(Player player) {
        // On ouvre la bdd
        con = plugin.bddOpenConn();

        calculatePlayerPlayTime(player); // On actualise le temps de jeu du joueur
        playerAddPlayerEntryOrExit(player, false); // On ajoute son sortie

        // On ferme la bdd
        try {
            con.close();
        } catch (SQLException e) {
            plugin.getLogger().warning(ChatColor.RED + "Impossible de fermer la connexion à la bdd. Func savePlayerData::saveOnQuit(Player player)");
            e.printStackTrace();
        }
    }

    // Fonctions
    private void insertPlayerName(Player player){
        try {
            // On va d'abord regarder si on a déjà renseigné le nom du joueur
            PreparedStatement rechercheUtilisateur = con.prepareStatement("SELECT * FROM site_userSetting WHERE uuid = ? AND name = 'playerName' AND value = ?");
            rechercheUtilisateur.setString(1, player.getUniqueId().toString());
            rechercheUtilisateur.setString(2, player.getName());
            ResultSet resultat = rechercheUtilisateur.executeQuery();

            if(resultat.next()){
                // On a déjà renseigné le nom du joueur on va donc vérifier s'il a besoin d'être mis à jour
                if(!resultat.getString("value").equals(player.getName())){
                    // On va mettre à jour le nom du joueur
                    PreparedStatement updateUtilisateur = con.prepareStatement("UPDATE site_userSetting SET value = ? WHERE uuid = ? AND name = 'playerName'");
                    updateUtilisateur.setString(1, player.getName());
                    updateUtilisateur.setString(2, player.getUniqueId().toString());
                    updateUtilisateur.executeUpdate();
                }
            }else{
                // On peut insérer le nom du joueur
                PreparedStatement insertUtilisateur = con.prepareStatement("INSERT INTO site_userSetting (uuid, name, value) VALUES (?, 'playerName', ?)");
                insertUtilisateur.setString(1, player.getUniqueId().toString());
                insertUtilisateur.setString(2, player.getName());
                insertUtilisateur.executeQuery();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning(ChatColor.RED + "Func savePlayerData::insertPlayerName(Player player)");
            e.printStackTrace();
        }
    }

    private void playerAddPlayerEntryOrExit(Player player, boolean isEnter){
        try {
            PreparedStatement insertPlayerEntryOrExit = con.prepareStatement("INSERT INTO site_playerEntries (uuid, isJoin, date) VALUES (?, ?, ?)");
            insertPlayerEntryOrExit.setString(1, player.getUniqueId().toString());
            insertPlayerEntryOrExit.setBoolean(2, isEnter);
            insertPlayerEntryOrExit.setString(3, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()).toString());
            insertPlayerEntryOrExit.executeQuery();
        } catch (SQLException e) {
            plugin.getLogger().warning(ChatColor.RED + "Func savePlayerData::playerAddPlayerEntryOrExit(Player player, boolean isEnter)");
            e.printStackTrace();
        }
    }

    private void checkJoinedDate(Player player){
        try {
            // On va vérifier si on l'a déjà renseigné par le passé
            PreparedStatement rechercheUtilisateur = con.prepareStatement("SELECT * FROM site_userSetting WHERE uuid = ? AND name = 'joinedDate'");
            rechercheUtilisateur.setString(1, player.getUniqueId().toString());
            ResultSet resultat = rechercheUtilisateur.executeQuery();

            if(!resultat.next()){
                // On n'a pas renseigné la date de création du joueur
                if(player.hasPlayedBefore()){
                    // On va piocher la date d'inscription chez CoreProtect (si elle existe)
                    // On la prend chez CoreProtect car le plugin a été installé dans les premières semaines du serveur. Il a donc bcp plus de données que nous concernant les anciens joueurs.
                    PreparedStatement rechercheDateInscription = con.prepareStatement("SELECT time FROM co_user WHERE uuid = ?");
                    rechercheDateInscription.setString(1, player.getUniqueId().toString());
                    resultat = rechercheDateInscription.executeQuery();

                    if(resultat.next()){
                        // On insère la date d'inscription
                        PreparedStatement insertionDateInscription = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'joinedDate',?)");
                        insertionDateInscription.setString(1, player.getUniqueId().toString());
                        insertionDateInscription.setString(2, java.sql.Timestamp.valueOf(LocalDateTime.ofEpochSecond(Long.parseLong(resultat.getString("time")), 0, ZoneOffset.UTC)).toString()); // Il faut convertir le timestamp (epoch second) en date
                        insertionDateInscription.executeQuery();

                        // On va précisier que la date d'inscription a été trouvée chez CoreProtect
                        plugin.getLogger().info("Le joueur "+ ChatColor.GOLD+player.getName()+ChatColor.RESET+" n'avait pas de données sur sa date d'inscription dans dans la table des paramètres utilisateurs. On lui a donc attribué comme date de création du compte, celle que détenait CoreProtect.");
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

                        plugin.getLogger().info("Le joueur "+ChatColor.GOLD+player.getName()+ChatColor.RESET+" n'avait pas de données sur sa date d'inscription dans dans la table des paramètres utilisateurs, ni dans la table des utilisateurs de CoreProtect. On lui a donc attribué comme date de création du compte, la date du début de sa partie.");
                    }
                }else{
                    // Le joueur est nouveau, on insère la date d'inscription
                    PreparedStatement insertionDateInscription = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'joinedDate',?)");
                    insertionDateInscription.setString(1, player.getUniqueId().toString());
                    insertionDateInscription.setString(2, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()).toString());
                    insertionDateInscription.executeQuery();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning(ChatColor.RED + "Func savePlayerData::checkJoinedDate(Player player)");
            e.printStackTrace();
        }
    }

    void setPlayerJoinCount(Player player){
        try{
            // On va vérifier si on a déjà renseigné le nombre de fois que le joueur a rejoint le serveur par le passé
            PreparedStatement rechercheJoinCount = con.prepareStatement("SELECT * FROM site_userSetting WHERE uuid = ? AND name = 'joins'");
            rechercheJoinCount.setString(1, player.getUniqueId().toString());
            ResultSet resultat = rechercheJoinCount.executeQuery();

            if(resultat.next()){
                // On a déjà renseigné ça par le passé, on va donc faire un update
                PreparedStatement updateJoinCount = con.prepareStatement("UPDATE site_userSetting SET value = ? WHERE uuid = ? AND name = 'joins'");
                updateJoinCount.setString(1, String.valueOf(player.getStatistic(Statistic.LEAVE_GAME) + 1));
                updateJoinCount.setString(2, player.getUniqueId().toString());
                updateJoinCount.executeQuery();
            }else{
                // On n'a pas encore renseigné le nombre de fois que le joueur a rejoint le serveur, on va donc faire un insert
                PreparedStatement insertionJoinCount = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'joins',?)");
                insertionJoinCount.setString(1, player.getUniqueId().toString());
                insertionJoinCount.setString(2, String.valueOf(player.getStatistic(Statistic.LEAVE_GAME) + 1));
                insertionJoinCount.executeQuery();
            }

        } catch (SQLException e) {
            plugin.getLogger().warning(ChatColor.RED + "Func savePlayerData::setPlayerJoinCount(Player player)");
            e.printStackTrace();
        }
    }

    private void calculatePlayerPlayTime(Player player){
        // On va calculer le temps de jeu du joueur
        LocalDateTime timeNow = LocalDateTime.now();
        Duration duration = Duration.between(timeNow, playTimeUsersDate.get(playTimeUsersIndexes.indexOf(player.getUniqueId())));
        long playedTimeInSeconds = Math.abs(duration.toSeconds());

        try{
            // On va vérifier si on a déjà renseigné le temps de jeu du joueur par le passé
            PreparedStatement recherchePlayTime = con.prepareStatement("SELECT * FROM site_userSetting WHERE uuid = ? AND name = 'playedTime'");
            recherchePlayTime.setString(1, player.getUniqueId().toString());
            ResultSet resultat = recherchePlayTime.executeQuery();

            if(resultat.next()){
                // On a déjà renseigné ça par le passé, on va donc faire un update
                PreparedStatement updatePlayTime = con.prepareStatement("UPDATE site_userSetting SET value = ? WHERE uuid = ? AND name = 'playedTime'");
                updatePlayTime.setString(1, String.valueOf(Long.parseLong(resultat.getString("value")) + playedTimeInSeconds));
                updatePlayTime.setString(2, player.getUniqueId().toString());
                updatePlayTime.executeQuery();
            }else{
                // On n'a pas encore renseigné le temps de jeu du joueur, on va donc faire un insert
                PreparedStatement insertionPlayTime = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'playedTime',?)");
                insertionPlayTime.setString(1, player.getUniqueId().toString());
                insertionPlayTime.setString(2, String.valueOf(playedTimeInSeconds));
                insertionPlayTime.executeQuery();
            }

        } catch (SQLException e) {
            plugin.getLogger().warning(ChatColor.RED + "Func savePlayerData::increasePlayerPlayTime(Player player)");
            e.printStackTrace();
        }
    }
}

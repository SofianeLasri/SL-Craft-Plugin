// Contient une partie du code de ce plugin: https://github.com/Twi5TeD/PlayTime

package com.slprojects.slcraftplugin;

import com.slprojects.slcraftplugin.commandes.linkCodeCommand;
import com.slprojects.slcraftplugin.commandes.wildCommand;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONObject;
import org.mariadb.jdbc.MariaDbPoolDataSource;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import me.clip.placeholderapi.PlaceholderAPI;

import static java.lang.Integer.parseInt;

public final class Main extends JavaPlugin implements Listener {
    // Variables
    private List<UUID> wildCommandActiveUsers;
    private List<UUID> playTimeUsersIndexes;
    private List<LocalDateTime> playTimeUsersDate;
    private static FileConfiguration config;

    // Fonctions appelées à des évènements clés
    @Override
    public void onEnable() {
        // On s'assure qu'on a placeholder api
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            getLogger().info("PlaceholderAPI chargé");
            // On initialise les listeners
            getServer().getPluginManager().registerEvents(this, this);
        } else {
            getLogger().info(ChatColor.RED+"PlaceholderAPI n'est pas accessible!");
            getServer().getPluginManager().disablePlugin(this);
        }

        // Plugin startup logic
        saveDefaultConfig();
        reloadConfig();
        config = getConfig();

        // On initialise la base de donnée
        initDatabase();

        wildCommandActiveUsers = new ArrayList<>();
        playTimeUsersIndexes = new ArrayList<>();
        playTimeUsersDate = new ArrayList<>();
        wildCommand wildCommand = new wildCommand(this);
        getCommand("wild").setExecutor(wildCommand);

        linkCodeCommand linkCodeCommand = new linkCodeCommand(this);
        getCommand("getLinkCode").setExecutor(linkCodeCommand);

        getLogger().info(ChatColor.GREEN+"SL-Craft | Plugin démarré");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info(ChatColor.RED+"SL-Craft | Plugin éteint");

        getServer().getOnlinePlayers().forEach(this::savePlayer);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent e) {
        playTimeUsersIndexes.add(e.getPlayer().getUniqueId());
        playTimeUsersDate.add(LocalDateTime.now());

        // On affiche le message de bienvenue
        String welcomeMessage = PlaceholderAPI.setPlaceholders(e.getPlayer(), getConfig().getString("player-join-message"));
        // Et on joue un petit son chez tous les joueurs
        for(Player p : getServer().getOnlinePlayers()){
            p.sendMessage(welcomeMessage);
            if(getConfig().getBoolean("player-join-playSound")){
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 0);
            }
            //p.sendMessage(welcomeMessage);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent e) {
        savePlayer(e.getPlayer());
        String quitMessage = PlaceholderAPI.setPlaceholders(e.getPlayer(), getConfig().getString("player-quit-message"));
        for(Player p : getServer().getOnlinePlayers()){
            p.sendMessage(quitMessage);
        }
    }

    // Propre au compteur de temps de jeu
    @SuppressWarnings("unchecked")
    public void savePlayer(Player player) {
        JSONObject target = new JSONObject();
        // On ajoute l'uuid et son nom
        target.put("uuid", player.getUniqueId().toString());
        target.put("name", player.getName());
        
        // La date de join (locale, au cas où CoreProtect ne l'a pas)
        target.put("joinedDate", playTimeUsersDate.get(playTimeUsersIndexes.indexOf(player.getUniqueId())).toString());

        // On calcule le temps de jeu
        LocalDateTime timeNow = LocalDateTime.now();
        Duration duration = Duration.between(timeNow, playTimeUsersDate.get(playTimeUsersIndexes.indexOf(player.getUniqueId())));
        long playedTimeInSeconds = Math.abs(duration.toSeconds());

        // On ajoute le temps de jeu au joueur
        target.put("playedTime", playedTimeInSeconds);

        playTimeUsersDate.remove(playTimeUsersIndexes.indexOf(player.getUniqueId()));
        playTimeUsersIndexes.remove(player.getUniqueId());

        target.put("joins", player.getStatistic(Statistic.LEAVE_GAME) + 1);
        target.put("hasPlayedBefore", player.hasPlayedBefore());
        writePlayer(target);
    }

    private void writePlayer(JSONObject target) {
        // On ouvre la bdd
        Connection con = bddOpenConn();
        try {
            // On va regarder si l'utilisateur existe
            PreparedStatement rechercheUtilisateur = con.prepareStatement("SELECT COUNT(*) FROM site_userSetting WHERE uuid = ?");
            rechercheUtilisateur.setString(1, target.get("uuid").toString());
            ResultSet resultat = rechercheUtilisateur.executeQuery();
            if(resultat.next()) {
                int playerExist = resultat.getInt(1);

                if(playerExist==0){
                    // On insère la dernière date de join
                    PreparedStatement insertionLastJoin = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'playedTime',?)");
                    insertionLastJoin.setString(1, target.get("uuid").toString());
                    insertionLastJoin.setString(2, target.get("playedTime").toString());
                    insertionLastJoin.executeQuery();

                    // On insère le nombre de connexions
                    PreparedStatement insertionNbJoins = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'joins',?)");
                    insertionNbJoins.setString(1, target.get("uuid").toString());
                    insertionNbJoins.setString(2, target.get("joins").toString());
                    insertionNbJoins.executeQuery();

                    // On va regarder si l'utilisateur a déjà joué avant (vu qu'on avait pas de données sur ce joueur)
                    if(target.get("hasPlayedBefore").toString().equals("true")){
                        // On va piocher la date d'inscription chez CoreProtect (si elle existe)
                        // On la prend chez CoreProtect car le plugin a été installé dans les premières semaines du serveur. Il a donc bcp plus de données que nous concernant les anciens joueurs.
                        PreparedStatement rechercheDateInscription = con.prepareStatement("SELECT time FROM co_user WHERE uuid = ?");
                        rechercheDateInscription.setString(1, target.get("uuid").toString());
                        resultat = rechercheDateInscription.executeQuery();

                        if(resultat.next()){
                            // On insère la date d'inscription
                            PreparedStatement insertionDateInscription = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'joinedDate',?)");
                            insertionDateInscription.setString(1, target.get("uuid").toString());
                            insertionDateInscription.setString(2, java.sql.Timestamp.valueOf(LocalDateTime.ofEpochSecond(Long.parseLong(resultat.getString("time")), 0, ZoneOffset.UTC)).toString()); // Il faut convertir le timestamp (epoch second) en date
                            insertionDateInscription.executeQuery();

                            // On va précisier que la date d'inscription a été trouvée chez CoreProtect
                            getLogger().info("L'utilisateur "+ChatColor.GOLD+target.get("name").toString()+ChatColor.RESET+" n'avait pas de données sur sa date d'inscription dans dans la table des paramètres utilisateurs. On lui a donc attribué comme date de création du compte, celle que détenait CoreProtect.");
                        } else {
                            // On insère la date d'inscription (du coup on considère que l'utilisateur n'a pas joué avant, malgré la condition)
                            PreparedStatement insertionDateInscription = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'joinedDate',?)");
                            insertionDateInscription.setString(1, target.get("uuid").toString());
                            insertionDateInscription.setString(2, java.sql.Timestamp.valueOf(target.get("joinedDate").toString()).toString());
                            insertionDateInscription.executeQuery();
                            
                            // On va préciser que la date d'inscription n'a pas été trouvée chez CoreProtect
                            PreparedStatement insertionInaccurrateJoinedDate = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'inaccurrateJoinedDate',?)");
                            insertionInaccurrateJoinedDate.setString(1, target.get("uuid").toString());
                            insertionInaccurrateJoinedDate.setString(2, "true");
                            insertionInaccurrateJoinedDate.executeQuery();
                            
                            getLogger().info("L'utilisateur "+ChatColor.GOLD+target.get("name").toString()+ChatColor.RESET+" n'avait pas de données sur sa date d'inscription dans dans la table des paramètres utilisateurs, ni dans la table des utilisateurs de CoreProtect. On lui a donc attribué comme date de création du compte, la date du début de sa partie.");
                        }
                    }else{
                        // C'est un nouvel utilisateur, on peut lui attribuer la date d'inscription précédement calculée
                        PreparedStatement insertionDateInscription = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'joinedDate',?)");
                        insertionDateInscription.setString(1, target.get("uuid").toString());
                        insertionDateInscription.setString(2, java.sql.Timestamp.valueOf(target.get("joinedDate").toString()).toString());
                        insertionDateInscription.executeQuery();
                    }
                }else{
                    PreparedStatement tempsJeuJoueur = con.prepareStatement("SELECT value FROM site_userSetting WHERE uuid = ? AND name = 'playedTime'");
                    tempsJeuJoueur.setString(1, target.get("uuid").toString());
                    resultat = tempsJeuJoueur.executeQuery();
                    if(resultat.next()) {
                        int totalPlayedTime = parseInt(resultat.getString(1)) + parseInt(target.get("playedTime").toString());
                        PreparedStatement modifyPlayedTime = con.prepareStatement("UPDATE `site_userSetting` SET `value`=? WHERE  `uuid`=? AND `name`='playedTime'");
                        modifyPlayedTime.setInt(1, totalPlayedTime);
                        modifyPlayedTime.setString(2, target.get("uuid").toString());
                        modifyPlayedTime.executeQuery();

                        PreparedStatement modifyNbJoins = con.prepareStatement("UPDATE `site_userSetting` SET `value`=? WHERE  `uuid`=? AND `name`='joins'");
                        modifyNbJoins.setString(1, target.get("joins").toString());
                        modifyNbJoins.setString(2, target.get("uuid").toString());
                        modifyNbJoins.executeQuery();

                        // On va regarder s'il a sa date d'inscription de renseignée
                        PreparedStatement rechercheDateInscription = con.prepareStatement("SELECT * FROM site_userSetting WHERE uuid = ? AND name = 'joinedDate'");
                        rechercheDateInscription.setString(1, target.get("uuid").toString());
                        resultat = rechercheDateInscription.executeQuery();

                        if(!resultat.next()){
                            // On va regarder si l'on dispose de sa date d'inscription chez CoreProtect
                            rechercheDateInscription = con.prepareStatement("SELECT time FROM co_user WHERE uuid = ?");
                            rechercheDateInscription.setString(1, target.get("uuid").toString());
                            resultat = rechercheDateInscription.executeQuery();

                            if(resultat.next()){
                                // On insère la date d'inscription
                                PreparedStatement insertionDateInscription = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'joinedDate',?)");
                                insertionDateInscription.setString(1, target.get("uuid").toString());
                                insertionDateInscription.setString(2, java.sql.Timestamp.valueOf(LocalDateTime.ofEpochSecond(Long.parseLong(resultat.getString("time")), 0, ZoneOffset.UTC)).toString()); // Il faut convertir le timestamp (epoch second) en date
                                insertionDateInscription.executeQuery();

                                // On va précisier que la date d'inscription a été trouvée chez CoreProtect
                                getLogger().info("L'utilisateur "+ChatColor.GOLD+target.get("name").toString()+ChatColor.RESET+" n'avait pas de données sur sa date d'inscription dans dans la table des paramètres utilisateurs. On lui a donc attribué comme date de création du compte, celle que détenait CoreProtect.");
                            } else {
                                // On insère la date d'inscription (du coup, comme précédement, on prend la date d'inscription locale)
                                PreparedStatement insertionDateInscription = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'joinedDate',?)");
                                insertionDateInscription.setString(1, target.get("uuid").toString());
                                insertionDateInscription.setString(2, java.sql.Timestamp.valueOf(target.get("joinedDate").toString()).toString());
                                insertionDateInscription.executeQuery();

                                // On va préciser que la date d'inscription n'a pas été trouvée chez CoreProtect
                                PreparedStatement insertionInaccurrateJoinedDate = con.prepareStatement("INSERT INTO site_userSetting (`uuid`, `name`, `value`) VALUES (?,'inaccurrateJoinedDate',?)");
                                insertionInaccurrateJoinedDate.setString(1, target.get("uuid").toString());
                                insertionInaccurrateJoinedDate.setString(2, "true");
                                insertionInaccurrateJoinedDate.executeQuery();

                                getLogger().info("L'utilisateur "+ChatColor.GOLD+target.get("name").toString()+ChatColor.RESET+" n'avait pas de données sur sa date d'inscription dans dans la table des paramètres utilisateurs, ni dans la table des utilisateurs de CoreProtect. On lui a donc attribué comme date de création du compte, la date du début de sa partie.");
                            }
                        }

                    }else{
                        getLogger().warning(ChatColor.RED+"Erreur, nous n'avons pas de resultats pour la requête: SELECT value FROM site_userSetting WHERE uuid = '"+target.get("uuid")+"' AND name = playedTime");
                    }
                }
            }else{
                getLogger().warning(ChatColor.RED+"Erreur, nous n'avons pas de resultats pour la requête: SELECT COUNT(*) FROM site_userSetting WHERE uuid = '"+target.get("uuid").toString()+"' AND rownum = 1");
            }
            con.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Propre à la commande wild: évite les spams de la commande
    public boolean checkActiveUserForWildCommand(UUID playerUuid){
        if(wildCommandActiveUsers.contains(playerUuid)){
            return false;
        }else{
            wildCommandActiveUsers.add(playerUuid);
            return true;
        }
    }
    public void removeActiveUserForWildCommand(UUID playerUuid){
        if(wildCommandActiveUsers.contains(playerUuid)){
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            wildCommandActiveUsers.remove(playerUuid);
        }
    }

    public Connection bddOpenConn() { // si mot de passe avec des caractère spéciaux
        Connection conn=null;
        try {
            Class.forName("org.mariadb.jdbc.MariaDbPoolDataSource");
        } catch (ClassNotFoundException e){
            getLogger().warning (ChatColor.RED+"Il manque le driver MariaDB!");
            getServer().getPluginManager().disablePlugin(this);
        }
        try {
            MariaDbPoolDataSource dataSource = new MariaDbPoolDataSource("jdbc:mariadb://"+config.getString("database.host")+"/"+config.getString("database.database")+"?user="+config.getString("database.user")+"&password="+config.getString("database.password")+"&maxPoolSize=10");
            conn = dataSource.getConnection();
            //getLogger().info(ChatColor.GREEN+"Connexion à la base de données réussie!");
        }// ou les saisir
        catch (SQLException e) {
            getLogger().warning(ChatColor.RED+"Erreur lors de la connexion à la base de données.");
            getServer().getPluginManager().disablePlugin(this);
        }
        return conn;
    }

    private void initDatabase(){
        try{
            Connection con = bddOpenConn();
            PreparedStatement ps=con.prepareStatement("CREATE TABLE IF NOT EXISTS `site_userSetting` (\n" +
                    "  `uuid` varchar(36) NOT NULL DEFAULT '',\n" +
                    "  `name` varchar(128) NOT NULL,\n" +
                    "  `value` text CHARACTER SET utf8 COLLATE utf8_bin NOT NULL,\n" +
                    "  PRIMARY KEY (`uuid`,`name`) USING BTREE\n" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            ps.executeQuery();
            ps=con.prepareStatement("CREATE TABLE IF NOT EXISTS `site_linkCode` (\n" +
                    " `uuid` VARCHAR(36) NOT NULL,\n" +
                    " `code` VARCHAR(8) NOT NULL,\n" +
                    " `time` TIMESTAMP NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),\n" +
                    " `used` BOOLEAN,\n" +
                    " PRIMARY KEY (`uuid`),\n" +
                    " UNIQUE INDEX `code` (`code`)\n" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            ps.executeQuery();
            con.close();
        }catch(Exception e){
            getLogger().warning(ChatColor.RED+"Erreur lors de l'exécution de initDatabase(): "+e);
        }
    }
}

package com.slprojects.slcraftplugin.utils;

import com.slprojects.slcraftplugin.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.mariadb.jdbc.MariaDbPoolDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.bukkit.Bukkit.getServer;

@SuppressWarnings("UnusedReturnValue")
public class Database {
    static final private String userSettingsTabName = "site_userSetting";

    public static String getUserSetting(String uuid, String key) {
        Connection con = bddOpenConn();
        String returnValue = null;

        try {
            PreparedStatement query = con.prepareStatement("SELECT * FROM " + userSettingsTabName + " WHERE uuid = ? AND name = ?");
            query.setString(1, uuid);
            query.setString(2, key);
            ResultSet resultat = query.executeQuery();

            if (resultat.next()) {
                returnValue = resultat.getString("value");
            }
        } catch (SQLException e) {
            ConsoleLog.danger("Erreur lors de l'exécution de la requête sql." + e);
        }

        // On ferme la bdd
        try {
            con.close();
        } catch (SQLException e) {
            ConsoleLog.danger("Impossible de fermer la connexion à la bdd.");
            e.printStackTrace();
        }
        return returnValue;
    }

    public static boolean setUserSetting(String uuid, String key, String value) {
        Connection con = bddOpenConn();
        boolean isOperationASuccess = false;
        boolean isEntryExists = (getUserSetting(uuid, key) != null);

        try {
            if (isEntryExists) {
                PreparedStatement updateEntry = con.prepareStatement("UPDATE site_userSetting SET value = ? WHERE uuid = ? AND name = ?");
                updateEntry.setString(1, value);
                updateEntry.setString(2, uuid);
                updateEntry.setString(3, key);
                updateEntry.executeUpdate();
                isOperationASuccess = true;
            } else {
                isOperationASuccess = insertUserSettingEntry(uuid, key, value);
            }
        } catch (SQLException e) {
            ConsoleLog.danger("Erreur lors de l'exécution de la requête sql." + e);
        }

        // On ferme la bdd
        try {
            con.close();
        } catch (SQLException e) {
            ConsoleLog.danger("Impossible de fermer la connexion à la bdd.");
            e.printStackTrace();
        }

        return isOperationASuccess;
    }

    private static boolean insertUserSettingEntry(String uuid, String key, String value) {
        Connection con = bddOpenConn();
        boolean isOperationASuccess = false;

        try {
            PreparedStatement insertEntry = con.prepareStatement("INSERT INTO site_userSetting (uuid, name, value) VALUES (?, ?, ?)");
            insertEntry.setString(1, uuid);
            insertEntry.setString(2, key);
            insertEntry.setString(3, value);
            insertEntry.executeQuery();
            isOperationASuccess = true;
        } catch (SQLException e) {
            ConsoleLog.danger("Erreur lors de l'exécution de la requête sql." + e);
        }

        // On ferme la bdd
        try {
            con.close();
        } catch (SQLException e) {
            ConsoleLog.danger("Impossible de fermer la connexion à la bdd.");
            e.printStackTrace();
        }

        return isOperationASuccess;
    }

    public static Connection bddOpenConn() {
        FileConfiguration config = getServer().getPluginManager().getPlugin(Main.pluginName).getConfig();
        Connection conn = null;

        try {
            Class.forName("org.mariadb.jdbc.MariaDbPoolDataSource");
        } catch (ClassNotFoundException e) {
            ConsoleLog.danger("Il manque le driver MariaDB!");
        }

        try {
            MariaDbPoolDataSource dataSource = new MariaDbPoolDataSource("jdbc:mariadb://" + config.getString("database.host") + "/" + config.getString("database.database") + "?user=" + config.getString("database.user") + "&password=" + config.getString("database.password") + "&maxPoolSize=10");
            conn = dataSource.getConnection();
            //ConsoleLog.success("Connexion à la base de données réussie!");
        } catch (SQLException e) {
            ConsoleLog.danger("Erreur lors de la connexion à la base de données.");
        }

        return conn;
    }
}

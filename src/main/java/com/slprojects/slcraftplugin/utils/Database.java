package com.slprojects.slcraftplugin.utils;

import com.slprojects.slcraftplugin.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.mariadb.jdbc.MariaDbPoolDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.bukkit.Bukkit.getServer;

@SuppressWarnings("UnusedReturnValue")
public class Database {
    static final private String userSettingsTabName = "site_userSetting";

    /**
     * Récupère une valeur dans la table site_userSetting
     *
     * @param uuid UUID du joueur
     * @param key  Nom de la clé
     * @return Valeur de la clé
     */
    public static String getUserSetting(String uuid, String key) {
        Connection con;
        try {
            con = bddOpenConn();
        } catch (SQLException e) {
            ConsoleLog.danger("Impossible d'ouvrir la connexion à la bdd.");
            throw new RuntimeException(e);
        }
        String returnValue = null;

        try {
            PreparedStatement query = con.prepareStatement("SELECT * FROM " + userSettingsTabName + " WHERE uuid = ? AND name = ?");
            query.setString(1, uuid);
            query.setString(2, key);
            ResultSet result = query.executeQuery();

            if (result.next()) {
                returnValue = result.getString("value");
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

    /**
     * Ajoute ou modifie une valeur dans la table site_userSetting
     *
     * @param uuid  UUID du joueur
     * @param key   Nom de la clé
     * @param value Valeur de la clé
     */
    public static void setUserSetting(String uuid, String key, String value) {
        Connection con;
        try {
            con = bddOpenConn();
        } catch (SQLException e) {
            ConsoleLog.danger("Impossible d'ouvrir la connexion à la bdd.");
            throw new RuntimeException(e);
        }
        boolean isEntryExists = (getUserSetting(uuid, key) != null);

        try {
            if (isEntryExists) {
                PreparedStatement updateEntry = con.prepareStatement("UPDATE site_userSetting SET value = ? WHERE uuid = ? AND name = ?");
                updateEntry.setString(1, value);
                updateEntry.setString(2, uuid);
                updateEntry.setString(3, key);
                updateEntry.executeUpdate();
            } else {
                insertUserSettingEntry(uuid, key, value);
            }
        } catch (SQLException e) {
            ConsoleLog.danger("Erreur lors de l'exécution de la requête sql.");
            throw new RuntimeException(e);
        }

        // On ferme la bdd
        try {
            con.close();
        } catch (SQLException e) {
            ConsoleLog.danger("Impossible de fermer la connexion à la bdd.");
            throw new RuntimeException(e);
        }
    }

    /**
     * Ajoute une entrée dans la table site_userSetting
     *
     * @param uuid  UUID du joueur
     * @param key   Nom de la clé
     * @param value Valeur de la clé
     */
    private static void insertUserSettingEntry(String uuid, String key, String value) {
        Connection con;
        try {
            con = bddOpenConn();
        } catch (SQLException e) {
            ConsoleLog.danger("Erreur lors de l'ouverture de la connexion à la bdd.");
            throw new RuntimeException(e);
        }

        try {
            PreparedStatement insertEntry = con.prepareStatement("INSERT INTO site_userSetting (uuid, name, value) VALUES (?, ?, ?)");
            insertEntry.setString(1, uuid);
            insertEntry.setString(2, key);
            insertEntry.setString(3, value);
            insertEntry.executeQuery();
        } catch (SQLException e) {
            ConsoleLog.danger("Erreur lors de l'exécution de la requête sql.");
            throw new RuntimeException(e);
        }

        // On ferme la bdd
        try {
            con.close();
        } catch (SQLException e) {
            ConsoleLog.danger("Impossible de fermer la connexion à la bdd.");
            throw new RuntimeException(e);
        }
    }

    /**
     * Ouvre une connexion à la base de données
     *
     * @return Connection
     */
    public static Connection bddOpenConn() throws SQLException {
        Plugin plugin = getServer().getPluginManager().getPlugin(Main.pluginName);
        if (plugin == null) {
            throw new IllegalStateException("Le plugin " + Main.pluginName + " n'a pas été trouvé.");
        }

        FileConfiguration config = plugin.getConfig();

        String connectionString = "jdbc:mariadb://" + config.getString("database.host");
        connectionString += "/" + config.getString("database.database");
        connectionString += "?user=" + config.getString("database.user");
        connectionString += "&password=" + config.getString("database.password");
        connectionString += "&maxPoolSize=10";

        MariaDbPoolDataSource dataSource = new MariaDbPoolDataSource(connectionString);

        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(1000)) {
                throw new SQLException("Could not establish database connection.");
            }
        }

        testDataSource(dataSource);

        return dataSource.getConnection();
    }

    private static void testDataSource(MariaDbPoolDataSource dataSource) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(1000)) {
                throw new SQLException("Erreur lors de la connexion à la base de données.");
            }
        }
    }
}

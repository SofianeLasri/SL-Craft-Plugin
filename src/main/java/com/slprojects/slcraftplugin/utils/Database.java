package com.slprojects.slcraftplugin.utils;

import com.slprojects.slcraftplugin.models.UserSetting;
import com.slprojects.slcraftplugin.utils.database.Configuration;
import net.luckperms.api.model.user.User;
import org.bjloquent.Connector;
import org.bjloquent.Model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@SuppressWarnings("UnusedReturnValue")
public class Database {
    static final private String userSettingsTabName = "site_userSetting";
    static Connection connection = null;
    static Connector jLoquentConnector = null;

    /**
     * Récupère une valeur dans la table site_userSetting
     *
     * @param uuid UUID du joueur
     * @param key  Nom de la clé
     * @return Valeur de la clé
     */
    public static String getUserSetting(String uuid, String key) {
        List<UserSetting> settings = Model.where(
                UserSetting.class,
                new String[]{"uuid", "name"},
                new String[]{"=", "="},
                new String[]{uuid, key}
        );

        if (settings.size() != 1) {
            return null;
        }

        return (settings.get(0)).getValue();
    }

    /**
     * Ajoute ou modifie une valeur dans la table site_userSetting
     *
     * @param uuid  UUID du joueur
     * @param key   Nom de la clé
     * @param value Valeur de la clé
     */
    public static void setUserSetting(String uuid, String key, String value) {
        List<UserSetting> userSettingExists = Model.where(
                UserSetting.class,
                new String[]{"uuid", "name"},
                new String[]{"=", "="},
                new String[]{uuid, key}
        );

        if (userSettingExists.size() == 0) {
            UserSetting userSetting = new UserSetting();
            userSetting.setUuid(uuid);
            userSetting.setName(key);
            userSetting.setValue(value);
            userSetting.create();
        } else {
            UserSetting userSetting = userSettingExists.get(0);
            userSetting.setValue(value);
            userSetting.save();
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
            PreparedStatement insertEntry = con.prepareStatement("INSERT INTO " + userSettingsTabName + " (uuid, name, value) VALUES (?, ?, ?)");
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
        jLoquentConnector = Connector.getInstance();
        jLoquentConnector.setDBConfig(new Configuration());
        connection = jLoquentConnector.open();
        return connection;
    }

    /**
     * Ferme la connexion à la base de données
     */
    public static void bddCloseConn() throws SQLException {
        connection.close();
        jLoquentConnector.close();
    }
}

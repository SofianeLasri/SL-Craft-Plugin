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

    public static void initDatabase() {
        try {
            PreparedStatement ps = connection.prepareStatement("CREATE TABLE IF NOT EXISTS `site_userSetting` (\n" +
                    "  `uuid` varchar(36) NOT NULL DEFAULT '',\n" +
                    "  `name` varchar(128) NOT NULL,\n" +
                    "  `value` text CHARACTER SET utf8 COLLATE utf8_bin NOT NULL,\n" +
                    "  PRIMARY KEY (`uuid`,`name`) USING BTREE\n" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            ps.executeQuery();
            ps = connection.prepareStatement("CREATE TABLE IF NOT EXISTS `site_linkCode` (\n" +
                    " `uuid` VARCHAR(36) NOT NULL,\n" +
                    " `code` VARCHAR(8) NOT NULL,\n" +
                    " `time` TIMESTAMP NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),\n" +
                    " `used` BOOLEAN,\n" +
                    " PRIMARY KEY (`uuid`),\n" +
                    " UNIQUE INDEX `code` (`code`)\n" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            ps.executeQuery();
        } catch (Exception e) {
            ConsoleLog.danger("Erreur lors de l'exécution de initDatabase(): " + e);
        }
    }
}

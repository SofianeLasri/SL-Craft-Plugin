package com.slprojects.slcraftplugin.utils.database;

import com.slprojects.slcraftplugin.Main;
import org.bjloquent.DBConfig;
import org.bjloquent.DatabaseType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import static org.bukkit.Bukkit.getServer;

public class Configuration implements DBConfig {
    FileConfiguration config = null;

    public Configuration() {
        Plugin plugin = getServer().getPluginManager().getPlugin(Main.pluginName);
        if (plugin == null) {
            throw new IllegalStateException("Le plugin " + Main.pluginName + " n'a pas été trouvé.");
        }

        config = plugin.getConfig();
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.MARIADB;
    }

    @Override
    public String getHostName() {
        return config.getString("database.host");
    }

    @Override
    public String getPortNumber() {
        return config.getString("database.port");
    }

    @Override
    public String getDatabaseName() {
        return config.getString("database.database");
    }

    @Override
    public String getUsername() {
        return config.getString("database.user");
    }

    @Override
    public String getPassword() {
        return config.getString("database.password");
    }
}

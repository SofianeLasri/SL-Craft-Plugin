// Contient une partie du code de ce plugin: https://github.com/Twi5TeD/PlayTime

package com.slprojects.slcraftplugin;

import com.slprojects.slcraftplugin.commandes.linkCodeCommand;
import com.slprojects.slcraftplugin.commandes.wildCommand;
import com.slprojects.slcraftplugin.tachesParalleles.savePlayerData;
import com.slprojects.slcraftplugin.tachesParalleles.waitForDiscordMsg;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONObject;
import org.mariadb.jdbc.MariaDbPoolDataSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class Main extends JavaPlugin implements Listener {
    // Variables
    private List<UUID> wildCommandActiveUsers;
    private static FileConfiguration config;
    private com.slprojects.slcraftplugin.tachesParalleles.savePlayerData savePlayerData;

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
        savePlayerData = new savePlayerData(this);

        // On initialise la base de donnée
        initDatabase();

        wildCommandActiveUsers = new ArrayList<>();
        wildCommand wildCommand = new wildCommand(this);
        Objects.requireNonNull(getCommand("wild")).setExecutor(wildCommand);

        linkCodeCommand linkCodeCommand = new linkCodeCommand(this);
        Objects.requireNonNull(getCommand("getLinkCode")).setExecutor(linkCodeCommand);

        waitForDiscordMsg.startServer(this);

        getLogger().info(ChatColor.GREEN+"SL-Craft | Plugin démarré");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info(ChatColor.RED+"SL-Craft | Plugin éteint");

        getServer().getOnlinePlayers().forEach(player -> savePlayerData.saveOnQuit(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent e) {
        // On désactive le message par défaut
        e.joinMessage(null);
        savePlayerData.saveOnJoin(e.getPlayer());

        // On affiche le message de bienvenue
        String welcomeMessage = PlaceholderAPI.setPlaceholders(e.getPlayer(), Objects.requireNonNull(getConfig().getString("player-join-message")));
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
        // On désactive le message par défaut
        e.quitMessage(null);
        savePlayerData.saveOnQuit(e.getPlayer());
        String quitMessage = PlaceholderAPI.setPlaceholders(e.getPlayer(), Objects.requireNonNull(getConfig().getString("player-quit-message")));
        for(Player p : getServer().getOnlinePlayers()){
            p.sendMessage(quitMessage);
        }
    }

    // On renvoie chaque message des joueurs sur le canal de chat du serveur discord
    @SuppressWarnings({"unchecked", "deprecation"})
    @EventHandler(priority = EventPriority.LOWEST)
    void AsyncChatEvent(AsyncPlayerChatEvent e) {
        // On va appeler l'api du bot discord
        JSONObject json = new JSONObject();
        json.put("message", e.getMessage());
        json.put("username", e.getPlayer().getName());

        try {
            String urlString = config.getString("discordBot-api-url") + "mc/chat/" + URLEncoder.encode(json.toJSONString(), "UTF-8").replace("+", "%20");

            String response = getHttp(urlString);
            if(getConfig().getBoolean("msg-verbose")){
                getLogger().info("Func AsyncChatEvent(PlayerChatEvent e), HTTP response:" + response);
            }
        } catch (UnsupportedEncodingException ex) {
            getLogger().warning(ChatColor.RED + "Impossible de d'encoder les données. Func AsyncChatEvent(PlayerChatEvent e)");
            ex.printStackTrace();
        }
    }

    // Permet de faire des appels vers l'api discord
    public String getHttp(String urlString) {
        String returnData = "";
        // Processus long et chiant
        try {
            URL url = new URL(urlString);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "Mozilla/5.0");
            con.setRequestProperty("Accept-Language", "fr-FR,fr;q=0.5");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            con.setDoInput(true);
            con.setUseCaches(false);
            con.setAllowUserInteraction(false);
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.connect();
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;

            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }


            in.close();
            con.disconnect();
            returnData = response.toString();
        } catch (Exception ex) {
            getLogger().warning(ChatColor.RED + "Impossible de se connecter à l'url " + urlString + ". Func getHttp(String urlString)");
            ex.printStackTrace();
        }

        return returnData;
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

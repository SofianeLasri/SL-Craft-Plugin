package com.slprojects.slcraftplugin;

import com.slprojects.slcraftplugin.commands.admins.WildReset;
import com.slprojects.slcraftplugin.commands.publics.LinkCode;
import com.slprojects.slcraftplugin.commands.publics.Wild;
import com.slprojects.slcraftplugin.parallelTasks.InternalWebServer;
import com.slprojects.slcraftplugin.parallelTasks.events.PeriodicEvent;
import com.slprojects.slcraftplugin.parallelTasks.dataHandlers.PlayerDataHandler;
import com.slprojects.slcraftplugin.utils.ConsoleLog;
import me.clip.placeholderapi.PlaceholderAPI;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Main extends JavaPlugin implements Listener {
    // Variables
    private static FileConfiguration config;
    public static LuckPerms luckPermsApi;
    public static String pluginName;

    // Publiques car on les appelle ailleurs
    public PlayerDataHandler playerDataHandler;
    public Wild wildCommand;
    public PeriodicEvent periodicEvent;

    @Override
    public void onEnable() {
        pluginName = this.getName();
        // On s'assure qu'on a placeholder api
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            ConsoleLog.info("PlaceholderAPI chargé");
            // On initialise les listeners
            getServer().getPluginManager().registerEvents(this, this);
        } else {
            ConsoleLog.danger("PlaceholderAPI n'est pas accessible!");
            getServer().getPluginManager().disablePlugin(this);
        }

        // LuckPerms
        // S'assure que le plugin est installé et évite l'affichage d'une erreur de classe inaccessible
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            if (provider != null) {
                ConsoleLog.info("LuckPerms chargé");
                luckPermsApi = provider.getProvider();
            } else {
                ConsoleLog.danger("LuckPerms n'est pas accessible!");
                getServer().getPluginManager().disablePlugin(this);
            }
        } else {
            ConsoleLog.danger("LuckPerms n'est pas accessible!");
            getServer().getPluginManager().disablePlugin(this);
        }

        // Plugin startup logic
        saveDefaultConfig();
        reloadConfig();
        config = getConfig();
        updateConfig();

        initDatabase();

        playerDataHandler = new PlayerDataHandler(this);
        InternalWebServer.startServer(this);
        periodicEvent = new PeriodicEvent(this);

        // On initialise les commandes
        wildCommand = new Wild(this);
        getCommand("wild").setExecutor(wildCommand);

        WildReset wildReset = new WildReset(this);
        getCommand("reset-wild").setExecutor(wildReset);

        LinkCode linkCodeCommand = new LinkCode(this);
        getCommand("getLinkCode").setExecutor(linkCodeCommand);

        ConsoleLog.success("Plugin démarré");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        ConsoleLog.danger("Plugin désactivé, au revoir!");

        getServer().getOnlinePlayers().forEach(player -> playerDataHandler.quitEvent(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent e) {
        // On désactive le message par défaut
        e.joinMessage(null);
        playerDataHandler.joinEvent(e.getPlayer());

        // On affiche le message de bienvenue
        String welcomeMessage = PlaceholderAPI.setPlaceholders(e.getPlayer(), Objects.requireNonNull(getConfig().getString("player-join-message")));
        // Et on joue un petit son chez tous les joueurs
        for (Player p : getServer().getOnlinePlayers()) {
            p.sendMessage(welcomeMessage);
            if (getConfig().getBoolean("player-join-playSound")) {
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 0);
            }
        }
        sendMessageToDiscord("**" + e.getPlayer().getName() + "** vient de rejoindre le serveur");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent e) {
        // On désactive le message par défaut
        e.quitMessage(null);
        playerDataHandler.quitEvent(e.getPlayer());
        String quitMessage = PlaceholderAPI.setPlaceholders(e.getPlayer(), Objects.requireNonNull(getConfig().getString("player-quit-message")));
        for (Player p : getServer().getOnlinePlayers()) {
            p.sendMessage(quitMessage);
        }
        sendMessageToDiscord("**" + e.getPlayer().getName() + "** a quitté le serveur");
    }

    // On renvoie chaque message des joueurs sur le canal de chat du serveur discord
    @SuppressWarnings({"deprecation"})
    @EventHandler(priority = EventPriority.LOWEST)
    void AsyncChatEvent(AsyncPlayerChatEvent e) {
        String playerFormattedMessage = e.getMessage();
        // On applique les text markup
        // Markdown
        //italique + gras "***"
        playerFormattedMessage = Pattern.compile("\\*\\*\\*(.*?)\\*\\*\\*").matcher(playerFormattedMessage).replaceAll("§l§o$1§r");
        //gras "**"
        playerFormattedMessage = Pattern.compile("\\*\\*(.*?)\\*\\*").matcher(playerFormattedMessage).replaceAll("§l$1§r");
        //italique "*"
        playerFormattedMessage = Pattern.compile("\\*(.*?)\\*").matcher(playerFormattedMessage).replaceAll("§o$1§r");
        //underline
        playerFormattedMessage = Pattern.compile("__(.*?)__").matcher(playerFormattedMessage).replaceAll("§n$1§r");
        //barré
        playerFormattedMessage = Pattern.compile("~~(.*?)~~").matcher(playerFormattedMessage).replaceAll("§m$1§r ");

        // Couleurs
        playerFormattedMessage = Pattern.compile("&([a-f]|r|[0-9])").matcher(playerFormattedMessage).replaceAll("§$1");

        // Ping utilisateur
        Matcher m = Pattern.compile("@(.*?)($|[ ,;:!])").matcher(playerFormattedMessage);
        List<String> playerTags = new ArrayList<>();
        while (m.find()) {
            playerTags.add(m.group(1));
        }
        // On va chercher le préfix dans LuckPerms
        CachedMetaData playerMetaData = luckPermsApi.getPlayerAdapter(Player.class).getMetaData(e.getPlayer());

        for (Player p : Bukkit.getOnlinePlayers()) {
            // Si le joueur a qui on va poster le message (p) a été mentionné
            if (playerTags.contains(p.getName())) {
                // On colorise sa mention
                playerFormattedMessage = Pattern.compile("@(" + p.getName() + ")($|[ ,;:!])").matcher(playerFormattedMessage).replaceAll("§r§l§d@$1§r$2");

                // On lui joue un son + un texte dans la barre d'action
                p.sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§b " + e.getPlayer().getName() + " §aVous a mentionné !"));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 100, 2);
                // On colorie les autres mentions
                playerFormattedMessage = Pattern.compile(" @(.*?)($|[ ,;:!])").matcher(playerFormattedMessage).replaceAll("§r§b @$1§r$2");
            }

            String CompleteMessage = playerMetaData.getPrefix() + e.getPlayer().getName() + "§r: " + playerFormattedMessage;
            // On envoie le message au joueur
            p.sendMessage(CompleteMessage);

            // Et dans la console
            if (e.getPlayer() == p) {
                ConsoleLog.info(CompleteMessage);
            }
        }
        // On envoie le message sur discord (on envoie le msg sans les couleur ni le formatage)
        String discordFriendlyMsg = Pattern.compile("&([a-f]|r|[0-8])").matcher(e.getMessage()).replaceAll("");
        sendMessageToDiscord(discordFriendlyMsg, e.getPlayer().getName());
        // On désactive le message de base de minecraft
        e.setCancelled(true);
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
            con.setRequestProperty("Server-Type", config.getString("server-type"));
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
            ConsoleLog.danger("Impossible de se connecter à l'url " + urlString + ". Func getHttp(String urlString)");
            ex.printStackTrace();
        }

        return returnData;
    }

    // Envoyer un message sur le discord
    @SuppressWarnings({"unchecked"})
    public void sendMessageToDiscord(String message, String username) {
        // On va vérifier que le joueur ne fait pas de @everyone ou de @here
        message = message.replace("<@everyone>", "**everyone**");
        message = message.replace("<@here>", "**here**");
        message = message.replace("@everyone", "**everyone**");
        message = message.replace("@here", "**here**");

        // On forme le JSON
        JSONObject json = new JSONObject();
        json.put("message", message);
        json.put("username", username);

        // On va appeler l'api du bot discord
        try {
            String urlString = config.getString("discordBot-api-url") + "mc/chat/" + URLEncoder.encode(json.toJSONString(), "UTF-8").replace("+", "%20");

            String response = getHttp(urlString);
            if (getConfig().getBoolean("msg-verbose")) {
                ConsoleLog.info("Func AsyncChatEvent(PlayerChatEvent e), HTTP response:" + response);
            }
        } catch (UnsupportedEncodingException ex) {
            ConsoleLog.danger("Impossible de d'encoder les données. Func AsyncChatEvent(PlayerChatEvent e)");
            ex.printStackTrace();
        }
    }

    public void sendMessageToDiscord(String message) {
        sendMessageToDiscord(message, "SL-Craft");
    }

    public Connection bddOpenConn() { // si mot de passe avec des caractère spéciaux
        Connection conn = null;
        try {
            Class.forName("org.mariadb.jdbc.MariaDbPoolDataSource");
        } catch (ClassNotFoundException e) {
            ConsoleLog.danger("Il manque le driver MariaDB!");
            getServer().getPluginManager().disablePlugin(this);
        }
        try {
            MariaDbPoolDataSource dataSource = new MariaDbPoolDataSource("jdbc:mariadb://" + config.getString("database.host") + "/" + config.getString("database.database") + "?user=" + config.getString("database.user") + "&password=" + config.getString("database.password") + "&maxPoolSize=10");
            conn = dataSource.getConnection();
            ConsoleLog.success("Connexion à la base de données réussie!");
        }// ou les saisir
        catch (SQLException e) {
            ConsoleLog.danger("Erreur lors de la connexion à la base de données.");
            getServer().getPluginManager().disablePlugin(this);
        }
        return conn;
    }

    private void updateConfig() {
        ConsoleLog.info("Vérification du fichier de configuration...");
        // 1.6.0
        if (!config.contains("server-type")) {
            ConsoleLog.info("Ajout de la variable serverType dans le fichier de configuration...");
            config.set("server-type", "dev");

            saveConfig();
            reloadConfig();
        }

        if (config.contains("wild") && (config.contains("excluded-biomes") && config.contains("world") && config.contains("max-range"))) {
            ConsoleLog.info("Mise à jour des paramètres concernant la commande /wild");

            config.set("wild.excluded-biomes", config.get("excluded-biomes"));
            config.set("wild.world", config.get("world"));
            config.set("wild.max-range", config.get("max-range"));

            config.set("excluded-biomes", null);
            config.set("world", null);
            config.set("max-range", null);

            config.options().copyDefaults(true);
            saveConfig();
            reloadConfig();
        }

        // 1.6.1
        config.options().copyDefaults(true);
        saveConfig();
        reloadConfig();
    }

    private void initDatabase() {
        try {
            Connection con = bddOpenConn();
            PreparedStatement ps = con.prepareStatement("CREATE TABLE IF NOT EXISTS `site_userSetting` (\n" +
                    "  `uuid` varchar(36) NOT NULL DEFAULT '',\n" +
                    "  `name` varchar(128) NOT NULL,\n" +
                    "  `value` text CHARACTER SET utf8 COLLATE utf8_bin NOT NULL,\n" +
                    "  PRIMARY KEY (`uuid`,`name`) USING BTREE\n" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            ps.executeQuery();
            ps = con.prepareStatement("CREATE TABLE IF NOT EXISTS `site_linkCode` (\n" +
                    " `uuid` VARCHAR(36) NOT NULL,\n" +
                    " `code` VARCHAR(8) NOT NULL,\n" +
                    " `time` TIMESTAMP NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),\n" +
                    " `used` BOOLEAN,\n" +
                    " PRIMARY KEY (`uuid`),\n" +
                    " UNIQUE INDEX `code` (`code`)\n" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            ps.executeQuery();
            con.close();
        } catch (Exception e) {
            ConsoleLog.danger("Erreur lors de l'exécution de initDatabase(): " + e);
        }
    }
}

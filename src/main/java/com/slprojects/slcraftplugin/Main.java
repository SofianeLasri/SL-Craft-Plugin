package com.slprojects.slcraftplugin;

import com.slprojects.slcraftplugin.commandes.linkCodeCommand;
import com.slprojects.slcraftplugin.commandes.wildCommand;
import com.slprojects.slcraftplugin.tachesParalleles.savePlayerData;
import com.slprojects.slcraftplugin.tachesParalleles.internalWebServer;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
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
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            getServer().getConsoleSender().sendMessage("PlaceholderAPI chargé");
            // On initialise les listeners
            getServer().getPluginManager().registerEvents(this, this);
        } else {
            getServer().getConsoleSender().sendMessage(ChatColor.RED+"PlaceholderAPI n'est pas accessible!");
            getServer().getPluginManager().disablePlugin(this);
        }

        // Plugin startup logic
        saveDefaultConfig();
        reloadConfig();
        config = getConfig();
        updateConfig();
        savePlayerData = new savePlayerData(this);

        // On initialise la base de donnée
        initDatabase();

        wildCommandActiveUsers = new ArrayList<>();
        wildCommand wildCommand = new wildCommand(this);
        Objects.requireNonNull(getCommand("wild")).setExecutor(wildCommand);

        linkCodeCommand linkCodeCommand = new linkCodeCommand(this);
        Objects.requireNonNull(getCommand("getLinkCode")).setExecutor(linkCodeCommand);

        internalWebServer.startServer(this);

        getServer().getConsoleSender().sendMessage(ChatColor.GREEN+"SL-Craft | Plugin démarré");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getServer().getConsoleSender().sendMessage(ChatColor.RED+"SL-Craft | Plugin éteint");

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
        }
        sendMessageToDiscord("**"+e.getPlayer().getName()+"** vient de rejoindre le serveur");
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
        sendMessageToDiscord("**"+e.getPlayer().getName()+"** a quitté le serveur");
    }

    // On renvoie chaque message des joueurs sur le canal de chat du serveur discord
    @SuppressWarnings({"unchecked", "deprecation"})
    @EventHandler(priority = EventPriority.LOWEST)
    void AsyncChatEvent(AsyncPlayerChatEvent e) {
        String FinalMessage = e.getMessage();
        //on applique les text markup
        //italique + gras "***"
        FinalMessage = Pattern.compile("\\*\\*\\*(.*?)\\*\\*\\*").matcher(FinalMessage).replaceAll("§l§o$1§r");
        //gras "**"
        FinalMessage = Pattern.compile("\\*\\*(.*?)\\*\\*").matcher(FinalMessage).replaceAll("§l$1§r");
        //italique "*"
        FinalMessage = Pattern.compile("\\*(.*?)\\*").matcher(FinalMessage).replaceAll("§o$1§r");
        //underline
        FinalMessage = Pattern.compile("__(.*?)__").matcher(FinalMessage).replaceAll("§n$1§r");
        //barré
        FinalMessage = Pattern.compile("~~(.*?)~~").matcher(FinalMessage).replaceAll("§m$1§r ");


        //on poste le message aux joueurs 1 par 1
        for (Player p: Bukkit.getOnlinePlayers()){
            Matcher m = Pattern.compile("@(.*?)($|[ ,;:!])").matcher(FinalMessage);
            List<String> list = new ArrayList<>();
            while (m.find()) {list.add(m.group(1));}
            // si le joueur a qui on va poster le message (p) a été mentionné
            if(list.contains(p.getName())){
                //On colorise sa mention
                FinalMessage = Pattern.compile("@(" + p.getName() + ")($|[ ,;:!])").matcher(FinalMessage).replaceAll("§r§l§d@$1§r$2");
                //on lui joue un son + un texte dans la barre d'action
                p.sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§b " + e.getPlayer().getName() + " §aVous a mentionné !"));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 100, 2);
                //on colorie les autres mentions
                FinalMessage = Pattern.compile(" @(.*?)($|[ ,;:!])").matcher(FinalMessage).replaceAll("§r§b @$1§r$2");

            }
            //on ajoute le préfix (Admin|Joueur) puis le pseudo du joueur qui envoie le message
            String CompleteMessage = "§3[" + (e.getPlayer().isOp() ? "§dAdmin" : "§bPlayer") + "§3] §a" + e.getPlayer().getName() + "§r: " + FinalMessage;
            //on envoie le message au joueur
            p.sendMessage(CompleteMessage);
            //on envoie le message sur discord (on envoie le msg sans les couleur ni le formatage)
            sendMessageToDiscord(e.getMessage(), e.getPlayer().getName());
        }
        //on désactive le message de base de minecraft
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
            getServer().getConsoleSender().sendMessage(ChatColor.RED + "Impossible de se connecter à l'url " + urlString + ". Func getHttp(String urlString)");
            ex.printStackTrace();
        }

        return returnData;
    }

    // Envoyer un message sur le discord
    @SuppressWarnings({"unchecked"})
    public void sendMessageToDiscord(String message, String username){
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
            if(getConfig().getBoolean("msg-verbose")){
                getServer().getConsoleSender().sendMessage("Func AsyncChatEvent(PlayerChatEvent e), HTTP response:" + response);
            }
        } catch (UnsupportedEncodingException ex) {
            getLogger().warning(ChatColor.RED + "Impossible de d'encoder les données. Func AsyncChatEvent(PlayerChatEvent e)");
            ex.printStackTrace();
        }
    }
    public void sendMessageToDiscord(String message){
        sendMessageToDiscord(message, "SL-Craft");
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
            getServer().getConsoleSender().sendMessage (ChatColor.RED+"Il manque le driver MariaDB!");
            getServer().getPluginManager().disablePlugin(this);
        }
        try {
            MariaDbPoolDataSource dataSource = new MariaDbPoolDataSource("jdbc:mariadb://"+config.getString("database.host")+"/"+config.getString("database.database")+"?user="+config.getString("database.user")+"&password="+config.getString("database.password")+"&maxPoolSize=10");
            conn = dataSource.getConnection();
            //getLogger().info(ChatColor.GREEN+"Connexion à la base de données réussie!");
        }// ou les saisir
        catch (SQLException e) {
            getServer().getConsoleSender().sendMessage(ChatColor.RED+"Erreur lors de la connexion à la base de données.");
            getServer().getPluginManager().disablePlugin(this);
        }
        return conn;
    }
    
    private void updateConfig(){
        getLogger().info("Vérification du fichier de configuration...");
        // On va vérifier si l'on dispose de la nouvelle variable du port du serveur web
        if(!config.contains("serverType")){
            getLogger().info("Ajout de la variable serverType dans le fichier de configuration...");
            config.set("server-type", "dev");

            config.options().copyDefaults(true);
            saveConfig();
            reloadConfig();
        }
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
            getServer().getConsoleSender().sendMessage(ChatColor.RED+"Erreur lors de l'exécution de initDatabase(): "+e);
        }
    }
}

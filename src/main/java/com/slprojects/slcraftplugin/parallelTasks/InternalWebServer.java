package com.slprojects.slcraftplugin.parallelTasks;

import com.slprojects.slcraftplugin.Main;
import com.slprojects.slcraftplugin.utils.ConsoleLog;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;

public class InternalWebServer {
    /**
     * Lance le serveur web intégré
     *
     * @param plugin Instance du plugin
     */
    @SuppressWarnings({"unchecked", "InfiniteLoopStatement"})
    public static void startServer(Main plugin) {
        int serverPort = plugin.getConfig().getInt("internal-webserver-port");

        ConsoleLog.info("Lancement du serveur web intégré sur le port " + ChatColor.GOLD + serverPort);
        ConsoleLog.warning("Attention! Le serveur ne fonctionne pas avec les requêtes https!");
        // On fait un thread pour écouter le port
        Runnable serverThread = () -> {
            try {
                ServerSocket serverSocket = new ServerSocket(serverPort);
                while (true) {
                    Socket client = serverSocket.accept();

                    //ConsoleLog.info("Nouvelle connexion sur le port " + ChatColor.GOLD + serverPort);

                    // Get input and output streams to talk to the client
                    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    PrintWriter out = new PrintWriter(client.getOutputStream());

                    // Start sending our reply, using the HTTP 1.1 protocol
                    out.print("HTTP/1.1 200 \r\n"); // Version & status code
                    out.print("Content-Type: application/json\r\n"); // The type of data
                    out.print("Connection: close\r\n"); // Will close stream
                    out.print("\r\n"); // End of headers

                    // Now, read the HTTP request from the client, and send it
                    // right back to the client as part of the body of our
                    // response. The client doesn't disconnect, so we never get
                    // an EOF. It does sends an empty line at the end of the
                    // headers, though. So when we see the empty line, we stop
                    // reading. This means we don't mirror the contents of POST
                    // requests, for example. Note that the readLine() method
                    // works with Unix, Windows, and Mac line terminators.
                    String line, commandName = "";
                    String[] aliases = new String[0];
                    while ((line = in.readLine()) != null) {
                        if (line.equals("")) {
                            break;
                        }
                        // On va regarder si la ligne commence par GET
                        if (line.startsWith("GET")) {
                            // On split par les espaces
                            String[] split = line.split(" ");
                            // Et on récupère le nom de la commande
                            String command = split[1];

                            // On split par des /
                            aliases = command.split("/");
                            // On récupère le nom de la commande
                            commandName = aliases[1];
                            // On ne process pas la commande ici car ça cause des problèmes vu qu'on va renvoyer le résultat avant que le client n'écoute
                        }
                    }


                    JSONObject answer = new JSONObject();
                    switch (commandName) {
                        case "discordMsg":
                            JSONObject json = (JSONObject) new JSONParser().parse(URLDecoder.decode(aliases[2], "UTF-8"));
                            String message = json.get("message").toString();
                            String playerName = json.get("playerName").toString();

                            // On envoie le message aux joueurs
                            for (Player p : plugin.getServer().getOnlinePlayers()) {
                                p.sendMessage(ChatColor.DARK_PURPLE + playerName + ChatColor.WHITE + ": " + message);
                            }
                            plugin.getServer().getConsoleSender().sendMessage(ChatColor.DARK_PURPLE + playerName + ": " + message);
                            answer.put("status", "ok");
                            out.print(answer.toJSONString());
                            break;
                        case "getPlayers":
                            // On renvoie la liste des joueurs
                            JSONObject listToReturn = new JSONObject();
                            JSONObject players = new JSONObject();

                            for (Player p : plugin.getServer().getOnlinePlayers()) {
                                JSONObject playerInfos = new JSONObject();
                                playerInfos.put("username", p.getName());
                                playerInfos.put("uuid", p.getUniqueId().toString());
                                players.put(p.getName(), playerInfos);
                            }

                            listToReturn.put("players", players);
                            out.print(listToReturn.toJSONString());
                            break;
                        default:
                            answer.put("status", "error");
                            answer.put("message", "Commande " + commandName + " inconnue");
                            out.print(answer.toJSONString());
                            break;
                    }

                    // Close socket, breaking the connection to the client, and
                    // closing the input and output streams
                    out.close(); // Flush and close the output stream
                    in.close(); // Close the input stream
                    client.close(); // Close the socket itself
                }
            } catch (IOException e) {
                ConsoleLog.danger("Erreur lors de l'écoute du port " + ChatColor.GOLD + serverPort);
                e.printStackTrace();

                // On va logger le message sur discord
                JSONObject json = new JSONObject();
                json.put("desc", "Erreur lors de l'écoute du port " + serverPort);
                json.put("message", e.getMessage());
                String urlString = null;
                try {
                    urlString = plugin.getConfig().getString("discordBot-api-url") + "mc/error/" + URLEncoder.encode(json.toJSONString(), "UTF-8").replace("+", "%20");
                    relaunchListener(plugin);
                } catch (UnsupportedEncodingException ex) {
                    ConsoleLog.danger("Erreur lors de l'encodage du message. Func waitForDiscordMsg::startServer(Main plugin)");
                    ex.printStackTrace();
                }
                plugin.getHttp(urlString);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        };

        new Thread(serverThread).start();
    }

    // TODO: Vérifier l'utilité de cette fonction
    public static void relaunchListener(Main plugin) {
        // On relance la fonction avec une latence
        startServer(plugin);
    }
}

package com.slprojects.slcraftplugin.tachesParalleles;

import com.slprojects.slcraftplugin.Main;
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

public class waitForDiscordMsg {
    @SuppressWarnings({ "unchecked", "InfiniteLoopStatement" })
    public static void startServer(Main plugin){
        int serverPort = plugin.getConfig().getInt("msg-server-port");

        plugin.getLogger().info("Écoute des messages Discord sur le port " + ChatColor.GOLD + serverPort);
        // On fait un thread pour écouter le port
        Runnable serverThread = () -> {
            try {
                ServerSocket serverSocket = new ServerSocket(serverPort);
                while (true) {
                    Socket client = serverSocket.accept();

                    // Get input and output streams to talk to the client
                    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    PrintWriter out = new PrintWriter(client.getOutputStream());

                    // Start sending our reply, using the HTTP 1.1 protocol
                    out.print("HTTP/1.1 200 \r\n"); // Version & status code
                    out.print("Content-Type: text/plain\r\n"); // The type of data
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
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (line.length() == 0)
                            break;
                        //out.print(line + "\r\n");
                        //plugin.getLogger().info(line);

                        // On va regarder si la ligne commence par GET
                        if (line.startsWith("GET")) {
                            // On split par les espaces
                            String[] split = line.split(" ");
                            // Et on récupère le nom de la commande
                            String command = split[1];

                            // On split par des /
                            String[] split2 = command.split("/");
                            // On récupère le nom de la commande
                            String commandName = split2[1];

                            if ("discordMsg".equals(commandName)) {// On récupère le message
                                JSONObject json = (JSONObject) new JSONParser().parse(URLDecoder.decode(split2[2], "UTF-8"));
                                String message = json.get("message").toString();
                                String playerName = json.get("playerName").toString();

                                // On envoie le message aux joueurs
                                for (Player p : plugin.getServer().getOnlinePlayers()) {
                                    p.sendMessage(ChatColor.DARK_PURPLE + playerName + ChatColor.WHITE + ": " + message);
                                }
                                plugin.getLogger().info(ChatColor.DARK_PURPLE + playerName + ": " + message);
                                out.print("Message envoyé !");
                            } else {
                                out.print("La commande \"" + commandName + "\" n'est pas reconnue.\r\n");
                            }
                        }
                    }

                    // Close socket, breaking the connection to the client, and
                    // closing the input and output streams
                    out.close(); // Flush and close the output stream
                    in.close(); // Close the input stream
                    client.close(); // Close the socket itself
                }
            } catch (IOException e) {
                plugin.getLogger().info(ChatColor.RED + "Erreur lors de l'écoute du port " + ChatColor.GOLD  + serverPort);
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
                    plugin.getLogger().info(ChatColor.RED + "Erreur lors de l'encodage du message. Func waitForDiscordMsg::startServer(Main plugin)");
                    ex.printStackTrace();
                }
                plugin.getHttp(urlString);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        };

        new Thread(serverThread).start();


    }

    public static void relaunchListener(Main plugin) {
        // On relance la fonction avec une latence
        startServer(plugin);
    }
}

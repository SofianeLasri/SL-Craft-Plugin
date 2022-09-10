package com.slprojects.slcraftplugin.commands.publics;

import com.slprojects.slcraftplugin.Main;
import com.slprojects.slcraftplugin.utils.ConsoleLog;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Random;

public class LinkCode implements CommandExecutor {

    // Variables
    private final Main plugin;

    public LinkCode(Main plugin) {
        // On récupère la classe parente pour les paramètres
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;

            // On ouvre la bdd
            Connection con = plugin.bddOpenConn();

            try {
                // On créé le code
                int leftLimit = 48; // numeral '0'
                int rightLimit = 122; // letter 'z'
                int targetStringLength = 8;
                Random random = new Random();

                String generatedString = random.ints(leftLimit, rightLimit + 1)
                        .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                        .limit(targetStringLength)
                        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                        .toString();

                // On va regarder si l'utilisateur a déjà généré un code auparavant
                PreparedStatement rechercheLinkingCode = con.prepareStatement("SELECT * FROM site_linkCode WHERE uuid = ?");
                rechercheLinkingCode.setString(1, player.getUniqueId().toString());
                ResultSet resultat = rechercheLinkingCode.executeQuery();

                if (resultat.next()) {
                    PreparedStatement modifyAccountLinkingCode = con.prepareStatement("UPDATE `site_linkCode` SET `code`=?, `time`=?, `used`='0' WHERE `uuid`=?");
                    modifyAccountLinkingCode.setString(1, generatedString);
                    modifyAccountLinkingCode.setString(2, java.sql.Timestamp.valueOf(LocalDateTime.now()).toString());
                    modifyAccountLinkingCode.setString(3, player.getUniqueId().toString());
                    modifyAccountLinkingCode.executeQuery();

                } else {
                    PreparedStatement insertionAccountLinkingCode = con.prepareStatement("INSERT INTO site_linkCode (`uuid`, `code`, `time`, `used`) VALUES (?, ?, ?, '0')");
                    insertionAccountLinkingCode.setString(1, player.getUniqueId().toString());
                    insertionAccountLinkingCode.setString(2, generatedString);
                    insertionAccountLinkingCode.setString(3, java.sql.Timestamp.valueOf(LocalDateTime.now()).toString());
                    insertionAccountLinkingCode.executeQuery();
                }
                player.sendMessage("Utilise ce code pour lier ton compte: " + ChatColor.GREEN + generatedString);
                player.sendMessage(ChatColor.GRAY + "Ce code à usage unique expirera dans 5 minutes.");
                ConsoleLog.info("Le joueur " + ChatColor.GOLD + player.getName() + ChatColor.RESET + " a généré le code " + ChatColor.GREEN + generatedString + ChatColor.RESET + ChatColor.GRAY + " - Il expirera le " + java.sql.Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)));

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return true;
    }
}

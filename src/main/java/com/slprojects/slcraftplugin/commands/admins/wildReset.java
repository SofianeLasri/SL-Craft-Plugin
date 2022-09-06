package com.slprojects.slcraftplugin.commands.admins;

import com.slprojects.slcraftplugin.Main;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class wildReset implements CommandExecutor {
    private final Main plugin;

    public wildReset(Main plugin){
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args){
        if(args.length > 0){
            for(int i=0; i< args.length; i++){
                Player player = plugin.getServer().getPlayer(args[i]);
                if(player != null){
                    List<Object> reset = new ArrayList<Object>(){
                        {
                            add(0);
                            add(LocalDateTime.parse("2001-12-11 12:30", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                        }
                    };
                    plugin.playerDataHandler.savePlayerWildCmdStats(player, reset);
                    plugin.wildCommand.setPlayerStats(player, reset);
                    String msg = "Passage de 'wildCmdLastUsed' au 11/12/2001 et 'wildCmdAskNum' à 0 pour " + player.getName() + " UUID: " + player.getUniqueId();
                    if (sender instanceof Player){
                        sender.sendMessage("§7§o"+msg);
                    }else{
                        plugin.getServer().getConsoleSender().sendMessage(msg);
                    }
                }else{
                    String errorMsg = "Joueur n°" + i + " (dans la liste) non trouvé. :(";
                    if (sender instanceof Player){
                        sender.sendMessage("§c"+errorMsg);
                    }else{
                        plugin.getServer().getConsoleSender().sendMessage(ChatColor.RED + errorMsg);
                    }
                }
            }
        }else{
            String errorMsg = "Vous devez écrire le pseudo d'un ou plusieurs joueurs.";
            if (sender instanceof Player){
                sender.sendMessage("§c"+errorMsg);
            }else{
                plugin.getServer().getConsoleSender().sendMessage(ChatColor.RED + errorMsg);
            }
        }
        return true;
    }
}
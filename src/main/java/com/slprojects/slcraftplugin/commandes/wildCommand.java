package com.slprojects.slcraftplugin.commandes;

import com.slprojects.slcraftplugin.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Random;

import static java.lang.Math.abs;

public class wildCommand implements CommandExecutor {
    // Variables
    private final Main plugin;


    public wildCommand(Main plugin){
        // On récupère la classe parente pour les paramètres
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        // On vérifie que la commande a bien été lancée par un joueur
        if (sender instanceof Player) {
            Player player = (Player) sender;

            // On vérifie qu'il n'a pas déjà lancé la commande wild
            if(!plugin.checkActiveUserForWildCommand(player.getUniqueId())){
                plugin.getLogger().info("Le joueur "+ChatColor.GOLD+player.getName()+ChatColor.RESET+" a exécuté la commande "+ChatColor.GOLD+"/wild"+ChatColor.RESET+" : "+ChatColor.RED+"refusé");
                player.sendMessage("§cVous devez attendre 5s avant de relancer la commande.");
                return true;
            }
            plugin.getLogger().info("Le joueur "+ChatColor.GOLD+player.getName()+ChatColor.RESET+" a exécuté la commande "+ChatColor.GOLD+"/wild"+ChatColor.RESET+" : "+ChatColor.GREEN+"accepté");

            // on récupère la liste des biomes exclus
            List<String> excludedBiomes;
            excludedBiomes = (List<String>) plugin.getConfig().getList("excluded-biomes");

            player.sendMessage("§6Téléportation vers une coordonnée aléatoire.");

            // On défini le radius de téléportation
            Random r = new Random();
            int low = plugin.getConfig().getInt("max-range")*(-1);
            int high = plugin.getConfig().getInt("max-range");

            // Tant qu'on a un biome non souhaite, on va regérer les coordonnées
            boolean flag=true;
            int x=0, z=0, y=0;
            while(flag){
                flag=false;
                x = r.nextInt(high-low) + low;
                z = r.nextInt(high-low) + low;
                y = Objects.requireNonNull(Bukkit.getWorld(Objects.requireNonNull(plugin.getConfig().getString("world")))).getHighestBlockYAt(x, z);
                y++; // On incrémente la pos Y pour éviter que le joueur se retrouve dans le sol

                for (String excludedBiome : Objects.requireNonNull(excludedBiomes)) {
                    try{
                        Biome.valueOf(excludedBiome.toUpperCase());
                        if (Objects.requireNonNull(Bukkit.getWorld(Objects.requireNonNull(plugin.getConfig().getString("world")))).getBiome(x, y, z).equals(Biome.valueOf(excludedBiome.toUpperCase()))) {
                            flag = true;
                        }
                    }catch(Exception ignored){}
                }
            }
            // On téléporte le joueur

            Location loc = new Location(Bukkit.getWorld(Objects.requireNonNull(plugin.getConfig().getString("world"))), x, y, z, 0, 0);
            player.teleport(loc);

            int maxVal = Math.max(abs(x), abs(z));

            if(maxVal <= 10000){
                player.sendMessage("§7§oVous êtes sur un biome généré en 1.16");
            }else if(maxVal <= 14500){
                player.sendMessage("§7§oVous êtes sur un biome généré en 1.17");
            }else{
                player.sendMessage("§7§oVous êtes sur un biome généré en 1.18");
            }

            // Vu qu'il y a un sleep et que ça bloque le thread, on va exécuter la fonction dans un thread
            Runnable runnableRemoveActiveUser = () -> {
                // On retire le joueur de la liste des utilisateurs en attente
                plugin.removeActiveUserForWildCommand(player.getUniqueId());
            };

            new Thread(runnableRemoveActiveUser).start();
        }
        return true;
    }
}

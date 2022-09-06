package com.slprojects.slcraftplugin.commands.publics;

import com.slprojects.slcraftplugin.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static java.lang.Math.abs;

public class Wild implements CommandExecutor {

    // Variables
    private final Main plugin;

    private List<UUID> wildUsersIndexes;
    private List<LocalDateTime> wildUsersLastAsked;
    private List<Integer> wildUsersAskNum;
    private List<Location> wildUsersStartLocation;
    private final int usageCooldown;
    private final int usagePerDay;

    public Wild(Main plugin){
        // On récupère la classe parente pour les paramètres
        this.plugin = plugin;
        wildUsersIndexes = new ArrayList<>();
        wildUsersLastAsked = new ArrayList<>();
        wildUsersAskNum = new ArrayList<>();
        wildUsersStartLocation = new ArrayList<>();
        usageCooldown = plugin.getConfig().getInt("wild.usage-cooldown");
        usagePerDay = plugin.getConfig().getInt("wild.usage-per-day");

        plugin.getServer().getConsoleSender().sendMessage("Instance de wild.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        // On vérifie que la commande a bien été lancée par un joueur
        if (sender instanceof Player) {
            Player player = (Player) sender;
            UUID playerUUID = player.getUniqueId();
            int playerIndex;
            LocalDateTime dateTimeNow = LocalDateTime.now();

            playerIndex = wildUsersIndexes.indexOf(playerUUID);

            if(abs(ChronoUnit.SECONDS.between(wildUsersLastAsked.get(playerIndex), dateTimeNow)) > usageCooldown){
                if(wildUsersAskNum.get(playerIndex) < usagePerDay){
                    wildUsersLastAsked.set(playerIndex, dateTimeNow);
                    wildUsersStartLocation.set(playerIndex, player.getLocation());
                    askForTeleport(player);
                }else{
                    plugin.getServer().getConsoleSender().sendMessage("["+ plugin.getName() +"] Le joueur "+ChatColor.GOLD+player.getName()+ChatColor.RESET+" a exécuté la commande "+ChatColor.GOLD+"/wild"+ChatColor.RESET+" : "+ChatColor.RED+"refusé");
                    player.sendMessage("§cVous n'avez le droit qu'à §n"+usagePerDay+"§r§c téléportations aléatoires par jour.");
                }
            }else{
                plugin.getServer().getConsoleSender().sendMessage("["+ plugin.getName() +"] Le joueur "+ChatColor.GOLD+player.getName()+ChatColor.RESET+" a exécuté la commande "+ChatColor.GOLD+"/wild"+ChatColor.RESET+" : "+ChatColor.RED+"refusé");
                player.sendMessage("§cVous devez attendre §n"+usageCooldown+"s§r§c avant de relancer la commande.");
            }
        }
        return true;
    }

    private void askForTeleport(Player player){
        int playerIndex = wildUsersIndexes.indexOf(player.getUniqueId());
        plugin.getServer().getConsoleSender().sendMessage("["+ plugin.getName() +"] Le joueur "+ChatColor.GOLD+player.getName()+ChatColor.RESET+" a exécuté la commande "+ChatColor.GOLD+"/wild"+ChatColor.RESET+" : "+ChatColor.GREEN+"accepté");
        player.sendMessage("Vous allez être téléporté dans §c" + plugin.getConfig().getInt("wild.move-cooldown") + "s§r, ne bougez pas.");
        int delayInTicks = plugin.getConfig().getInt("wild.move-cooldown") * 20;

        new BukkitRunnable() {
            @Override
            public void run(){
                Location oldPlayerLocation = wildUsersStartLocation.get(playerIndex);
                Location newPlayerLocation = player.getLocation();

                if((oldPlayerLocation.getX() != newPlayerLocation.getX()) && (oldPlayerLocation.getY() != newPlayerLocation.getY()) && (oldPlayerLocation.getZ() != newPlayerLocation.getZ())){
                    player.sendMessage("§cVous avez bougé, téléportation annulée.");
                    // Date bidon pour annuler le cooldown (c'est ma date de naissance :D)
                    wildUsersLastAsked.set(playerIndex, LocalDateTime.parse("2001-12-11 12:30", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                }else{
                    teleportPlayer(player);
                }
            }
        }.runTaskLater(plugin, delayInTicks);
    }

    private void teleportPlayer(Player player){
        int playerIndex = wildUsersIndexes.indexOf(player.getUniqueId());
        wildUsersAskNum.set(playerIndex, wildUsersAskNum.get(playerIndex)+1);

        // on récupère la liste des biomes exclus
        List<String> excludedBiomes;
        excludedBiomes = (List<String>) plugin.getConfig().getList("wild.excluded-biomes");

        player.sendMessage("§6Téléportation vers une coordonnée aléatoire.");

        // On défini le radius de téléportation
        Random r = new Random();
        int low = plugin.getConfig().getInt("wild.max-range")*(-1);
        int high = plugin.getConfig().getInt("wild.max-range");

        // Tant qu'on a un biome non souhaite, on va regérer les coordonnées
        boolean flag=true;
        int x=0, z=0, y=0;
        while(flag){
            flag=false;
            x = r.nextInt(high-low) + low;
            z = r.nextInt(high-low) + low;
            y = Bukkit.getWorld(plugin.getConfig().getString("wild.world")).getHighestBlockYAt(x, z);
            y++; // On incrémente la pos Y pour éviter que le joueur se retrouve dans le sol

            for (String excludedBiome : excludedBiomes) {
                // Biomes non reconnus ou supprimés (deep warm ocean)
                try{
                    Biome.valueOf(excludedBiome.toUpperCase());
                    if (Bukkit.getWorld(plugin.getConfig().getString("wild.world")).getBiome(x, y, z).equals(Biome.valueOf(excludedBiome.toUpperCase()))) {
                        flag = true;
                    }
                }catch(Exception ignored){}
            }
        }

        // On téléporte le joueur
        Location loc = new Location(Bukkit.getWorld(plugin.getConfig().getString("wild.world")), x, y, z, 0, 0);
        player.teleport(loc);

        int maxVal = Math.max(abs(x), abs(z));

        if(maxVal <= 10000){
            player.sendMessage("§7§oVous êtes sur un biome généré en 1.16");
        }else if(maxVal <= 14500){
            player.sendMessage("§7§oVous êtes sur un biome généré en 1.17");
        }else{
            player.sendMessage("§7§oVous êtes sur un biome généré en 1.18");
        }
        if((usagePerDay - wildUsersAskNum.get(playerIndex)) > 0){
            player.sendMessage("§7§oIl vous reste " + (usagePerDay - wildUsersAskNum.get(playerIndex)) + " téléportations pour aujourd'hui.");
        }else{
            player.sendMessage("§7§oVous avez épuisé toutes vos téléportations du jour.");
        }
    }

    public List<Object> getPlayerStats(Player player){
        if(!wildUsersIndexes.contains(player.getUniqueId())){
            return new ArrayList<>();
        }else{
            int playerIndex = wildUsersIndexes.indexOf(player.getUniqueId());
            // Indexes:
            // - 0: Nombre d'utilisation du jour
            // - 1: Date de la dernière commande
            List<Object> stats = new ArrayList<Object>();
            stats.add(wildUsersAskNum.get(playerIndex));
            stats.add(wildUsersLastAsked.get(playerIndex));
            return stats;
        }
    }

    public void setPlayerStats(Player player, List<Object> stats){
        LocalDateTime dateTimeNow = LocalDateTime.now();

        if(!wildUsersIndexes.contains(player.getUniqueId())){
            wildUsersIndexes.add(player.getUniqueId());
            wildUsersLastAsked.add(dateTimeNow);
            wildUsersAskNum.add(0);
            wildUsersStartLocation.add(player.getLocation());
        }
        int playerIndex = wildUsersIndexes.indexOf(player.getUniqueId());

        // Indexes:
        // - 0: Nombre d'utilisation du jour
        // - 1: Date de la dernière commande
        LocalDateTime savedDateTime = (LocalDateTime)stats.get(1);
        if(ChronoUnit.HOURS.between(savedDateTime, dateTimeNow) > 24){
            wildUsersAskNum.set(playerIndex, 0);
            wildUsersLastAsked.set(playerIndex, savedDateTime);
        }else{
            wildUsersAskNum.set(playerIndex, (int)stats.get(0));
            wildUsersLastAsked.set(playerIndex, savedDateTime);
        }
    }
}

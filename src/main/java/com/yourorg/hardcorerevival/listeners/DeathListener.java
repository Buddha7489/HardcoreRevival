package com.yourorg.hardcorerevival.listeners;

import com.yourorg.hardcorerevival.HardcoreRevivalPlugin;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Handles player death events. Drops the deceased player's head bound to
 * them at their death location and switches them to spectator mode. The head
 * contains lore marking it as a revival token. Behaviour is controlled via
 * the dropHeadOnDeath config option.
 */
public class DeathListener implements Listener {
    private final HardcoreRevivalPlugin plugin;

    public DeathListener(HardcoreRevivalPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        // Check if head drops are enabled
        if (!plugin.getConfig().getBoolean("settings.dropHeadOnDeath", true)) {
            return;
        }
        // Check world whitelist/blacklist
        String worldName = player.getWorld().getName();
        java.util.List<String> whitelist = plugin.getConfig().getStringList("settings.worldWhitelist");
        java.util.List<String> blacklist = plugin.getConfig().getStringList("settings.worldBlacklist");
        if (!whitelist.isEmpty()) {
            if (!whitelist.contains(worldName)) {
                return;
            }
        } else if (blacklist.contains(worldName)) {
            return;
        }
        // Drop the player's head
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        // Lore for revival token
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(plugin.colour("&eRevival Token for &f" + player.getName()));
        meta.setLore(lore);
        head.setItemMeta(meta);
        // Drop naturally at the player's death location
        player.getWorld().dropItemNaturally(player.getLocation(), head);
        // Schedule spectator mode after death to ensure it persists
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            }
        }.runTaskLater(plugin, 1L);
    }
}
package com.yourorg.hardcorerevival.listeners;

import com.yourorg.hardcorerevival.HardcoreRevivalPlugin;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Applies pending revives for players who were offline when revived. When
 * such a player joins, they are restored to survival mode with full health
 * and receive temporary regeneration and damage resistance effects.
 */
public class JoinListener implements Listener {
    private final HardcoreRevivalPlugin plugin;

    public JoinListener(HardcoreRevivalPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Check if player is pending revival
        if (plugin.getDataStore().isPendingRevive(player.getUniqueId())) {
            plugin.getDataStore().removePendingRevive(player.getUniqueId());
            revivePlayer(player);
        }
    }

    /**
     * Restores a player to survival mode, heals them to full health and
     * applies regeneration and damage resistance potion effects. Negative
     * potion effects are cleared. Food level is restored to maximum.
     *
     * @param player the player to revive
     */
    public static void revivePlayer(Player player) {
        // Set game mode to survival
        player.setGameMode(GameMode.SURVIVAL);
        // Heal to full health
        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getDefaultValue();
        player.setHealth(maxHealth);
        player.setFoodLevel(20);
        // Clear negative potion effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            // Remove only negative effects (we consider all for simplicity)
            player.removePotionEffect(effect.getType());
        }
        // Apply regeneration and resistance (10s, level 1)
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 200, 0));
    }
}
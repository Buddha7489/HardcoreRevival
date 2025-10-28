package com.yourorg.hardcorerevival.util;

import com.yourorg.hardcorerevival.HardcoreRevivalPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LightningStrike;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;

/**
 * Utility class for playing sounds and spawning particles defined in the
 * configuration during revival events. All methods here are static and
 * intended to be called from event handlers.
 */
public final class EffectsUtil {
    private EffectsUtil() {
    }

    /**
     * Plays the configured revival sounds at the given location. Each sound entry
     * may specify a delay (in ticks) after which it plays relative to the
     * previous one. Uses the Paper scheduler to avoid blocking the main
     * thread.
     *
     * @param plugin   plugin instance
     * @param location location at which to play sounds
     */
    public static void playSounds(final HardcoreRevivalPlugin plugin, final Location location) {
        List<Map<?, ?>> soundList = plugin.getConfig().getMapList("sounds");
        long accumulatedDelay = 0L;
        for (Map<?, ?> map : soundList) {
            String soundName = String.valueOf(map.getOrDefault("sound", ""));
            float volume = ((Number) map.getOrDefault("volume", 1.0)).floatValue();
            float pitch = ((Number) map.getOrDefault("pitch", 1.0)).floatValue();
            int delay = ((Number) map.getOrDefault("delay", 0)).intValue();
            accumulatedDelay += delay;
            // Schedule each sound according to cumulative delay
            final long runDelay = accumulatedDelay;
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        Sound sound = Sound.valueOf(soundName);
                        location.getWorld().playSound(location, sound, volume, pitch);
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("Unknown sound configured: " + soundName);
                    }
                }
            }.runTaskLater(plugin, runDelay);
        }
    }

    /**
     * Spawns the configured particles at the given location. All particle
     * effects are spawned immediately when this method is called. Counts and
     * offsets are read from the configuration.
     *
     * @param plugin   plugin instance
     * @param location central location for particle spawning
     */
    public static void spawnParticles(final HardcoreRevivalPlugin plugin, final Location location) {
        List<Map<?, ?>> particles = plugin.getConfig().getMapList("particles");
        for (Map<?, ?> map : particles) {
            String typeName = String.valueOf(map.getOrDefault("type", ""));
            int count = ((Number) map.getOrDefault("count", 1)).intValue();
            double offsetX = ((Number) map.getOrDefault("offsetX", 0.0)).doubleValue();
            double offsetY = ((Number) map.getOrDefault("offsetY", 0.0)).doubleValue();
            double offsetZ = ((Number) map.getOrDefault("offsetZ", 0.0)).doubleValue();
            try {
                Particle particle = Particle.valueOf(typeName);
                location.getWorld().spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Unknown particle configured: " + typeName);
            }
        }
    }

    /**
     * Spawns a lightning bolt at the given location. The lightning may be
     * configured to deal no damage based on the plugin configuration. Uses
     * Paper’s lightning strike API to create a lightning entity if necessary.
     *
     * @param plugin   plugin instance
     * @param location location at which to spawn lightning
     */
    public static void spawnLightning(final HardcoreRevivalPlugin plugin, final Location location) {
        boolean noDamage = plugin.getConfig().getBoolean("settings.lightningNoDamage", true);
        if (noDamage) {
            // Use the effect-only method to avoid damage and fire
            location.getWorld().strikeLightningEffect(location);
        } else {
            location.getWorld().strikeLightning(location);
        }
    }
}
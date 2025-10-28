package com.yourorg.hardcorerevival.util;

import com.yourorg.hardcorerevival.HardcoreRevivalPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Handles persistent storage for HardcoreRevival. This stores pending revives
 * for offline players as well as per-altar cooldown timestamps. The data is
 * stored in a YAML file in the plugin’s data folder and loaded on plugin
 * startup. Modifications should be followed by a save() call to write
 * changes to disk.
 */
public class DataStore {
    private final HardcoreRevivalPlugin plugin;
    private final File dataFile;
    private FileConfiguration data;

    // Cache for quick lookups. These mirror the YAML contents and are
    // synchronised on load/save.
    private final Set<UUID> pendingRevives = new HashSet<>();
    private final Map<String, Long> cooldowns = new HashMap<>();

    public DataStore(HardcoreRevivalPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    /**
     * Loads data from data.yml into memory. If the file does not exist it is
     * created with empty sections. Any IO errors will be logged but not
     * propagated to callers.
     */
    public void load() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                if (!dataFile.createNewFile()) {
                    plugin.getLogger().warning("Could not create data.yml");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create data.yml: " + e.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        pendingRevives.clear();
        cooldowns.clear();

        // Load pending revives
        List<String> pendingList = data.getStringList("pendingRevives");
        for (String uuidStr : pendingList) {
            try {
                pendingRevives.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ignored) {
                // Skip invalid UUID strings
            }
        }

        // Load cooldowns: map of location key -> long timestamp
        if (data.isConfigurationSection("cooldowns")) {
            for (String key : Objects.requireNonNull(data.getConfigurationSection("cooldowns").getKeys(false))) {
                long time = data.getLong("cooldowns." + key, 0L);
                cooldowns.put(key, time);
            }
        }
    }

    /**
     * Saves the current in-memory pending revives and cooldowns to disk. Any IO
     * errors will be logged. This should be called whenever the state has
     * changed.
     */
    public void save() {
        if (data == null) {
            data = new YamlConfiguration();
        }
        // Persist pending revives
        List<String> pendingList = new ArrayList<>();
        for (UUID uuid : pendingRevives) {
            pendingList.add(uuid.toString());
        }
        data.set("pendingRevives", pendingList);

        // Persist cooldowns
        for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
            data.set("cooldowns." + entry.getKey(), entry.getValue());
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save data.yml: " + e.getMessage());
        }
    }

    /**
     * Checks whether the given player UUID is pending a revive.
     *
     * @param uuid the player unique ID
     * @return true if the player will be revived on next login
     */
    public boolean isPendingRevive(UUID uuid) {
        return pendingRevives.contains(uuid);
    }

    /**
     * Marks a player as pending a revival. The change is saved immediately.
     *
     * @param uuid the player unique ID
     */
    public void addPendingRevive(UUID uuid) {
        if (pendingRevives.add(uuid)) {
            save();
        }
    }

    /**
     * Removes a player from the pending revive list. The change is saved
     * immediately.
     *
     * @param uuid the player unique ID
     */
    public void removePendingRevive(UUID uuid) {
        if (pendingRevives.remove(uuid)) {
            save();
        }
    }

    /**
     * Retrieves the last used timestamp for the given altar location key. If
     * none exists, returns 0.
     *
     * @param key a serialised location key
     * @return last usage time in milliseconds
     */
    public long getCooldown(String key) {
        return cooldowns.getOrDefault(key, 0L);
    }

    /**
     * Updates the cooldown timestamp for the given altar location. The change
     * is saved immediately.
     *
     * @param key      the location key
     * @param millis   timestamp in milliseconds when the altar was used
     */
    public void setCooldown(String key, long millis) {
        cooldowns.put(key, millis);
        save();
    }
}
package com.yourorg.hardcorerevival;

import com.yourorg.hardcorerevival.commands.HRCheckCommand;
import com.yourorg.hardcorerevival.commands.HRReloadCommand;
import com.yourorg.hardcorerevival.listeners.AltarListener;
import com.yourorg.hardcorerevival.listeners.DeathListener;
import com.yourorg.hardcorerevival.listeners.JoinListener;
import com.yourorg.hardcorerevival.util.DataStore;
import com.yourorg.hardcorerevival.util.RecipeRegistrar;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main entry point for HardcoreRevival. Registers listeners, commands,
 * recipes and handles configuration loading. Provides access to the
 * {@link DataStore} used to persist pending revives and cooldowns.
 */
public class HardcoreRevivalPlugin extends JavaPlugin {
    private DataStore dataStore;

    @Override
    public void onEnable() {
        // Ensure the plugin data folder exists and save default config
        saveDefaultConfig();
        // Save default files if they don't exist
        saveResource("config.yml", false);

        // Initialise data storage
        dataStore = new DataStore(this);
        dataStore.load();

        // Register crafting recipe
        RecipeRegistrar.registerBlankHeadRecipe(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new AltarListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);

        // Register commands
        PluginCommand reloadCmd = getCommand("hrreload");
        if (reloadCmd != null) {
            reloadCmd.setExecutor(new HRReloadCommand(this));
        }
        PluginCommand checkCmd = getCommand("hrcheck");
        if (checkCmd != null) {
            checkCmd.setExecutor(new HRCheckCommand(this));
        }
    }

    @Override
    public void onDisable() {
        // Persist data on disable
        if (dataStore != null) {
            dataStore.save();
        }
    }

    /**
     * Reloads configuration files and re-registers the blank head recipe.
     * Invoked by the /hrreload command.
     */
    public void reload() {
        reloadConfig();
        // Re-register recipe with updated display name
        RecipeRegistrar.registerBlankHeadRecipe(this);
    }

    /**
     * Retrieves the data store used to track pending revives and cooldowns.
     *
     * @return data store instance
     */
    public DataStore getDataStore() {
        return dataStore;
    }

    /**
     * Convenience method to colourise strings using the '&' colour codes.
     *
     * @param input raw string with colour codes
     * @return coloured string with Minecraft colour codes applied
     */
    public String colour(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    /**
     * Retrieves a message from the configuration and prepends the prefix.
     * If the path does not exist, an empty string is returned.
     *
     * @param path the configuration path under the messages section
     * @return coloured message with prefix
     */
    public String getMessage(String path) {
        String prefix = getConfig().getString("messages.prefix", "");
        String msg = getConfig().getString("messages." + path, "");
        return colour(prefix + msg);
    }

    /**
     * Retrieves a message without the prefix. Useful for command
     * notifications where the prefix is added manually.
     *
     * @param path configuration path under messages
     * @return coloured message
     */
    public String getRawMessage(String path) {
        String msg = getConfig().getString("messages." + path, "");
        return colour(msg);
    }
}
package com.yourorg.hardcorerevival.commands;

import com.yourorg.hardcorerevival.HardcoreRevivalPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the /hrreload command. Reloads the plugin configuration and
 * re-registers the crafting recipe. Requires the hardcorerevival.admin
 * permission.
 */
public class HRReloadCommand implements CommandExecutor {
    private final HardcoreRevivalPlugin plugin;

    public HRReloadCommand(HardcoreRevivalPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("hardcorerevival.admin")) {
            sender.sendMessage(plugin.colour("&cYou do not have permission to run this command."));
            return true;
        }
        plugin.reload();
        sender.sendMessage(plugin.getRawMessage("command.reload"));
        return true;
    }
}
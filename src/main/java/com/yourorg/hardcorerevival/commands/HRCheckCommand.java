package com.yourorg.hardcorerevival.commands;

import com.yourorg.hardcorerevival.HardcoreRevivalPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles the /hrcheck command. Checks whether a player is currently dead
 * (spectator), pending revival, or alive. Only available to admins.
 */
public class HRCheckCommand implements CommandExecutor {
    private final HardcoreRevivalPlugin plugin;

    public HRCheckCommand(HardcoreRevivalPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("hardcorerevival.admin")) {
            sender.sendMessage(plugin.colour("&cYou do not have permission to run this command."));
            return true;
        }
        String targetName;
        if (args.length > 0) {
            targetName = args[0];
        } else {
            // Default to the sender if player, else error
            if (sender instanceof Player player) {
                targetName = player.getName();
            } else {
                sender.sendMessage(plugin.getRawMessage("errors.unknownPlayer"));
                return true;
            }
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
        if (offline == null || offline.getName() == null) {
            sender.sendMessage(plugin.getRawMessage("errors.unknownPlayer"));
            return true;
        }
        UUID uuid = offline.getUniqueId();
        boolean pending = plugin.getDataStore().isPendingRevive(uuid);
        if (pending) {
            sender.sendMessage(plugin.getRawMessage("command.checkPending").replace("{player}", offline.getName()));
            return true;
        }
        // Check if online and spectator
        Player targetOnline = Bukkit.getPlayer(uuid);
        if (targetOnline != null && targetOnline.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            sender.sendMessage(plugin.getRawMessage("command.checkDead").replace("{player}", targetOnline.getName()));
        } else {
            sender.sendMessage(plugin.getRawMessage("command.checkAlive").replace("{player}", offline.getName()));
        }
        return true;
    }
}
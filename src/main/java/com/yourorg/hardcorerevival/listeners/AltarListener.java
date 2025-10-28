package com.yourorg.hardcorerevival.listeners;

import com.yourorg.hardcorerevival.HardcoreRevivalPlugin;
import com.yourorg.hardcorerevival.util.AltarValidator;
import com.yourorg.hardcorerevival.util.EffectsUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Listens for placement of player heads. When a head is placed on top of a
 * fence over a valid altar structure, it attempts to revive the targeted
 * player. Handles structure validation, target resolution, item consumption,
 * effects, and broadcast messaging.
 */
public class AltarListener implements Listener {
    private final HardcoreRevivalPlugin plugin;

    public AltarListener(HardcoreRevivalPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        Material placedType = placed.getType();
        // Check if a player head has been placed (wall or standing)
        if (placedType != Material.PLAYER_HEAD && placedType != Material.PLAYER_WALL_HEAD) {
            return;
        }
        Player placer = event.getPlayer();
        // Ensure there is a fence below the head
        Block fenceBlock = placed.getRelative(0, -1, 0);
        if (!isAcceptableFence(fenceBlock.getType())) {
            placer.sendMessage(plugin.getMessage("errors.notOnFence"));
            plugin.getLogger().info("Altar placement failed: head not on fence by " + placer.getName());
            return;
        }

        // Validate altar structure and chest contents
        AltarValidator.ValidationResult result = AltarValidator.validate(fenceBlock, plugin);
        if (!result.valid) {
            placer.sendMessage(plugin.getMessage("errors." + result.errorKey));
            plugin.getLogger().info("Altar validation failed at " + fenceBlock.getLocation() + ": " + result.errorKey);
            return;
        }

        // Check cooldown for this altar
        long cooldownSeconds = plugin.getConfig().getLong("settings.cooldownSeconds", 0L);
        if (cooldownSeconds > 0) {
            // Use centre location to key cooldowns
            Location centre = result.descriptor.getCentre();
            String key = serialiseLocationKey(centre);
            long last = plugin.getDataStore().getCooldown(key);
            long now = System.currentTimeMillis();
            long elapsed = (now - last) / 1000L;
            if (last > 0 && elapsed < cooldownSeconds) {
                placer.sendMessage(plugin.getMessage("errors.cooldownActive"));
                return;
            }
        }

        // Determine the target player from the head meta
        ItemStack handItem = event.getItemInHand();
        String targetName = null;
        UUID targetUUID = null;
        boolean foundValidTarget = false;
        if (handItem != null && handItem.getType() == Material.PLAYER_HEAD) {
            ItemMeta meta = handItem.getItemMeta();
            if (meta instanceof SkullMeta skullMeta) {
                // Try owner profile first (real player head)
                if (skullMeta.hasOwningPlayer()) {
                    OfflinePlayer owner = skullMeta.getOwningPlayer();
                    if (owner != null && owner.getName() != null) {
                        targetName = owner.getName();
                        targetUUID = owner.getUniqueId();
                        foundValidTarget = true;
                    }
                }
                // Fallback to display name (blank head renamed)
                if (!foundValidTarget) {
                    String displayName = meta.hasDisplayName() ? ChatColor.stripColor(meta.getDisplayName()) : null;
                    if (displayName != null) {
                        String trimmed = displayName.trim();
                        if (!trimmed.isEmpty()) {
                            OfflinePlayer offline = Bukkit.getOfflinePlayer(trimmed);
                            if (offline != null && offline.getName() != null) {
                                targetName = offline.getName();
                                targetUUID = offline.getUniqueId();
                                foundValidTarget = true;
                            }
                        }
                    }
                }
            }
        }
        if (!foundValidTarget || targetUUID == null) {
            placer.sendMessage(plugin.getMessage("errors.headNotNamed"));
            return;
        }

        // Check if target is dead (spectator) or pending revive
        boolean enableOffline = plugin.getConfig().getBoolean("settings.enableOfflineRevive", true);
        boolean isPending = plugin.getDataStore().isPendingRevive(targetUUID);
        Player targetPlayer = Bukkit.getPlayer(targetUUID);
        if (targetPlayer != null) {
            if (targetPlayer.getGameMode() != GameMode.SPECTATOR) {
                // Player is online and not in spectator
                placer.sendMessage(plugin.getMessage("errors.playerNotDead"));
                return;
            }
        } else {
            // target offline
            if (!enableOffline) {
                placer.sendMessage(plugin.getMessage("errors.playerNotDead"));
                return;
            }
            // Only allow if they are pending or we know they have died (we can't detect). We'll allow offline even if not pending.
        }

        // All validations passed; proceed with revival
        // Consume chest items if enabled
        if (plugin.getConfig().getBoolean("settings.consumeItems", true)) {
            AltarValidator.consumeChestItems(result.descriptor.getChestInventories());
        }
        // Remove head placed
        new BukkitRunnable() {
            @Override
            public void run() {
                placed.setType(Material.AIR);
            }
        }.runTask(plugin);

        // Spawn effects
        EffectsUtil.spawnParticles(plugin, result.descriptor.getCentre().clone().add(0.5, 1.5, 0.5));
        EffectsUtil.playSounds(plugin, result.descriptor.getCentre().clone().add(0.5, 1.5, 0.5));
        EffectsUtil.spawnLightning(plugin, result.descriptor.getCentre().clone().add(0.5, 0.0, 0.5));

        // Set cooldown timestamp
        if (cooldownSeconds > 0) {
            String key = serialiseLocationKey(result.descriptor.getCentre());
            plugin.getDataStore().setCooldown(key, System.currentTimeMillis());
        }

        // Process revival
        if (targetPlayer != null) {
            // Online spectator
            reviveOnlinePlayer(targetPlayer, result.descriptor.getCentre());
        } else {
            // Offline player; mark pending
            plugin.getDataStore().addPendingRevive(targetUUID);
        }

        // Broadcast success
        String message = plugin.getMessage("success.revive").replace("{player}", targetName);
        Bukkit.getServer().broadcastMessage(message);
        plugin.getLogger().info(placer.getName() + " revived " + targetName + " at " + result.descriptor.getCentre());
    }

    private static boolean isAcceptableFence(Material type) {
        // Accept any fence except Nether brick fence. Wood variants end with _FENCE.
        if (type == Material.NETHER_BRICK_FENCE) return false;
        return type.toString().endsWith("_FENCE");
    }

    private static String serialiseLocationKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private void reviveOnlinePlayer(Player target, Location centre) {
        // Teleport to centre + one block up (just above the altar) to avoid suffocation
        Location tp = centre.clone().add(0.5, 1.1, 0.5);
        target.teleport(tp);
        // Use the static revive logic from JoinListener
        JoinListener.revivePlayer(target);
    }
}
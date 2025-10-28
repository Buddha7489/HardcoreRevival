package com.yourorg.hardcorerevival.util;

import com.yourorg.hardcorerevival.HardcoreRevivalPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class responsible for validating altar structures. Given the fence block
 * supporting a placed head, this class checks the gold block platform, chest
 * placements, chest contents and world restrictions. If valid, an
 * {@link AltarDescriptor} containing the altar details is returned.
 */
public final class AltarValidator {
    private AltarValidator() {
    }

    /**
     * Represents the validated altar including its centre location and the four
     * chests used in the ritual. The inventories are returned so callers can
     * modify them (e.g. remove items).
     */
    public static class AltarDescriptor {
        private final Location centre;
        private final List<Inventory> chestInventories;

        public AltarDescriptor(Location centre, List<Inventory> chestInventories) {
            this.centre = centre;
            this.chestInventories = chestInventories;
        }

        public Location getCentre() {
            return centre;
        }

        public List<Inventory> getChestInventories() {
            return chestInventories;
        }
    }

    /**
     * Result returned by validation. If valid is false, errorKey points at
     * a message configured under messages.errors.*. The descriptor is
     * populated only when valid.
     */
    public static class ValidationResult {
        public final boolean valid;
        public final String errorKey;
        public final AltarDescriptor descriptor;

        private ValidationResult(boolean valid, String errorKey, AltarDescriptor descriptor) {
            this.valid = valid;
            this.errorKey = errorKey;
            this.descriptor = descriptor;
        }

        public static ValidationResult success(AltarDescriptor descriptor) {
            return new ValidationResult(true, null, descriptor);
        }

        public static ValidationResult failure(String errorKey) {
            return new ValidationResult(false, errorKey, null);
        }
    }

    /**
     * Validates the altar structure beneath the given fence block. Checks
     * world restrictions, Y-level limits, gold block layout, chest placement
     * and chest contents. If requireExactStructure is disabled in config,
     * structural checks are skipped but chest contents are still validated.
     *
     * @param fenceBlock the fence block on which the head sits
     * @param plugin     the plugin instance to read configuration from
     * @return a validation result containing an altar descriptor or an error key
     */
    public static ValidationResult validate(Block fenceBlock, HardcoreRevivalPlugin plugin) {
        World world = fenceBlock.getWorld();
        String worldName = world.getName();

        // World whitelist/blacklist
        List<String> whitelist = plugin.getConfig().getStringList("settings.worldWhitelist");
        List<String> blacklist = plugin.getConfig().getStringList("settings.worldBlacklist");
        if (!whitelist.isEmpty()) {
            // Only worlds explicitly whitelisted are allowed
            if (!whitelist.contains(worldName)) {
                return ValidationResult.failure("worldNotAllowed");
            }
        } else if (blacklist.contains(worldName)) {
            return ValidationResult.failure("worldNotAllowed");
        }

        // Y-level limits
        int yMin = plugin.getConfig().getInt("settings.yMin", 0);
        int yMax = plugin.getConfig().getInt("settings.yMax", 320);
        int baseY = fenceBlock.getY() - 1;
        if (baseY < yMin || baseY > yMax) {
            return ValidationResult.failure("structureInvalid");
        }

        boolean requireExact = plugin.getConfig().getBoolean("settings.requireExactStructure", true);

        Location fenceLoc = fenceBlock.getLocation();
        Location centre = fenceLoc.clone().subtract(0, 1, 0);

        // Validate gold platform if required
        if (requireExact) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Location check = centre.clone().add(dx, 0, dz);
                    if (check.getBlock().getType() != Material.GOLD_BLOCK) {
                        return ValidationResult.failure("structureInvalid");
                    }
                }
            }
        }

        // Gather chests at the four corners
        List<Inventory> inventories = new ArrayList<>();
        int[][] corners = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        for (int[] corner : corners) {
            int dx = corner[0];
            int dz = corner[1];
            // Chest is at base+1 (centre Y + 1)
            Location chestLoc = centre.clone().add(dx, 1, dz);
            Block chestBlock = chestLoc.getBlock();
            Material type = chestBlock.getType();
            if (requireExact) {
                // Only accept chest blocks (single or part of double chest)
                if (type != Material.CHEST) {
                    return ValidationResult.failure("structureInvalid");
                }
            } else {
                if (type != Material.CHEST) {
                    // Still gather even if not exact; skip invalid but continue
                    continue;
                }
            }
            BlockState state = chestBlock.getState();
            if (state instanceof Chest chest) {
                inventories.add(chest.getBlockInventory());
            }
        }

        // If exact structure is required, there must be exactly four chests
        if (requireExact && inventories.size() != 4) {
            return ValidationResult.failure("structureInvalid");
        }

        // Validate chest contents: each must contain ≥1 Totem of Undying and ≥1 Wither Rose
        for (Inventory inv : inventories) {
            boolean hasTotem = false;
            boolean hasRose = false;
            for (ItemStack item : inv.getContents()) {
                if (item == null) continue;
                if (item.getType() == Material.TOTEM_OF_UNDYING) {
                    if (item.getAmount() > 0) {
                        hasTotem = true;
                    }
                } else if (item.getType() == Material.WITHER_ROSE) {
                    if (item.getAmount() > 0) {
                        hasRose = true;
                    }
                }
                if (hasTotem && hasRose) break;
            }
            if (!hasTotem || !hasRose) {
                return ValidationResult.failure("chestMissingItems");
            }
        }

        return ValidationResult.success(new AltarDescriptor(centre, inventories));
    }

    /**
     * Removes exactly one Totem of Undying and one Wither Rose from each
     * provided inventory. The caller should only invoke this after a successful
     * validation. This method modifies the inventories directly.
     *
     * @param inventories list of chest inventories
     */
    public static void consumeChestItems(List<Inventory> inventories) {
        for (Inventory inv : inventories) {
            removeOne(inv, Material.TOTEM_OF_UNDYING);
            removeOne(inv, Material.WITHER_ROSE);
        }
    }

    private static void removeOne(Inventory inv, Material mat) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == mat) {
                int amount = item.getAmount();
                if (amount > 1) {
                    item.setAmount(amount - 1);
                } else {
                    inv.setItem(i, null);
                }
                return;
            }
        }
    }
}
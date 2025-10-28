package com.yourorg.hardcorerevival.util;

import com.yourorg.hardcorerevival.HardcoreRevivalPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;
import org.bukkit.ChatColor;

/**
 * Registers crafting recipes for HardcoreRevival. Currently only one shapeless
 * recipe is provided: combining a skeleton skull and an Instant Health II
 * potion to create a blank revival head. The resulting head has no owner
 * profile and uses a configurable display name.
 */
public final class RecipeRegistrar {
    private RecipeRegistrar() {
    }

    /**
     * Registers the blank head crafting recipe. This should be called once
     * during plugin enable. If the recipe already exists from a previous
     * enable, it is replaced. The output name is read from config.recipe.outputName.
     *
     * @param plugin plugin instance
     */
    public static void registerBlankHeadRecipe(HardcoreRevivalPlugin plugin) {
        // Create output head with no owning profile
        ItemStack output = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) output.getItemMeta();
        String displayName = plugin.getConfig().getString("recipe.outputName", "Blank Revival Head");
        skullMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
        // Add a simple lore instructing players to rename in an anvil
        skullMeta.setLore(java.util.Arrays.asList(ChatColor.GRAY + "Rename in an anvil to the player's name"));
        // Ensure the blank head has no owner assigned so it functions as a generic head
        skullMeta.setOwningPlayer(null);
        output.setItemMeta(skullMeta);

        // Define an exact choice for the Instant Health II potion
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta potionMeta = (PotionMeta) potion.getItemMeta();
        // Base potion data: INSTANT_HEAL, upgraded = true (tier II)
        potionMeta.setBasePotionData(new PotionData(PotionType.INSTANT_HEAL, false, true));
        potion.setItemMeta(potionMeta);

        ShapelessRecipe recipe = new ShapelessRecipe(new NamespacedKey(plugin, "blank_head"), output);
        // Skeleton skull
        recipe.addIngredient(Material.SKELETON_SKULL);
        // Potion with exact meta
        recipe.addIngredient(new org.bukkit.inventory.RecipeChoice.ExactChoice(potion));

        // Remove existing recipe with same key if present
        plugin.getServer().removeRecipe(recipe.getKey());
        plugin.getServer().addRecipe(recipe);
    }
}
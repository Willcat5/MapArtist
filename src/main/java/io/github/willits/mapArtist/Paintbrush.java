package io.github.willits.mapArtist;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;

/**
 * The MapArtist paintbrush. Required in the player's off hand to open a map
 * drawing session, or in either hand to interact with map walls. All its
 * properties (base item, name, item model, custom model data) are configurable
 * so servers can reskin it and avoid clashing with other plugins' models.
 */
public final class Paintbrush {

    private static final String DEFAULT_BRUSH_MODEL = "minecraft:brush";

    private final Material baseMaterial;
    private final String displayName;
    private final NamespacedKey itemModel;
    private final int customModelData;

    public Paintbrush(ConfigurationSection section) {
        String rawName = section == null ? null : section.getString("name");
        if (rawName == null) {
            rawName = "&6&lPaintbrush";
        }
        String rawModel = section == null ? null : section.getString("item-model");
        String rawBase = section == null ? null : section.getString("base-item");
        this.baseMaterial = parseMaterial(rawBase);
        this.displayName = ChatColor.translateAlternateColorCodes('&', rawName);
        this.itemModel = parseKey(rawModel == null ? DEFAULT_BRUSH_MODEL : rawModel);
        this.customModelData = section == null ? 1 : section.getInt("custom-model-data", 1);
    }

    private static Material parseMaterial(String name) {
        Material material = name == null ? null : Material.matchMaterial(name);
        if (material == null) {
            material = Material.STICK;
        }
        return material;
    }

    private static NamespacedKey parseKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return NamespacedKey.fromString(key.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public ItemStack create() {
        ItemStack brush = new ItemStack(baseMaterial);
        ItemMeta meta = brush.getItemMeta();
        meta.setDisplayName(displayName);
        if (itemModel != null) {
            meta.setItemModel(itemModel);
        }
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        brush.setItemMeta(meta);
        brush.setAmount(1);
        setMaxStackSize(brush, 1);
        return brush;
    }

    public boolean isPaintbrush(ItemStack item) {
        if (item == null || item.getType() != baseMaterial) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (customModelData > 0 && meta.getCustomModelData() != customModelData) {
            return false;
        }
        if (itemModel != null && meta.hasItemModel() && !itemModel.equals(meta.getItemModel())) {
            return false;
        }
        return true;
    }

    /**
     * Sets the max stack size. Only available at runtime on server jars that
     * expose {@code ItemStack#setMaxStackSize(int)} (Paper and forks); falls
     * back silently where the API method is absent.
     */
    private static void setMaxStackSize(ItemStack item, int max) {
        try {
            Method method = ItemStack.class.getMethod("setMaxStackSize", int.class);
            method.invoke(item, max);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Not supported on this server implementation.
        }
    }
}
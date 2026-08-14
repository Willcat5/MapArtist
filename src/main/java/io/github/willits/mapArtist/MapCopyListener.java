package io.github.willits.mapArtist;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

public final class MapCopyListener implements Listener {

    private static final double DROP_RADIUS = 2.0;

    private final MapArtist plugin;

    public MapCopyListener(MapArtist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Item dropped = event.getItemDrop();
        Material type = dropped.getItemStack().getType();
        if (type != Material.FILLED_MAP && type != Material.MAP) {
            return;
        }

        Item emptyMap;
        Item drawnMap;
        if (type == Material.MAP) {
            emptyMap = dropped;
            drawnMap = findNearby(dropped, Material.FILLED_MAP);
        } else {
            drawnMap = dropped;
            emptyMap = findNearby(dropped, Material.MAP);
        }
        if (emptyMap == null || drawnMap == null) {
            return;
        }

        ItemStack drawnStack = drawnMap.getItemStack();
        if (!(drawnStack.getItemMeta() instanceof MapMeta drawnMeta) || !drawnMeta.hasMapId()) {
            return;
        }
        int sourceId = drawnMeta.getMapId();
        byte[][] drawing = plugin.getDrawingStore().get(sourceId);
        if (drawing == null) {
            return;
        }

        MapView newView = plugin.getServer().createMap(emptyMap.getWorld());
        if (newView == null) {
            return;
        }
        int newId = newView.getId();
        byte[][] copy = new byte[128][128];
        for (int y = 0; y < 128; y++) {
            System.arraycopy(drawing[y], 0, copy[y], 0, 128);
        }
        plugin.getDrawingStore().put(newId, copy);
        plugin.saveDrawing(newId, copy);

        ItemStack newItem = new ItemStack(Material.FILLED_MAP);
        MapMeta newMeta = (MapMeta) newItem.getItemMeta();
        newMeta.setMapId(newId);
        ItemMeta drawnItemMeta = drawnStack.getItemMeta();
        if (drawnItemMeta != null) {
            String name = drawnItemMeta.getDisplayName();
            if (name != null && !name.isEmpty()) {
                newMeta.setDisplayName(name);
            }
        }
        newItem.setItemMeta(newMeta);

        ItemStack emptyStack = emptyMap.getItemStack();
        if (emptyStack.getAmount() > 1) {
            emptyStack.setAmount(emptyStack.getAmount() - 1);
            emptyMap.setItemStack(emptyStack);
            emptyMap.getWorld().dropItem(emptyMap.getLocation(), newItem);
        } else {
            emptyMap.setItemStack(newItem);
        }

        event.getPlayer().sendMessage(ChatColor.GREEN + "Created copy of map #" + sourceId + " as map #" + newId);
    }

    @EventHandler
    public void onMapInitialize(MapInitializeEvent event) {
        plugin.prepareNewMap(event.getMap());
    }

    private Item findNearby(Item origin, Material type) {
        for (Entity entity : origin.getWorld().getNearbyEntities(origin.getLocation(), DROP_RADIUS, DROP_RADIUS, DROP_RADIUS)) {
            if (entity instanceof Item item && !item.isDead() && item.getItemStack().getType() == type) {
                return item;
            }
        }
        return null;
    }
}

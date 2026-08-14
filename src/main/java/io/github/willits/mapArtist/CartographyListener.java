package io.github.willits.mapArtist;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class CartographyListener implements Listener {

    private final MapArtist plugin;

    public CartographyListener(MapArtist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled()) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.CARTOGRAPHY_TABLE) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "Cartography tables are disabled on this server.");
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (enabled() && event.getInventory().getType() == InventoryType.CARTOGRAPHY) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCraftPrepare(PrepareItemCraftEvent event) {
        if (enabled() && event.getInventory().getType() == InventoryType.CARTOGRAPHY) {
            event.getInventory().setResult(null);
        }
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("disable-cartography-table", true);
    }
}

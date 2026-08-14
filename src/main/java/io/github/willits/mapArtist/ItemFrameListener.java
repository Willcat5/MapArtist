package io.github.willits.mapArtist;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

public final class ItemFrameListener implements Listener {

    private final MapArtist plugin;

    public ItemFrameListener(MapArtist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onFrameInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame frame) {
            syncNextTick(frame);
        }
    }

    @EventHandler
    public void onFrameDamaged(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            syncNextTick(frame);
        }
    }

    private void syncNextTick(ItemFrame frame) {
        plugin.getServer().getScheduler().runTask(plugin, () -> syncFrame(frame));
    }

    private void syncFrame(ItemFrame frame) {
        if (frame.isDead() || !frame.isValid()) {
            return;
        }
        if (!plugin.getConfig().getBoolean("invisible-item-frames", true)) {
            return;
        }
        frame.setVisible(!isCustomMap(frame.getItem()));
    }

    private boolean isCustomMap(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP) {
            return false;
        }
        if (!(item.getItemMeta() instanceof MapMeta meta) || !meta.hasMapView()) {
            return false;
        }
        MapView view = Bukkit.getMap(meta.getMapId());
        return view != null && view.getRenderers().stream().anyMatch(r -> r instanceof DrawingRenderer);
    }
}

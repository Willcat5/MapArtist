package io.github.willits.mapArtist;

import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Protects MapArtist maps from destruction:
 *  - map-protection-itemdestruction: loose/dropped MapArtist map items are
 *    immune to fire, lava, cactus, explosions and other item-deleters, sort of
 *    like netherite. Only applies while the map is an item entity.
 *  - map-protection-anti-break-when-locked: the frame holding a locked map
 *    cannot be broken by damaging it. The owner of the locked map (and admins)
 *    may still break their own locked map out of the frame.
 */
public final class MapProtectionListener implements Listener {

    private final MapArtist plugin;

    public MapProtectionListener(MapArtist plugin) {
        this.plugin = plugin;
    }

    private boolean itemDestructionEnabled() {
        return plugin.getConfig().getBoolean("map-protection-itemdestruction", true);
    }

    private boolean antiBreakEnabled() {
        return plugin.getConfig().getBoolean("map-protection-anti-break-when-locked", false);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item item && itemDestructionEnabled()) {
            if ((event.getCause() == EntityDamageEvent.DamageCause.FIRE
                    || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                    || event.getCause() == EntityDamageEvent.DamageCause.HOT_FLOOR
                    || event.getCause() == EntityDamageEvent.DamageCause.LAVA
                    || event.getCause() == EntityDamageEvent.DamageCause.CONTACT
                    || event.getCause() == EntityDamageEvent.DamageCause.DROWNING
                    || event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION
                    || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                    || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)
                    && MapWallDetector.isCustomMap(item.getItemStack())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (itemDestructionEnabled()
                && event.getEntity() instanceof Item item
                && MapWallDetector.isCustomMap(item.getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame)) {
            return;
        }
        if (!antiBreakEnabled()) {
            return;
        }
        ItemStack item = frame.getItem();
        org.bukkit.map.MapView view = MapWallDetector.mapViewOf(item);
        if (view == null) {
            return;
        }
        int mapId = view.getId();
        if (plugin.getLockStore() == null || !plugin.getLockStore().isLocked(mapId)) {
            return;
        }
        if (event.getDamager() instanceof Player damager) {
            UUID owner = plugin.getLockStore().ownerOf(mapId);
            if (owner != null && owner.equals(damager.getUniqueId())) {
                return; // the owner may break their own locked map out of the frame
            }
            if (plugin.isAdmin(damager.getUniqueId())) {
                return; // admins may also break locked maps out
            }
        }
        event.setCancelled(true);
    }
}

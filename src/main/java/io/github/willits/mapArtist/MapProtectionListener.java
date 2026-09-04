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
import org.bukkit.map.MapView;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Protects MapArtist maps from destruction:
 *  - map-protection-itemdestruction: loose/dropped MapArtist map items are
 *    immune to fire, lava, cactus, explosions and other item-deleters, sort of
 *    like netherite. Only applies while the map is an item entity.
 *  - map-protection-break-resistance: a frame holding a LOCKED MapArtist map
 *    must be hit a configurable number of times within one second to break.
 *    -1 (or any negative) makes the frame unbreakable, 0 disables the
 *    protection. No one bypasses it, not even the owner or admins.
 */
public final class MapProtectionListener implements Listener {

    private static final long WINDOW_MS = 1000;

    private final MapArtist plugin;
    private final Map<Integer, Map<UUID, Hit>> breakHits = new HashMap<>();

    public MapProtectionListener(MapArtist plugin) {
        this.plugin = plugin;
    }

    private boolean itemDestructionEnabled() {
        return plugin.getConfig().getBoolean("map-protection-itemdestruction", true);
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
    public void onFrameDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame)) {
            return;
        }
        int resistance = plugin.getConfig().getInt("map-protection-break-resistance", 3);
        if (resistance == 0) {
            return; // protection disabled
        }
        MapView view = MapWallDetector.mapViewOf(frame.getItem());
        if (view == null) {
            return;
        }
        if (plugin.getLockStore() == null || !plugin.getLockStore().isLocked(view.getId())) {
            return;
        }
        if (resistance < 0) {
            event.setCancelled(true); // unbreakable
            return;
        }
        if (!(event.getDamager() instanceof Player damager)) {
            return;
        }
        // Count this hit; only the hit that reaches the required number within
        // the window is allowed through so the frame breaks.
        if (!hitAllowsBreak(view.getId(), damager.getUniqueId(), resistance)) {
            event.setCancelled(true);
        }
    }

    private boolean hitAllowsBreak(int mapId, UUID player, int resistance) {
        long now = System.currentTimeMillis();
        prune(now);
        Map<UUID, Hit> perPlayer = breakHits.computeIfAbsent(mapId, k -> new HashMap<>());
        Hit hit = perPlayer.get(player);
        if (hit == null || now - hit.windowStart > WINDOW_MS) {
            hit = new Hit(now, 1);
        } else {
            hit = new Hit(hit.windowStart, hit.count + 1);
        }
        if (hit.count >= resistance) {
            perPlayer.remove(player);
            return true;
        }
        perPlayer.put(player, hit);
        return false;
    }

    private void prune(long now) {
        Iterator<Map.Entry<Integer, Map<UUID, Hit>>> outer = breakHits.entrySet().iterator();
        while (outer.hasNext()) {
            Map.Entry<Integer, Map<UUID, Hit>> e = outer.next();
            e.getValue().entrySet().removeIf(h -> now - h.getValue().windowStart > WINDOW_MS);
            if (e.getValue().isEmpty()) {
                outer.remove();
            }
        }
    }

    private static final class Hit {
        final long windowStart;
        final int count;

        Hit(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}

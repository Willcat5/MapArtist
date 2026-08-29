package io.github.willits.mapArtist;

import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Protects MapArtist maps (and the item frames holding them) from destruction.
 * Three independent switches:
 *  - map-protection-explosions: frames holding drawing maps survive explosions.
 *  - map-protection-itemdestruction: loose/dropped MapArtist map items are
 *    immune to fire, lava, cactus, explosions and other item-deleters, sort of
 *    like netherite. Only applies while the map is an item entity.
 *  - map-protection-anti-break-when-locked: the frame itself (not the block
 *    behind it) cannot be broken by damaging it.
 */
public final class MapProtectionListener implements Listener {

    private final MapArtist plugin;

    public MapProtectionListener(MapArtist plugin) {
        this.plugin = plugin;
    }

    private boolean explosionsEnabled() {
        return plugin.getConfig().getBoolean("map-protection-explosions", true);
    }

    private boolean itemDestructionEnabled() {
        return plugin.getConfig().getBoolean("map-protection-itemdestruction", true);
    }

    private boolean antiBreakEnabled() {
        return plugin.getConfig().getBoolean("map-protection-anti-break-when-locked", false);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            if (explosionsEnabled() && isExplosion(event.getCause())
                    && MapWallDetector.isCustomMap(frame.getItem())) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getEntity() instanceof Item item && itemDestructionEnabled()) {
            if ((isExplosion(event.getCause())
                    || event.getCause() == EntityDamageEvent.DamageCause.FIRE
                    || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                    || event.getCause() == EntityDamageEvent.DamageCause.HOT_FLOOR
                    || event.getCause() == EntityDamageEvent.DamageCause.LAVA
                    || event.getCause() == EntityDamageEvent.DamageCause.CONTACT
                    || event.getCause() == EntityDamageEvent.DamageCause.DROWNING
                    || event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION)
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
        if (!MapWallDetector.isCustomMap(frame.getItem())) {
            return;
        }
        // The frame can neither be broken by hand nor destroyed by an
        // explosion. This never touches the block behind the frame.
        if (antiBreakEnabled() || (explosionsEnabled() && isExplosion(event.getCause()))) {
            event.setCancelled(true);
        }
    }

    private static boolean isExplosion(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION;
    }
}
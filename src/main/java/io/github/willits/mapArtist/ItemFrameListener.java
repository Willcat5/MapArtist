package io.github.willits.mapArtist;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public final class ItemFrameListener implements Listener {

    private final MapArtist plugin;

    public ItemFrameListener(MapArtist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onFrameInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) {
            return;
        }
        Player player = event.getPlayer();
        boolean custom = MapWallDetector.isCustomMap(frame.getItem());
        boolean isMap = MapWallDetector.mapViewOf(frame.getItem()) != null;
        if (custom) {
            // MapArtist drawing maps must never be rotated by hand; the wall
            // detection (and grid alignment) relies on frames staying square.
            // Cancelling the interaction also prevents replacing the map here.
            event.setCancelled(true);
        }
        if (player.isSneaking()
                && plugin.getConfig().getBoolean("multi-map-drawing", true)
                && isMap) {
            if (!plugin.getPaintbrush().isPaintbrush(player.getInventory().getItemInMainHand())
                    && !plugin.getPaintbrush().isPaintbrush(player.getInventory().getItemInOffHand())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.GOLD + "MapArtist: " + ChatColor.RED
                        + "You need a paintbrush in your hand to draw on a map wall.");
                syncNextTick(frame);
                return;
            }
            int maxGrid = plugin.getConfig().getInt("multi-map-max-grid", 5);
            MapWallDetector.Grid grid = MapWallDetector.detect(frame, maxGrid);
            if (grid != null) {
                if (grid.width() > maxGrid || grid.height() > maxGrid) {
                    player.sendMessage(ChatColor.GOLD + "MapArtist: " + ChatColor.RED
                            + "This map grid is too large. Maximum size is " + maxGrid + "x" + maxGrid + ".");
                } else {
                    // Opening a wall with the brush converts any vanilla maps
                    // in the grid into drawing maps, same as opening one in hand.
                    for (int mapId : grid.mapIds()) {
                        plugin.convertMap(mapId);
                    }
                    plugin.logEdits(player.getUniqueId(), grid.mapIds());
                    player.sendMessage(ChatColor.GOLD + "MapArtist: " + ChatColor.WHITE
                            + "Detected a map grid: " + grid.width() + " wide x " + grid.height() + " tall.");
                    player.sendMessage(ChatColor.GOLD + "MapArtist: " + ChatColor.GRAY
                            + "Map IDs by row (top to bottom): " + formatMapIds(grid) + ".");
                    if (grid.rotation() == null) {
                        player.sendMessage(ChatColor.GOLD + "MapArtist: " + ChatColor.RED
                                + "Warning: the frames have mixed rotations - align them (map-north up) before editing.");
                    } else {
                        player.sendMessage(ChatColor.GOLD + "MapArtist: " + ChatColor.GRAY
                                + "All frames aligned (rotation " + grid.rotation().name().replace('_', ' ').toLowerCase() + ").");
                    }
                    highlightWall(frame.getWorld(), grid);
                }
                event.setCancelled(true);
            } else {
                player.sendMessage(ChatColor.GOLD + "MapArtist: " + ChatColor.RED
                        + "That frame isn't part of a clean grid of maps.");
                event.setCancelled(true);
            }
        }
        syncNextTick(frame);
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

    private String formatMapIds(MapWallDetector.Grid grid) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < grid.mapIds().size(); i++) {
            if (i > 0) {
                sb.append(i % grid.width() == 0 ? " / " : "-");
            }
            sb.append(grid.mapIds().get(i));
        }
        return sb.toString();
    }

    private void highlightWall(org.bukkit.World world, MapWallDetector.Grid grid) {
        for (int i = 0; i < 10; i++) {
            long tick = i * 2L;
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> MapWallDetector.spawnHighlight(world, grid), tick);
        }
    }

    private void syncFrame(ItemFrame frame) {
        if (frame.isDead() || !frame.isValid()) {
            return;
        }
        if (!plugin.getConfig().getBoolean("invisible-item-frames", true)) {
            return;
        }
        frame.setVisible(!MapWallDetector.shouldHideFrame(frame.getItem(), plugin.getDrawingStore()));
    }
}

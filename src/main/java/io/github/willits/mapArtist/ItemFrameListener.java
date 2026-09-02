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
                    // Opening a wall with the brush opens a whole-grid drawing
                    // session. Vanilla maps in the grid are converted into
                    // drawing maps (same as opening one in hand) but only after
                    // a chat confirmation, so the conversion is explicit. Cells
                    // locked to another player are omitted from the session
                    // (with a warning) and are not converted or opened for this
                    // player.
                    java.util.List<Integer> locked = new java.util.ArrayList<>();
                    java.util.List<Integer> editable = new java.util.ArrayList<>();
                    for (int mapId : grid.mapIds()) {
                        if (!plugin.canEdit(mapId, player.getUniqueId())) {
                            locked.add(mapId);
                        } else {
                            editable.add(mapId);
                        }
                    }
                    plugin.logEdits(player.getUniqueId(), editable);
                    if (!locked.isEmpty()) {
                        player.sendMessage(ChatColor.GOLD + "MapArtist: " + ChatColor.RED
                                + "Skipped locked map(s): #" + String.join(", ",
                                locked.stream().map(String::valueOf).toList()) + ". Only the player who locked them can edit.");
                    }
                    if (grid.rotation() == null) {
                        player.sendMessage(ChatColor.GOLD + "MapArtist: " + ChatColor.RED
                                + "Warning: the frames have mixed rotations. Align them (map-north up) before editing. (you may want to break&replace them)");
                    }
                    highlightWall(frame.getWorld(), grid);
                    java.util.List<Integer> vanilla = editable.stream()
                            .filter(id -> !plugin.isDrawingMap(id)).toList();
                    if (vanilla.isEmpty()) {
                        plugin.openWallSession(player, grid, frame.getLocation());
                    } else {
                        plugin.setPendingWallConversion(player.getUniqueId(), grid, frame.getLocation(), vanilla);
                        MapInteractListener.sendWallConversionPrompt(player, vanilla);
                    }
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

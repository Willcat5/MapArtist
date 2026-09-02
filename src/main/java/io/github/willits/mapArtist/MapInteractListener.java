package io.github.willits.mapArtist;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

public final class MapInteractListener implements Listener {

    private final MapArtist plugin;

    public MapInteractListener(MapArtist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!event.getPlayer().isSneaking()) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FILLED_MAP) {
            return;
        }
        if (!(item.getItemMeta() instanceof MapMeta meta) || !meta.hasMapView()) {
            return;
        }
        event.setCancelled(true);
        MapView view = MapWallDetector.mapViewOf(item);
        if (view == null) {
            return;
        }
        if (MapWallDetector.isCustomMap(item)) {
            // Already a MapArtist drawing map: open the editor directly.
            plugin.openDrawingSession(event.getPlayer(), view.getId());
            return;
        }
        // A normal (vanilla) map: show a chat confirmation before converting.
        plugin.setPendingConversion(event.getPlayer().getUniqueId(), view.getId());
        sendConversionPrompt(event.getPlayer(), view.getId());
    }

    /**
     * Asks the player to confirm converting a vanilla map into a MapArtist
     * drawing map. Converting wipes vanilla mapping functionality, so this is
     * an explicit, reversable-by-choice step rather than something done the
     * moment a map is clicked.
     */
    private void sendConversionPrompt(Player player, int mapId) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "MapArtist: " + ChatColor.WHITE
                + "Do you want to convert this map (map #" + mapId + ") into a paintable map?");
        player.sendMessage(ChatColor.GRAY
                + "This will remove the map's vanilla mapping functionality. The map's current pixels will be erased. "
                + "This can't be undone.");

        TextComponent confirm = new TextComponent("[Confirm]");
        confirm.setColor(ChatColor.GREEN);
        confirm.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mapartist confirm"));
        confirm.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to convert this map into a MapArtist drawing map")));

        TextComponent cancel = new TextComponent("[Cancel]");
        cancel.setColor(ChatColor.RED);
        cancel.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mapartist cancel"));
        cancel.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to cancel")));

        TextComponent line = new TextComponent("");
        line.addExtra(confirm);
        line.addExtra("  ");
        line.addExtra(cancel);
        player.spigot().sendMessage(line);
    }

    /**
     * Asks the player to confirm converting the vanilla maps of a wall into
     * MapArtist drawing maps. Mirrors {@link #sendConversionPrompt} for single
     * maps, but covers several maps at once and reuses the same /mapartist
     * confirm|cancel commands.
     */
    static void sendWallConversionPrompt(Player player, java.util.List<Integer> vanilla) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "MapArtist: " + ChatColor.WHITE
                + "This wall has " + vanilla.size()
                + " vanilla map(s) that aren't paintable yet.");
        player.sendMessage(ChatColor.GRAY
                + "Converting map(s) #" + String.join(", ",
                vanilla.stream().map(String::valueOf).toList())
                + " will remove their vanilla mapping. Their current pixels will be erased. This can't be undone.");

        TextComponent confirm = new TextComponent("[Confirm]");
        confirm.setColor(ChatColor.GREEN);
        confirm.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mapartist confirm"));
        confirm.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to convert the wall maps into MapArtist drawing maps")));

        TextComponent cancel = new TextComponent("[Cancel]");
        cancel.setColor(ChatColor.RED);
        cancel.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mapartist cancel"));
        cancel.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to cancel")));

        TextComponent line = new TextComponent("");
        line.addExtra(confirm);
        line.addExtra("  ");
        line.addExtra(cancel);
        player.spigot().sendMessage(line);
    }

    static void sendLink(Player player, String url) {
        TextComponent link = new TextComponent("[Open Drawing Editor]");
        link.setColor(ChatColor.AQUA);
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to open the drawing canvas")));

        TextComponent message = new TextComponent("MapArtist: ");
        message.setColor(ChatColor.GOLD);
        message.addExtra(link);

        player.spigot().sendMessage(message);
        player.sendMessage("If it didn't open, paste this in your browser: " + url);
    }
}

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
        plugin.openDrawingSession(event.getPlayer(), meta.getMapId());
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

package io.github.willits.mapArtist;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.util.List;

public final class MapArtistCommand implements TabExecutor {

    private final MapArtist plugin;

    public MapArtistCommand(MapArtist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "give" -> giveMap(player);
            case "draw" -> drawMap(player);
            case "brush" -> giveBrush(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private void giveBrush(Player player) {
        player.getInventory().addItem(plugin.getPaintbrush().create());
        player.sendMessage("Gave you a paintbrush. Hold it in your off hand to open maps, or in either hand to inspect map walls.");
    }

    private void giveMap(Player player) {
        World world = player.getWorld();
        MapView view = Bukkit.createMap(world);
        if (!plugin.getConfig().getBoolean("vanilla-mapping", true)) {
            plugin.attachRenderer(view);
        }
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);
        item.setItemMeta(meta);
        player.getInventory().addItem(item);
        if (plugin.getConfig().getBoolean("vanilla-mapping", true)) {
            player.sendMessage("Gave you a fresh map (map #" + view.getId() + "). Sneak-right-click it while holding the paintbrush to turn it into a drawing.");
        } else {
            player.sendMessage("Gave you a drawing map (map #" + view.getId() + "). Right-click it to start drawing.");
        }
    }

    private void drawMap(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.FILLED_MAP || !(item.getItemMeta() instanceof MapMeta meta) || !meta.hasMapView()) {
            player.sendMessage("Hold a filled map in your main hand.");
            return;
        }
        plugin.openDrawingSession(player, meta.getMapId());
    }

    private void sendHelp(Player player) {
        player.sendMessage("MapArtist commands:");
        player.sendMessage("/mapartist give - Get a fresh drawable map");
        player.sendMessage("/mapartist draw - Open the editor for the map in your hand");
        player.sendMessage("/mapartist brush - Get the paintbrush (off hand)");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of("give", "draw", "brush");
    }
}

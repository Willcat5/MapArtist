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
            case "reload" -> reloadConfig(player);
            case "confirm" -> confirmConversion(player);
            case "cancel" -> plugin.cancelConversion(player);
            case "setowner" -> setOwner(player, args);
            case "unlock" -> unlockMap(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    private void confirmConversion(Player player) {
        plugin.confirmConversion(player);
    }

    private void reloadConfig(Player player) {
        if (!player.hasPermission("mapartist.admin")) {
            player.sendMessage("You don't have permission to reload MapArtist.");
            return;
        }
        plugin.reloadConfigAndRebuild();
        player.sendMessage("MapArtist config reloaded.");
    }

    /**
     * Transfers a map's lock ownership to another player. Admins can either
     * specify the map id explicitly or (default) use the filled map held in
     * their hand. Assigning an owner locks the map to that player; use the
     * map's web-editor unlock toggle to release it.
     */
    private void setOwner(Player player, String[] args) {
        if (!player.hasPermission("mapartist.admin")) {
            player.sendMessage("You don't have permission to change map ownership.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("Usage: /mapartist setowner <player>   (uses the map in your hand)");
            player.sendMessage("       /mapartist setowner <mapId> <player>");
            return;
        }
        int mapId;
        int targetIndex;
        if (args.length >= 3) {
            try {
                mapId = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("Invalid map ID: " + args[1]);
                return;
            }
            targetIndex = 2;
        } else {
            mapId = heldMapId(player);
            if (mapId < 0) {
                player.sendMessage("Hold a filled map in your main hand, or provide a map ID.");
                return;
            }
            targetIndex = 1;
        }

        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[targetIndex]);
        if (target.getName() == null) {
            player.sendMessage("Unknown player: " + args[targetIndex]);
            return;
        }
        if (Bukkit.getMap(mapId) == null) {
            player.sendMessage("No map found with ID " + mapId);
            return;
        }
        plugin.getLockStore().lock(mapId, target.getUniqueId(), player.getUniqueId());
        player.sendMessage("Map #" + mapId + " is now locked to " + target.getName()
                + ". Only they can edit it until it's unlocked again.");
    }

    private int heldMapId(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.FILLED_MAP || !(item.getItemMeta() instanceof MapMeta meta) || !meta.hasMapView()) {
            return -1;
        }
        return meta.getMapId();
    }

    /**
     * Release a map's lock, making it freely editable by anyone again. A player
     * may unlock a map they own (by held map or id); admins may unlock any map.
     */
    private void unlockMap(Player player, String[] args) {
        int mapId;
        if (args.length >= 2) {
            try {
                mapId = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("Invalid map ID: " + args[1]);
                return;
            }
        } else {
            mapId = heldMapId(player);
            if (mapId < 0) {
                player.sendMessage("Hold a filled map in your main hand, or provide a map ID: /mapartist unlock <mapId>");
                return;
            }
        }
        if (Bukkit.getMap(mapId) == null) {
            player.sendMessage("No map found with ID " + mapId);
            return;
        }
        if (plugin.getLockStore() == null || !plugin.getLockStore().isLocked(mapId)) {
            player.sendMessage("Map #" + mapId + " isn't locked.");
            return;
        }
        boolean isAdmin = plugin.isAdmin(player.getUniqueId());
        boolean isOwner = plugin.getLockStore().ownerOf(mapId).equals(player.getUniqueId());
        if (!isAdmin && !isOwner) {
            player.sendMessage("You can only unlock a map you own. This map is locked by someone else.");
            return;
        }
        plugin.getLockStore().unlock(mapId, player.getUniqueId());
        player.sendMessage("Map #" + mapId + " unlocked. Anyone can now edit it.");
    }

    private void giveBrush(Player player) {
        if (!player.hasPermission("mapartist.admin")) {
            player.sendMessage("You don't have permission to spawn a paintbrush.");
            return;
        }
        player.getInventory().addItem(plugin.getPaintbrush().create());
        player.sendMessage("Gave you a paintbrush. Hold it in your off hand to open maps, or in either hand to inspect map walls.");
    }

    private void giveMap(Player player) {
        if (!player.hasPermission("mapartist.admin")) {
            player.sendMessage("Only admins can spawn maps with /mapartist give.");
            return;
        }
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
            player.sendMessage("Gave you a fresh map (map #" + view.getId() + ")");
        } else {
            player.sendMessage("Gave you a drawing map (map #" + view.getId() + ")");
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
        player.sendMessage("/mapartist draw - Open the editor for the map in your hand");
        player.sendMessage("/mapartist brush - Admin: get a paintbrush");
        player.sendMessage("/mapartist give - Admin: spawn a fresh drawable map");
        player.sendMessage("/mapartist setowner <player> - Admin: lock map (held map) to a player");
        player.sendMessage("/mapartist setowner <mapId> <player> - Admin: lock map id to a player");
        player.sendMessage("/mapartist unlock - Unlock the held map you own");
        player.sendMessage("/mapartist unlock <mapId> - Unlock a map you own (admins: any map)");
        player.sendMessage("/mapartist reload - Admin: reload the config");
        player.sendMessage("Tip: hold a normal map + paintbrush in your off hand and sneak-right-click to convert it to drawable");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        boolean admin = player.hasPermission("mapartist.admin");

        if (args.length == 1) {
            List<String> subs = new java.util.ArrayList<>(List.of("draw", "confirm", "cancel", "unlock"));
            if (admin) {
                subs.add(0, "give");
                subs.add("brush");
                subs.add("reload");
                subs.add("setowner");
            }
            String prefix = args[0].toLowerCase();
            subs.removeIf(s -> !s.startsWith(prefix));
            return subs;
        }

        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            switch (sub) {
                case "setowner":
                    if (!admin) {
                        return List.of();
                    }
                    // First param is either <mapId> or <player>; guess from how it's typed.
                    if (args[1].length() > 0 && Character.isDigit(args[1].charAt(0))) {
                        return match("<mapId>", args[1]);
                    }
                    return matchingPlayers(args[1]);
                case "unlock":
                    return match("<mapId>", args[1]);
                default:
                    return List.of();
            }
        }

        // /mapartist setowner <mapId> <player>
        if (args.length == 3 && "setowner".equals(sub) && admin) {
            return matchingPlayers(args[2]);
        }
        return List.of();
    }

    private List<String> matchingPlayers(String typed) {
        String low = typed.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(low))
                .collect(java.util.stream.Collectors.toList());
    }

    private List<String> match(String value, String typed) {
        return value.toLowerCase().startsWith(typed.toLowerCase()) ? List.of(value) : List.of();
    }
}

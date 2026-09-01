package io.github.willits.mapArtist;

import io.github.willits.mapArtist.web.WebServer;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MapArtist extends JavaPlugin {

    private static final int MAP_SIZE = 128;

    private WebServer webServer;
    private TokenManager tokenManager;
    private DrawingStore drawingStore;
    private MapLockStore lockStore;
    private EditLogger editLogger;
    private Paintbrush paintbrush;
    private String host;
    private int port;
    private File drawingsDir;
    private File draftsDir;

    /**
     * A player's pending map-conversion request, awaiting chat confirmation.
     * Stored per player so the map id survives between the sneak-right-click
     * (which shows the prompt) and the confirm click.
     */
    private final Map<UUID, PendingConversion> pendingConversions = new ConcurrentHashMap<>();
    private static final long CONFIRM_TTL_MILLIS = 30_000L;

    public record PendingConversion(int mapId, long expiresAtMillis) {}

    public void setPendingConversion(UUID player, int mapId) {
        pendingConversions.put(player,
                new PendingConversion(mapId, System.currentTimeMillis() + CONFIRM_TTL_MILLIS));
    }

    public PendingConversion pendingConversion(UUID player) {
        PendingConversion pending = pendingConversions.get(player);
        if (pending == null || System.currentTimeMillis() > pending.expiresAtMillis()) {
            pendingConversions.remove(player);
            return null;
        }
        return pending;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        host = getConfig().getString("host", "localhost");
        port = getConfig().getInt("port", 8080);
        long ttlSeconds = getConfig().getLong("token-ttl-seconds", 1209600);
        paintbrush = new Paintbrush(getConfig().getConfigurationSection("paintbrush"));

        tokenManager = new TokenManager(ttlSeconds * 1000,
                getConfig().getInt("rate-limit-per-minute", 30));
        drawingStore = new DrawingStore();
        lockStore = new MapLockStore(this);
        lockStore.load();
        drawingsDir = new File(getDataFolder(), "drawings");
        draftsDir = new File(getDataFolder(), "drafts");
        editLogger = new EditLogger(this);

        loadDrawings();
        sweepDrafts();

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            tokenManager.sweep();
            sweepDrafts();
        }, 0L, 6 * 60 * 60 * 20L);

        webServer = new WebServer(this, port);
        try {
            webServer.start();
        } catch (Exception e) {
            getLogger().severe("Failed to start web server on port " + port + ": " + e.getMessage());
        }

        getCommand("mapartist").setExecutor(new MapArtistCommand(this));
        getServer().getPluginManager().registerEvents(new MapInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new MapCopyListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemFrameListener(this), this);
        getServer().getPluginManager().registerEvents(new CartographyListener(this), this);
        getServer().getPluginManager().registerEvents(new MapProtectionListener(this), this);

        getLogger().info("MapArtist enabled. Public host configured as: " + host);
        if (getConfig().getBoolean("show-ascii-banner", true)) {
        Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.GREEN + """
                $$\\      $$\\                      $$$$$$\\             $$\\     $$\\             $$\\    \s
                $$$\\    $$$ |                    $$  __$$\\            $$ |    \\__|            $$ |   \s
                $$$$\\  $$$$ | $$$$$$\\   $$$$$$\\  $$ /  $$ | $$$$$$\\ $$$$$$\\   $$\\  $$$$$$$\\ $$$$$$\\  \s
                $$\\$$\\$$ $$ | \\____$$\\ $$  __$$\\ $$$$$$$$ |$$  __$$\\\\_$$  _|  $$ |$$  _____|\\_$$  _| \s
                $$ \\$$$  $$ | $$$$$$$ |$$ /  $$ |$$  __$$ |$$ |  \\__| $$ |    $$ |\\$$$$$$\\    $$ |   \s
                $$ |\\$  /$$ |$$  __$$ |$$ |  $$ |$$ |  $$ |$$ |       $$ |$$\\ $$ | \\____$$\\   $$ |$$\\\s
                $$ | \\_/ $$ |\\$$$$$$$ |$$$$$$$  |$$ |  $$ |$$ |       \\$$$$  |$$ |$$$$$$$  |  \\$$$$  |
                \\__|     \\__| \\_______|$$  ____/ \\__|  \\__|\\__|        \\____/ \\__|\\_______/    \\____/\s
                                       $$ |                                                          \s
                                       $$ |                                                          \s
                                       \\__|                                                          \s""");
        }
    }

    @Override
    public void onDisable() {
        if (lockStore != null) {
            lockStore.save();
        }
        if (webServer != null) {
            webServer.stop();
        }
        if (drawingStore != null) {
            for (int mapId : drawingStore.mapIds()) {
                byte[][] pixels = drawingStore.get(mapId);
                if (pixels != null) {
                    saveDrawing(mapId, pixels);
                }
            }
        }
    }

    /**
     * Reloads the config from disk and re-applies the hot-swappable settings
     * without a server restart: host/port + web server, token TTL and rate
     * limit, the paintbrush item, and draft retention. Sessions and drawings
     * in memory are preserved.
     */
    public void reloadConfigAndRebuild() {
        reloadConfig();
        host = getConfig().getString("host", "localhost");
        port = getConfig().getInt("port", 8080);
        long ttlSeconds = getConfig().getLong("token-ttl-seconds", 1209600);
        paintbrush = new Paintbrush(getConfig().getConfigurationSection("paintbrush"));
        tokenManager = new TokenManager(ttlSeconds * 1000,
                getConfig().getInt("rate-limit-per-minute", 30));

        if (webServer != null) {
            try {
                webServer.stop();
            } catch (Exception ignored) {
            }
            webServer = null;
        }
        webServer = new WebServer(this, port);
        try {
            webServer.start();
        } catch (Exception e) {
            getLogger().severe("Failed to restart web server on port " + port + " after reload: " + e.getMessage());
        }

        getLogger().info("MapArtist config reloaded. Host: " + host + ", port: " + port);
    }

    private void loadDrawings() {
        if (!drawingsDir.isDirectory()) {
            return;
        }
        File[] files = drawingsDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                String name = file.getName();
                int mapId = Integer.parseInt(name.substring(0, name.length() - 4));
                byte[] flat = Files.readAllBytes(file.toPath());
                if (flat.length != MAP_SIZE * MAP_SIZE) {
                    getLogger().warning("Ignoring malformed drawing file " + name);
                    continue;
                }
                byte[][] pixels = new byte[MAP_SIZE][MAP_SIZE];
                for (int y = 0; y < MAP_SIZE; y++) {
                    System.arraycopy(flat, y * MAP_SIZE, pixels[y], 0, MAP_SIZE);
                }
                drawingStore.put(mapId, pixels);
                attachRenderer(mapId);
                getLogger().info("Restored drawing for map #" + mapId);
            } catch (NumberFormatException | IOException e) {
                getLogger().warning("Failed to load drawing " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    public void saveDrawing(int mapId, byte[][] pixels) {
        if (!drawingsDir.isDirectory() && !drawingsDir.mkdirs()) {
            getLogger().warning("Could not create drawings folder");
            return;
        }
        byte[] flat = new byte[MAP_SIZE * MAP_SIZE];
        for (int y = 0; y < MAP_SIZE; y++) {
            System.arraycopy(pixels[y], 0, flat, y * MAP_SIZE, MAP_SIZE);
        }
        try {
            Files.write(new File(drawingsDir, mapId + ".dat").toPath(), flat);
        } catch (IOException e) {
            getLogger().warning("Failed to save drawing for map #" + mapId + ": " + e.getMessage());
        }
    }

    public void deleteDrawing(int mapId) {
        File file = new File(drawingsDir, mapId + ".dat");
        if (file.isFile() && !file.delete()) {
            getLogger().warning("Could not delete drawing file for map #" + mapId);
        }
    }

    public void saveDraft(UUID player, int mapId, byte[] flat) {
        if (!draftsDir.isDirectory() && !draftsDir.mkdirs()) {
            getLogger().warning("Could not create drafts folder");
            return;
        }
        File dir = new File(draftsDir, player.toString());
        if (!dir.isDirectory() && !dir.mkdirs()) {
            getLogger().warning("Could not create draft folder for player " + player);
            return;
        }
        try {
            Files.write(new File(dir, mapId + ".dat").toPath(), flat);
        } catch (IOException e) {
            getLogger().warning("Failed to save draft for map #" + mapId + ": " + e.getMessage());
        }
    }

    public byte[] loadDraft(UUID player, int mapId) {
        File file = new File(new File(draftsDir, player.toString()), mapId + ".dat");
        if (!file.isFile()) {
            return null;
        }
        try {
            byte[] flat = Files.readAllBytes(file.toPath());
            return flat.length == MAP_SIZE * MAP_SIZE ? flat : null;
        } catch (IOException e) {
            getLogger().warning("Failed to load draft for map #" + mapId + ": " + e.getMessage());
            return null;
        }
    }

    public long draftLastModified(UUID player, int mapId) {
        File file = new File(new File(draftsDir, player.toString()), mapId + ".dat");
        return file.isFile() ? file.lastModified() : 0L;
    }

    public void deleteDraft(UUID player, int mapId) {
        File file = new File(new File(draftsDir, player.toString()), mapId + ".dat");
        if (file.isFile() && !file.delete()) {
            getLogger().warning("Could not delete draft file for map #" + mapId);
        }
    }

    private void sweepDrafts() {
        if (draftsDir == null || !draftsDir.isDirectory()) {
            return;
        }
        long retentionMillis = getConfig().getLong("draft-retention-days", 21) * 24L * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();
        File[] players = draftsDir.listFiles(File::isDirectory);
        if (players == null) {
            return;
        }
        for (File playerDir : players) {
            File[] files = playerDir.listFiles((dir, name) -> name.endsWith(".dat"));
            if (files == null) {
                continue;
            }
            for (File file : files) {
                if (now - file.lastModified() > retentionMillis && file.delete()) {
                    getLogger().info("Removed stale draft " + file.getName());
                }
            }
        }
    }

    public boolean openDrawingSession(Player player, int mapId) {
        if (!canEdit(mapId, player.getUniqueId())) {
            player.sendMessage(ChatColor.RED
                    + "This map is locked and cannot be edited by anyone except who locked it.");
            return false;
        }
        if (paintbrush == null || !paintbrush.isPaintbrush(player.getInventory().getItemInOffHand())) {
            player.sendMessage(ChatColor.RED
                    + "You need a paintbrush in your off hand to draw on a map.");
            return false;
        }
        MapView view = Bukkit.getMap(mapId);
        if (view == null) {
            getLogger().warning("No map view found for map #" + mapId);
            return false;
        }

        String token = tokenManager.create(player.getUniqueId(), mapId);
        if (token == null) {
            int limit = getConfig().getInt("rate-limit-per-minute", 30);
            player.sendMessage(ChatColor.RED
                    + "You have reached the drawing link limit of " + limit + " per minute. Try again shortly.");
            return false;
        }
        String url = "http://" + host + ":" + port + "/draw?token=" + token;
        MapInteractListener.sendLink(player, url);
        convertMap(mapId);
        return true;
    }

    public TokenManager getTokenManager() {
        return tokenManager;
    }

    /**
     * Opens a wall (multi-map) drawing session spanning the whole detected grid.
     * Only cells the player may edit (they own / aren't locked to someone else)
     * are part of the session; locked cells are omitted entirely. The frames
     * must be uniformly rotated (north-aligned), otherwise the wall is refused.
     * Returns true if a session link was created.
     */
    public boolean openWallSession(Player player, MapWallDetector.Grid grid, Location anchor) {
        if (grid.rotation() == null) {
            player.sendMessage(ChatColor.GOLD + "MapArtist: " + ChatColor.RED
                    + "This map grid has mixed rotations. Align all frames (map-north up) before editing as a wall. (you may want to break&replace them)");
            return false;
        }
        int width = grid.width();
        int height = grid.height();
        List<Integer> ids = grid.mapIds();
        java.util.List<TokenManager.Cell> cells = new java.util.ArrayList<>();
        java.util.List<Integer> editable = new java.util.ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            int mapId = ids.get(i);
            if (canEdit(mapId, player.getUniqueId())) {
                cells.add(new TokenManager.Cell(mapId, i / width, i % width));
                editable.add(mapId);
            }
        }
        if (cells.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "MapArtist: " + ChatColor.RED
                    + "You don't own any of the maps in this grid.");
            return false;
        }
        String token = tokenManager.createGrid(player.getUniqueId(),
                new TokenManager.GridSession(width, height, cells,
                        anchor.getWorld().getName(), anchor.getX(), anchor.getY(), anchor.getZ()));
        if (token == null) {
            int limit = getConfig().getInt("rate-limit-per-minute", 30);
            player.sendMessage(ChatColor.RED
                    + "You have reached the drawing link limit of " + limit + " per minute. Try again shortly.");
            return false;
        }
        for (int mapId : editable) {
            convertMap(mapId);
        }
        String url = "http://" + host + ":" + port + "/draw?token=" + token;
        MapInteractListener.sendLink(player, url);
        return true;
    }

    public Paintbrush getPaintbrush() {
        return paintbrush;
    }

    public DrawingStore getDrawingStore() {
        return drawingStore;
    }

    public MapLockStore getLockStore() {
        return lockStore;
    }

    /** Whether the player may open/edit the given map, respecting its lock. */
    public boolean canEdit(int mapId, UUID player) {
        if (lockStore == null || !lockStore.isLocked(mapId)) {
            return true;
        }
        return lockStore.ownerOf(mapId).equals(player);
    }

    public boolean isAdmin(UUID player) {
        org.bukkit.entity.Player p = Bukkit.getPlayer(player);
        return p != null && p.hasPermission("mapartist.admin");
    }

    public EditLogger getEditLogger() {
        return editLogger;
    }

    /**
     * Records an edit (or conversion) of one or more maps by a player, if
     * edit logging is enabled. Multi-map walls are logged as a single entry
     * listing every map ID touched.
     */
    public void logEdits(UUID playerId, java.util.List<Integer> mapIds) {
        if (editLogger != null) {
            editLogger.log(playerId, mapIds);
        }
    }

    public void attachRenderer(int mapId) {
        MapView view = Bukkit.getMap(mapId);
        if (view == null) {
            getLogger().warning("No map view found for map #" + mapId);
            return;
        }
        attachRenderer(view);
    }

    /**
     * Synchronizes the visibility of every item frame holding one of the given
     * drawing maps (hides frames whose map now shows content) and pushes the
     * updated map pixels to all online players. Used after a wall submit so the
     * frames hide immediately instead of waiting for the player to right-click
     * each map.
     */
    public void refreshFrames(String worldName, List<Integer> mapIds) {
        java.util.Set<Integer> ids = new java.util.HashSet<>(mapIds);
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        // Entity queries must run on the main thread; the web submit happens on
        // a background thread, so defer the frame visibility sync.
        final World w = world;
        getServer().getScheduler().runTask(this, () -> {
            for (Entity entity : w.getEntities()) {
                if (entity instanceof ItemFrame frame) {
                    ItemStack item = frame.getItem();
                    MapView view = MapWallDetector.mapViewOf(item);
                    if (view == null || !ids.contains(view.getId())) {
                        continue;
                    }
                    frame.setVisible(!MapWallDetector.shouldHideFrame(item, drawingStore));
                    w.getPlayers().forEach(p -> {
                        try {
                            p.sendMap(view);
                        } catch (Exception ignored) {
                        }
                    });
                }
            }
        });
    }

    /**
     * Converts a vanilla map into a MapArtist drawing map by attaching the
     * capture + drawing renderers (idempotent: already-drawing maps are left
     * untouched). This is what "opening a map with a paintbrush" does, whether
     * the map is held in hand or sits in an item frame on a wall.
     */
    public void convertMap(int mapId) {
        MapView view = Bukkit.getMap(mapId);
        if (view == null) {
            getLogger().warning("No map view found for map #" + mapId);
            return;
        }
        if (view.getRenderers().stream().anyMatch(r -> r instanceof DrawingRenderer)) {
            return;
        }
        prepareMap(view, true);
    }

    /**
     * Converts the map this player still holds into a drawing map, in place,
     * retaining the current vanilla pixels as its base. Used by the chat
     * confirmation flow (the map is converted in the hand it was held in).
     * Returns false if the player no longer holds the map or has changed it.
     */
    public boolean confirmConversion(Player player) {
        PendingConversion pending = pendingConversion(player.getUniqueId());
        if (pending == null) {
            player.sendMessage(ChatColor.RED + "You don't have a pending map conversion.");
            return false;
        }
        pendingConversions.remove(player.getUniqueId());

        ItemStack main = player.getInventory().getItemInMainHand();
        if (main.getType() != Material.FILLED_MAP
                || !(main.getItemMeta() instanceof org.bukkit.inventory.meta.MapMeta meta)
                || !meta.hasMapView() || meta.getMapId() != pending.mapId()) {
            player.sendMessage(ChatColor.RED + "The map you're holding changed. Please sneak-right-click the map with the paintbrush to try again.");
            return false;
        }
        if (paintbrush == null || !paintbrush.isPaintbrush(player.getInventory().getItemInOffHand())) {
            player.sendMessage(ChatColor.RED + "You need the paintbrush in your off hand to convert the map.");
            return false;
        }

        convertMap(pending.mapId());
        player.sendMessage(ChatColor.GREEN + "Map #" + pending.mapId() + " converted into a MapArtist drawing map. Right-click it with a paintbrush to draw.");
        return true;
    }

    public void cancelConversion(Player player) {
        pendingConversions.remove(player.getUniqueId());
        player.sendMessage(ChatColor.GRAY + "Map conversion cancelled.");
    }

    public void attachRenderer(MapView view) {
        prepareMap(view, false);
    }

    public void prepareMap(MapView view, boolean captureBase) {
        getServer().getScheduler().runTask(this, () -> applyRenderers(view, captureBase));
    }

    private void applyRenderers(MapView view, boolean captureBase) {
        boolean hasCapture = view.getRenderers().stream()
                .anyMatch(r -> r instanceof BaseCaptureRenderer);

        // Drop every renderer that isn't ours. This removes the vanilla terrain
        // renderer, so maps only ever show what was drawn on them.
        for (MapRenderer renderer : view.getRenderers()) {
            view.removeRenderer(renderer);
        }

        if (captureBase || hasCapture) {
            drawingStore.clearBase(view.getId());
            view.addRenderer(new BaseCaptureRenderer(drawingStore));
        }
        view.addRenderer(new DrawingRenderer(drawingStore));

        // Push the updated pixels to clients immediately; erased pixels would
        // otherwise only appear after a server restart.
        for (Player player : getServer().getOnlinePlayers()) {
            try {
                player.sendMap(view);
            } catch (Exception ignored) {
                // Not all implementations support sending arbitrary maps.
            }
        }
    }
}

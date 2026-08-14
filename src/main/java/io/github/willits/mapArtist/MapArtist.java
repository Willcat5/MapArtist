package io.github.willits.mapArtist;

import io.github.willits.mapArtist.web.WebServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

public final class MapArtist extends JavaPlugin {

    private static final int MAP_SIZE = 128;

    private WebServer webServer;
    private TokenManager tokenManager;
    private DrawingStore drawingStore;
    private String host;
    private int port;
    private File drawingsDir;
    private File draftsDir;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        host = getConfig().getString("host", "localhost");
        port = getConfig().getInt("port", 8080);
        long ttlSeconds = getConfig().getLong("token-ttl-seconds", 1209600);

        tokenManager = new TokenManager(ttlSeconds * 1000);
        drawingStore = new DrawingStore();
        drawingsDir = new File(getDataFolder(), "drawings");
        draftsDir = new File(getDataFolder(), "drafts");

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

        getLogger().info("MapArtist enabled. Public host configured as: " + host);
        getLogger().info("┏┳┓┏━┓┏━┓   ┏━┓┏━┓╺┳╸╻┏━┓╺┳╸");
        getLogger().info("┃┃┃┣━┫┣━┛   ┣━┫┣┳┛ ┃ ┃┗━┓ ┃ ");
        getLogger().info("╹ ╹╹ ╹╹    ╹ ╹╹┗╸ ╹ ╹┗━┛ ╹ ");
    }

    @Override
    public void onDisable() {
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

    public void openDrawingSession(Player player, int mapId) {
        MapView view = Bukkit.getMap(mapId);
        if (view == null) {
            getLogger().warning("No map view found for map #" + mapId);
            return;
        }

        String token = tokenManager.create(player.getUniqueId(), mapId);
        String url = "http://" + host + ":" + port + "/draw?token=" + token;
        MapInteractListener.sendLink(player, url);
        prepareMap(view, true);
    }

    public TokenManager getTokenManager() {
        return tokenManager;
    }

    public DrawingStore getDrawingStore() {
        return drawingStore;
    }

    public void attachRenderer(int mapId) {
        MapView view = Bukkit.getMap(mapId);
        if (view == null) {
            getLogger().warning("No map view found for map #" + mapId);
            return;
        }
        attachRenderer(view);
    }

    public void attachRenderer(MapView view) {
        prepareMap(view, false);
    }

    public void prepareMap(MapView view, boolean captureBase) {
        getServer().getScheduler().runTask(this, () -> applyRenderers(view, captureBase));
    }

    public void prepareNewMap(MapView view) {
        applyRenderers(view, false);
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

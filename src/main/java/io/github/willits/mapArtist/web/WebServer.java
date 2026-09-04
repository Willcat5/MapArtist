package io.github.willits.mapArtist.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.willits.mapArtist.DrawingStore;
import io.github.willits.mapArtist.MapColors;
import io.github.willits.mapArtist.MapLockStore;
import io.github.willits.mapArtist.TokenManager;
import io.github.willits.mapArtist.MapArtist;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class WebServer {

    private static final String PALETTE_PLACEHOLDER = "/*__MAP_PALETTE__*/[]";

    private final MapArtist plugin;
    private final Logger logger;
    private final int port;
    private final String paletteJson;
    private HttpServer server;
    private ExecutorService executor;

    public WebServer(MapArtist plugin, int port) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.port = port;
        this.paletteJson = buildPaletteJson();
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", this::handleHealth);
        server.createContext("/draw", this::handleDraw);
        server.createContext("/base", this::handleBase);
        server.createContext("/submit", this::handleSubmit);
        server.createContext("/lock", this::handleLock);
        server.createContext("/export", this::handleExport);
        server.createContext("/import", this::handleImport);
        server.createContext("/draft", this::handleDraft);
        server.createContext("/img/", this::handleImage);
        server.createContext("/fonts/", this::handleFont);
        server.createContext("/", this::handleRoot);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        logger.info("Web server listening on port " + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        byte[] body = "MapArtist web server is running.".getBytes(StandardCharsets.UTF_8);
        respond(exchange, 200, "text/plain; charset=utf-8", body);
    }

    private void handleImage(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith("/img/")) {
            respond(exchange, 400, "text/plain; charset=utf-8", "Bad request".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String rel = path.substring("/img/".length());
        if (rel.isEmpty() || rel.contains("..") || !rel.matches("[\\w./-]+")) {
            respond(exchange, 400, "text/plain; charset=utf-8", "Bad request".getBytes(StandardCharsets.UTF_8));
            return;
        }
        byte[] body;
        try (InputStream in = WebServer.class.getResourceAsStream("/web/" + rel)) {
            if (in == null) {
                respond(exchange, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            body = in.readAllBytes();
        }
        String contentType = rel.endsWith(".svg") ? "image/svg+xml" : "image/png";
        respond(exchange, 200, contentType, body);
    }

    private void handleFont(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith("/fonts/")) {
            respond(exchange, 400, "text/plain; charset=utf-8", "Bad request".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String rel = path.substring("/fonts/".length());
        if (rel.isEmpty() || rel.contains("..") || !rel.matches("[\\w.-]+")) {
            respond(exchange, 400, "text/plain; charset=utf-8", "Bad request".getBytes(StandardCharsets.UTF_8));
            return;
        }
        byte[] body;
        try (InputStream in = WebServer.class.getResourceAsStream("/web/fonts/" + rel)) {
            if (in == null) {
                respond(exchange, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            body = in.readAllBytes();
        }
        String contentType = rel.endsWith(".woff2") ? "font/woff2"
                : rel.endsWith(".woff") ? "font/woff"
                : rel.endsWith(".otf") ? "font/otf"
                : rel.endsWith(".png") ? "image/png"
                : rel.endsWith(".fnt") ? "text/plain; charset=utf-8"
                : "font/ttf";
        respond(exchange, 200, contentType, body);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        byte[] body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        respond(exchange, 200, "application/json; charset=utf-8", body);
    }

    private void handleDraw(HttpExchange exchange) throws IOException {
        byte[] body;
        try (InputStream in = WebServer.class.getResourceAsStream("/web/draw.html")) {
            if (in == null) {
                respond(exchange, 500, "text/plain; charset=utf-8",
                        "draw.html not found in plugin jar".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String html = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace(PALETTE_PLACEHOLDER, paletteJson);
            body = html.getBytes(StandardCharsets.UTF_8);
        }
        respond(exchange, 200, "text/html; charset=utf-8", body);
    }

    private void handleBase(HttpExchange exchange) throws IOException {
        try {
            String token = queryParam(exchange, "token");
            if (token == null) {
                respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Missing token\"}");
                return;
            }
            TokenManager.Session session = plugin.getTokenManager().peek(token);
            if (session == null) {
                respondJson(exchange, 403, "{\"status\":\"error\",\"message\":\"Invalid or expired token\"}");
                return;
            }
            if (session.grid() != null) {
                handleWallBase(exchange, token, session);
                return;
            }
            DrawingStore store = plugin.getDrawingStore();
            int mapId = session.mapId();
            TokenManager tokenManager = plugin.getTokenManager();

            byte[][] base = store.getBase(mapId);
            byte[][] drawing = store.get(mapId);

            if (base == null && drawing == null) {
                tokenManager.setShown(token, null);
                if (!store.isBaseCaptured(mapId)) {
                    respondJson(exchange, 200, "{\"captured\":false}");
                } else {
                    respondJson(exchange, 200, "{\"captured\":true,\"base\":null}");
                }
                return;
            }

            byte[][] composite = merge(base, drawing);
            boolean hasContent = false;
            outer:
            for (byte[] row : composite) {
                for (byte p : row) {
                    if ((p & 0xFF) != 0) {
                        hasContent = true;
                        break outer;
                    }
                }
            }
            if (!hasContent) {
                tokenManager.setShown(token, null);
                respondJson(exchange, 200, "{\"captured\":true,\"base\":null}");
                return;
            }
            tokenManager.setShown(token, composite);
            byte[] flat = new byte[128 * 128];
            for (int y = 0; y < 128; y++) {
                System.arraycopy(composite[y], 0, flat, y * 128, 128);
            }
            String encoded = Base64.getEncoder().encodeToString(flat);
            respondJson(exchange, 200, "{\"captured\":true,\"base\":\"" + encoded + "\"}");
        } catch (Exception e) {
            logger.warning("Base fetch failed: " + e.getMessage());
            respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Bad request\"}");
        }
    }

    /**
     * Serves the whole wall grid as one large composite image, plus the grid
     * geometry (width/height in cells and the editable cell layout) so the
     * editor knows how big to make its canvas. Omitted (locked) cells are left
     * transparent. The flat image is WIDTH*HEIGHT pixels (W=width*128, H=height*128).
     */
    private void handleWallBase(HttpExchange exchange, String token, TokenManager.Session session) throws IOException {
        TokenManager.GridSession grid = session.grid();
        DrawingStore store = plugin.getDrawingStore();
        final int MAP = 128;
        int pixelsW = grid.width() * MAP;
        int pixelsH = grid.height() * MAP;

        byte[] flat = new byte[pixelsW * pixelsH];
        boolean anyContent = false;
        for (TokenManager.Cell cell : grid.cells()) {
            byte[][] composite = merge(store.getBase(cell.mapId()), store.get(cell.mapId()));
            if (composite != null) {
                for (int y = 0; y < MAP; y++) {
                    for (int x = 0; x < MAP; x++) {
                        byte p = composite[y][x];
                        if ((p & 0xFF) != 0) {
                            anyContent = true;
                        }
                        int fx = cell.col() * MAP + x;
                        int fy = cell.row() * MAP + y;
                        flat[fy * pixelsW + fx] = p;
                    }
                }
            }
        }
        plugin.getTokenManager().setShown(token, anyContent ? toByte2(pixelsW, pixelsH, flat) : null);

        StringBuilder cellsJson = new StringBuilder("[");
        for (int i = 0; i < grid.cells().size(); i++) {
            TokenManager.Cell c = grid.cells().get(i);
            if (i > 0) {
                cellsJson.append(',');
            }
            cellsJson.append('[').append(c.mapId()).append(',').append(c.row()).append(',').append(c.col()).append(']');
        }
        cellsJson.append(']');

        String encoded = Base64.getEncoder().encodeToString(flat);
        respondJson(exchange, 200, "{\"captured\":true,\"wall\":true,\"width\":"
                + grid.width() + ",\"height\":" + grid.height()
                + ",\"cells\":" + cellsJson + ",\"base\":\"" + encoded + "\"}");
    }

    private static byte[][] merge(byte[][] base, byte[][] drawing) {
        byte[][] out = new byte[128][128];
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                byte p = 0;
                if (drawing != null && (drawing[y][x] & 0xFF) != 0) {
                    p = drawing[y][x];
                } else if (base != null) {
                    p = base[y][x];
                }
                out[y][x] = p;
            }
        }
        return out;
    }

    private static byte[][] mergeEdits(byte[][] submitted, byte[][] shown, byte[][] oldStrokes) {
        byte[][] out = new byte[128][128];
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                byte sub = submitted[y][x];
                if (sub == 0) {
                    out[y][x] = 0;
                    continue;
                }
                byte sh = (shown == null) ? 0 : shown[y][x];
                if (sub == sh) {
                    out[y][x] = (oldStrokes == null) ? 0 : oldStrokes[y][x];
                } else {
                    out[y][x] = sub;
                }
            }
        }
        return out;
    }

    private static byte[][] toByte2(int w, int h, byte[] flat) {
        byte[][] out = new byte[h][w];
        for (int y = 0; y < h; y++) {
            System.arraycopy(flat, y * w, out[y], 0, w);
        }
        return out;
    }

    private void handleSubmit(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                respondJson(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
                return;
            }
            String token = queryParam(exchange, "token");
            if (token == null) {
                respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Missing token\"}");
                return;
            }
            TokenManager tokenManager = plugin.getTokenManager();
            byte[][] shown = tokenManager.getShown(token);
            TokenManager.Session session = tokenManager.peek(token);
            if (session == null) {
                respondJson(exchange, 403, "{\"status\":\"error\",\"message\":\"Invalid or expired token\"}");
                return;
            }

            if (session.grid() != null) {
                handleWallSubmit(exchange, token, session, shown);
                return;
            }

            int mapId = session.mapId();
            if (!plugin.canEdit(mapId, session.player())) {
                respondJson(exchange, 403, "{\"status\":\"error\",\"message\":\"This map is locked and cannot be edited by anyone except who locked it.\"}");
                return;
            }
            if (plugin.getConfig().getBoolean("require-holding-to-submit", true)
                    && !isHoldingMap(session.player(), mapId)) {
                respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"You must be holding the map in your hand to submit changes\"}");
                return;
            }

            session = tokenManager.consume(token);
            if (session == null) {
                respondJson(exchange, 403, "{\"status\":\"error\",\"message\":\"Session expired\"}");
                return;
            }

            String body = new String(readBody(exchange), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            String imageData = root.get("image").getAsString();
            byte[] png = Base64.getDecoder().decode(imageData);

            BufferedImage image;
            try (InputStream in = new ByteArrayInputStream(png)) {
                image = javax.imageio.ImageIO.read(in);
            }
            if (image == null) {
                respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Not a valid image\"}");
                return;
            }

            DrawingStore store = plugin.getDrawingStore();
            byte[][] submitted = toPaletteBytes(image);
            byte[][] oldStrokes = store.get(session.mapId());
            byte[][] strokes = mergeEdits(submitted, shown, oldStrokes);

            int colored = 0;
            for (byte[] row : strokes) {
                for (byte p : row) {
                    if ((p & 0xFF) != 0) colored++;
                }
            }
            if (colored == 0) {
                store.remove(session.mapId());
                plugin.deleteDrawing(session.mapId());
            } else {
                store.put(session.mapId(), strokes);
                plugin.saveDrawing(session.mapId(), strokes);
            }
            plugin.attachRenderer(session.mapId());
            plugin.deleteDraft(session.player(), session.mapId());
            plugin.logEdits(session.player(), List.of(session.mapId()));

            logger.info("Saved drawing for map #" + session.mapId()
                    + " (" + colored + " colored pixels)");

            respondJson(exchange, 200, "{\"status\":\"ok\"}");
        } catch (Exception e) {
            logger.warning("Submit failed: " + e.getMessage());
            respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Bad request\"}");
        }
    }

    /**
     * Saves a wall (multi-map) submission. The incoming image is the whole grid
     * merged into one W*128 x H*128 canvas. It is sliced back into per-cell
     * 128x128 tiles and each editable cell is written. Locked cells were omitted
     * from the session and never appear in the image, so they are not touched.
     * Instead of the "holding the map" check, the player must be within
     * wall-proximity-blocks of the wall center. The session is consumed (closed).
     */
    private void handleWallSubmit(HttpExchange exchange, String token, TokenManager.Session session, byte[][] shown)
            throws IOException {
        TokenManager.GridSession grid = session.grid();
        if (!withinProximity(session.player(), grid)) {
            respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"You must be within " 
                    + plugin.getConfig().getInt("wall-proximity-blocks", 10)
                    + " blocks of the map wall to submit changes\"}");
            return;
        }
        TokenManager.Session consumed = plugin.getTokenManager().consume(token);
        if (consumed == null) {
            respondJson(exchange, 403, "{\"status\":\"error\",\"message\":\"Session expired\"}");
            return;
        }

        final int MAP = 128;
        int pixelsW = grid.width() * MAP;
        int pixelsH = grid.height() * MAP;

        String body = new String(readBody(exchange), StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        String imageData = root.get("image").getAsString();
        byte[] png = Base64.getDecoder().decode(imageData);

        BufferedImage image;
        try (InputStream in = new ByteArrayInputStream(png)) {
            image = javax.imageio.ImageIO.read(in);
        }
        if (image == null) {
            respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Not a valid image\"}");
            return;
        }
        if (image.getWidth() != pixelsW || image.getHeight() != pixelsH) {
            respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Image size does not match the wall grid\"}");
            return;
        }

        byte[][] submitted = toPaletteBytesAt(image, pixelsW, pixelsH);
        DrawingStore store = plugin.getDrawingStore();
        java.util.List<Integer> savedIds = new java.util.ArrayList<>();
        for (TokenManager.Cell cell : grid.cells()) {
            byte[][] cellPixels = new byte[MAP][MAP];
            for (int y = 0; y < MAP; y++) {
                System.arraycopy(submitted[y + cell.row() * MAP], cell.col() * MAP, cellPixels[y], 0, MAP);
            }
            byte[][] cellShown = sliceShown(shown, grid, cell, MAP);
            byte[][] oldStrokes = store.get(cell.mapId());
            byte[][] strokes = mergeEdits(cellPixels, cellShown, oldStrokes);

            int colored = 0;
            for (byte[] row : strokes) {
                for (byte p : row) {
                    if ((p & 0xFF) != 0) colored++;
                }
            }
            if (colored == 0) {
                store.remove(cell.mapId());
                plugin.deleteDrawing(cell.mapId());
            } else {
                store.put(cell.mapId(), strokes);
                plugin.saveDrawing(cell.mapId(), strokes);
            }
            plugin.attachRenderer(cell.mapId());
            plugin.deleteDraft(session.player(), cell.mapId());
            savedIds.add(cell.mapId());
            logger.info("Saved wall cell map #" + cell.mapId() + " (" + colored + " colored pixels)");
        }
        plugin.logEdits(session.player(), savedIds);
        plugin.refreshFrames(grid.world(), savedIds);
        respondJson(exchange, 200, "{\"status\":\"ok\"}");
    }

    /** Slices a cell-sized region out of the big shown image (or null). */
    private static byte[][] sliceShown(byte[][] shown, TokenManager.GridSession grid, TokenManager.Cell cell, int map) {
        if (shown == null) {
            return null;
        }
        byte[][] out = new byte[map][map];
        for (int y = 0; y < map; y++) {
            System.arraycopy(shown[y + cell.row() * map], cell.col() * map, out[y], 0, map);
        }
        return out;
    }

    /** Whether the player is within wall-proximity-blocks of the grid center. */
    private boolean withinProximity(UUID playerId, TokenManager.GridSession grid) {
        int radius = plugin.getConfig().getInt("wall-proximity-blocks", 10);
        if (radius <= 0) {
            return true;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.getWorld().getName().equals(grid.world())) {
            return false;
        }
        org.bukkit.Location loc = player.getLocation();
        double dx = loc.getX() - grid.centerX();
        double dy = loc.getY() - grid.centerY();
        double dz = loc.getZ() - grid.centerZ();
        return (dx * dx + dy * dy + dz * dz) <= (double) radius * radius;
    }

    /**
     * Reads or toggles the lock on the session's map. GET returns the current
     * lock state and owner name. POST with {"locked":true} locks the map to the
     * session's player; {"locked":false} unlocks it in place of the owner.
     * Only the current owner (or an admin) may change the lock, enforced here
     * server-side.
     */
    private void handleLock(HttpExchange exchange) throws IOException {
        try {
            String token = queryParam(exchange, "token");
            if (token == null) {
                respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Missing token\"}");
                return;
            }
            TokenManager.Session session = plugin.getTokenManager().peek(token);
            if (session == null) {
                respondJson(exchange, 403, "{\"status\":\"error\",\"message\":\"Invalid or expired token\"}");
                return;
            }
            if (session.grid() != null) {
                handleWallLock(exchange, session);
                return;
            }
            int mapId = session.mapId();
            MapLockStore locks = plugin.getLockStore();
            boolean locked = locks.isLocked(mapId);
            String ownerName = locks.ownerName(mapId);

            if (!"POST".equals(exchange.getRequestMethod())) {
                respondJson(exchange, 200, "{\"locked\":" + locked
                        + ",\"owner\":" + (ownerName == null ? "null" : "\"" + ownerName + "\"")
                        + ",\"canToggle\":" + plugin.canEdit(mapId, session.player()) + "}");
                return;
            }

            if (!plugin.canEdit(mapId, session.player())) {
                respondJson(exchange, 403, "{\"status\":\"error\",\"message\":\"This map is locked and cannot be edited by anyone except who locked it.\"}");
                return;
            }

            String body = new String(readBody(exchange), StandardCharsets.UTF_8);
            boolean wantLocked = JsonParser.parseString(body).getAsJsonObject().get("locked").getAsBoolean();
            if (wantLocked) {
                locks.lock(mapId, session.player(), session.player());
            } else {
                locks.unlock(mapId, session.player());
            }
            respondJson(exchange, 200, "{\"status\":\"ok\",\"locked\":" + wantLocked + "}");
        } catch (Exception e) {
            logger.warning("Lock request failed: " + e.getMessage());
            respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Bad request\"}");
        }
    }

    /**
     * Handles locking for a wall (multi-map) session. Editable cells in a wall
     * session are only those the player can edit, so every cell is either
     * unowned (unlocked) or locked to this editor. A GET reports the aggregate
     * state (all owned-by-me, all unowned, or mixed) so the editor can show a
     * lock, open-lock, or striped indicator. A POST {@code locked:true} locks
     * every unowned cell (claiming it); {@code locked:false} unlocks every cell
     * owned by the editor. Cells owned by someone else can't appear and are
     * never touched.
     */
    private void handleWallLock(HttpExchange exchange, TokenManager.Session session) throws IOException {
        TokenManager.GridSession grid = session.grid();
        MapLockStore locks = plugin.getLockStore();
        java.util.UUID me = session.player();

        int total = 0, mine = 0, unowned = 0;
        for (TokenManager.Cell cell : grid.cells()) {
            total++;
            java.util.UUID owner = locks.ownerOf(cell.mapId());
            if (owner == null) {
                unowned++;
            } else if (owner.equals(me)) {
                mine++;
            }
        }
        boolean allMine = total > 0 && mine == total;
        boolean allUnowned = total > 0 && unowned == total;
        boolean mixed = total > 0 && !allMine && !allUnowned;
        boolean canToggle = unowned > 0 || mine > 0;

        if (!"POST".equals(exchange.getRequestMethod())) {
            respondJson(exchange, 200, "{\"wall\":true,\"locked\":" + allMine
                    + ",\"unlocked\":" + allUnowned + ",\"mixed\":" + mixed
                    + ",\"canToggle\":" + canToggle
                    + ",\"total\":" + total + ",\"owned\":" + mine + ",\"unowned\":" + unowned + "}");
            return;
        }

        String body = new String(readBody(exchange), StandardCharsets.UTF_8);
        boolean wantLocked = JsonParser.parseString(body).getAsJsonObject().get("locked").getAsBoolean();
        if (wantLocked) {
            for (TokenManager.Cell cell : grid.cells()) {
                if (locks.ownerOf(cell.mapId()) == null) {
                    locks.lock(cell.mapId(), me, me);
                }
            }
        } else {
            for (TokenManager.Cell cell : grid.cells()) {
                java.util.UUID owner = locks.ownerOf(cell.mapId());
                if (owner != null && owner.equals(me)) {
                    locks.unlock(cell.mapId(), me);
                }
            }
        }
        respondJson(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void handleExport(HttpExchange exchange) throws IOException {
        try {
            String token = queryParam(exchange, "token");
            if (token == null) {
                respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Missing token\"}");
                return;
            }
            TokenManager.Session session = plugin.getTokenManager().peek(token);
            if (session == null) {
                respondJson(exchange, 403, "{\"status\":\"error\",\"message\":\"Invalid or expired token\"}");
                return;
            }
            if (session.grid() != null) {
                handleWallExport(exchange, session);
                return;
            }
            byte[][] pixels = plugin.getDrawingStore().get(session.mapId());
            if (pixels == null) {
                respondJson(exchange, 404, "{\"status\":\"error\",\"message\":\"No drawing saved for this map. Upload it to the server first.\"}");
                return;
            }
            byte[] flat = new byte[128 * 128];
            for (int y = 0; y < 128; y++) {
                System.arraycopy(pixels[y], 0, flat, y * 128, 128);
            }
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"map-" + session.mapId() + ".dat\"");
            respond(exchange, 200, "application/octet-stream", flat);
        } catch (Exception e) {
            logger.warning("Export failed: " + e.getMessage());
            respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Bad request\"}");
        }
    }

    /**
     * Exports a wall (multi-map) session as a zip: one .dat per editable cell
     * plus an "arrangement.json" describing the grid layout and the cell's map
     * ids in row-major order. The zip can be re-imported to restore the wall.
     */
    private void handleWallExport(HttpExchange exchange, TokenManager.Session session) throws IOException {
        TokenManager.GridSession grid = session.grid();
        DrawingStore store = plugin.getDrawingStore();
        java.util.List<TokenManager.Cell> cells = grid.cells();

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            StringBuilder sb = new StringBuilder("{\"w\":").append(grid.width())
                    .append(",\"h\":").append(grid.height()).append(",\"mapIds\":[");
            for (int i = 0; i < cells.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(cells.get(i).mapId());
            }
            sb.append("]}");
            zos.putNextEntry(new java.util.zip.ZipEntry("arrangement.json"));
            zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            for (int i = 0; i < cells.size(); i++) {
                byte[][] pixels = store.get(cells.get(i).mapId());
                byte[] flat = new byte[128 * 128];
                if (pixels != null) {
                    for (int y = 0; y < 128; y++) {
                        System.arraycopy(pixels[y], 0, flat, y * 128, 128);
                    }
                }
                zos.putNextEntry(new java.util.zip.ZipEntry("cell_" + i + ".dat"));
                zos.write(flat);
                zos.closeEntry();
            }
        }
        byte[] zip = baos.toByteArray();
        exchange.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"wall-" + grid.width() + "x" + grid.height() + ".zip\"");
        respond(exchange, 200, "application/zip", zip);
    }

    private void handleImport(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                respondJson(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
                return;
            }
            String token = queryParam(exchange, "token");
            if (token == null) {
                respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Missing token\"}");
                return;
            }
            TokenManager tokenManager = plugin.getTokenManager();
            TokenManager.Session session = tokenManager.peek(token);
            if (session == null) {
                respondJson(exchange, 403, "{\"status\":\"error\",\"message\":\"Invalid or expired token\"}");
                return;
            }
            if (session.grid() != null) {
                handleWallImport(exchange, session);
                return;
            }
            int mapId = session.mapId();
            if (!plugin.canEdit(mapId, session.player())) {
                respondJson(exchange, 403, "{\"status\":\"error\",\"message\":\"This map is locked and cannot be edited by anyone except who locked it.\"}");
                return;
            }

            byte[] flat = readBody(exchange);
            if (flat.length != 128 * 128) {
                respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Invalid .dat file: expected "
                        + (128 * 128) + " bytes, got " + flat.length + "\"}");
                return;
            }

            byte[][] pixels = new byte[128][128];
            for (int y = 0; y < 128; y++) {
                System.arraycopy(flat, y * 128, pixels[y], 0, 128);
            }

            DrawingStore store = plugin.getDrawingStore();
            store.put(mapId, pixels);
            plugin.saveDrawing(mapId, pixels);
            plugin.attachRenderer(mapId);
            plugin.deleteDraft(session.player(), mapId);
            plugin.logEdits(session.player(), List.of(mapId));

            byte[][] composite = merge(store.getBase(mapId), pixels);
            tokenManager.setShown(token, hasColor(composite) ? composite : null);

            logger.info("Imported drawing for map #" + mapId);

            respondJson(exchange, 200, "{\"status\":\"ok\"}");
        } catch (Exception e) {
            logger.warning("Import failed: " + e.getMessage());
            respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Bad request\"}");
        }
    }

    /**
     * Imports a wall (multi-map) session from a zip produced by
     * {@link #handleWallExport}. The zip contains "arrangement.json" (grid
     * dims + map ids in row-major order) and one "cell_N.dat" per editable
     * cell. Each .dat is applied to the session's cell in matching order. The
     * imported mapIds must line up with the current session cells.
     */
    private void handleWallImport(HttpExchange exchange, TokenManager.Session session) throws IOException {
        TokenManager.GridSession grid = session.grid();
        byte[] zipBytes = readBody(exchange);
        java.util.Map<String, byte[]> entries = new java.util.HashMap<>();
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                entries.put(entry.getName(), out.toByteArray());
            }
        } catch (java.util.zip.ZipException e) {
            respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Not a valid wall .zip file\"}");
            return;
        }

        byte[] arrBytes = entries.get("arrangement.json");
        if (arrBytes == null) {
            respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Missing arrangement.json in zip\"}");
            return;
        }
        JsonObject arrangement;
        try {
            arrangement = JsonParser.parseString(new String(arrBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Invalid arrangement.json in zip\"}");
            return;
        }
        int count = arrangement.getAsJsonArray("mapIds").size();
        if (count != grid.cells().size()) {
            respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Wall zip has " + count
                    + " cells but this session has " + grid.cells().size() + ".\"}");
            return;
        }

        DrawingStore store = plugin.getDrawingStore();
        java.util.List<Integer> savedIds = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            byte[] dat = entries.get("cell_" + i + ".dat");
            if (dat == null || dat.length != 128 * 128) {
                respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Missing or invalid cell_" + i + ".dat in zip\"}");
                return;
            }
            byte[][] pixels = new byte[128][128];
            for (int y = 0; y < 128; y++) {
                System.arraycopy(dat, y * 128, pixels[y], 0, 128);
            }
            int mapId = grid.cells().get(i).mapId();
            store.put(mapId, pixels);
            plugin.saveDrawing(mapId, pixels);
            plugin.attachRenderer(mapId);
            plugin.deleteDraft(session.player(), mapId);
            savedIds.add(mapId);
            logger.info("Imported wall cell map #" + mapId);
        }
        plugin.logEdits(session.player(), savedIds);
        plugin.refreshFrames(grid.world(), savedIds);
        respondJson(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void handleDraft(HttpExchange exchange) throws IOException {
        try {
            String token = queryParam(exchange, "token");
            if (token == null) {
                respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Missing token\"}");
                return;
            }
            TokenManager.Session session = plugin.getTokenManager().peek(token);
            if (session == null) {
                respondJson(exchange, 403, "{\"status\":\"error\",\"message\":\"Invalid or expired token\"}");
                return;
            }
            if (session.grid() != null) {
                respondJson(exchange, 200, "{\"exists\":false}");
                return;
            }
            UUID player = session.player();
            int mapId = session.mapId();

            switch (exchange.getRequestMethod()) {
                case "GET" -> {
                    byte[] flat = plugin.loadDraft(player, mapId);
                    if (flat == null) {
                        respondJson(exchange, 200, "{\"exists\":false}");
                        return;
                    }
                    String encoded = Base64.getEncoder().encodeToString(flat);
                    respondJson(exchange, 200, "{\"exists\":true,\"timestamp\":"
                            + plugin.draftLastModified(player, mapId)
                            + ",\"base\":\"" + encoded + "\"}");
                }
                case "POST" -> {
                    String body = new String(readBody(exchange), StandardCharsets.UTF_8);
                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                    String imageData = root.get("image").getAsString();
                    byte[] png = Base64.getDecoder().decode(imageData);
                    BufferedImage image;
                    try (InputStream in = new ByteArrayInputStream(png)) {
                        image = javax.imageio.ImageIO.read(in);
                    }
                    if (image == null) {
                        respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Not a valid image\"}");
                        return;
                    }
                    byte[][] pixels = toPaletteBytes(image);
                    byte[] flat = new byte[128 * 128];
                    for (int y = 0; y < 128; y++) {
                        System.arraycopy(pixels[y], 0, flat, y * 128, 128);
                    }
                    plugin.saveDraft(player, mapId, flat);
                    respondJson(exchange, 200, "{\"status\":\"ok\"}");
                }
                case "DELETE" -> {
                    plugin.deleteDraft(player, mapId);
                    respondJson(exchange, 200, "{\"status\":\"ok\"}");
                }
                default -> respondJson(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
            }
        } catch (Exception e) {
            logger.warning("Draft request failed: " + e.getMessage());
            respondJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Bad request\"}");
        }
    }

    private static boolean hasColor(byte[][] pixels) {
        for (byte[] row : pixels) {
            for (byte p : row) {
                if ((p & 0xFF) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isHoldingMap(UUID playerId, int mapId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return false;
        }
        return holdsMap(player.getInventory().getItemInMainHand(), mapId)
                || holdsMap(player.getInventory().getItemInOffHand(), mapId);
    }

    private static boolean holdsMap(ItemStack item, int mapId) {
        if (item == null || item.getType() != Material.FILLED_MAP) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof MapMeta mapMeta) {
            return mapMeta.getMapId() == mapId;
        }
        return false;
    }

    private static byte[][] toPaletteBytes(BufferedImage image) {
        BufferedImage scaled = image;
        if (image.getWidth() != 128 || image.getHeight() != 128) {
            scaled = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(image, 0, 0, 128, 128, null);
            g.dispose();
        }
        byte[][] pixels = new byte[128][128];
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int argb = scaled.getRGB(x, y);
                pixels[y][x] = MapColors.matchColor(argb);
            }
        }
        return pixels;
    }

    /** Converts a submitted image at its native size (used for wall grids). */
    private static byte[][] toPaletteBytesAt(BufferedImage image, int w, int h) {
        byte[][] pixels = new byte[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                pixels[y][x] = MapColors.matchColor(argb);
            }
        }
        return pixels;
    }

    private static String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            return null;
        }
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(name)) {
                return part.substring(eq + 1);
            }
        }
        return null;
    }

    /**
     * Reads a request body, capping its size to max-upload-size-megabytes so a
     * single request cannot flood the server memory. Throws IOException with a
     * "too large" message when the limit is exceeded (shown to the client).
     */
    private byte[] readBody(HttpExchange exchange) throws IOException {
        int megabytes = plugin.getConfig().getInt("max-upload-size-megabytes", 10);
        int maxBytes = megabytes > 0 ? megabytes * 1024 * 1024 : Integer.MAX_VALUE;
        InputStream in = exchange.getRequestBody();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Upload exceeds the maximum size of " + megabytes + " MB");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private void respondJson(HttpExchange exchange, int status, String json) throws IOException {
        respond(exchange, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private static String buildPaletteJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 4; i < 248; i++) {
            if (i > 4) {
                sb.append(',');
            }
            sb.append("{\"i\":").append(i)
                    .append(",\"c\":\"").append(toHex(MapColors.color((byte) i))).append("\"}");
        }
        return sb.append(']').toString();
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}

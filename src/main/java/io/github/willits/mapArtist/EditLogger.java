package io.github.willits.mapArtist;

import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Appends a human-readable, append-only log of drawing edits. Each entry
 * records the UTC timestamp, the player, and the map ID(s) edited. Multi-map
 * walls are logged as a single entry listing every map ID touched. The log can
 * be capped to the newest N entries via log-entry-limit; -1 keeps everything.
 */
public final class EditLogger {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MapArtist plugin;
    private final File file;

    public EditLogger(MapArtist plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "edits.log");
    }

    public void log(UUID playerId, List<Integer> mapIds) {
        try {
            if (!plugin.getConfig().getBoolean("log-edits", true)) {
                return;
            }
            if (mapIds == null || mapIds.isEmpty()) {
                return;
            }
            String stamp = ZonedDateTime.now(ZoneOffset.UTC).format(TIME);
            StringBuilder line = new StringBuilder("[").append(stamp).append(" UTC] ");
            line.append(displayName(playerId)).append(" edited map(s): ");
            for (int i = 0; i < mapIds.size(); i++) {
                if (i > 0) {
                    line.append(", ");
                }
                line.append('#').append(mapIds.get(i));
            }
            writeLine(line.toString());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write edit log: " + e.getMessage());
        }
    }

    /**
     * Logs a lock-related event (LOCK/UNLOCK) for a single map. The modifier is
     * the player who changed the lock (the locking player, the owner unlocking,
     * or an admin reassigning/unlocking).
     */
    public void logLock(UUID modifierId, int mapId, String action) {
        try {
            if (!plugin.getConfig().getBoolean("log-edits", true)) {
                return;
            }
            String stamp = ZonedDateTime.now(ZoneOffset.UTC).format(TIME);
            String line = "[" + stamp + " UTC] " + displayName(modifierId)
                    + " " + action + " map #" + mapId;
            writeLine(line);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write edit log: " + e.getMessage());
        }
    }

    private void writeLine(String line) throws IOException {
        String text = line + System.lineSeparator();
        int limit = plugin.getConfig().getInt("log-entry-limit", -1);
        if (limit < 0) {
            append(line + System.lineSeparator());
            return;
        }
        List<String> lines = file.isFile()
                ? Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)
                : new java.util.ArrayList<>();
        lines.add(text.replace("\r\n", "\n").replace("\n", System.lineSeparator()));
        if (lines.size() > limit) {
            lines = lines.subList(lines.size() - limit, lines.size());
        }
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
    }

    private String displayName(UUID playerId) {
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        return name == null ? playerId.toString() : name;
    }

    private void append(String line) {
        try {
            if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs()) {
                return;
            }
            Files.writeString(file.toPath(), line, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write edit log: " + e.getMessage());
        }
    }
}
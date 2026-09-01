package io.github.willits.mapArtist;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which map is locked and who locked (owns) it. Unlocked maps have no
 * owner. The lock/owner metadata is persisted to disk keyed by map id, so it
 * survives server restarts. The player who locks a map becomes its exclusive
 * editor until they (or an admin) unlock it again.
 */
public final class MapLockStore {

    private final MapArtist plugin;
    private final File file;
    private final Map<Integer, UUID> owners = new ConcurrentHashMap<>();

    public MapLockStore(MapArtist plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "locks.tsv");
    }

    public void load() {
        owners.clear();
        if (!file.isFile()) {
            return;
        }
        try {
            for (String raw : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String[] parts = raw.trim().split("\t");
                if (parts.length < 2) {
                    continue;
                }
                try {
                    int mapId = Integer.parseInt(parts[0]);
                    UUID owner = UUID.fromString(parts[1]);
                    owners.put(mapId, owner);
                } catch (NumberFormatException ignored) {
                    // skip malformed lines
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load edit locks: " + e.getMessage());
        }
    }

    public void save() {
        if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, UUID> e : owners.entrySet()) {
            sb.append(e.getKey()).append('\t').append(e.getValue()).append(System.lineSeparator());
        }
        try {
            Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save edit locks: " + e.getMessage());
        }
    }

    /** Whether the given map is currently locked to an owner. */
    public boolean isLocked(int mapId) {
        return owners.containsKey(mapId);
    }

    /** The owner UUID of a locked map, or null if it isn't locked. */
    public UUID ownerOf(int mapId) {
        return owners.get(mapId);
    }

    /** Locks a map to the given owner. Replaces any previous owner. */
    public void lock(int mapId, UUID owner, UUID modifier) {
        owners.put(mapId, owner);
        save();
        if (plugin.getEditLogger() != null) {
            if (modifier != null) {
                plugin.getEditLogger().logLock(modifier, mapId, "LOCKED (owner " + owner + ")");
            } else {
                plugin.getEditLogger().logLock(owner, mapId, "LOCKED (owner " + owner + ")");
            }
        }
    }

    /** Unlocks a map, clearing its owner (the map becomes freely claimable). */
    public void unlock(int mapId, UUID modifier) {
        if (owners.remove(mapId) != null) {
            save();
            if (modifier != null && plugin.getEditLogger() != null) {
                plugin.getEditLogger().logLock(modifier, mapId, "UNLOCKED");
            }
        }
    }

    public String ownerName(int mapId) {
        UUID owner = ownerOf(mapId);
        if (owner == null) {
            return null;
        }
        OfflinePlayer p = Bukkit.getOfflinePlayer(owner);
        return p.getName() == null ? owner.toString() : p.getName();
    }
}

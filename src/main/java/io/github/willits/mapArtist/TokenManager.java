package io.github.willits.mapArtist;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TokenManager {

    public record Session(UUID player, int mapId, Instant expiresAt) {}

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, byte[][]> shown = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> created = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final int perMinuteLimit;

    public TokenManager(long ttlMillis, int perMinuteLimit) {
        this.ttlMillis = ttlMillis;
        this.perMinuteLimit = perMinuteLimit;
    }

    /**
     * Creates a session token for the player, enforcing the per-minute rate
     * limit. Returns null when the player has hit the limit.
     */
    public String create(UUID player, int mapId) {
        if (!allow(player)) {
            return null;
        }
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            Session session = entry.getValue();
            if (session.player().equals(player) && session.mapId() == mapId) {
                sessions.remove(entry.getKey(), session);
                shown.remove(entry.getKey());
            }
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Session(player, mapId, Instant.now().plusMillis(ttlMillis)));
        return token;
    }

    private boolean allow(UUID player) {
        if (perMinuteLimit <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Deque<Long> times = created.computeIfAbsent(player, p -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= 60_000L) {
                times.pollFirst();
            }
            if (times.size() >= perMinuteLimit) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    public void sweep() {
        Instant now = Instant.now();
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            if (now.isAfter(entry.getValue().expiresAt())) {
                sessions.remove(entry.getKey(), entry.getValue());
                shown.remove(entry.getKey());
            }
        }
    }

    public void setShown(String token, byte[][] pixels) {
        if (pixels == null) {
            shown.remove(token);
        } else {
            shown.put(token, pixels);
        }
    }

    public byte[][] getShown(String token) {
        return shown.get(token);
    }

    public Session consume(String token) {
        Session session = sessions.remove(token);
        shown.remove(token);
        if (session == null) {
            return null;
        }
        if (Instant.now().isAfter(session.expiresAt())) {
            return null;
        }
        return session;
    }

    public Session peek(String token) {
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (Instant.now().isAfter(session.expiresAt())) {
            return null;
        }
        return session;
    }
}

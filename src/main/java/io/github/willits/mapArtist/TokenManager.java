package io.github.willits.mapArtist;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TokenManager {

    public record Session(UUID player, int mapId, Instant expiresAt) {}

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, byte[][]> shown = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public TokenManager(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public String create(UUID player, int mapId) {
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

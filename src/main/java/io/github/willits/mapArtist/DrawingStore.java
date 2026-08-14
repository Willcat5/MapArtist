package io.github.willits.mapArtist;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DrawingStore {

    private final ConcurrentHashMap<Integer, byte[][]> drawings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, byte[][]> basePixels = new ConcurrentHashMap<>();
    private final Set<Integer> baseCaptured = ConcurrentHashMap.newKeySet();

    public void put(int mapId, byte[][] pixels) {
        drawings.put(mapId, pixels);
    }

    public void remove(int mapId) {
        drawings.remove(mapId);
    }

    public Set<Integer> mapIds() {
        return drawings.keySet();
    }

    public byte[][] get(int mapId) {
        return drawings.get(mapId);
    }

    public boolean has(int mapId) {
        return drawings.containsKey(mapId);
    }

    public void clearBase(int mapId) {
        basePixels.remove(mapId);
        baseCaptured.remove(mapId);
    }

    public void putBase(int mapId, byte[][] pixels) {
        basePixels.put(mapId, pixels);
    }

    public byte[][] getBase(int mapId) {
        return basePixels.get(mapId);
    }

    public void markBaseCaptured(int mapId) {
        baseCaptured.add(mapId);
    }

    public boolean isBaseCaptured(int mapId) {
        return baseCaptured.contains(mapId);
    }
}

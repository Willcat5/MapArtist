package io.github.willits.mapArtist;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public final class BaseCaptureRenderer extends MapRenderer {

    private final DrawingStore store;
    private boolean captured = false;

    public BaseCaptureRenderer(DrawingStore store) {
        super(false);
        this.store = store;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (captured) {
            return;
        }
        captured = true;
        byte[][] base = new byte[128][128];
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                base[y][x] = canvas.getBasePixel(x, y);
            }
        }
        store.putBase(map.getId(), base);
        store.markBaseCaptured(map.getId());
    }
}

package io.github.willits.mapArtist;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public final class DrawingRenderer extends MapRenderer {

    private final DrawingStore store;

    public DrawingRenderer(DrawingStore store) {
        super(false);
        this.store = store;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        byte[][] pixels = store.get(map.getId());
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                byte p = (pixels == null) ? 0 : pixels[y][x];
                if ((p & 0xFF) != 0) {
                    canvas.setPixel(x, y, p);
                } else {
                    canvas.setPixel(x, y, canvas.getBasePixel(x, y));
                }
            }
        }
    }
}

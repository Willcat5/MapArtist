package io.github.willits.mapArtist;

import java.awt.Color;

/**
 * Bundled Minecraft map palette (indices 0..255).
 *
 * <p>Replaces {@code org.bukkit.map.MapPalette}, whose color-matching and
 * color-lookup methods were removed on modern Paper. The palette is a fixed,
 * stable part of the map format: 62 base colors, each expanded into 4 shades
 * by multiplying the base RGB by 180, 220, 255 or 135 and dividing by 255
 * (floor). Index 0 (and 1-3, which are the same base color) is transparent.
 * Drawable colors are indices 4..247.
 */
public final class MapColors {

    private static final int[] SHADE = { 180, 220, 255, 135 };

    // Base colors 0..61 as packed RGB (index 0 = transparent/unused).
    private static final int[] BASE = {
        0x000000, 0x7FB238, 0xF7E9A3, 0xC7C7C7, 0xFF0000, 0xA0A0FF, 0xA7A7A7,
        0x007C00, 0xFFFFFF, 0xA4A8B8, 0x976D4D, 0x707070, 0x4040FF, 0x8F7748,
        0xFFFCF5, 0xD87F33, 0xB24CD8, 0x6699D8, 0xE5E533, 0x7FCC19, 0xF27FA5,
        0x4C4C4C, 0x999999, 0x4C7F99, 0x7F3FB2, 0x334CB2, 0x664C33, 0x667F33,
        0x993333, 0x191919, 0xFAEE4D, 0x5CDBD5, 0x4A80FF, 0x00D93A, 0x815631,
        0x700200, 0xD1B1A1, 0x9F5224, 0x95576C, 0x706C8A, 0xBA8524, 0x677535,
        0xA04D4E, 0x392923, 0x876B62, 0x575C5C, 0x7A4958, 0x4C3E5C, 0x4C3223,
        0x4C522A, 0x8E3C2E, 0x251610, 0xBD3031, 0x943F61, 0x5C191D, 0x167E86,
        0x3A8E8C, 0x562C3E, 0x14B485, 0x646464, 0xD8AF93, 0x7FA796
    };

    private static final Color[] COLORS = new Color[256];

    static {
        COLORS[0] = new Color(0, 0, 0, 0);
        COLORS[1] = new Color(0, 0, 0, 0);
        COLORS[2] = new Color(0, 0, 0, 0);
        COLORS[3] = new Color(0, 0, 0, 0);
        for (int id = 1; id <= 61; id++) {
            int rgb = BASE[id];
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            for (int s = 0; s < 4; s++) {
                COLORS[id * 4 + s] = new Color(
                    (r * SHADE[s]) / 255,
                    (g * SHADE[s]) / 255,
                    (b * SHADE[s]) / 255);
            }
        }
    }

    private MapColors() {
    }

    /** Returns the color for a map color index (0..255). Transparent = 0. */
    public static Color color(byte index) {
        int i = index & 0xFF;
        Color c = COLORS[i];
        return c != null ? c : new Color(0, 0, 0, 0);
    }

    /**
     * Matches an ARGB pixel to the nearest drawable map color index.
     * Fully (or mostly) transparent pixels map to index 0.
     */
    public static byte matchColor(int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a < 128) {
            return 0;
        }
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int best = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 4; i <= 247; i++) {
            Color c = COLORS[i];
            int dr = c.getRed() - r;
            int dg = c.getGreen() - g;
            int db = c.getBlue() - b;
            int d = dr * dr + dg * dg + db * db;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return (byte) best;
    }
}

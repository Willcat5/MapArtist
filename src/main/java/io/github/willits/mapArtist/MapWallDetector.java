package io.github.willits.mapArtist;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects a rectangular grid of custom-map item frames on the same wall.
 * A grid is valid when every block in its bounding rectangle on that wall
 * holds an item frame containing a MapArtist drawing map.
 */
public final class MapWallDetector {

    public record Grid(BlockFace facing, int width, int height,
                       double minH, double maxH, double minV, double maxV, double plane,
                       List<Integer> mapIds, Rotation rotation) {}

    private MapWallDetector() {
    }

    /**
     * Returns the grid containing {@code clicked}, or {@code null} if the frame
     * is not part of a clean, filled rectangle of custom-map frames.
     */
    public static Grid detect(ItemFrame clicked, int maxGrid) {
        if (!isCustomMap(clicked.getItem())) {
            return null;
        }
        BlockFace facing = clicked.getFacing();
        Location loc = clicked.getLocation();
        int radius = Math.max(8, maxGrid + 1);
        Collection<Entity> nearby = clicked.getWorld().getNearbyEntities(loc, radius, radius, radius);

        Set<Point> cells = new HashSet<>();
        Map<BlockPos, Integer> framesPerBlock = new HashMap<>();
        Map<Point, Integer> cellMapIds = new HashMap<>();
        Map<Point, Rotation> cellRotations = new HashMap<>();
        for (Entity entity : nearby) {
            if (!(entity instanceof ItemFrame frame)) {
                continue;
            }
            BlockPos pos = new BlockPos(frame.getLocation().getBlockX(),
                    frame.getLocation().getBlockY(), frame.getLocation().getBlockZ());
            if (framesPerBlock.merge(pos, 1, Integer::sum) > 1) {
                // Multiple item frames stacked in a single block (any facing):
                // the grid isn't trustworthy, so refuse to treat it as a wall.
                return null;
            }
            if (frame.getFacing() != facing) {
                continue;
            }
            MapView view = mapViewOf(frame.getItem());
            if (view == null || view.getRenderers().stream().noneMatch(r -> r instanceof DrawingRenderer)) {
                continue;
            }
            Point point = planePoint(frame, facing);
            cells.add(point);
            cellMapIds.put(point, view.getId());
            cellRotations.put(point, frame.getRotation());
        }
        if (cells.isEmpty()) {
            return null;
        }

        // Only the contiguous group of frames around the clicked one counts,
        // so a second grid on the same plane (or any gap) elsewhere doesn't
        // invalidate this grid.
        Point start = planePoint(clicked, facing);
        if (!cells.contains(start)) {
            return null;
        }
        Set<Point> component = floodFill(cells, start);

        int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE;
        int minV = Integer.MAX_VALUE, maxV = Integer.MIN_VALUE;
        for (Point cell : component) {
            if (cell.h() < minH) minH = cell.h();
            if (cell.h() > maxH) maxH = cell.h();
            if (cell.v() < minV) minV = cell.v();
            if (cell.v() > maxV) maxV = cell.v();
        }

        int width = maxH - minH + 1;
        int height = maxV - minV + 1;
        if (component.size() != width * height) {
            return null;
        }

        // Map IDs in row-major order, top row first, and the shared frame
        // rotation (null when frames are rotated inconsistently).
        List<Integer> mapIds = new ArrayList<>(component.size());
        Set<Rotation> rotations = new HashSet<>();
        for (int v = maxV; v >= minV; v--) {
            for (int h = minH; h <= maxH; h++) {
                Point cell = new Point(h, v);
                mapIds.add(cellMapIds.get(cell));
                rotations.add(cellRotations.get(cell));
            }
        }

        Location clickedLoc = clicked.getLocation();
        double plane = switch (facing) {
            case NORTH, SOUTH -> clickedLoc.getZ();
            case EAST, WEST -> clickedLoc.getX();
            default -> clickedLoc.getY(); // UP / DOWN (floor / ceiling)
        };
        Rotation rotation = rotations.size() == 1 ? rotations.iterator().next() : null;
        return new Grid(facing, width, height, minH, maxH, minV, maxV, plane, mapIds, rotation);
    }
    private record Point(int h, int v) {}

    private record BlockPos(int x, int y, int z) {}

    /**
     * Flood-fills the 4-connected component of {@code cells} containing
     * {@code start}.
     */
    private static Set<Point> floodFill(Set<Point> cells, Point start) {
        Set<Point> component = new HashSet<>();
        ArrayDeque<Point> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            Point p = stack.pop();
            if (!component.add(p)) {
                continue;
            }
            Point[] neighbors = {
                    new Point(p.h() - 1, p.v()),
                    new Point(p.h() + 1, p.v()),
                    new Point(p.h(), p.v() - 1),
                    new Point(p.h(), p.v() + 1)
            };
            for (Point n : neighbors) {
                if (cells.contains(n)) {
                    stack.push(n);
                }
            }
        }
        return component;
    }

    /**
     * Maps a frame to its position in the wall plane. Horizontal and vertical
     * axes depend on the direction the frame is facing.
     */
    private static Point planePoint(ItemFrame frame, BlockFace facing) {
        int x = frame.getLocation().getBlockX();
        int y = frame.getLocation().getBlockY();
        int z = frame.getLocation().getBlockZ();
        return switch (facing) {
            case NORTH, SOUTH -> new Point(x, y);
            case EAST, WEST -> new Point(z, y);
            default -> new Point(x, z); // UP / DOWN (floor / ceiling)
        };
    }

    /**
     * Traces a particle outline around the outside edge of the grid.
     */
    public static void spawnHighlight(World world, Grid grid) {
        double lo = grid.minH();
        double hi = grid.maxH() + 1.0;
        double vlo = grid.minV();
        double vhi = grid.maxV() + 1.0;
        for (double h = lo; h <= hi; h += 0.5) {
            spawnBorderParticle(world, grid, h, vlo);
            spawnBorderParticle(world, grid, h, vhi);
        }
        for (double v = vlo + 0.5; v < vhi; v += 0.5) {
            spawnBorderParticle(world, grid, lo, v);
            spawnBorderParticle(world, grid, hi, v);
        }
    }

    private static void spawnBorderParticle(World world, Grid grid, double h, double v) {
        double x, y, z;
        switch (grid.facing()) {
            case NORTH, SOUTH -> {
                x = h;
                y = v;
                z = grid.plane();
            }
            case EAST, WEST -> {
                z = h;
                y = v;
                x = grid.plane();
            }
            default -> {
                x = h;
                z = v;
                y = grid.plane();
            }
        }
        world.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0,
                new Particle.DustOptions(org.bukkit.Color.WHITE, 1f), true);
    }

    public static MapView mapViewOf(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP) {
            return null;
        }
        if (!(item.getItemMeta() instanceof MapMeta meta) || !meta.hasMapView()) {
            return null;
        }
        return org.bukkit.Bukkit.getMap(meta.getMapId());
    }

    public static boolean isCustomMap(ItemStack item) {
        MapView view = mapViewOf(item);
        return view != null && view.getRenderers().stream().anyMatch(r -> r instanceof DrawingRenderer);
    }

    /**
     * Whether the item frame holding this map should be hidden: it's a drawing
     * map and it actually shows something (drawn pixels or a captured base).
     * Completely transparent maps keep their frame visible.
     */
    public static boolean shouldHideFrame(ItemStack item, DrawingStore store) {
        MapView view = mapViewOf(item);
        if (view == null || view.getRenderers().stream().noneMatch(r -> r instanceof DrawingRenderer)) {
            return false;
        }
        return hasColor(store.get(view.getId())) || hasColor(store.getBase(view.getId()));
    }

    private static boolean hasColor(byte[][] pixels) {
        if (pixels == null) {
            return false;
        }
        for (byte[] row : pixels) {
            for (byte p : row) {
                if ((p & 0xFF) != 0) {
                    return true;
                }
            }
        }
        return false;
    }
}

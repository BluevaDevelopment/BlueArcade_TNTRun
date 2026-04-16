package net.blueva.arcade.modules.tntrun.state;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TNTRunFloor {

    private final String key;
    private final int index;
    private final Location min;
    private final Location max;
    private final int autoRemoveTimeSeconds;
    private final List<Location> blocks;
    private final Set<TNTRunBlockKey> blockKeys;

    public TNTRunFloor(String key, int index, Location min, Location max, int autoRemoveTimeSeconds, List<Location> blocks) {
        this.key = key;
        this.index = index;
        this.min = min;
        this.max = max;
        this.autoRemoveTimeSeconds = autoRemoveTimeSeconds;
        this.blocks = blocks != null ? new ArrayList<>(blocks) : new ArrayList<>();
        this.blockKeys = new HashSet<>();
        for (Location location : this.blocks) {
            TNTRunBlockKey keyValue = TNTRunBlockKey.from(location);
            if (keyValue != null) {
                this.blockKeys.add(keyValue);
            }
        }
    }

    public String getKey() {
        return key;
    }

    public int getIndex() {
        return index;
    }

    public Location getMin() {
        return min;
    }

    public Location getMax() {
        return max;
    }

    public int getAutoRemoveTimeSeconds() {
        return autoRemoveTimeSeconds;
    }

    public List<Location> getBlocks() {
        return new ArrayList<>(blocks);
    }

    /**
     * Returns the explicit block list if one was configured, otherwise generates
     * all block locations within the floor's bounding box at runtime.
     */
    public List<Location> getBlockLocations() {
        if (!blocks.isEmpty()) {
            return new ArrayList<>(blocks);
        }
        if (min == null || max == null || min.getWorld() == null) {
            return new ArrayList<>();
        }
        int[] bounds = getNormalizedIntBounds();
        World world = min.getWorld();
        List<Location> locations = new ArrayList<>();
        for (int x = bounds[0]; x <= bounds[1]; x++) {
            for (int y = bounds[2]; y <= bounds[3]; y++) {
                for (int z = bounds[4]; z <= bounds[5]; z++) {
                    locations.add(new Location(world, x, y, z));
                }
            }
        }
        return locations;
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!isWithinBounds(location)) {
            return false;
        }
        if (!blockKeys.isEmpty()) {
            return blockKeys.contains(TNTRunBlockKey.from(location));
        }
        // Bounds-only mode: anything within bounds is part of the floor
        return true;
    }

    public boolean contains(TNTRunBlockKey key) {
        if (key == null) {
            return false;
        }
        if (!blockKeys.isEmpty()) {
            return blockKeys.contains(key);
        }
        // Bounds-only mode: fall back to coordinate bounds check
        return isWithinBoundsKey(key);
    }

    private boolean isWithinBoundsKey(TNTRunBlockKey key) {
        if (min == null || max == null || min.getWorld() == null) {
            return false;
        }
        if (!min.getWorld().getName().equals(key.getWorld())) {
            return false;
        }
        int[] b = getNormalizedIntBounds();
        return key.getX() >= b[0] && key.getX() <= b[1]
                && key.getY() >= b[2] && key.getY() <= b[3]
                && key.getZ() >= b[4] && key.getZ() <= b[5];
    }

    /** Returns [minX, maxX, minY, maxY, minZ, maxZ] as integer block coordinates. */
    private int[] getNormalizedIntBounds() {
        return new int[]{
                (int) Math.min(min.getX(), max.getX()),
                (int) Math.max(min.getX(), max.getX()),
                (int) Math.min(min.getY(), max.getY()),
                (int) Math.max(min.getY(), max.getY()),
                (int) Math.min(min.getZ(), max.getZ()),
                (int) Math.max(min.getZ(), max.getZ())
        };
    }

    public Location getCenter() {
        if (min == null || max == null) {
            return null;
        }
        World world = min.getWorld();
        if (world == null) {
            return null;
        }
        double centerX = (min.getX() + max.getX()) / 2.0;
        double centerY = (min.getY() + max.getY()) / 2.0;
        double centerZ = (min.getZ() + max.getZ()) / 2.0;
        return new Location(world, centerX, centerY, centerZ);
    }

    private boolean isWithinBounds(Location location) {
        if (min == null || max == null || min.getWorld() == null || max.getWorld() == null) {
            return false;
        }
        if (!min.getWorld().equals(location.getWorld())) {
            return false;
        }

        double minX = Math.min(min.getX(), max.getX());
        double maxX = Math.max(min.getX(), max.getX());
        double minY = Math.min(min.getY(), max.getY());
        double maxY = Math.max(min.getY(), max.getY());
        double minZ = Math.min(min.getZ(), max.getZ());
        double maxZ = Math.max(min.getZ(), max.getZ());

        return location.getX() >= minX && location.getX() <= maxX
                && location.getY() >= minY && location.getY() <= maxY
                && location.getZ() >= minZ && location.getZ() <= maxZ;
    }
}

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

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!isWithinBounds(location)) {
            return false;
        }
        return blockKeys.contains(TNTRunBlockKey.from(location));
    }

    public boolean contains(TNTRunBlockKey key) {
        if (key == null) {
            return false;
        }
        return blockKeys.contains(key);
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

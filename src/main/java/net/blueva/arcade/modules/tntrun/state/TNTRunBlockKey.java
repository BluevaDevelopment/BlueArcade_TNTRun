package net.blueva.arcade.modules.tntrun.state;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

public class TNTRunBlockKey {

    private final String world;
    private final int x;
    private final int y;
    private final int z;

    private TNTRunBlockKey(String world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static TNTRunBlockKey from(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return new TNTRunBlockKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public Location toLocation() {
        World resolvedWorld = Bukkit.getWorld(world);
        if (resolvedWorld == null) {
            return null;
        }
        return new Location(resolvedWorld, x, y, z);
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        TNTRunBlockKey blockKey = (TNTRunBlockKey) obj;
        return x == blockKey.x && y == blockKey.y && z == blockKey.z && Objects.equals(world, blockKey.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, y, z);
    }

    @Override
    public String toString() {
        return world + ":" + x + ":" + y + ":" + z;
    }
}

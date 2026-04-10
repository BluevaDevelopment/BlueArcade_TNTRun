package net.blueva.arcade.modules.tntrun.state;

import net.blueva.arcade.api.game.GameContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TNTRunArenaState {

    private final GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context;
    private final List<TNTRunFloor> floors;
    private final Set<TNTRunBlockKey> scheduledBlocks = ConcurrentHashMap.newKeySet();
    private final Set<TNTRunBlockKey> removedBlocks = ConcurrentHashMap.newKeySet();
    private final Set<UUID> eliminatedPlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, Integer> doubleJumps = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, TNTRunBlockKey> lastPlayerPosition = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> playerStationaryTicks = new ConcurrentHashMap<>();
    private final int maxDoubleJumps;
    private volatile boolean ended;
    private volatile int timeLeft;

    public TNTRunArenaState(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                            List<TNTRunFloor> floors,
                            int maxDoubleJumps) {
        this.context = context;
        this.floors = floors != null ? new ArrayList<>(floors) : new ArrayList<>();
        this.maxDoubleJumps = Math.max(0, maxDoubleJumps);
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getContext() {
        return context;
    }

    public List<TNTRunFloor> getFloors() {
        return new ArrayList<>(floors);
    }

    public Set<TNTRunBlockKey> getScheduledBlocks() {
        return scheduledBlocks;
    }

    public Set<TNTRunBlockKey> getRemovedBlocks() {
        return removedBlocks;
    }

    public Set<UUID> getEliminatedPlayers() {
        return eliminatedPlayers;
    }

    public ConcurrentMap<UUID, Integer> getDoubleJumps() {
        return doubleJumps;
    }

    public int getMaxDoubleJumps() {
        return maxDoubleJumps;
    }

    public boolean isEnded() {
        return ended;
    }

    public boolean markEnded() {
        if (ended) {
            return true;
        }
        ended = true;
        return false;
    }

    public int getTimeLeft() {
        return timeLeft;
    }

    public void setTimeLeft(int timeLeft) {
        this.timeLeft = timeLeft;
    }

    public ConcurrentMap<UUID, TNTRunBlockKey> getLastPlayerPosition() {
        return lastPlayerPosition;
    }

    public ConcurrentMap<UUID, Integer> getPlayerStationaryTicks() {
        return playerStationaryTicks;
    }

    public void clearTracking() {
        scheduledBlocks.clear();
        removedBlocks.clear();
        eliminatedPlayers.clear();
        doubleJumps.clear();
        lastPlayerPosition.clear();
        playerStationaryTicks.clear();
    }

    public TNTRunFloor findFloorAt(TNTRunBlockKey key) {
        for (TNTRunFloor floor : floors) {
            if (floor.contains(key)) {
                return floor;
            }
        }
        return null;
    }

    public TNTRunFloor findFloorAt(org.bukkit.Location location) {
        if (location == null) {
            return null;
        }
        for (TNTRunFloor floor : floors) {
            if (floor.contains(location)) {
                return floor;
            }
        }
        return null;
    }
}

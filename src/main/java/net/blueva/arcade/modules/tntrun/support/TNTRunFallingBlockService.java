package net.blueva.arcade.modules.tntrun.support;

import net.blueva.arcade.api.game.GameContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class TNTRunFallingBlockService {

    private final TNTRunSettings settings;
    private final Map<Integer, Set<UUID>> arenaFallingBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> fallingBlockArena = new ConcurrentHashMap<>();

    public TNTRunFallingBlockService(TNTRunSettings settings) {
        this.settings = settings;
    }

    public void spawnFallingShard(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                  Location origin,
                                  org.bukkit.block.data.BlockData blockData) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        org.bukkit.entity.FallingBlock fallingBlock = world.spawnFallingBlock(origin.clone().add(0.5, 0.1, 0.5), blockData);
        fallingBlock.setDropItem(false);
        fallingBlock.setHurtEntities(false);
        double horizontalX = ThreadLocalRandom.current().nextDouble(-settings.getFallingHorizontalRandomness(), settings.getFallingHorizontalRandomness());
        double horizontalZ = ThreadLocalRandom.current().nextDouble(-settings.getFallingHorizontalRandomness(), settings.getFallingHorizontalRandomness());
        fallingBlock.setVelocity(new org.bukkit.util.Vector(horizontalX, -Math.abs(settings.getFallingDownwardVelocity()), horizontalZ));

        int arenaId = context.getArenaId();
        trackFallingBlock(arenaId, fallingBlock.getUniqueId());

        String taskId = "arena_" + arenaId + "_tnt_run_falling_" + fallingBlock.getUniqueId();
        context.getSchedulerAPI().runLater(taskId, () -> removeTrackedFallingBlock(fallingBlock), 40L);
    }

    public boolean isTrackedFallingBlock(UUID uuid) {
        return fallingBlockArena.containsKey(uuid);
    }

    public void handleFallingBlockLand(org.bukkit.entity.FallingBlock fallingBlock) {
        removeTrackedFallingBlock(fallingBlock);
    }

    public void cleanupArena(int arenaId) {
        Set<UUID> fallingBlocks = arenaFallingBlocks.remove(arenaId);
        if (fallingBlocks == null) {
            return;
        }

        for (UUID uuid : fallingBlocks) {
            fallingBlockArena.remove(uuid);
            org.bukkit.entity.Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    public void cleanupAll() {
        for (Integer arenaId : new HashSet<>(arenaFallingBlocks.keySet())) {
            cleanupArena(arenaId);
        }
        fallingBlockArena.clear();
        arenaFallingBlocks.clear();
    }

    private void trackFallingBlock(int arenaId, UUID uuid) {
        arenaFallingBlocks.computeIfAbsent(arenaId, id -> ConcurrentHashMap.newKeySet()).add(uuid);
        fallingBlockArena.put(uuid, arenaId);
    }

    private void removeTrackedFallingBlock(org.bukkit.entity.FallingBlock fallingBlock) {
        if (fallingBlock == null) {
            return;
        }
        UUID uuid = fallingBlock.getUniqueId();
        fallingBlock.remove();
        untrackFallingBlock(uuid);
    }

    private void untrackFallingBlock(UUID uuid) {
        Integer arenaId = fallingBlockArena.remove(uuid);
        if (arenaId == null) {
            return;
        }

        Set<UUID> arenaSet = arenaFallingBlocks.get(arenaId);
        if (arenaSet != null) {
            arenaSet.remove(uuid);
            if (arenaSet.isEmpty()) {
                arenaFallingBlocks.remove(arenaId);
            }
        }
    }
}

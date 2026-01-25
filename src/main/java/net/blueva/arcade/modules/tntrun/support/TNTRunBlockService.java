package net.blueva.arcade.modules.tntrun.support;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.modules.tntrun.state.TNTRunArenaState;
import net.blueva.arcade.modules.tntrun.state.TNTRunBlockKey;
import net.blueva.arcade.modules.tntrun.state.TNTRunFloor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TNTRunBlockService {

    private static final int MAX_FLOOR_SCAN = 100;
    private final TNTRunSettings settings;
    private final TNTRunStatsService statsService;
    private final TNTRunFallingBlockService fallingBlockService;
    private final TNTRunMessagingService messagingService;

    public TNTRunBlockService(TNTRunSettings settings,
                              TNTRunStatsService statsService,
                              TNTRunFallingBlockService fallingBlockService,
                              TNTRunMessagingService messagingService) {
        this.settings = settings;
        this.statsService = statsService;
        this.fallingBlockService = fallingBlockService;
        this.messagingService = messagingService;
    }

    public List<TNTRunFloor> loadFloors(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        List<TNTRunFloor> floors = new ArrayList<>();

        Integer total = context.getDataAccess().getGameData("game.floors.total", Integer.class);
        int floorTotal = total != null ? total : 0;
        if (floorTotal == 0) {
            floorTotal = resolveFloorTotal(context);
        }

        if (floorTotal == 0 && context.getDataAccess().hasGameData("game.floor.bounds.min.x")) {
            TNTRunFloor floor = loadFloor(context, "game.floor", "floor", 1);
            if (floor != null) {
                floors.add(floor);
            }
            return floors;
        }

        for (int i = 1; i <= floorTotal; i++) {
            String floorKey = "f" + i;
            TNTRunFloor floor = loadFloor(context, "game.floors." + floorKey, floorKey, i);
            if (floor != null) {
                floors.add(floor);
            }
        }

        return floors;
    }

    public void handlePlayerStep(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                 TNTRunArenaState state,
                                 Player player,
                                 Location to) {
        if (context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        Set<Block> touchedBlocks = detectTouchedBlocks(state, to);
        if (touchedBlocks.isEmpty()) {
            return;
        }

        for (Block block : touchedBlocks) {
            TNTRunBlockKey key = TNTRunBlockKey.from(block.getLocation());
            if (key == null) {
                continue;
            }
            scheduleBlockRemoval(context, state, block, key, player.getUniqueId());
        }
    }

    public void startFloorRemovalTimers(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                        TNTRunArenaState state) {
        for (TNTRunFloor floor : state.getFloors()) {
            int autoRemoveTime = floor.getAutoRemoveTimeSeconds();
            if (autoRemoveTime <= 0) {
                continue;
            }
            scheduleFloorRemoval(context, state, floor, autoRemoveTime);
        }
    }

    public boolean shouldEliminate(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                   Location to) {
        if (!context.isInsideBounds(to)) {
            return true;
        }

        String deathBlockName = context.getDataAccess().getGameData("basic.death_block", String.class);
        if (deathBlockName == null) {
            deathBlockName = context.getCoreConfig().getSettingsString("game.global.default_death_block", "BARRIER");
        }

        Material deathBlock = parseMaterial(deathBlockName, Material.BARRIER);
        Block blockUnder = to.clone().subtract(0, 1, 0).getBlock();
        if (blockUnder.getType() == deathBlock) {
            return true;
        }

        return false;
    }

    private TNTRunFloor loadFloor(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                  String basePath,
                                  String floorKey,
                                  int index) {
        Location min = context.getDataAccess().getGameLocation(basePath + ".bounds.min");
        Location max = context.getDataAccess().getGameLocation(basePath + ".bounds.max");

        if (min == null || max == null) {
            return null;
        }

        List<String> blockList = context.getDataAccess().getGameData(basePath + ".blocks", List.class);
        if (blockList == null || blockList.isEmpty()) {
            return null;
        }

        List<Location> blocks = parseBlocks(blockList);

        Integer autoRemove = context.getDataAccess().getGameData(basePath + ".auto_remove_time", Integer.class);
        int autoRemoveTime = autoRemove != null ? autoRemove : settings.getDefaultAutoRemoveSeconds();

        return new TNTRunFloor(floorKey, index, min, max, autoRemoveTime, blocks);
    }

    private int resolveFloorTotal(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int last = 0;
        for (int i = 1; i <= MAX_FLOOR_SCAN; i++) {
            if (context.getDataAccess().hasGameData("game.floors.f" + i + ".bounds.min.x")) {
                last = i;
            }
        }
        return last;
    }

    private void scheduleFloorRemoval(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                      TNTRunArenaState state,
                                      TNTRunFloor floor,
                                      int seconds) {
        for (int warning : settings.getRemovalWarnings()) {
            if (warning <= 0 || warning >= seconds) {
                continue;
            }

            long delayTicks = (seconds - warning) * 20L;
            String warningTaskId = "arena_" + context.getArenaId() + "_tnt_run_floor_warning_" + floor.getKey() + "_" + warning;
            context.getSchedulerAPI().runLater(warningTaskId, () -> {
                if (state.isEnded()) {
                    return;
                }
                messagingService.sendFloorRemovalWarning(context, floor.getIndex(), warning);
            }, delayTicks);
        }

        String taskId = "arena_" + context.getArenaId() + "_tnt_run_floor_remove_" + floor.getKey();
        context.getSchedulerAPI().runLater(taskId, () -> {
            if (state.isEnded()) {
                return;
            }
            messagingService.sendFloorRemoved(context, floor.getIndex());
            removeEntireFloor(context, state, floor);
        }, seconds * 20L);
    }

    private void removeEntireFloor(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                   TNTRunArenaState state,
                                   TNTRunFloor floor) {
        Location center = floor.getCenter();
        if (center == null) {
            return;
        }

        context.getSchedulerAPI().runAtLocation(center, () -> {
            for (Location blockLocation : floor.getBlocks()) {
                if (blockLocation.getWorld() == null) {
                    continue;
                }

                Block block = blockLocation.getBlock();
                if (block.getType() == Material.AIR || block.getType() == Material.BARRIER) {
                    continue;
                }

                handleBlockRemoval(context, state, block, TNTRunBlockKey.from(blockLocation), null);
            }
        });
    }

    private void scheduleBlockRemoval(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                      TNTRunArenaState state,
                                      Block block,
                                      TNTRunBlockKey key,
                                      UUID trigger) {
        if (block.getType() == Material.AIR || block.getType() == Material.BARRIER || block.getType() == Material.BEDROCK) {
            return;
        }

        Block blockBelow = block.getLocation().clone().subtract(0, 1, 0).getBlock();
        if (blockBelow.getType() == Material.BEDROCK || blockBelow.getType() == Material.BARRIER) {
            return;
        }

        if (state.getRemovedBlocks().contains(key) || state.getScheduledBlocks().contains(key)) {
            return;
        }

        TNTRunFloor floor = state.findFloorAt(key);
        if (floor == null) {
            return;
        }

        state.getScheduledBlocks().add(key);
        int delayTicks = resolveFloorDelayTicks(context);
        String taskId = "arena_" + context.getArenaId() + "_tnt_run_block_" + key;
        Location blockLocation = block.getLocation();

        context.getSchedulerAPI().runLater(taskId, () -> {
            if (state.isEnded()) {
                state.getScheduledBlocks().remove(key);
                return;
            }

            context.getSchedulerAPI().runAtLocation(blockLocation, () -> {
                Block target = blockLocation.getBlock();
                handleBlockRemoval(context, state, target, key, trigger);
                state.getScheduledBlocks().remove(key);
            });
        }, delayTicks);
    }

    private void handleBlockRemoval(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    TNTRunArenaState state,
                                    Block block,
                                    TNTRunBlockKey key,
                                    UUID trigger) {
        if (block == null) {
            return;
        }
        if (block.getType() == Material.AIR || block.getType() == Material.BARRIER) {
            return;
        }

        org.bukkit.block.data.BlockData blockData = block.getBlockData();

        spawnParticles(block, blockData);
        if (settings.isFallingBlocksEnabled()) {
            fallingBlockService.spawnFallingShard(context, block.getLocation(), blockData);
        }

        block.setType(Material.AIR);
        if (key != null) {
            state.getRemovedBlocks().add(key);
        }
        if (trigger != null) {
            Player player = Bukkit.getPlayer(trigger);
            if (player != null) {
                statsService.recordBlockDrop(player);
            }
        }
    }

    private void spawnParticles(Block block, org.bukkit.block.data.BlockData blockData) {
        if (!settings.isParticlesEnabled()) {
            return;
        }

        Particle particle = settings.getParticleType();
        Location particleLocation = block.getLocation().add(0.5, 0.5, 0.5);
        if (particle == Particle.BLOCK) {
            block.getWorld().spawnParticle(particle, particleLocation,
                    settings.getParticleCount(), settings.getParticleSpread(),
                    settings.getParticleSpread() * 0.6, settings.getParticleSpread(),
                    settings.getParticleSpeed(), blockData);
        } else {
            block.getWorld().spawnParticle(particle, particleLocation,
                    settings.getParticleCount(), settings.getParticleSpread(),
                    settings.getParticleSpread() * 0.6, settings.getParticleSpread(),
                    settings.getParticleSpeed());
        }
    }

    private int resolveFloorDelayTicks(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        Integer delay = context.getDataAccess().getGameData("basic.floor_delay", Integer.class);
        if (delay == null || delay <= 0) {
            return settings.getFloorDelayTicks();
        }
        return delay;
    }

    private Set<Block> detectTouchedBlocks(TNTRunArenaState state, Location playerLocation) {
        Set<Block> blocks = new HashSet<>();
        if (playerLocation == null || playerLocation.getWorld() == null) {
            return blocks;
        }

        Block baseBlock = findBaseBlock(state, playerLocation);
        if (baseBlock == null) {
            return blocks;
        }

        World world = baseBlock.getWorld();
        int baseX = baseBlock.getX();
        int baseY = baseBlock.getY();
        int baseZ = baseBlock.getZ();

        addBlockIfValid(blocks, state, world, baseX, baseY, baseZ);
        for (int i = 1; i <= settings.getDetectionAdditionalBelow(); i++) {
            addBlockIfValid(blocks, state, world, baseX, baseY - i, baseZ);
        }

        double moveX = playerLocation.getX() - Math.floor(playerLocation.getX()) - 0.5;
        double moveZ = playerLocation.getZ() - Math.floor(playerLocation.getZ()) - 0.5;

        int xDir = moveX > settings.getDetectionEdgeThreshold() ? 1 : (moveX < -settings.getDetectionEdgeThreshold() ? -1 : 0);
        int zDir = moveZ > settings.getDetectionEdgeThreshold() ? 1 : (moveZ < -settings.getDetectionEdgeThreshold() ? -1 : 0);

        if (xDir != 0) {
            addBlockIfValid(blocks, state, world, baseX + xDir, baseY, baseZ);
            for (int i = 1; i <= settings.getDetectionAdditionalBelow(); i++) {
                addBlockIfValid(blocks, state, world, baseX + xDir, baseY - i, baseZ);
            }
        }

        if (zDir != 0) {
            addBlockIfValid(blocks, state, world, baseX, baseY, baseZ + zDir);
            for (int i = 1; i <= settings.getDetectionAdditionalBelow(); i++) {
                addBlockIfValid(blocks, state, world, baseX, baseY - i, baseZ + zDir);
            }
        }

        if (xDir != 0 && zDir != 0) {
            addBlockIfValid(blocks, state, world, baseX + xDir, baseY, baseZ + zDir);
            for (int i = 1; i <= settings.getDetectionAdditionalBelow(); i++) {
                addBlockIfValid(blocks, state, world, baseX + xDir, baseY - i, baseZ + zDir);
            }
        }

        return blocks;
    }

    private Block findBaseBlock(TNTRunArenaState state, Location playerLocation) {
        World world = playerLocation.getWorld();
        if (world == null) {
            return null;
        }

        int playerX = playerLocation.getBlockX();
        int playerZ = playerLocation.getBlockZ();

        for (int depth = 1; depth <= settings.getDetectionScanDepth(); depth++) {
            Block candidate = world.getBlockAt(playerX, playerLocation.getBlockY() - depth, playerZ);
            if (isFloorCandidate(state, candidate)) {
                return candidate;
            }
        }

        Block standing = world.getBlockAt(playerX, playerLocation.getBlockY(), playerZ);
        if (isFloorCandidate(state, standing)) {
            return standing;
        }

        return null;
    }

    private void addBlockIfValid(Set<Block> blocks, TNTRunArenaState state, World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        if (isFloorCandidate(state, block)) {
            blocks.add(block);
        }
    }

    private boolean isFloorCandidate(TNTRunArenaState state, Block block) {
        if (block == null || block.getWorld() == null) {
            return false;
        }
        Material type = block.getType();
        if (type == Material.AIR || type == Material.BARRIER) {
            return false;
        }
        return state.findFloorAt(TNTRunBlockKey.from(block.getLocation())) != null;
    }

    private Material parseMaterial(String value, Material fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Material.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return fallback;
        }
    }

    private List<Location> parseBlocks(List<String> blockList) {
        List<Location> locations = new ArrayList<>();
        if (blockList == null) {
            return locations;
        }

        for (String entry : blockList) {
            if (entry == null) {
                continue;
            }

            String[] parts = entry.split(",");
            if (parts.length != 5 && parts.length != 8) {
                continue;
            }

            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                continue;
            }

            Integer startX = parseInt(parts[1]);
            Integer startY = parseInt(parts[2]);
            Integer startZ = parseInt(parts[3]);

            if (startX == null || startY == null || startZ == null) {
                continue;
            }

            int endX = startX;
            int endY = startY;
            int endZ = startZ;

            if (parts.length == 8) {
                Integer parsedEndX = parseInt(parts[4]);
                Integer parsedEndY = parseInt(parts[5]);
                Integer parsedEndZ = parseInt(parts[6]);
                if (parsedEndX == null || parsedEndY == null || parsedEndZ == null) {
                    continue;
                }
                endX = parsedEndX;
                endY = parsedEndY;
                endZ = parsedEndZ;
            }

            Material material = Material.getMaterial(parts[parts.length - 1]);
            if (material == null || material == Material.AIR || material == Material.BARRIER) {
                continue;
            }

            for (int x = startX; x <= endX; x++) {
                for (int y = startY; y <= endY; y++) {
                    for (int z = startZ; z <= endZ; z++) {
                        locations.add(new Location(world, x, y, z));
                    }
                }
            }
        }

        return locations;
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

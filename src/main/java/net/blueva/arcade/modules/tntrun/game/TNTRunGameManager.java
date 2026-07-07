package net.blueva.arcade.modules.tntrun.game;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.modules.tntrun.state.TNTRunArenaState;
import net.blueva.arcade.modules.tntrun.state.TNTRunFloor;
import net.blueva.arcade.modules.tntrun.support.TNTRunBlockService;
import net.blueva.arcade.modules.tntrun.support.TNTRunFallingBlockService;
import net.blueva.arcade.modules.tntrun.support.TNTRunJumpService;
import net.blueva.arcade.modules.tntrun.support.TNTRunLoadoutService;
import net.blueva.arcade.modules.tntrun.support.TNTRunMessagingService;
import net.blueva.arcade.modules.tntrun.support.TNTRunSettings;
import net.blueva.arcade.modules.tntrun.support.TNTRunStatsService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TNTRunGameManager {

    private final ModuleInfo moduleInfo;
    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final TNTRunStatsService statsService;
    private final TNTRunLoadoutService loadoutService;
    private final TNTRunMessagingService messagingService;
    private final TNTRunBlockService blockService;
    private final TNTRunFallingBlockService fallingBlockService;
    private final TNTRunJumpService jumpService;
    private final TNTRunSettings settings;

    private final Map<Integer, TNTRunArenaState> arenaStates = new ConcurrentHashMap<>();
    private final Map<Player, Integer> playerArenas = new ConcurrentHashMap<>();

    public TNTRunGameManager(ModuleInfo moduleInfo,
                             ModuleConfigAPI moduleConfig,
                             CoreConfigAPI coreConfig,
                             TNTRunStatsService statsService,
                             TNTRunLoadoutService loadoutService,
                             TNTRunMessagingService messagingService,
                             TNTRunBlockService blockService,
                             TNTRunFallingBlockService fallingBlockService,
                             TNTRunJumpService jumpService,
                             TNTRunSettings settings) {
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.statsService = statsService;
        this.loadoutService = loadoutService;
        this.messagingService = messagingService;
        this.blockService = blockService;
        this.fallingBlockService = fallingBlockService;
        this.jumpService = jumpService;
        this.settings = settings;
    }

    public void handleStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);
        fallingBlockService.cleanupArena(arenaId);

        List<TNTRunFloor> floors = blockService.loadFloors(context);
        TNTRunArenaState state = new TNTRunArenaState(context, floors, jumpService.resolveMaxJumps(context));
        arenaStates.put(arenaId, state);
        statsService.resetArena(arenaId);

        for (Player player : context.getPlayers()) {
            playerArenas.put(player, arenaId);
        }

        messagingService.sendDescription(context);
    }

    public void handleCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    int secondsLeft) {
        messagingService.sendCountdownTick(context, secondsLeft);
    }

    public void handleCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        messagingService.sendCountdownFinish(context);
    }

    public boolean freezePlayersOnCountdown() {
        return false;
    }

    public void handleGameStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();
        TNTRunArenaState state = arenaStates.get(arenaId);
        if (state == null) {
            state = new TNTRunArenaState(context, blockService.loadFloors(context), jumpService.resolveMaxJumps(context));
            arenaStates.put(arenaId, state);
        }

        messagingService.sendStartTitle(context);
        startGameTimer(context, state);
        startTrailScanTimer(context, state);
        blockService.startFloorRemovalTimers(context, state);

        for (Player player : context.getPlayers()) {
            loadoutService.giveStartingItems(player);
            loadoutService.applyStartingEffects(player);
            jumpService.initializeJumps(state, player);
            if (state.getMaxDoubleJumps() > 0) {
                player.setAllowFlight(true);
                player.setFlying(false);
            }
            context.getScoreboardAPI().showScoreboard(player, messagingService.getScoreboardPath());
        }
    }

    private void startGameTimer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                TNTRunArenaState state) {
        int arenaId = context.getArenaId();

        Integer gameTime = context.getDataAccess().getGameData("basic.time", Integer.class);
        if (gameTime == null || gameTime == 0) {
            gameTime = moduleConfig.getInt("gameplay.time_limit_seconds", 300);
        }

        final int[] timeLeft = {gameTime};
        state.setTimeLeft(gameTime);

        String taskId = "arena_" + arenaId + "_tnt_run_timer";

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (state.isEnded()) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            timeLeft[0]--;
            state.setTimeLeft(timeLeft[0]);

            List<Player> alivePlayers = context.getAlivePlayers();
            List<Player> allPlayers = context.getPlayers();

            if (alivePlayers.size() <= 1 || timeLeft[0] <= 0) {
                endGameOnce(context, state);
                return;
            }
            for (Player player : allPlayers) {
                String actionBarTemplate = coreConfig.getLanguage(player, "action_bar.in_game.global");
                if (!player.isOnline()) {
                    continue;
                }

                Map<String, String> customPlaceholders = getCustomPlaceholders(player);
                customPlaceholders.put("time", formatCountdownTime(timeLeft[0]));
                customPlaceholders.put("alive", String.valueOf(alivePlayers.size()));
                customPlaceholders.put("spectators", String.valueOf(context.getSpectators().size()));

                if (actionBarTemplate != null) {
                    String actionBarMessage = actionBarTemplate
                            .replace("{time}", formatCountdownTime(timeLeft[0]))
                            .replace("{round}", String.valueOf(context.getCurrentRound()))
                            .replace("{round_max}", String.valueOf(context.getMaxRounds()));
                    context.getMessagesAPI().sendActionBar(player, actionBarMessage);
                }

                context.getScoreboardAPI().update(player, messagingService.getScoreboardPath(), customPlaceholders);
            }
        }, 0L, 20L);
    }

    private void startTrailScanTimer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                     TNTRunArenaState state) {
        int arenaId = context.getArenaId();
        int intervalTicks = Math.max(1, settings.getDetectionStationaryScanTicks());
        String taskId = "arena_" + arenaId + "_tnt_run_trail_scan";

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (state.isEnded()) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            if (context.getPhase() != GamePhase.PLAYING) {
                return;
            }

            for (Player player : context.getAlivePlayers()) {
                if (player == null || !player.isOnline()) {
                    continue;
                }

                if (!context.isPlayerPlaying(player)) {
                    continue;
                }

                Location location = player.getLocation();
                if (blockService.shouldEliminate(context, location)) {
                    handlePlayerElimination(player);
                    continue;
                }

                // Update player position tracking for stationary detection
                blockService.updatePlayerPosition(context, state, player, location);

                blockService.handlePlayerStep(context, state, player, location);
            }
        }, 0L, intervalTicks);
    }

    private void endGameOnce(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                             TNTRunArenaState state) {
        if (state.markEnded()) {
            return;
        }

        context.getSchedulerAPI().cancelArenaTasks(context.getArenaId());

        List<Player> alivePlayers = new ArrayList<>(context.getAlivePlayers());
        if (alivePlayers.size() == 1) {
            Player winner = alivePlayers.get(0);
            context.setWinner(winner);
            handleWin(winner);
            messagingService.sendVictoryMessage(context, winner);
        }

        context.endGame();
    }

    public void handleEnd(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        arenaStates.remove(arenaId);
        fallingBlockService.cleanupArena(arenaId);
        statsService.resetArena(arenaId);

        for (Player player : context.getPlayers()) {
            playerArenas.remove(player);
        }

        statsService.recordGamesPlayed(context.getPlayers());
    }

    public void handleDisable() {
        if (!arenaStates.isEmpty()) {
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> anyContext =
                    arenaStates.values().iterator().next().getContext();
            anyContext.getSchedulerAPI().cancelModuleTasks(moduleInfo.getId());
        }

        arenaStates.clear();
        playerArenas.clear();
        fallingBlockService.cleanupAll();
    }

    public void handlePlayerElimination(Player player) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context == null) {
            return;
        }

        // Don't eliminate spectators
        if (context.getSpectators().contains(player)) {
            return;
        }

        int arenaId = context.getArenaId();
        TNTRunArenaState state = arenaStates.get(arenaId);
        if (state == null) {
            return;
        }

        if (!state.getEliminatedPlayers().add(player.getUniqueId())) {
            return;
        }

        messagingService.sendDeathMessage(context, player);
        context.eliminatePlayer(player, moduleConfig.getTranslation(player, "messages.eliminated"));
        player.getInventory().clear();
        
        // Clear double jump state without modifying flight (we'll set it correctly below)
        jumpService.clearJumps(state, player, false);
        
        // Teleport player to spawn point to prevent falling in void
        Location spawn = context.getArenaAPI().getRandomSpawn();
        if (spawn != null) {
            player.teleport(spawn);
        }
        
        // Set to spectator mode immediately
        context.setPlayerSpectating(player, true);
        
        // Schedule flight activation 1 second later to avoid conflicts with double jump mechanics
        context.getSchedulerAPI().runLater(
                "tnt_run_spectator_flight_" + arenaId + "_" + player.getUniqueId(),
                () -> {
                    // Only enable flight if player is still in spectator mode
                    if (player.isOnline() && context.isPlayerSpectating(player)) {
                        player.setAllowFlight(true);
                        player.setFlying(true);
                    }
                },
                20L // 1 second delay
        );
        
        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.respawn"));
    }

    public void handleRespawnEffects(Player player) {
        loadoutService.applyRespawnEffects(player);
    }

    public void refreshDoubleJumpState(Player player) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context == null) {
            return;
        }

        TNTRunArenaState state = arenaStates.get(context.getArenaId());
        if (state == null) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        if (!context.isPlayerPlaying(player)) {
            return;
        }

        // Don't touch flight state for spectators - they need it to move
        if (context.isPlayerSpectating(player)) {
            return;
        }

        if (state.getMaxDoubleJumps() <= 0) {
            player.setAllowFlight(false);
            player.setFlying(false);
            return;
        }

        int remaining = jumpService.getRemainingJumps(state, player);
        if (remaining <= 0) {
            player.setAllowFlight(false);
            player.setFlying(false);
            return;
        }

        if (player.isOnGround() && !player.getAllowFlight()) {
            player.setAllowFlight(true);
        }
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getGameContext(Player player) {
        Integer arenaId = playerArenas.get(player);
        if (arenaId == null) {
            return null;
        }
        TNTRunArenaState state = arenaStates.get(arenaId);
        return state != null ? state.getContext() : null;
    }

    public Map<String, String> getCustomPlaceholders(Player player) {
        Map<String, String> placeholders = new HashMap<>();

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context != null) {
            TNTRunArenaState state = arenaStates.get(context.getArenaId());
            placeholders.put("alive", String.valueOf(context.getAlivePlayers().size()));
            placeholders.put("spectators", String.valueOf(context.getSpectators().size()));
            if (state != null) {
                placeholders.put("time", formatCountdownTime(state.getTimeLeft()));
                placeholders.put("double_jumps", String.valueOf(jumpService.getRemainingJumps(state, player)));
                placeholders.put("double_jumps_max", String.valueOf(state.getMaxDoubleJumps()));
            }
        }

        return placeholders;
    }

    public void handlePlayerStep(Player player, Location to) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context == null) {
            return;
        }

        TNTRunArenaState state = arenaStates.get(context.getArenaId());
        if (state == null) {
            return;
        }

        blockService.handlePlayerStep(context, state, player, to);
    }

    public boolean shouldEliminate(Player player, Location to) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context == null) {
            return false;
        }

        TNTRunArenaState state = arenaStates.get(context.getArenaId());
        if (state == null) {
            return false;
        }

        return blockService.shouldEliminate(context, to);
    }

    public void handleWin(Player player) {
        Integer arenaId = playerArenas.get(player);
        if (arenaId == null) {
            return;
        }
        statsService.recordWin(player, arenaId);
    }

    public boolean isTrackedFallingBlock(java.util.UUID uuid) {
        return fallingBlockService.isTrackedFallingBlock(uuid);
    }

    public void handleFallingBlockLand(org.bukkit.entity.FallingBlock fallingBlock) {
        fallingBlockService.handleFallingBlockLand(fallingBlock);
    }

    public boolean handleDoubleJump(Player player) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context == null) {
            return false;
        }

        TNTRunArenaState state = arenaStates.get(context.getArenaId());
        if (state == null) {
            return false;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            return false;
        }

        if (!context.isPlayerPlaying(player)) {
            return false;
        }

        // Don't handle double jump for spectators - they have normal flight
        if (context.isPlayerSpectating(player)) {
            return false;
        }

        if (player.isOnGround()) {
            return true;
        }

        boolean jumped = jumpService.performDoubleJump(context, state, player);
        if (!jumped) {
            player.setAllowFlight(false);
            player.setFlying(false);
            return true;
        }

        return true;
    }

    private static String formatCountdownTime(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        return String.format("%02d:%02d", safeSeconds / 60, safeSeconds % 60);
    }

}

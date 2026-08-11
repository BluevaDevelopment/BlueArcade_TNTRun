package net.blueva.arcade.modules.tntrun.listener;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.modules.tntrun.game.TNTRunGameManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;

public class TNTRunListener implements Listener {

    private final TNTRunGameManager gameManager;

    public TNTRunListener(TNTRunGameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                gameManager.getGameContext(player);

        if (context == null) {
            return;
        }

        if (!context.isPlayerPlaying(player)) {
            return;
        }

        // Spectators should not trigger sand physics
        if (context.isPlayerSpectating(player)) {
            return;
        }

        Location to = event.getTo();
        if (to == null) {
            return;
        }

        if (context.getPhase() == GamePhase.COUNTDOWN && gameManager.freezePlayersOnCountdown()) {
            player.teleport(event.getFrom());
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            if (!context.isInsideBounds(to)) {
                Location spawn = context.getArenaAPI().getRandomSpawn();
                if (spawn != null) {
                    player.teleport(spawn);
                }
            }
            return;
        }

        gameManager.refreshDoubleJumpState(player);

        if (gameManager.shouldEliminate(player, to)) {
            gameManager.handlePlayerElimination(player);
            return;
        }

        if (hasChangedBlock(event)) {
            gameManager.handlePlayerStep(player, to);
        }
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        if (!gameManager.handleDoubleJump(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fallingBlock)) {
            return;
        }

        if (!gameManager.isTrackedFallingBlock(fallingBlock.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        gameManager.handleFallingBlockLand(fallingBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                gameManager.getGameContext(player);

        if (context == null) {
            return;
        }

        if (!context.isPlayerPlaying(player)) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }

        if (player.getHealth() - event.getFinalDamage() > 0) {
            return;
        }

        event.setCancelled(true);
        gameManager.handlePlayerElimination(player);
    }

    private boolean hasChangedBlock(PlayerMoveEvent event) {
        if (event.getFrom() == null || event.getTo() == null) {
            return false;
        }

        return event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()
                || !event.getFrom().getWorld().equals(event.getTo().getWorld());
    }
}

package net.blueva.arcade.modules.tntrun.support;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.tntrun.state.TNTRunArenaState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class TNTRunJumpService {

    private final TNTRunSettings settings;

    public TNTRunJumpService(TNTRunSettings settings) {
        this.settings = settings;
    }

    public int resolveMaxJumps(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        Integer value = context.getDataAccess().getGameData("basic.double_jump_uses", Integer.class);
        if (value == null) {
            value = settings.getDefaultDoubleJumpUses();
        }
        return Math.min(settings.getMaxDoubleJumpUses(), Math.max(0, value));
    }

    public void initializeJumps(TNTRunArenaState state, Player player) {
        if (state.getMaxDoubleJumps() <= 0) {
            return;
        }
        state.getDoubleJumps().put(player.getUniqueId(), state.getMaxDoubleJumps());
    }

    public int getRemainingJumps(TNTRunArenaState state, Player player) {
        return state.getDoubleJumps().getOrDefault(player.getUniqueId(), 0);
    }

    public void clearJumps(TNTRunArenaState state, Player player) {
        clearJumps(state, player, true);
    }
    
    public void clearJumps(TNTRunArenaState state, Player player, boolean updateFlightState) {
        state.getDoubleJumps().remove(player.getUniqueId());
        if (updateFlightState) {
            // Don't disable flight for spectators - they need it to move
            if (player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        }
    }

    public boolean performDoubleJump(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                     TNTRunArenaState state,
                                     Player player) {
        int remaining = getRemainingJumps(state, player);
        if (remaining <= 0) {
            player.setAllowFlight(false);
            player.setFlying(false);
            return false;
        }

        state.getDoubleJumps().put(player.getUniqueId(), remaining - 1);

        double horizontalBoost = resolveHorizontalBoost(context);
        double verticalBoost = resolveVerticalBoost(context);

        player.setFlying(false);
        Vector direction = player.getLocation().getDirection().clone();
        direction.setY(0).normalize();

        Vector velocity = player.getVelocity();
        if (velocity.lengthSquared() > 0.01) {
            velocity.setX(velocity.getX() * 1.2 + direction.getX() * horizontalBoost);
            velocity.setZ(velocity.getZ() * 1.2 + direction.getZ() * horizontalBoost);
        } else {
            velocity.setX(direction.getX() * horizontalBoost);
            velocity.setZ(direction.getZ() * horizontalBoost);
        }

        velocity.setY(verticalBoost);
        player.setVelocity(velocity);
        context.getSoundsAPI().play(player, context.getCoreConfig().getSound("sounds.in_game.double_jump"));

        if (getRemainingJumps(state, player) <= 0) {
            context.getSchedulerAPI().runLater("arena_" + context.getArenaId() + "_tnt_run_disable_flight_" + player.getUniqueId(),
                    () -> {
                        if (player.isOnline()) {
                            player.setAllowFlight(false);
                        }
                    }, 5L);
        }

        return true;
    }

    private double resolveVerticalBoost(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        Double value = context.getDataAccess().getGameData("basic.double_jump_vertical_boost", Double.class);
        return value != null ? value : settings.getDoubleJumpVerticalBoost();
    }

    private double resolveHorizontalBoost(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        Double value = context.getDataAccess().getGameData("basic.double_jump_horizontal_boost", Double.class);
        return value != null ? value : settings.getDoubleJumpHorizontalBoost();
    }
}

package net.blueva.arcade.modules.tntrun.support;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import org.bukkit.Particle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TNTRunSettings {

    private int detectionScanDepth = 3;
    private int detectionAdditionalBelow = 1;
    private double detectionEdgeThreshold = 0.25;
    private int floorDelayTicks = 10;
    private int floorDelayMinTicks = 1;
    private int floorDelayMaxTicks = 100;
    private int defaultAutoRemoveSeconds = 0;
    private int autoRemoveMaxSeconds = 600;
    private final List<Integer> removalWarnings = new ArrayList<>();
    private int defaultDoubleJumpUses = 0;
    private int maxDoubleJumpUses = 10;
    private double doubleJumpVerticalBoost = 0.8;
    private double doubleJumpHorizontalBoost = 0.8;
    private double doubleJumpBoostMin = 0.1;
    private double doubleJumpBoostMax = 2.0;
    private boolean particlesEnabled = true;
    private Particle particleType = Particle.BLOCK;
    private int particleCount = 12;
    private double particleSpread = 0.35;
    private double particleSpeed = 0.15;
    private boolean fallingBlocksEnabled = true;
    private double fallingDownwardVelocity = 0.3;
    private double fallingHorizontalRandomness = 0.08;

    public void load(ModuleConfigAPI moduleConfig) {
        detectionScanDepth = Math.max(1, moduleConfig.getInt("trail.detection.scan_depth", 3));
        detectionAdditionalBelow = Math.max(0, moduleConfig.getInt("trail.detection.additional_blocks_below", 1));
        detectionEdgeThreshold = Math.max(0.0, moduleConfig.getDouble("trail.detection.edge_threshold", 0.25));

        floorDelayTicks = Math.max(1, moduleConfig.getInt("trail.floor_delay_ticks", 10));
        floorDelayMinTicks = Math.max(1, moduleConfig.getInt("trail.floor_delay_limits.min", 1));
        floorDelayMaxTicks = Math.max(floorDelayMinTicks, moduleConfig.getInt("trail.floor_delay_limits.max", 100));

        defaultAutoRemoveSeconds = Math.max(0, moduleConfig.getInt("floors.default_auto_remove_seconds", 0));
        autoRemoveMaxSeconds = Math.max(0, moduleConfig.getInt("floors.auto_remove_max_seconds", 600));

        removalWarnings.clear();
        List<String> warnings = moduleConfig.getStringList("floors.auto_remove_warnings_seconds");
        if (warnings != null) {
            for (String warning : warnings) {
                try {
                    removalWarnings.add(Integer.parseInt(warning));
                } catch (NumberFormatException ignored) {
                    // ignore invalid
                }
            }
        }

        defaultDoubleJumpUses = Math.max(0, moduleConfig.getInt("double_jump.default_uses", 0));
        maxDoubleJumpUses = Math.max(1, moduleConfig.getInt("double_jump.max_uses", 10));
        doubleJumpVerticalBoost = moduleConfig.getDouble("double_jump.boost.vertical", 0.8);
        doubleJumpHorizontalBoost = moduleConfig.getDouble("double_jump.boost.horizontal", 0.8);
        doubleJumpBoostMin = moduleConfig.getDouble("double_jump.boost_limits.min", 0.1);
        doubleJumpBoostMax = moduleConfig.getDouble("double_jump.boost_limits.max", 2.0);

        particlesEnabled = moduleConfig.getBoolean("blocks.particles.enabled", true);
        particleType = parseParticle(moduleConfig.getString("blocks.particles.type", "BLOCK"));
        particleCount = moduleConfig.getInt("blocks.particles.count", 12);
        particleSpread = moduleConfig.getDouble("blocks.particles.spread", 0.35);
        particleSpeed = moduleConfig.getDouble("blocks.particles.speed", 0.15);

        fallingBlocksEnabled = moduleConfig.getBoolean("blocks.falling.enabled", true);
        fallingDownwardVelocity = moduleConfig.getDouble("blocks.falling.downward_velocity", 0.3);
        fallingHorizontalRandomness = moduleConfig.getDouble("blocks.falling.horizontal_randomness", 0.08);
    }

    public int getDetectionScanDepth() {
        return detectionScanDepth;
    }

    public int getDetectionAdditionalBelow() {
        return detectionAdditionalBelow;
    }

    public double getDetectionEdgeThreshold() {
        return detectionEdgeThreshold;
    }

    public int getFloorDelayTicks() {
        return floorDelayTicks;
    }

    public int getFloorDelayMinTicks() {
        return floorDelayMinTicks;
    }

    public int getFloorDelayMaxTicks() {
        return floorDelayMaxTicks;
    }

    public int getDefaultAutoRemoveSeconds() {
        return defaultAutoRemoveSeconds;
    }

    public int getAutoRemoveMaxSeconds() {
        return autoRemoveMaxSeconds;
    }

    public List<Integer> getRemovalWarnings() {
        return Collections.unmodifiableList(removalWarnings);
    }

    public int getDefaultDoubleJumpUses() {
        return defaultDoubleJumpUses;
    }

    public int getMaxDoubleJumpUses() {
        return maxDoubleJumpUses;
    }

    public double getDoubleJumpVerticalBoost() {
        return doubleJumpVerticalBoost;
    }

    public double getDoubleJumpHorizontalBoost() {
        return doubleJumpHorizontalBoost;
    }

    public double getDoubleJumpBoostMin() {
        return doubleJumpBoostMin;
    }

    public double getDoubleJumpBoostMax() {
        return doubleJumpBoostMax;
    }

    public boolean isParticlesEnabled() {
        return particlesEnabled;
    }

    public Particle getParticleType() {
        return particleType;
    }

    public int getParticleCount() {
        return particleCount;
    }

    public double getParticleSpread() {
        return particleSpread;
    }

    public double getParticleSpeed() {
        return particleSpeed;
    }

    public boolean isFallingBlocksEnabled() {
        return fallingBlocksEnabled;
    }

    public double getFallingDownwardVelocity() {
        return fallingDownwardVelocity;
    }

    public double getFallingHorizontalRandomness() {
        return fallingHorizontalRandomness;
    }

    private Particle parseParticle(String name) {
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return Particle.BLOCK;
        }
    }
}

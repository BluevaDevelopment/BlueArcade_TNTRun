package net.blueva.arcade.modules.tntrun;

import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.achievements.AchievementsAPI;
import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.events.CustomEventRegistry;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GameModule;
import net.blueva.arcade.api.game.GameResult;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.ui.VoteMenuAPI;
import net.blueva.arcade.modules.tntrun.game.TNTRunGameManager;
import net.blueva.arcade.modules.tntrun.listener.TNTRunListener;
import net.blueva.arcade.modules.tntrun.setup.TNTRunSetup;
import net.blueva.arcade.modules.tntrun.support.TNTRunBlockService;
import net.blueva.arcade.modules.tntrun.support.TNTRunFallingBlockService;
import net.blueva.arcade.modules.tntrun.support.TNTRunJumpService;
import net.blueva.arcade.modules.tntrun.support.TNTRunLoadoutService;
import net.blueva.arcade.modules.tntrun.support.TNTRunMessagingService;
import net.blueva.arcade.modules.tntrun.support.TNTRunSettings;
import net.blueva.arcade.modules.tntrun.support.TNTRunStatsService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import net.blueva.arcade.api.setup.ModuleSetupCommand;
import net.blueva.arcade.api.setup.ModuleSetupMetadata;
import net.blueva.arcade.api.setup.ModuleSetupStep;
import net.blueva.arcade.api.setup.ModuleSetupStatusCheck;
import java.util.List;

public class TNTRunModule implements GameModule<Player, Location, World, Material, ItemStack, Sound, Block, Entity, Listener, EventPriority> {

    private ModuleConfigAPI moduleConfig;
    private CoreConfigAPI coreConfig;
    private ModuleInfo moduleInfo;
    private TNTRunGameManager gameManager;

    @Override
    public void onLoad() {
        moduleInfo = ModuleAPI.getModuleInfo("tnt_run");

        if (moduleInfo == null) {
            throw new IllegalStateException("ModuleInfo not available for TNT Run module");
        }

        moduleConfig = ModuleAPI.getModuleConfig(moduleInfo.getId());
        coreConfig = ModuleAPI.getCoreConfig();
        StatsAPI statsAPI = ModuleAPI.getStatsAPI();
        VoteMenuAPI voteMenu = ModuleAPI.getVoteMenuAPI();
        AchievementsAPI achievementsAPI = ModuleAPI.getAchievementsAPI();

        moduleConfig.register("settings.yml");
        moduleConfig.register("achievements.yml");

        TNTRunSettings settings = new TNTRunSettings();
        settings.load(moduleConfig);

        TNTRunStatsService statsService = new TNTRunStatsService(statsAPI, moduleInfo, moduleConfig);
        statsService.registerStats();

        TNTRunLoadoutService loadoutService = new TNTRunLoadoutService(moduleConfig);
        TNTRunMessagingService messagingService = new TNTRunMessagingService(moduleConfig, coreConfig, moduleInfo);
        TNTRunFallingBlockService fallingBlockService = new TNTRunFallingBlockService(settings);
        TNTRunBlockService blockService = new TNTRunBlockService(settings, statsService, fallingBlockService, messagingService);
        TNTRunJumpService jumpService = new TNTRunJumpService(settings);

        gameManager = new TNTRunGameManager(moduleInfo, moduleConfig, coreConfig, statsService,
                loadoutService, messagingService, blockService, fallingBlockService, jumpService, settings);

        if (achievementsAPI != null) {
            achievementsAPI.registerModuleAchievements(moduleInfo.getId(), "achievements.yml");
        }

        ModuleAPI.getSetupAPI().registerHandler(moduleInfo.getId(), new TNTRunSetup(moduleConfig, coreConfig, settings));

        if (moduleConfig != null && voteMenu != null) {
            voteMenu.registerGame(
                    moduleInfo.getId(),
                    Material.valueOf(moduleConfig.getString("menus.vote.item")),
                    moduleConfig.getTranslation(null, "vote_menu.name"),
                    moduleConfig.getTranslationList(null, "vote_menu.lore")
            );
        }
    }

    @Override
    public void onStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        gameManager.handleStart(context);
    }

    @Override
    public void onCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                int secondsLeft) {
        gameManager.handleCountdownTick(context, secondsLeft);
    }

    @Override
    public void onCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        gameManager.handleCountdownFinish(context);
    }

    @Override
    public boolean freezePlayersOnCountdown() {
        return gameManager.freezePlayersOnCountdown();
    }

    @Override
    public void onGameStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        gameManager.handleGameStart(context);
    }

    @Override
    public void onEnd(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                      GameResult<Player> result) {
        gameManager.handleEnd(context);
    }

    @Override
    public void onDisable() {
        gameManager.handleDisable();
    }

    @Override
    public void registerEvents(CustomEventRegistry<Listener, EventPriority> registry) {
        registry.register(new TNTRunListener(gameManager));
    }

    @Override
    public Map<String, String> getCustomPlaceholders(Player player) {
        return gameManager.getCustomPlaceholders(player);
    }


    @Override
    public boolean requiresSpawnCapacityValidation() {
        return false;
    }

    @Override
    public ModuleSetupMetadata getSetupMetadata() {
        return new ModuleSetupMetadata() {

            @Override
            public List<ModuleSetupStep> getSetupSteps() {
                return List.of(
                        new ModuleSetupStep("doublejump", true, "Configure Doublejump", "Configure the module-specific doublejump setup data.", List.of("/baa game <arena> tnt_run doublejump"), "number of uses"),
                        new ModuleSetupStep("floor", true, "Configure Floor", "Configure the module-specific floor setup data.", List.of("/baa game <arena> tnt_run floor"), "selection region")
                );
            }

            @Override
            public List<ModuleSetupCommand> getSetupCommands() {
                return List.of(
                        new ModuleSetupCommand("doublejump", "/baa game <arena> tnt_run doublejump", "Configure doublejump setup data.", true),
                        new ModuleSetupCommand("floor", "/baa game <arena> tnt_run floor", "Configure floor setup data.", true)
                );
            }

            @Override
            public List<ModuleSetupStatusCheck<?, ?, ?>> getStatusChecks() {
                return List.of(
                        new ModuleSetupStatusCheck<>("doublejump", true, "Set the double-jump uses.", context -> context.getData().getInt("basic.double_jump_uses", -1) >= 0),
                        new ModuleSetupStatusCheck<>("floor", true, "Add at least one floor region.", context -> context.getData().getInt("game.floors.total", 0) > 0)
                );
            }
        };
    }

}

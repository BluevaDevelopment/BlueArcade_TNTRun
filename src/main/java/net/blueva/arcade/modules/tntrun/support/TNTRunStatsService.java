package net.blueva.arcade.modules.tntrun.support;

import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatDefinition;
import net.blueva.arcade.api.stats.StatScope;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TNTRunStatsService {

    private final StatsAPI statsAPI;
    private final ModuleInfo moduleInfo;
    private final ModuleConfigAPI moduleConfig;
    private final Map<Integer, UUID> arenaWinners = new ConcurrentHashMap<>();

    public TNTRunStatsService(StatsAPI statsAPI, ModuleInfo moduleInfo, ModuleConfigAPI moduleConfig) {
        this.statsAPI = statsAPI;
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
    }

    public void registerStats() {
        if (statsAPI == null) {
            return;
        }

        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("wins", moduleConfig.getTranslation(null, "stats.labels.wins"), moduleConfig.getTranslation(null, "stats.descriptions.wins"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("games_played", moduleConfig.getTranslation(null, "stats.labels.games_played"), moduleConfig.getTranslation(null, "stats.descriptions.games_played"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("blocks_dropped", moduleConfig.getTranslation(null, "stats.labels.blocks_dropped"), moduleConfig.getTranslation(null, "stats.descriptions.blocks_dropped"), StatScope.MODULE));
    }

    public void resetArena(int arenaId) {
        arenaWinners.remove(arenaId);
    }

    public void recordWin(Player player, int arenaId) {
        if (statsAPI == null || player == null) {
            return;
        }

        if (!arenaWinners.containsKey(arenaId)) {
            arenaWinners.put(arenaId, player.getUniqueId());
            statsAPI.addModuleStat(player, moduleInfo.getId(), "wins", 1);
            statsAPI.addGlobalStat(player, "wins", 1);
        }
    }

    public void recordBlockDrop(Player player) {
        if (statsAPI == null || player == null) {
            return;
        }
        statsAPI.addModuleStat(player, moduleInfo.getId(), "blocks_dropped", 1);
    }

    public void recordGamesPlayed(Collection<Player> players) {
        if (statsAPI == null || players == null) {
            return;
        }

        for (Player player : players) {
            statsAPI.addModuleStat(player, moduleInfo.getId(), "games_played", 1);
        }
    }
}

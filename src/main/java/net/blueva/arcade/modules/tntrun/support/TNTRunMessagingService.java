package net.blueva.arcade.modules.tntrun.support;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.module.ModuleInfo;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class TNTRunMessagingService {

    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final ModuleInfo moduleInfo;

    public TNTRunMessagingService(ModuleConfigAPI moduleConfig, CoreConfigAPI coreConfig, ModuleInfo moduleInfo) {
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.moduleInfo = moduleInfo;
    }

    public void sendDescription(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        List<String> description = moduleConfig.getStringListFrom("language.yml", "description");
        for (Player player : context.getPlayers()) {
            for (String line : description) {
                context.getMessagesAPI().sendRaw(player, line);
            }
        }
    }

    public void sendCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                  int secondsLeft) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }

            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.countdown"));

            String title = coreConfig.getLanguage("titles.starting_game.title")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            String subtitle = coreConfig.getLanguage("titles.starting_game.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 5);
        }
    }

    public void sendCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }

            String title = coreConfig.getLanguage("titles.game_started.title")
                    .replace("{game_display_name}", moduleInfo.getName());

            String subtitle = coreConfig.getLanguage("titles.game_started.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName());

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 20);
            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.start"));
        }
    }

    public void sendStartTitle(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        String title = coreConfig.getLanguage("titles.game_started.title")
                .replace("{game_display_name}", moduleInfo.getName());
        String subtitle = coreConfig.getLanguage("titles.game_started.subtitle")
                .replace("{game_display_name}", moduleInfo.getName());

        for (Player player : context.getPlayers()) {
            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 10);
        }
    }

    public void sendDeathMessage(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                 Player victim) {
        String message = getRandomMessage("messages.deaths.generic");
        if (message == null) {
            return;
        }

        message = message.replace("{victim}", victim.getName());

        for (Player player : context.getPlayers()) {
            context.getMessagesAPI().sendRaw(player, message);
        }
    }

    public void sendVictoryMessage(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                   Player winner) {
        String message = getRandomMessage("messages.victory");
        if (message == null) {
            return;
        }

        message = message.replace("{winner}", winner.getName());

        for (Player player : context.getPlayers()) {
            context.getMessagesAPI().sendRaw(player, message);
        }
    }

    public void sendFloorRemovalWarning(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                        int floorIndex,
                                        int secondsLeft) {
        String message = moduleConfig.getStringFrom("language.yml", "messages.floor_removal_warning");
        if (message == null) {
            return;
        }

        message = message.replace("{floor}", String.valueOf(floorIndex))
                .replace("{time}", String.valueOf(secondsLeft));

        for (Player player : context.getPlayers()) {
            context.getMessagesAPI().sendRaw(player, message);
        }
    }

    public void sendFloorRemoved(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                 int floorIndex) {
        String message = moduleConfig.getStringFrom("language.yml", "messages.floor_removed");
        if (message == null) {
            return;
        }

        message = message.replace("{floor}", String.valueOf(floorIndex));

        for (Player player : context.getPlayers()) {
            context.getMessagesAPI().sendRaw(player, message);
        }
    }

    public String getScoreboardPath() {
        return "scoreboard.main";
    }

    private String getRandomMessage(String path) {
        List<String> messages = moduleConfig.getStringListFrom("language.yml", path);
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        int index = ThreadLocalRandom.current().nextInt(messages.size());
        return messages.get(index);
    }
}

package net.blueva.arcade.modules.tntrun.setup;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.setup.GameSetupHandler;
import net.blueva.arcade.api.setup.SetupContext;
import net.blueva.arcade.api.setup.SetupDataAPI;
import net.blueva.arcade.api.setup.TabCompleteContext;
import net.blueva.arcade.api.setup.TabCompleteResult;
import net.blueva.arcade.modules.tntrun.support.TNTRunSettings;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class TNTRunSetup implements GameSetupHandler {

    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final TNTRunSettings settings;
    private static final int MAX_FLOOR_SCAN = 100;

    public TNTRunSetup(ModuleConfigAPI moduleConfig, CoreConfigAPI coreConfig, TNTRunSettings settings) {
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.settings = settings;
    }

    @Override
    public boolean handle(SetupContext context) {
        return handleInternal(castSetupContext(context));
    }

    private boolean handleInternal(SetupContext<Player, CommandSender, Location> context) {
        String subcommand = context.getArg(context.getStartIndex() - 1).toLowerCase();

        if ("floor".equals(subcommand)) {
            return handleFloor(context);
        }

        if ("doublejump".equals(subcommand)) {
            return handleDoubleJump(context);
        }

        context.getMessagesAPI().send(context.getPlayer(),
                coreConfig.getLanguage("admin_commands.errors.unknown_subcommand"));
        return true;
    }

    @Override
    public TabCompleteResult tabComplete(TabCompleteContext context) {
        return tabCompleteInternal(castTabContext(context));
    }

    private TabCompleteResult tabCompleteInternal(TabCompleteContext<Player, CommandSender> context) {
        int relIndex = context.getRelativeArgIndex();
        String subcommand = context.getArg(context.getStartIndex() - 1);

        if ("floor".equals(subcommand)) {
            if (relIndex == 0) {
                return TabCompleteResult.of("add", "remove", "list", "delay", "removal");
            }
            if (relIndex == 1 && "removal".equalsIgnoreCase(context.getPreviousArg())) {
                return TabCompleteResult.of("1", "2", "3");
            }
            return TabCompleteResult.empty();
        }

        if ("doublejump".equals(subcommand)) {
            if (relIndex == 0) {
                return TabCompleteResult.of("set", "setboost");
            }
            if (relIndex == 1 && "setboost".equalsIgnoreCase(context.getPreviousArg())) {
                return TabCompleteResult.of("vertical", "horizontal");
            }
            return TabCompleteResult.empty();
        }

        return TabCompleteResult.empty();
    }

    @Override
    public List<String> getSubcommands() {
        return Arrays.asList("floor", "doublejump");
    }

    @Override
    public boolean validateConfig(SetupContext context) {
        return validateConfigInternal(castSetupContext(context));
    }

    private boolean validateConfigInternal(SetupContext<Player, CommandSender, Location> context) {
        SetupDataAPI<Location> data = context.getData();

        boolean hasFloor = resolveFloorTotal(data) > 0;

        if (!hasFloor && context.getSender() != null) {
            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.not_configured")
                            .replace("{arena_id}", String.valueOf(context.getArenaId())));
        }

        return hasFloor;
    }

    private boolean handleFloor(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(1)) {
            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.usage_floor"));
            return true;
        }

        String action = context.getHandlerArg(0).toLowerCase();

        return switch (action) {
            case "add" -> handleFloorAdd(context);
            case "remove" -> handleFloorRemove(context);
            case "list" -> handleFloorList(context);
            case "delay" -> handleFloorDelay(context);
            case "removal" -> handleFloorRemoval(context);
            default -> {
                context.getMessagesAPI().send(context.getPlayer(),
                        moduleConfig.getStringFrom("language.yml", "setup_messages.usage_floor"));
                yield true;
            }
        };
    }

    private boolean handleFloorAdd(SetupContext<Player, CommandSender, Location> context) {
        if (!context.isPlayer()) {
            context.getMessagesAPI().send(context.getPlayer(),
                    coreConfig.getLanguage("admin_commands.errors.must_be_player"));
            return true;
        }

        Player player = context.getPlayer();

        if (!context.getSelection().hasCompleteSelection(player)) {
            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.must_use_stick"));
            return true;
        }

        Location pos1 = context.getSelection().getPosition1(player);
        Location pos2 = context.getSelection().getPosition2(player);

        SetupDataAPI<Location> data = context.getData();

        int total = resolveFloorTotal(data);
        int nextFloor = total + 1;
        String floorKey = "f" + nextFloor;

        data.registerRegenerationRegion("game.floors." + floorKey, pos1, pos2);
        data.setInt("game.floors." + floorKey + ".auto_remove_time", settings.getDefaultAutoRemoveSeconds());
        data.setInt("game.floors.total", nextFloor);
        data.save();

        int x = (int) Math.abs(pos2.getX() - pos1.getX()) + 1;
        int y = (int) Math.abs(pos2.getY() - pos1.getY()) + 1;
        int z = (int) Math.abs(pos2.getZ() - pos1.getZ()) + 1;
        int blocks = x * y * z;

        context.getMessagesAPI().send(context.getPlayer(),
                moduleConfig.getStringFrom("language.yml", "setup_messages.floor_add_success")
                        .replace("{number}", String.valueOf(nextFloor))
                        .replace("{blocks}", String.valueOf(blocks)));

        return true;
    }

    private boolean handleFloorRemove(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(2)) {
            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.usage_floor_remove"));
            return true;
        }

        String numberStr = context.getHandlerArg(1);
        Integer floorNumber = parseInt(numberStr);
        if (floorNumber == null) {
            context.getMessagesAPI().send(context.getPlayer(),
                    coreConfig.getLanguage("admin_commands.errors.invalid_number")
                            .replace("{value}", numberStr));
            return true;
        }

        SetupDataAPI<Location> data = context.getData();
        String floorKey = "f" + floorNumber;
        if (!data.has("game.floors." + floorKey + ".bounds.min.x")) {
            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.floor_remove_not_found")
                            .replace("{number}", String.valueOf(floorNumber)));
            return true;
        }

        data.remove("game.floors." + floorKey);

        int total = resolveFloorTotal(data);
        if (floorNumber >= total) {
            int newTotal = findLastFloor(data, total);
            data.setInt("game.floors.total", newTotal);
        }

        data.save();

        context.getMessagesAPI().send(context.getPlayer(),
                moduleConfig.getStringFrom("language.yml", "setup_messages.floor_remove_success")
                        .replace("{number}", String.valueOf(floorNumber)));
        return true;
    }

    private boolean handleFloorList(SetupContext<Player, CommandSender, Location> context) {
        SetupDataAPI<Location> data = context.getData();
        int total = resolveFloorTotal(data);

        if (total == 0) {
            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.floor_list_empty"));
            return true;
        }

        context.getMessagesAPI().send(context.getPlayer(),
                moduleConfig.getStringFrom("language.yml", "setup_messages.floor_list_header")
                        .replace("{arena_id}", String.valueOf(context.getArenaId())));

        for (int i = 1; i <= total; i++) {
            String floorKey = "f" + i;
            if (!data.has("game.floors." + floorKey + ".bounds.min.x")) {
                continue;
            }

            int autoRemoveTime = data.getInt("game.floors." + floorKey + ".auto_remove_time", 0);
            int blocks = resolveBlockCount(data, floorKey);
            String autoRemoveLabel = autoRemoveTime > 0
                    ? autoRemoveTime + "s"
                    : moduleConfig.getStringFrom("language.yml", "setup_messages.floor_list_disabled");

            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.floor_list_item")
                            .replace("{number}", String.valueOf(i))
                            .replace("{blocks}", String.valueOf(blocks))
                            .replace("{auto_remove}", autoRemoveLabel));
        }

        return true;
    }

    private boolean handleFloorDelay(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(2)) {
            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.usage_floor_delay"));
            return true;
        }

        String ticksStr = context.getHandlerArg(1);
        Integer ticks = parseInt(ticksStr);
        if (ticks == null) {
            context.getMessagesAPI().send(context.getPlayer(),
                    coreConfig.getLanguage("admin_commands.errors.invalid_number")
                            .replace("{value}", ticksStr));
            return true;
        }

        if (ticks < settings.getFloorDelayMinTicks() || ticks > settings.getFloorDelayMaxTicks()) {
            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.floor_delay_invalid"));
            return true;
        }

        context.getData().setInt("basic.floor_delay", ticks);
        context.getData().save();

        context.getMessagesAPI().send(context.getPlayer(),
                moduleConfig.getStringFrom("language.yml", "setup_messages.floor_delay_success")
                        .replace("{ticks}", String.valueOf(ticks)));
        return true;
    }

    private boolean handleFloorRemoval(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(3)) {
            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.usage_floor_removal"));
            return true;
        }

        String floorNumStr = context.getHandlerArg(1);
        String secondsStr = context.getHandlerArg(2);

        Integer floorNum = parseInt(floorNumStr);
        Integer seconds = parseInt(secondsStr);

        if (floorNum == null || seconds == null) {
            context.getMessagesAPI().send(context.getPlayer(),
                    coreConfig.getLanguage("admin_commands.errors.invalid_number"));
            return true;
        }

        if (seconds < 0 || seconds > settings.getAutoRemoveMaxSeconds()) {
            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.floor_removal_invalid"));
            return true;
        }

        SetupDataAPI<Location> data = context.getData();
        String floorKey = "f" + floorNum;
        if (!data.has("game.floors." + floorKey + ".bounds.min.x")) {
            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.floor_remove_not_found")
                            .replace("{number}", String.valueOf(floorNum)));
            return true;
        }

        data.setInt("game.floors." + floorKey + ".auto_remove_time", seconds);
        data.save();

        String message = seconds == 0
                ? moduleConfig.getStringFrom("language.yml", "setup_messages.floor_removal_disabled")
                : moduleConfig.getStringFrom("language.yml", "setup_messages.floor_removal_success");

        context.getMessagesAPI().send(context.getPlayer(),
                message.replace("{floor}", String.valueOf(floorNum))
                        .replace("{seconds}", String.valueOf(seconds)));

        return true;
    }

    private boolean handleDoubleJump(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(2)) {
            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.usage_doublejump"));
            return true;
        }

        String action = context.getHandlerArg(0).toLowerCase();

        return switch (action) {
            case "set" -> handleDoubleJumpSet(context);
            case "setboost" -> handleDoubleJumpBoost(context);
            default -> {
                context.getMessagesAPI().send(context.getPlayer(),
                        moduleConfig.getStringFrom("language.yml", "setup_messages.usage_doublejump"));
                yield true;
            }
        };
    }

    private boolean handleDoubleJumpSet(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(2)) {
            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.usage_doublejump_set"));
            return true;
        }

        String amountStr = context.getHandlerArg(1);
        Integer amount = parseInt(amountStr);
        if (amount == null) {
            context.getMessagesAPI().send(context.getPlayer(),
                    coreConfig.getLanguage("admin_commands.errors.invalid_number")
                            .replace("{value}", amountStr));
            return true;
        }

        if (amount < 0 || amount > settings.getMaxDoubleJumpUses()) {
            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.doublejump_invalid"));
            return true;
        }

        context.getData().setInt("basic.double_jump_uses", amount);
        context.getData().save();

        String message = amount == 0
                ? moduleConfig.getStringFrom("language.yml", "setup_messages.doublejump_disabled")
                : moduleConfig.getStringFrom("language.yml", "setup_messages.doublejump_success");

        context.getMessagesAPI().send(context.getPlayer(),
                message.replace("{amount}", String.valueOf(amount)));
        return true;
    }

    private boolean handleDoubleJumpBoost(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(3)) {
            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.usage_doublejump_boost"));
            return true;
        }

        String type = context.getHandlerArg(1).toLowerCase();
        String valueStr = context.getHandlerArg(2);

        if (!type.equals("vertical") && !type.equals("horizontal")) {
            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.doublejump_boost_type_invalid"));
            return true;
        }

        Double value = parseDouble(valueStr);
        if (value == null) {
            context.getMessagesAPI().send(context.getPlayer(),
                    coreConfig.getLanguage("admin_commands.errors.invalid_number")
                            .replace("{value}", valueStr));
            return true;
        }

        if (value < settings.getDoubleJumpBoostMin() || value > settings.getDoubleJumpBoostMax()) {
            context.getMessagesAPI().send(context.getPlayer(),
                    moduleConfig.getStringFrom("language.yml", "setup_messages.doublejump_boost_invalid"));
            return true;
        }

        String configPath = type.equals("vertical") ? "basic.double_jump_vertical_boost" : "basic.double_jump_horizontal_boost";
        context.getData().setDouble(configPath, value);
        context.getData().save();

        context.getMessagesAPI().send(context.getPlayer(),
                moduleConfig.getStringFrom("language.yml", "setup_messages.doublejump_boost_success")
                        .replace("{type}", type)
                        .replace("{value}", String.valueOf(value)));
        return true;
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int findLastFloor(SetupDataAPI<Location> data, int currentTotal) {
        int last = 0;
        for (int i = currentTotal - 1; i >= 1; i--) {
            if (data.has("game.floors.f" + i + ".bounds.min.x")) {
                last = i;
                break;
            }
        }
        return last;
    }

    private int resolveFloorTotal(SetupDataAPI<Location> data) {
        int total = data.getInt("game.floors.total", 0);
        if (total > 0) {
            return total;
        }

        int last = 0;
        for (int i = 1; i <= MAX_FLOOR_SCAN; i++) {
            if (data.has("game.floors.f" + i + ".bounds.min.x")) {
                last = i;
            }
        }
        return last;
    }

    private int resolveBlockCount(SetupDataAPI<Location> data, String floorKey) {
        Location min = data.getLocation("game.floors." + floorKey + ".bounds.min");
        Location max = data.getLocation("game.floors." + floorKey + ".bounds.max");
        if (min == null || max == null) {
            return 0;
        }
        int x = (int) Math.abs(max.getX() - min.getX()) + 1;
        int y = (int) Math.abs(max.getY() - min.getY()) + 1;
        int z = (int) Math.abs(max.getZ() - min.getZ()) + 1;
        return x * y * z;
    }



    @SuppressWarnings("unchecked")
    private SetupContext<Player, CommandSender, Location> castSetupContext(SetupContext context) {
        return (SetupContext<Player, CommandSender, Location>) context;
    }

    @SuppressWarnings("unchecked")
    private TabCompleteContext<Player, CommandSender> castTabContext(TabCompleteContext context) {
        return (TabCompleteContext<Player, CommandSender>) context;
    }
}

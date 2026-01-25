package net.blueva.arcade.modules.tntrun.support;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public class TNTRunLoadoutService {

    private final ModuleConfigAPI moduleConfig;

    public TNTRunLoadoutService(ModuleConfigAPI moduleConfig) {
        this.moduleConfig = moduleConfig;
    }

    public void giveStartingItems(Player player) {
        List<String> startingItems = moduleConfig.getStringList("items.starting_items");

        if (startingItems == null || startingItems.isEmpty()) {
            return;
        }

        for (String itemString : startingItems) {
            try {
                String[] parts = itemString.split(":");
                if (parts.length >= 2) {
                    Material material = Material.valueOf(parts[0].toUpperCase());
                    int amount = Integer.parseInt(parts[1]);
                    int slot = parts.length >= 3 ? Integer.parseInt(parts[2]) : -1;

                    org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material, amount);

                    if (slot >= 0 && slot < 36) {
                        player.getInventory().setItem(slot, item);
                    } else {
                        player.getInventory().addItem(item);
                    }
                }
            } catch (Exception e) {
                // Ignore malformed entries
            }
        }
    }

    public void applyStartingEffects(Player player) {
        List<String> startingEffects = moduleConfig.getStringList("effects.starting_effects");

        if (startingEffects == null || startingEffects.isEmpty()) {
            return;
        }

        for (String effectString : startingEffects) {
            try {
                String[] parts = effectString.split(":");
                if (parts.length >= 3) {
                    org.bukkit.potion.PotionEffectType effectType =
                            org.bukkit.potion.PotionEffectType.getByName(parts[0].toUpperCase());
                    int duration = Integer.parseInt(parts[1]);
                    int amplifier = Integer.parseInt(parts[2]);

                    if (effectType != null) {
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                effectType, duration, amplifier, false, false
                        ));
                    }
                }
            } catch (Exception e) {
                // Ignore malformed entries
            }
        }
    }

    public void applyRespawnEffects(Player player) {
        List<String> respawnEffects = moduleConfig.getStringList("effects.respawn_effects");

        if (respawnEffects == null || respawnEffects.isEmpty()) {
            return;
        }

        for (String effectString : respawnEffects) {
            try {
                String[] parts = effectString.split(":");
                if (parts.length >= 3) {
                    org.bukkit.potion.PotionEffectType effectType =
                            org.bukkit.potion.PotionEffectType.getByName(parts[0].toUpperCase());
                    int duration = Integer.parseInt(parts[1]);
                    int amplifier = Integer.parseInt(parts[2]);

                    if (effectType != null) {
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                effectType, duration, amplifier, false, false
                        ));
                    }
                }
            } catch (Exception e) {
                // Ignore malformed entries
            }
        }
    }
}

package org.luxanticheat.luxAntiCheat.checks.player;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.UUID;

public class SpeedListener implements Listener {

    private final Plugin plugin;
    private final HashMap<UUID, Integer> flagMap = new HashMap<>();
    private final int MAX_FLAGS = 3; // Max. erlaubte Flags vor Kick
    private final double TOLERANCE = 1.3; // 30% Toleranz
    private final double BASE_SPEED = 0.215; // Vanilla-Basiswert (pro Tick, grob)

    public SpeedListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return; // safety

        Player player = event.getPlayer();

        // bypass/flug/vehicle ignorieren
        if (player.hasPermission("luxanticheat.bypass")) return;
        if (player.isFlying() || player.getAllowFlight()) return;
        if (player.isInsideVehicle()) return;

        // Nur in Survival oder Adventure prüfen
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) return;

        Location fromLoc = event.getFrom();
        Location toLoc = event.getTo();

        // Nur X/Z-Differenz (horizontal)
        double dx = toLoc.getX() - fromLoc.getX();
        double dz = toLoc.getZ() - fromLoc.getZ();
        double deltaXZ = Math.sqrt(dx * dx + dz * dz);

        double baseSpeed = BASE_SPEED;

        // SPEED-Potionen berücksichtigen
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.SPEED)) {
                int amplifier = effect.getAmplifier() + 1;
                baseSpeed *= 1.0 + (0.2 * amplifier);
            }
        }

        // Block unter den Füßen (clone(), damit Location des Spielers nicht verändert wird)
        Block blockUnder = player.getLocation().clone().subtract(0, 1, 0).getBlock();

        // Soul Speed (wenn Soul Sand / Soul Soil)
        if (blockUnder != null &&
                (blockUnder.getType() == Material.SOUL_SAND || blockUnder.getType() == Material.SOUL_SOIL)) {
            if (player.getInventory().getBoots() != null) {
                int soulSpeedLevel = player.getInventory().getBoots().getEnchantmentLevel(Enchantment.SOUL_SPEED);
                baseSpeed += 0.03 * soulSpeedLevel;
            }
        }

        // Depth Strider im Wasser
        if (player.isInWater()) {
            if (player.getInventory().getBoots() != null) {
                int depthStrider = player.getInventory().getBoots().getEnchantmentLevel(Enchantment.DEPTH_STRIDER);
                baseSpeed += 0.1 * depthStrider;
            }
        }

        // Swift Sneak / Sneaking
        if (player.isSneaking()) {
            if (player.getInventory().getLeggings() != null) {
                int swiftSneak = player.getInventory().getLeggings().getEnchantmentLevel(Enchantment.SWIFT_SNEAK);
                if (swiftSneak > 0) {
                    baseSpeed += 0.03 * swiftSneak;
                } else {
                    baseSpeed *= 0.3; // Sneak ohne SwiftSneak = deutlich langsamer
                }
            } else {
                baseSpeed *= 0.3;
            }
        }

        // Flüssigkeiten / Lava verlangsamen
        if (blockUnder != null && (blockUnder.getType() == Material.LAVA || blockUnder.isLiquid())) {
            baseSpeed *= 0.5;
        }

        // Magma Block leicht verlangsamt
        if (blockUnder != null && blockUnder.getType() == Material.MAGMA_BLOCK) {
            baseSpeed *= 0.9;
        }

        // Vergleich mit Toleranz
        if (deltaXZ > baseSpeed * TOLERANCE) {
            UUID uuid = player.getUniqueId();
            int flags = flagMap.getOrDefault(uuid, 0) + 1;
            flagMap.put(uuid, flags);

            if (flags >= MAX_FLAGS) {
                player.kickPlayer("§c[LuxAntiCheat] Du wurdest wegen verdächtiger Bewegung gekickt.");
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("luxanticheat.alert"))
                        .forEach(admin -> admin.sendMessage("§4[LuxAntiCheat] §c" + player.getName()
                                + " wurde gekickt (Flag " + flags + "/" + MAX_FLAGS + ")."));
                flagMap.remove(uuid);
            } else {
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("luxanticheat.alert"))
                        .forEach(admin -> admin.sendMessage("§c[LuxAntiCheat] §e" + player.getName()
                                + " bewegt sich verdächtig schnell! (Flag " + flags + "/" + MAX_FLAGS + ")"));
            }
        }
    }
}

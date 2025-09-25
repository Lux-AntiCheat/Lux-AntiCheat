package org.luxanticheat.luxAntiCheat.checks.player;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlyCheckListener implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Location> lastSafeLocation = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFlagTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> flyViolations = new ConcurrentHashMap<>();
    private final long FLAG_COOLDOWN_MS = 1000; // 1 Sekunde zwischen Flags
    private final int MAX_VIOLATIONS = 3;

    public FlyCheckListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();

        // Ignore Admins / OP / Permission
        if (p.isOp() || p.hasPermission("luxanticheat.bypass")) return;

        // Ignore Creative / Spectator
        if (p.getGameMode().name().equals("CREATIVE") || p.getGameMode().name().equals("SPECTATOR")) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Ignore Elytra
        if (p.isGliding()) {
            updateSafeLocation(p, to);
            return;
        }

        // Ignore Levitation
        if (p.hasPotionEffect(PotionEffectType.LEVITATION)) {
            updateSafeLocation(p, to);
            return;
        }

        // Ignore Vehicles
        if (p.getVehicle() != null) {
            updateSafeLocation(p, to);
            return;
        }

        // Ignore Falling
        double dy = to.getY() - from.getY();
        if (dy < 0) {
            updateSafeLocation(p, to);
            return;
        }

        // Ignore Safe Blocks: Boden, Ladder, Slab/Stairs, Flüssigkeit
        if (p.isOnGround() || isOnClimbableBlock(to) || isStandingOnLiquid(to) || isOnSlabOrStair(to)) {
            updateSafeLocation(p, to);
            return;
        }

        // Heuristik: Spieler "schwebt" in der Luft
        double verticalThreshold = 0.35;
        if (dy > verticalThreshold) {
            long now = System.currentTimeMillis();
            UUID id = p.getUniqueId();
            Long last = lastFlagTime.get(id);
            if (last != null && now - last < FLAG_COOLDOWN_MS) return;
            lastFlagTime.put(id, now);

            // Erhöhe Violation Counter
            int violations = flyViolations.getOrDefault(id, 0) + 1;
            flyViolations.put(id, violations);

            // Admins informieren
            String msg = "§c[LuxAntiCheat] Spieler §e" + p.getName() + " §cist verdächtigt zu fliegen! (" + violations + "/" + MAX_VIOLATIONS + ")";
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.isOp() || online.hasPermission("luxanticheat.bypass")) {
                    online.sendMessage(msg);
                }
            }

            // Kick nach 5 Mal
            if (violations >= MAX_VIOLATIONS) {
                p.kickPlayer("§cFly-Hack erkannt!");
                flyViolations.remove(id); // Counter zurücksetzen
                return;
            }

            // Teleportiere zur letzten sicheren Position
            Location safe = lastSafeLocation.get(id);
            if (safe != null && safe.getWorld().equals(p.getWorld())) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    p.teleport(safe);
                    p.setFallDistance(0f);
                    p.setVelocity(new Vector(0, 0, 0));
                });
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () -> p.setAllowFlight(false));
            }
            return;
        }

        // Safe Location aktualisieren
        updateSafeLocation(p, to);
    }

    private void updateSafeLocation(Player p, Location loc) {
        if (p.isOnGround() || isStandingOnLiquid(loc) || isOnClimbableBlock(loc) || isOnSlabOrStair(loc)) {
            lastSafeLocation.put(p.getUniqueId(), loc.clone());
        }
    }

    private boolean isStandingOnLiquid(Location loc) {
        Material under = loc.clone().subtract(0, 1, 0).getBlock().getType();
        return under == Material.WATER || under == Material.LAVA;
    }

    private boolean isOnClimbableBlock(Location loc) {
        Material under = loc.clone().subtract(0, 1, 0).getBlock().getType();
        return under == Material.LADDER || under == Material.VINE || under == Material.SCAFFOLDING;
    }

    private boolean isOnSlabOrStair(Location loc) {
        Material under = loc.clone().subtract(0, 1, 0).getBlock().getType();
        return under.name().contains("SLAB") || under.name().contains("STAIR");
    }
}

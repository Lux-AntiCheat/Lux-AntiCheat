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
    private final Map<UUID, Long> airStartTime = new ConcurrentHashMap<>();

    private final long FLAG_COOLDOWN_MS = 1000; // Zeit zwischen Flags
    private final int MAX_VIOLATIONS = 3;       // Anzahl bis Kick
    private final long JUMP_TOLERANCE_MS = 700; // normale Sprungdauer

    public FlyCheckListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();

        if (p.isOp() || p.hasPermission("luxanticheat.bypass")) return;
        if (p.getGameMode().name().equals("CREATIVE") || p.getGameMode().name().equals("SPECTATOR")) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Elytra, Levitation, Fahrzeuge → ignorieren
        if (p.isGliding() || p.hasPotionEffect(PotionEffectType.LEVITATION) || p.getVehicle() != null) {
            updateSafeLocation(p, to);
            airStartTime.remove(p.getUniqueId());
            return;
        }

        double dy = to.getY() - from.getY();

        // Spieler steht auf Boden, Leiter, Flüssigkeit oder Slab → reset
        if (p.isOnGround() || isOnClimbableBlock(to) || isStandingOnLiquid(to) || isOnSlabOrStair(to)) {
            updateSafeLocation(p, to);
            airStartTime.remove(p.getUniqueId());
            return;
        }

        // Spieler ist in der Luft → wie lange?
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        airStartTime.putIfAbsent(id, now);
        long airTime = now - airStartTime.get(id);

        // normale Sprünge erlauben
        if (airTime < JUMP_TOLERANCE_MS) {
            return;
        }

        // Cooldown beachten
        Long last = lastFlagTime.get(id);
        if (last != null && now - last < FLAG_COOLDOWN_MS) return;
        lastFlagTime.put(id, now);

        int violations = flyViolations.getOrDefault(id, 0) + 1;
        flyViolations.put(id, violations);

        // Admins benachrichtigen
        String msg = "§c[LuxAntiCheat] Spieler §e" + p.getName() + " §cist verdächtigt zu fliegen! (" + violations + "/" + MAX_VIOLATIONS + ")";
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.isOp() || online.hasPermission("luxanticheat.notify")) {
                online.sendMessage(msg);
            }
        }

        // Kick nach zu vielen Flags
        if (violations >= MAX_VIOLATIONS) {
            p.kickPlayer("§cFly-Hack erkannt!");
            flyViolations.remove(id);
            airStartTime.remove(id);
            return;
        }

        // Teleportiere zurück
        Location safe = lastSafeLocation.get(id);
        if (safe != null && safe.getWorld().equals(p.getWorld())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                p.teleport(safe);
                p.setFallDistance(0f);
                p.setVelocity(new Vector(0, 0, 0));
            });
        }
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

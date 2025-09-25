package org.luxanticheat.luxAntiCheat;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luxanticheat.luxAntiCheat.checks.player.FlyCheckListener;

public class LuxAntiCheat extends JavaPlugin {

    private boolean enabled = true; // gesamtes Plugin aktiv/deaktiviert
    private FlyCheckListener flyCheckListener;

    @Override
    public void onEnable() {
        flyCheckListener = new FlyCheckListener(this);
        Bukkit.getPluginManager().registerEvents(flyCheckListener, this);
        getLogger().info("LuxAntiCheat aktiviert!");
    }

    @Override
    public void onDisable() {
        getLogger().info("LuxAntiCheat deaktiviert!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cBenutze: /luxanticheat <enable|disable>");
            return true;
        }

        if (!sender.hasPermission("luxanticheat.admin")) {
            sender.sendMessage("§cKeine Rechte!");
            return true;
        }

        if (args[0].equalsIgnoreCase("disable")) {
            enabled = false;
            sender.sendMessage("§cLuxAntiCheat deaktiviert!");
            return true;
        }

        if (args[0].equalsIgnoreCase("enable")) {
            enabled = true;
            sender.sendMessage("§aLuxAntiCheat aktiviert!");
            return true;
        }

        return false;
    }

    public boolean isPluginEnabledCustom() {
        return enabled;
    }
}

package com.indifferenzah.hubcore.plugin.commands;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;

/**
 * Comandi /setlobby, /setspawn, /lobby, /spawn.
 * /setlobby e /setspawn fanno la stessa cosa (impostano spawn in H2).
 * /lobby e /spawn fanno la stessa cosa (teletrasportano allo spawn).
 */
public class SpawnCommand {

    private final HubCorePlugin plugin;

    public SpawnCommand(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // /setlobby  (alias: /setspawn)
    // =========================================================================

    @Command("setlobby")
    @CommandPermission("hubcore.set")
    public void setLobby(BukkitCommandActor actor) {
        doSetSpawn(actor);
    }

    @Command("setspawn")
    @CommandPermission("hubcore.set")
    public void setSpawn(BukkitCommandActor actor) {
        doSetSpawn(actor);
    }

    private void doSetSpawn(BukkitCommandActor actor) {
        if (!(actor.sender() instanceof Player player)) {
            actor.sender().sendMessage(ColorUtil.colorize("&cSolo i giocatori possono usare questo comando."));
            return;
        }
        Location loc = player.getLocation();
        plugin.getH2StatsService().setLobbyLocation(loc);
        player.sendMessage(ColorUtil.colorize(
                plugin.getConfigLoader().getPrefix()
                + " &aSpawn impostato in &e" + loc.getWorld().getName()
                + " &7(" + String.format("%.1f", loc.getX()) + ", "
                + String.format("%.1f", loc.getY()) + ", "
                + String.format("%.1f", loc.getZ()) + ")&a."
        ));
    }

    // =========================================================================
    // /lobby  (alias: /spawn)
    // =========================================================================

    @Command("lobby")
    @CommandPermission("hubcore.spawn")
    public void lobby(BukkitCommandActor actor) {
        doTeleport(actor);
    }

    @Command("spawn")
    @CommandPermission("hubcore.spawn")
    public void spawn(BukkitCommandActor actor) {
        doTeleport(actor);
    }

    private void doTeleport(BukkitCommandActor actor) {
        if (!(actor.sender() instanceof Player player)) {
            actor.sender().sendMessage(ColorUtil.colorize("&cSolo i giocatori possono usare questo comando."));
            return;
        }
        Location loc = plugin.getH2StatsService().getLobbyLocation();
        if (loc == null) {
            player.sendMessage(ColorUtil.colorize(
                    plugin.getConfigLoader().getPrefix()
                    + " &cLo spawn non è stato impostato. Usa &e/setlobby&c prima."
            ));
            return;
        }
        player.teleport(loc);
        player.sendMessage(ColorUtil.colorize(
                plugin.getConfigLoader().getPrefix() + " &aTeletrasportato allo spawn."
        ));
    }
}

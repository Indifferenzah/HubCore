package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Listener per la gestione del rispawn del giocatore.
 */
public class PlayerRespawnListener implements Listener {

    private final HubCorePlugin plugin;

    public PlayerRespawnListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();

        // Imposta il punto di respawn allo spawn configurato con /setlobby
        org.bukkit.Location lobbyLoc = plugin.getH2StatsService().getLobbyLocation();
        if (lobbyLoc != null) {
            event.setRespawnLocation(lobbyLoc);
        }

        // Applica il delay di rispawn: il giocatore non puo' attivare il PvP per un certo periodo
        plugin.getPvpService().applyRespawnDelay(player.getUniqueId());

        // Re-abilita double jump dopo il rispawn
        plugin.getDoubleJumpListener().enableFor(player);

        // Riconsegna tutti gli item dopo il rispawn con un piccolo delay
        // per assicurarsi che l'inventario sia pronto lato server
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            // Spada PvP
            if (!plugin.getSwordManager().hasSword(player)) {
                plugin.getSwordManager().giveSword(player);
            }
            // Blocchi lobby (blocco + selettore/bussola)
            plugin.getLobbyBlocksManager().giveItems(player);
            // Item custom (server selector, ecc.)
            plugin.getCustomJoinItemsManager().giveItems(player);
            // Item visibilità giocatori
            plugin.getPlayerHiderManager().giveItem(player);

            // L'armatura viene data solo quando il PvP viene attivato, non al rispawn
        }, 1L);
    }
}

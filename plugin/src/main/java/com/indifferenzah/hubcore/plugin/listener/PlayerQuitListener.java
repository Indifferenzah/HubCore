package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener per la gestione dell'uscita del giocatore dal server.
 */
public class PlayerQuitListener implements Listener {

    private final HubCorePlugin plugin;

    public PlayerQuitListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var uuid = player.getUniqueId();

        // Salva le statistiche del giocatore nel database
        plugin.getStatsService().savePlayer(uuid);

        // Rimuove il giocatore dalla gestione PvP (cancella tutti i task)
        plugin.getPvpService().removePlayer(uuid);

        // Rimuove il giocatore dalla cache statistiche
        plugin.getH2StatsService().unloadPlayer(uuid);
    }
}

package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Listener per la gestione della morte del giocatore in PvP.
 */
public class PlayerDeathListener implements Listener {

    private final HubCorePlugin plugin;

    public PlayerDeathListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // Incrementa le morti e azzera la killstreak nelle statistiche
        plugin.getPvpService().handleDeath(victim);

        // Annulla tutti i drop dell'inventario: il giocatore mantiene gli oggetti
        event.getDrops().clear();

        // Annulla il drop di XP
        event.setDroppedExp(0);

        // Cancella tutti i task PvP del giocatore morto
        plugin.getPvpService().removePlayer(victim.getUniqueId());
    }
}

package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
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

        // Messaggio di quit personalizzato (o soppresso)
        if (plugin.getConfigLoader().isJoinLeaveEnabled()) {
            String raw = plugin.getConfigLoader().getQuitMessage()
                    .replace("%player%", player.getName());
            if (player.hasPermission("hubcore.announce.leave") && !raw.isBlank()) {
                event.quitMessage(ColorUtil.colorize(raw));
            } else {
                event.quitMessage(null);
            }
        } else {
            event.quitMessage(null);
        }

        // Salva le statistiche nel database
        plugin.getStatsService().savePlayer(uuid);

        // Rimuove il giocatore dalla gestione PvP
        plugin.getPvpService().removePlayer(uuid);

        // Rimuove i dati lobby blocks del giocatore
        plugin.getLobbyBlocksManager().removePlayer(uuid);

        // Rimuove il giocatore dalla cache statistiche
        plugin.getH2StatsService().unloadPlayer(uuid);

        // Pulizia tab e sidebar
        plugin.getTabManager().onQuit(player);
        plugin.getScoreboardManager().onQuit(player);

        // Pulizia player hider
        plugin.getPlayerHiderManager().onQuit(player);
    }
}

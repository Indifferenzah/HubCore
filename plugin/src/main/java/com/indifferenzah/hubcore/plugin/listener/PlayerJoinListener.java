package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listener per la gestione dell'ingresso del giocatore nel server.
 */
public class PlayerJoinListener implements Listener {

    private final HubCorePlugin plugin;

    public PlayerJoinListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        // Carica le statistiche del giocatore dal database nella cache
        plugin.getStatsService().loadPlayer(player.getUniqueId(), player.getName());

        // Notifica di aggiornamento disponibile (solo agli admin con permesso hubcore.admin)
        if (plugin.isUpdateAvailable() && player.hasPermission("hubcore.admin")) {
            net.kyori.adventure.text.Component msg = plugin.getUpdateMessage();
            if (msg != null) player.sendMessage(msg);
        }

        // Delay di 2 tick: altri plugin (es. DeluxeHub) potrebbero sovrascrivere
        // l'inventario subito dopo il join; aspettiamo che finiscano prima di dare spada e armatura
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (!plugin.getSwordManager().hasSword(player)) {
                plugin.getSwordManager().giveSword(player);
            }
            // Consegna l'item lobby blocks (piazza-blocco)
            plugin.getLobbyBlocksManager().giveItems(player);
            // L'armatura viene data solo quando il PvP viene attivato, non al join
        }, 2L);
    }
}

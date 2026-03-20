package com.indifferenzah.hubcore.plugin.task;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Task periodico che salva automaticamente le statistiche di tutti i giocatori in cache.
 */
public class AutoSaveTask extends BukkitRunnable {

    private final HubCorePlugin plugin;

    public AutoSaveTask(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Salva tutte le statistiche in cache nel database
        plugin.getStatsService().saveAll();
    }
}

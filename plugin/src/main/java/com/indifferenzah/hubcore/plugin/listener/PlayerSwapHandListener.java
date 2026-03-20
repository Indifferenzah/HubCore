package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Listener che impedisce allo scambio di mano (F) di coinvolgere la spada PvP.
 */
public class PlayerSwapHandListener implements Listener {

    private final HubCorePlugin plugin;

    public PlayerSwapHandListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        // Annulla lo scambio se uno degli oggetti coinvolti e' la spada PvP
        boolean mainHandIsSword = plugin.getSwordManager().isPvPSword(event.getMainHandItem());
        boolean offHandIsSword = plugin.getSwordManager().isPvPSword(event.getOffHandItem());

        if (mainHandIsSword || offHandIsSword) {
            event.setCancelled(true);
        }
    }
}

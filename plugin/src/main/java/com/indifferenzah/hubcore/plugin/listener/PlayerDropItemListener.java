package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * Listener che impedisce al giocatore di buttare la spada PvP.
 */
public class PlayerDropItemListener implements Listener {

    private final HubCorePlugin plugin;

    public PlayerDropItemListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        var item = event.getItemDrop().getItemStack();
        // Impedisci il drop della spada PvP, dell'armatura PvP o degli item lobby
        if (plugin.getSwordManager().isPvPSword(item)
                || plugin.getArmorManager().isPvPArmor(item)
                || plugin.getLobbyBlocksManager().isBlockItem(item)
                || plugin.getLobbyBlocksManager().isSelectorItem(item)) {
            event.setCancelled(true);
        }
    }
}

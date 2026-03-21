package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.menu.ConfigurableMenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

/**
 * Gestisce i click sui menu configurabili caricati da MenuLoader.
 * Cancella il click e delega le azioni ad ActionExecutor.
 */
public class MenuClickListener implements Listener {

    private final HubCorePlugin plugin;

    public MenuClickListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof ConfigurableMenuHolder holder)) return;

        // Cancella sempre il click nei menu configurabili (nessun movimento item)
        event.setCancelled(true);

        int slot = event.getRawSlot();
        // Slot fuori dall'inventario superiore (es. inventario del player) → ignora
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        List<String> actions = holder.getActions(slot);
        if (!actions.isEmpty()) {
            plugin.getActionExecutor().executeAll(player, actions);
        }
    }
}

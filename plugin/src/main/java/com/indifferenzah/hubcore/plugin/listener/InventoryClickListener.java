package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;

/**
 * Impedisce al giocatore di spostare la spada PvP o rimuovere l'armatura PvP.
 */
public class InventoryClickListener implements Listener {

    private final HubCorePlugin plugin;

    public InventoryClickListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        var swordManager = plugin.getSwordManager();
        var armorManager = plugin.getArmorManager();

        // Blocca qualsiasi click che coinvolge la spada PvP:
        // sia lo slot che la contiene, sia il cursore (drag-and-drop)
        if (swordManager.isPvPSword(event.getCurrentItem())
                || swordManager.isPvPSword(event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        if (!plugin.getConfigLoader().isArmorEnabled()) return;

        // Blocca interazioni sullo slot corazza se contiene armatura PvP
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            if (armorManager.isPvPArmor(event.getCurrentItem())) {
                event.setCancelled(true);
                return;
            }
        }

        // Blocca shift-click su armatura PvP da qualsiasi slot
        if (event.isShiftClick() && armorManager.isPvPArmor(event.getCurrentItem())) {
            event.setCancelled(true);
        }
    }
}

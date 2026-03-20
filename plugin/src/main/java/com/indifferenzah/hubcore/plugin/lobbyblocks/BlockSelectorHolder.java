package com.indifferenzah.hubcore.plugin.lobbyblocks;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * InventoryHolder personalizzato per il menu di selezione blocchi.
 * Permette di identificare questa GUI negli eventi di inventario.
 */
public class BlockSelectorHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}

package com.indifferenzah.hubcore.plugin.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * InventoryHolder per i menu configurabili da YAML.
 * Tiene la mappa slot → azioni da eseguire al click.
 */
public class ConfigurableMenuHolder implements InventoryHolder {

    private Inventory inventory;

    // slot → lista di azioni (es. "[PROXY] survival", "[CLOSE]")
    private final Map<Integer, List<String>> slotActions;

    public ConfigurableMenuHolder(Map<Integer, List<String>> slotActions) {
        this.slotActions = slotActions;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Impostato dal MenuLoader dopo aver creato l'Inventory. */
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /** Restituisce le azioni per lo slot cliccato (lista vuota se nessuna). */
    public List<String> getActions(int slot) {
        return slotActions.getOrDefault(slot, Collections.emptyList());
    }
}

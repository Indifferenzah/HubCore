package com.indifferenzah.hubcore.plugin.service;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Gestisce gli item custom dati al join (sezione custom_join_items in config.yml).
 * Ogni item è taggato con PDC per essere identificato nei listener.
 */
public class CustomJoinItemsManager {

    private final HubCorePlugin plugin;

    // PDC key: hubcore:custom_item_id → chiave dell'item nel config (es. "server_selector")
    private final NamespacedKey itemKey;

    // Dati caricati dalla config
    private boolean enabled;
    private boolean disableMovement;

    // key → slot
    private final Map<String, Integer> slots = new LinkedHashMap<>();
    // key → ItemStack pronto
    private final Map<String, ItemStack> items = new LinkedHashMap<>();
    // key → lista azioni
    private final Map<String, List<String>> actions = new LinkedHashMap<>();

    public CustomJoinItemsManager(HubCorePlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "custom_item_id");
        load();
    }

    /** Carica (o ricarica) gli item dalla config. */
    public void load() {
        slots.clear();
        items.clear();
        actions.clear();

        enabled = plugin.getConfig().getBoolean("custom_join_items.enabled", true);
        disableMovement = plugin.getConfig().getBoolean("custom_join_items.disable_inventory_movement", true);

        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("custom_join_items.items");
        if (itemsSection == null) return;

        for (String id : itemsSection.getKeys(false)) {
            ConfigurationSection s = itemsSection.getConfigurationSection(id);
            if (s == null) continue;

            int slot = s.getInt("slot", 0);
            slots.put(id, slot);
            actions.put(id, s.getStringList("actions"));
            items.put(id, buildItem(s, id));
        }
    }

    /** Dà tutti gli item configurati al giocatore, nei rispettivi slot. */
    public void giveItems(Player player) {
        if (!enabled) return;
        for (Map.Entry<String, Integer> entry : slots.entrySet()) {
            String id = entry.getKey();
            int slot = entry.getValue();
            ItemStack item = items.get(id);
            if (item != null) {
                player.getInventory().setItem(slot, item.clone());
            }
        }
    }

    /** True se l'item ha il tag PDC di un custom item. */
    public boolean isCustomItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(itemKey, PersistentDataType.STRING);
    }

    /** Restituisce la chiave config dell'item (es. "server_selector"), o null se non è un custom item. */
    public String getItemId(ItemStack item) {
        if (!isCustomItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(itemKey, PersistentDataType.STRING);
    }

    /** Restituisce le azioni associate all'id dell'item. */
    public List<String> getActions(String id) {
        return actions.getOrDefault(id, Collections.emptyList());
    }

    public boolean isEnabled() { return enabled; }
    public boolean isDisableMovement() { return disableMovement; }

    // -------------------------------------------------------------------------

    private ItemStack buildItem(ConfigurationSection s, String id) {
        // Materiale
        Material material;
        try {
            material = Material.valueOf(s.getString("material", "STONE").toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[CustomJoinItems] Materiale non valido per '" + id + "', uso STONE.");
            material = Material.STONE;
        }

        ItemStack item = new ItemStack(material, Math.max(1, s.getInt("amount", 1)));
        ItemMeta meta = item.getItemMeta();

        // Display name (non corsivo)
        String name = s.getString("display_name", "");
        if (!name.isEmpty()) {
            meta.displayName(ColorUtil.itemName(name));
        }

        // Lore (non corsivo)
        List<String> loreList = s.getStringList("lore");
        if (!loreList.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreList) lore.add(ColorUtil.itemName(line));
            meta.lore(lore);
        }

        // PDC tag per identificazione
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);

        item.setItemMeta(meta);
        return item;
    }
}

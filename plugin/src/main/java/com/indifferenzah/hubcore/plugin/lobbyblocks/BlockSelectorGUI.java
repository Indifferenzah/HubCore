package com.indifferenzah.hubcore.plugin.lobbyblocks;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestisce la GUI del selettore di blocchi, caricata da menu/blockselector.yml.
 */
public class BlockSelectorGUI {

    private final HubCorePlugin plugin;
    // Chiave PDC per identificare gli slot filler (non selezionabili)
    private final NamespacedKey fillerKey;
    private YamlConfiguration config;

    public BlockSelectorGUI(HubCorePlugin plugin) {
        this.plugin = plugin;
        this.fillerKey = new NamespacedKey(plugin, "gui_filler");
        load();
    }

    /**
     * Carica (o ricarica) il file menu/blockselector.yml dalla cartella dati del plugin.
     */
    public void load() {
        File file = new File(plugin.getDataFolder(), "menu/blockselector.yml");
        if (!file.exists()) {
            plugin.saveResource("menu/blockselector.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Apre il menu di selezione blocchi per il giocatore.
     *
     * @param player Il giocatore a cui aprire il menu
     */
    public void open(Player player) {
        int rows = Math.min(6, Math.max(1, config.getInt("rows", 3)));
        String title = config.getString("title", "&8Seleziona Blocco");

        // Crea il menu con l'holder personalizzato per identificarlo negli eventi
        BlockSelectorHolder holder = new BlockSelectorHolder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, ColorUtil.colorize(title));
        holder.setInventory(inv);

        // Filler opzionale per gli slot vuoti
        if (config.getBoolean("filler.enabled", false)) {
            ItemStack filler = createFiller();
            for (int i = 0; i < rows * 9; i++) {
                inv.setItem(i, filler);
            }
        }

        // Popola gli slot configurati
        ConfigurationSection slots = config.getConfigurationSection("slots");
        if (slots != null) {
            for (String key : slots.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    if (slot < 0 || slot >= rows * 9) continue;
                    ConfigurationSection slotSection = slots.getConfigurationSection(key);
                    if (slotSection == null) continue;
                    inv.setItem(slot, createSlotItem(slotSection));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Slot GUI non valido nel blockselector.yml: " + key);
                }
            }
        }

        player.openInventory(inv);
    }

    /**
     * Crea l'item filler taggato con PDC per distinguerlo dagli item selezionabili.
     */
    private ItemStack createFiller() {
        String matName = config.getString("filler.material", "GRAY_STAINED_GLASS_PANE");
        Material mat;
        try {
            mat = Material.valueOf(matName.toUpperCase());
        } catch (IllegalArgumentException e) {
            mat = Material.GRAY_STAINED_GLASS_PANE;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = config.getString("filler.name", " ");
            // Nome vuoto o spazio per nascondere il nome
            meta.displayName(name.isBlank() ? Component.empty() : ColorUtil.itemName(name));
            meta.setUnbreakable(false);
            // Tagga il filler nel PDC per non selezionarlo
            meta.getPersistentDataContainer().set(fillerKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Crea un item selezionabile da una sezione di configurazione.
     */
    private ItemStack createSlotItem(ConfigurationSection section) {
        String matName = section.getString("material", "STONE");
        Material mat;
        try {
            mat = Material.valueOf(matName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Materiale non valido nel blockselector.yml: " + matName);
            mat = Material.STONE;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = section.getString("name", "&f" + mat.name());
            meta.displayName(ColorUtil.itemName(name));

            List<Component> lore = new ArrayList<>();
            for (String line : section.getStringList("lore")) {
                lore.add(ColorUtil.itemName(line));
            }
            if (!lore.isEmpty()) meta.lore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Controlla se un item è un filler della GUI (non selezionabile).
     */
    public boolean isFiller(ItemStack item) {
        if (item == null || item.getType().isAir()) return true;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(fillerKey, PersistentDataType.BYTE);
    }
}

package com.indifferenzah.hubcore.plugin.menu;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

/**
 * Scansiona la cartella menu/ e registra automaticamente ogni file .yml
 * come menu apribile tramite [MENU] nomeFile.
 *
 * Formato atteso del file YAML:
 * <pre>
 *   title: "&eServer Selector"
 *   slots: 27
 *   refresh:
 *     enabled: false
 *     rate: 40
 *   items:
 *     nomeItem:
 *       material: COMPASS
 *       slot: 13
 *       amount: 1
 *       glow: false
 *       display_name: "&7Nome"
 *       lore:
 *         - "&7Riga 1"
 *       actions:
 *         - "[PROXY] server"
 * </pre>
 */
public class MenuLoader {

    private final HubCorePlugin plugin;

    // File esclusi dal caricamento automatico (hanno logica propria)
    private static final Set<String> EXCLUDED = Set.of("blockselector.yml");

    public MenuLoader(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Carica tutti i menu dalla cartella menu/ e li registra nel MenuRegistry.
     * Può essere richiamato anche in fase di reload.
     */
    public void loadAll() {
        // Pulisce i menu già registrati prima di ricaricare
        plugin.getMenuRegistry().clear();

        File menuFolder = new File(plugin.getDataFolder(), "menu");
        if (!menuFolder.exists() || !menuFolder.isDirectory()) {
            plugin.getLogger().warning("[MenuLoader] Cartella menu/ non trovata in: " + menuFolder.getAbsolutePath());
            return;
        }

        File[] files = menuFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("[MenuLoader] Nessun file .yml trovato in: " + menuFolder.getAbsolutePath());
            return;
        }

        int count = 0;
        for (File file : files) {
            if (EXCLUDED.contains(file.getName().toLowerCase())) continue;
            try {
                loadMenu(file);
                count++;
                plugin.getLogger().info("[MenuLoader] Menu caricato: " + file.getName());
            } catch (Exception e) {
                plugin.getLogger().warning("[MenuLoader] Errore nel caricamento di '"
                        + file.getName() + "': " + e.getMessage());
            }
        }
        plugin.getLogger().info("[MenuLoader] Caricati " + count + " menu da: " + menuFolder.getAbsolutePath());
    }

    // -------------------------------------------------------------------------

    private void loadMenu(File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // Nome menu = nome file senza estensione (es. "serverselector")
        String menuName = file.getName().replace(".yml", "").toLowerCase();

        String rawTitle = cfg.getString("title", "&8Menu");
        int size = normalizeSize(cfg.getInt("slots", 27));

        // refresh (opzionale)
        boolean refreshEnabled = cfg.getBoolean("refresh.enabled", false);
        long refreshRate = cfg.getLong("refresh.rate", 40L);

        // Costruisce la mappa slot → azioni e il layout degli item
        Map<Integer, List<String>> slotActions = new LinkedHashMap<>();
        Map<Integer, ItemStack> slotItems = new LinkedHashMap<>();

        ConfigurationSection itemsSection = cfg.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection s = itemsSection.getConfigurationSection(key);
                if (s == null) continue;

                int slot = s.getInt("slot", -1);
                if (slot < 0 || slot >= size) {
                    plugin.getLogger().warning("[MenuLoader] Item '" + key + "' in '"
                            + file.getName() + "' ha slot non valido: " + slot);
                    continue;
                }

                slotItems.put(slot, buildItem(s, key, file.getName()));
                slotActions.put(slot, s.getStringList("actions"));
            }
        }

        // Registra nel MenuRegistry come Consumer<Player>
        plugin.getMenuRegistry().register(menuName, player -> {
            ConfigurableMenuHolder holder = new ConfigurableMenuHolder(slotActions);
            Component title = ColorUtil.colorize(rawTitle.replace("%player%", player.getName()));
            Inventory inv = Bukkit.createInventory(holder, size, title);
            holder.setInventory(inv);

            // Posiziona gli item
            for (Map.Entry<Integer, ItemStack> entry : slotItems.entrySet()) {
                inv.setItem(entry.getKey(), entry.getValue().clone());
            }

            player.openInventory(inv);

            // Refresh periodico (opzionale)
            if (refreshEnabled) {
                Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                    if (!player.isOnline() || !(player.getOpenInventory().getTopInventory().getHolder()
                            instanceof ConfigurableMenuHolder)) {
                        task.cancel();
                        return;
                    }
                    // Ricostruisce solo gli item (non la struttura)
                    for (Map.Entry<Integer, ItemStack> entry : slotItems.entrySet()) {
                        inv.setItem(entry.getKey(), entry.getValue().clone());
                    }
                }, refreshRate, refreshRate);
            }
        });
    }

    // -------------------------------------------------------------------------

    private ItemStack buildItem(ConfigurationSection s, String key, String fileName) {
        // Materiale
        Material material;
        try {
            material = Material.valueOf(s.getString("material", "STONE").toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[MenuLoader] Materiale non valido per item '" + key
                    + "' in '" + fileName + "', uso STONE.");
            material = Material.STONE;
        }

        int amount = Math.max(1, Math.min(64, s.getInt("amount", 1)));
        ItemStack item = new ItemStack(material, amount);
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
            for (String line : loreList) {
                lore.add(ColorUtil.itemName(line));
            }
            meta.lore(lore);
        }

        // Glow (luccichio senza enchant visibile)
        if (s.getBoolean("glow", false)) {
            meta.setEnchantmentGlintOverride(true);
        }

        item.setItemMeta(meta);
        return item;
    }

    /** Normalizza la dimensione dell'inventario al multiplo di 9 più vicino (9–54). */
    private int normalizeSize(int raw) {
        int clamped = Math.max(9, Math.min(54, raw));
        return (int) (Math.ceil(clamped / 9.0) * 9);
    }
}

package com.indifferenzah.hubcore.plugin.service;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.config.ConfigLoader;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gestisce la creazione e l'equip dell'armatura PvP configurabile.
 * Ogni pezzo e' taggato nel PersistentDataContainer per essere identificabile.
 */
public class ArmorManager {

    private final HubCorePlugin plugin;
    // Chiave PDC per identificare l'armatura PvP
    private final NamespacedKey pvpArmorKey;

    public ArmorManager(HubCorePlugin plugin) {
        this.plugin = plugin;
        this.pvpArmorKey = new NamespacedKey(plugin, "pvp_armor");
    }

    // -------------------------------------------------------------------------
    // Equip dell'armatura
    // -------------------------------------------------------------------------

    /**
     * Equipaggia tutti i pezzi dell'armatura PvP al giocatore.
     * Sostituisce solo i pezzi mancanti (non sovrascrive armatura non-PvP se gia' presente).
     *
     * @param player Il giocatore
     */
    public void giveArmor(Player player) {
        if (!plugin.getConfigLoader().isArmorEnabled()) return;

        PlayerInventory inv = player.getInventory();

        // Equipaggia solo i pezzi assenti o non-PvP
        if (!isPvPArmor(inv.getHelmet()))     inv.setHelmet(createPiece("helmet"));
        if (!isPvPArmor(inv.getChestplate())) inv.setChestplate(createPiece("chestplate"));
        if (!isPvPArmor(inv.getLeggings()))   inv.setLeggings(createPiece("leggings"));
        if (!isPvPArmor(inv.getBoots()))      inv.setBoots(createPiece("boots"));
    }

    /**
     * Controlla se il giocatore ha tutta l'armatura PvP equipaggiata.
     *
     * @param player Il giocatore
     * @return true se tutti i pezzi PvP sono presenti
     */
    public boolean hasFullArmor(Player player) {
        PlayerInventory inv = player.getInventory();
        return isPvPArmor(inv.getHelmet())
                && isPvPArmor(inv.getChestplate())
                && isPvPArmor(inv.getLeggings())
                && isPvPArmor(inv.getBoots());
    }

    // -------------------------------------------------------------------------
    // Creazione dei pezzi
    // -------------------------------------------------------------------------

    /**
     * Crea un pezzo di armatura PvP dal config.
     *
     * @param type "helmet", "chestplate", "leggings", "boots"
     * @return L'ItemStack creato e taggato
     */
    public ItemStack createPiece(String type) {
        ConfigLoader config = plugin.getConfigLoader();
        String path = "armor." + type;

        // Materiale
        Material material = config.getArmorMaterial(path);
        ItemStack item = new ItemStack(material);

        // Meta
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Nome
        String name = config.getArmorName(path);
        meta.displayName(ColorUtil.colorize(name));

        // Lore
        List<Component> loreComponents = new ArrayList<>();
        for (String line : config.getArmorLore(path)) {
            loreComponents.add(ColorUtil.colorize(line));
        }
        if (!loreComponents.isEmpty()) meta.lore(loreComponents);

        // Incantesimi
        for (Map.Entry<Enchantment, Integer> entry : config.getArmorEnchants(path).entrySet()) {
            meta.addEnchant(entry.getKey(), entry.getValue(), true);
        }

        // Flag
        for (ItemFlag flag : config.getArmorFlags(path)) {
            meta.addItemFlags(flag);
        }

        // Indistruttibile
        meta.setUnbreakable(config.isArmorUnbreakable(path));

        // Colore per armatura in cuoio (opzionale)
        if (meta instanceof LeatherArmorMeta leatherMeta) {
            String colorHex = config.getArmorColor(path);
            if (colorHex != null && !colorHex.isBlank()) {
                try {
                    int rgb = Integer.parseInt(colorHex.replace("#", "").trim(), 16);
                    leatherMeta.setColor(Color.fromRGB(rgb));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Colore armatura non valido per " + type + ": " + colorHex);
                }
            }
        }

        // Tag PDC per identificare l'armatura PvP
        meta.getPersistentDataContainer().set(pvpArmorKey, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Rimuove tutti i pezzi di armatura PvP dagli slot corazza del giocatore.
     * Chiamato quando il PvP viene disattivato.
     *
     * @param player Il giocatore
     */
    public void removeArmor(Player player) {
        if (!plugin.getConfigLoader().isArmorEnabled()) return;

        PlayerInventory inv = player.getInventory();
        if (isPvPArmor(inv.getHelmet()))     inv.setHelmet(null);
        if (isPvPArmor(inv.getChestplate())) inv.setChestplate(null);
        if (isPvPArmor(inv.getLeggings()))   inv.setLeggings(null);
        if (isPvPArmor(inv.getBoots()))      inv.setBoots(null);
    }

    // -------------------------------------------------------------------------
    // Utility di controllo
    // -------------------------------------------------------------------------

    /**
     * Controlla se un ItemStack e' un pezzo di armatura PvP tramite il tag PDC.
     *
     * @param item L'ItemStack da verificare (puo' essere null)
     * @return true se e' armatura PvP
     */
    public boolean isPvPArmor(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(pvpArmorKey, PersistentDataType.BYTE);
    }
}

package com.indifferenzah.hubcore.plugin.service;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.config.ConfigLoader;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gestisce la creazione, il posizionamento e l'aggiornamento dell'aspetto della spada PvP.
 */
public class SwordManager {

    private final HubCorePlugin plugin;
    // Chiave NBT per identificare la spada PvP
    private final NamespacedKey pvpSwordKey;

    public SwordManager(HubCorePlugin plugin) {
        this.plugin = plugin;
        this.pvpSwordKey = new NamespacedKey(plugin, "pvp_sword");
    }

    // -------------------------------------------------------------------------
    // Creazione della spada
    // -------------------------------------------------------------------------

    /**
     * Crea l'ItemStack della spada PvP con aspetto inattivo.
     *
     * @return L'ItemStack della spada inattiva
     */
    public ItemStack createInactiveSword() {
        ConfigLoader config = plugin.getConfigLoader();
        ItemStack sword = new ItemStack(config.getSwordInactiveMaterial());
        applyMeta(sword, config.getSwordInactiveName(), config.getSwordInactiveLore(),
                config.getSwordInactiveEnchants(), config.getSwordInactiveFlags(),
                config.isSwordUnbreakable());
        return sword;
    }

    /**
     * Crea l'ItemStack della spada PvP con aspetto attivo.
     *
     * @return L'ItemStack della spada attiva
     */
    public ItemStack createActiveSword() {
        ConfigLoader config = plugin.getConfigLoader();
        ItemStack sword = new ItemStack(config.getSwordInactiveMaterial());
        applyMeta(sword, config.getSwordActiveName(), config.getSwordActiveLore(),
                config.getSwordInactiveEnchants(), config.getSwordInactiveFlags(),
                config.isSwordUnbreakable());
        return sword;
    }

    /**
     * Applica il meta (nome, lore, incantesimi, flag, PDC) all'ItemStack.
     */
    private void applyMeta(ItemStack sword, String name, List<String> lore,
                            Map<Enchantment, Integer> enchants, List<ItemFlag> flags,
                            boolean unbreakable) {
        ItemMeta meta = sword.getItemMeta();
        if (meta == null) return;

        // Nome colorato (senza corsivo automatico di Minecraft)
        meta.displayName(ColorUtil.itemName(name));

        // Lore colorata (senza corsivo automatico di Minecraft)
        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(ColorUtil.itemName(line));
        }
        meta.lore(loreComponents);

        // Incantesimi (non-sicuri per permettere livelli personalizzati)
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            meta.addEnchant(entry.getKey(), entry.getValue(), true);
        }

        // Flag
        for (ItemFlag flag : flags) {
            meta.addItemFlags(flag);
        }

        // Indistruttibile
        meta.setUnbreakable(unbreakable);

        // Marca la spada con il tag PvP nel PersistentDataContainer
        meta.getPersistentDataContainer().set(pvpSwordKey, PersistentDataType.BYTE, (byte) 1);

        sword.setItemMeta(meta);
    }

    // -------------------------------------------------------------------------
    // Gestione inventario
    // -------------------------------------------------------------------------

    /**
     * Consegna la spada PvP al giocatore nello slot configurato.
     * Se lo slot e' occupato, aggiunge la spada al primo slot libero.
     *
     * @param player Il giocatore a cui consegnare la spada
     */
    public void giveSword(Player player) {
        int slot = plugin.getConfigLoader().getSwordSlot();
        ItemStack sword = createInactiveSword();

        ItemStack existing = player.getInventory().getItem(slot);
        if (existing == null || existing.getType().isAir()) {
            // Slot vuoto: posiziona direttamente
            player.getInventory().setItem(slot, sword);
        } else if (!isPvPSword(existing)) {
            // Slot occupato da altro oggetto: aggiunge la spada al primo slot libero
            player.getInventory().addItem(sword);
        }
        // Se c'e' gia' una spada PvP nel slot, non fa nulla
    }

    /**
     * Aggiorna l'aspetto della spada PvP nell'inventario del giocatore.
     *
     * @param player Il giocatore
     * @param active true = aspetto attivo, false = aspetto inattivo
     */
    public void updateSwordAppearance(Player player, boolean active) {
        int slot = getSwordSlot(player);
        if (slot == -1) return; // Il giocatore non ha la spada

        ItemStack sword = player.getInventory().getItem(slot);
        if (sword == null || !isPvPSword(sword)) return;

        ConfigLoader config = plugin.getConfigLoader();
        String newName = active ? config.getSwordActiveName() : config.getSwordInactiveName();
        List<String> newLore = active ? config.getSwordActiveLore() : config.getSwordInactiveLore();

        ItemMeta meta = sword.getItemMeta();
        if (meta == null) return;

        meta.displayName(ColorUtil.itemName(newName));
        List<Component> loreComponents = new ArrayList<>();
        for (String line : newLore) {
            loreComponents.add(ColorUtil.itemName(line));
        }
        meta.lore(loreComponents);

        sword.setItemMeta(meta);
    }

    // -------------------------------------------------------------------------
    // Utility di controllo
    // -------------------------------------------------------------------------

    /**
     * Controlla se l'ItemStack e' la spada PvP verificando il PersistentDataContainer.
     *
     * @param item L'ItemStack da verificare
     * @return true se e' la spada PvP
     */
    public boolean isPvPSword(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(pvpSwordKey, PersistentDataType.BYTE);
    }

    /**
     * Controlla se il giocatore possiede la spada PvP in qualsiasi slot.
     *
     * @param player Il giocatore
     * @return true se ha la spada
     */
    public boolean hasSword(Player player) {
        return getSwordSlot(player) != -1;
    }

    /**
     * Restituisce lo slot in cui si trova la spada PvP nell'inventario del giocatore.
     *
     * @param player Il giocatore
     * @return L'indice dello slot, o -1 se non trovata
     */
    public int getSwordSlot(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isPvPSword(contents[i])) {
                return i;
            }
        }
        return -1;
    }
}

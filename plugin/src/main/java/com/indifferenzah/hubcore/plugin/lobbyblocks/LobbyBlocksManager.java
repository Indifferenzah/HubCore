package com.indifferenzah.hubcore.plugin.lobbyblocks;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gestisce il sistema di blocchi lobby:
 * - Blocco piazzato → animazione di rottura (ProtocolLib) per break-time secondi → blocco sparisce
 * - Item selettore → apre GUI per cambiare il materiale dello stack nell'hotbar
 * - La quantità dello stack rimane sempre a 64 (blocchi infiniti)
 */
public class LobbyBlocksManager {

    private final HubCorePlugin plugin;
    private final NamespacedKey blockKey;
    private final NamespacedKey selectorKey;
    private final boolean hasProtocolLib;

    // Blocchi piazzati in corso di auto-rimozione: Location → task animazione
    private final Map<Location, BlockAutoRemoveTask> activeTasks = new ConcurrentHashMap<>();

    // Contatore per ID animazione univoci (range positivo per compatibilità ProtocolLib)
    private final AtomicInteger animIdCounter = new AtomicInteger(10000);

    private final BlockSelectorGUI selectorGUI;

    public LobbyBlocksManager(HubCorePlugin plugin) {
        this.plugin = plugin;
        this.blockKey   = new NamespacedKey(plugin, "lobby_block");
        this.selectorKey = new NamespacedKey(plugin, "lobby_selector");
        this.hasProtocolLib = Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
        this.selectorGUI = new BlockSelectorGUI(plugin);

        if (hasProtocolLib) {
            plugin.getLogger().info("ProtocolLib rilevato: animazione rottura blocchi abilitata.");
        } else {
            plugin.getLogger().warning("ProtocolLib non trovato: animazione di rottura disabilitata.");
        }
    }

    // -------------------------------------------------------------------------
    // Creazione item
    // -------------------------------------------------------------------------

    /**
     * Crea lo stack di blocchi. Appare come un blocco Minecraft normale
     * (nessun displayName custom, nessuna lore) con solo il tag PDC nascosto.
     */
    public ItemStack createBlockItem(Material material, int qty) {
        ItemStack item = new ItemStack(material, qty);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String nameTemplate = plugin.getConfigLoader().getLobbyBlockName();
            String blockName = formatMaterialName(material);
            String resolvedName = nameTemplate.replace("{block}", blockName);
            meta.displayName(ColorUtil.itemName(resolvedName));
            meta.getPersistentDataContainer().set(blockKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Formatta il nome del materiale: PURPLE_WOOL → Purple Wool */
    private String formatMaterialName(Material material) {
        String[] words = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    /**
     * Crea l'item selettore con nome, lore e flag dalla config.
     */
    public ItemStack createSelectorItem() {
        var config = plugin.getConfigLoader();
        Material mat;
        try {
            mat = Material.valueOf(config.getLobbySelectorMaterial().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Materiale selettore non valido: " + config.getLobbySelectorMaterial());
            mat = Material.COMPASS;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(ColorUtil.itemName(config.getLobbySelectorName()));

        List<Component> lore = new ArrayList<>();
        for (String line : config.getLobbySelectorLore()) {
            lore.add(ColorUtil.itemName(line));
        }
        if (!lore.isEmpty()) meta.lore(lore);

        for (ItemFlag flag : config.getLobbySelectorFlags()) {
            meta.addItemFlags(flag);
        }
        meta.setUnbreakable(config.isLobbySelectorUnbreakable());
        meta.getPersistentDataContainer().set(selectorKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Consegna item
    // -------------------------------------------------------------------------

    public void giveItems(Player player) {
        if (!plugin.getConfigLoader().isLobbyBlocksEnabled()) return;

        int blockSlot    = plugin.getConfigLoader().getLobbyBlockSlot();
        int selectorSlot = plugin.getConfigLoader().getLobbySelectorSlot();

        // Blocco: preserva il materiale se già correttamente taggato, altrimenti dai il default
        ItemStack existingBlock = player.getInventory().getItem(blockSlot);
        if (existingBlock != null && isBlockItem(existingBlock)) {
            // Assicura quantità 64 e invia aggiornamento
            existingBlock.setAmount(64);
        } else {
            player.getInventory().setItem(blockSlot, createBlockItem(getDefaultMaterial(), 64));
        }

        // Selettore: sempre rimpiazzato per garantire PDC e config aggiornati
        player.getInventory().setItem(selectorSlot, createSelectorItem());
    }

    private Material getDefaultMaterial() {
        try {
            return Material.valueOf(plugin.getConfigLoader().getLobbyDefaultBlock().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.STONE;
        }
    }

    // -------------------------------------------------------------------------
    // Ciclo di vita: piazza → animazione → sparisce
    // -------------------------------------------------------------------------

    /**
     * Avvia l'animazione di rottura sul blocco appena piazzato.
     * Dopo break-time secondi, il blocco viene rimosso automaticamente.
     *
     * @param block        Il blocco piazzato
     * @param originalData BlockData da ripristinare alla fine (ciò che c'era prima)
     */
    public void trackPlacedBlock(Block block, BlockData originalData) {
        Location loc = normalize(block.getLocation());

        // Annulla eventuale task già in corso sulla stessa posizione
        cancelTask(loc);

        // L'animazione dura break-time secondi divisi in 10 stage
        long breakTimeTicks = plugin.getConfigLoader().getLobbyBreakTime() * 20L;
        long periodTicks = Math.max(1L, breakTimeTicks / 10L);

        int animId = animIdCounter.updateAndGet(n -> n >= 100_000 ? 10_000 : n + 1);

        BlockAutoRemoveTask task = new BlockAutoRemoveTask(block, this, animId);
        task.runTaskTimer(plugin, 0L, periodTicks);
        activeTasks.put(loc, task);
    }

    /**
     * Chiamato da BlockAutoRemoveTask al completamento dell'animazione.
     * Rimuove il blocco senza drop.
     */
    public void finishAutoRemove(Block block, int animId) {
        Location loc = normalize(block.getLocation());
        activeTasks.remove(loc);

        // Resetta il crack visuale
        if (hasProtocolLib) sendCrackPacket(block, animId, -1);

        // Rimuovi il blocco (senza drop, senza exp)
        block.setType(Material.AIR, false);
    }

    /** Annulla il task in corso su una posizione (se presente). */
    private void cancelTask(Location loc) {
        BlockAutoRemoveTask existing = activeTasks.remove(loc);
        if (existing != null) {
            existing.resetCrack();
            try { existing.cancel(); } catch (Exception ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // Selezione blocco
    // -------------------------------------------------------------------------

    public void openSelector(Player player) {
        selectorGUI.open(player);
    }

    /**
     * Cambia il materiale dello stack di blocchi nell'hotbar del giocatore.
     */
    public void setSelectedBlock(Player player, Material material) {
        int slot = plugin.getConfigLoader().getLobbyBlockSlot();
        ItemStack current = player.getInventory().getItem(slot);
        int qty = (current != null && isBlockItem(current)) ? current.getAmount() : 64;
        player.getInventory().setItem(slot, createBlockItem(material, qty));
        player.sendActionBar(ColorUtil.colorize("&aHai selezionato &f" + material.name() + "&a!"));
    }

    public boolean isGUIFiller(ItemStack item) {
        return selectorGUI.isFiller(item);
    }

    // -------------------------------------------------------------------------
    // Reload
    // -------------------------------------------------------------------------

    public void reload() {
        selectorGUI.load();
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    public void removePlayer(UUID uuid) {
        // Nessuno stato per-player da pulire nel design attuale
    }

    // -------------------------------------------------------------------------
    // Identificazione item
    // -------------------------------------------------------------------------

    public boolean isBlockItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(blockKey, PersistentDataType.BYTE);
    }

    public boolean isSelectorItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(selectorKey, PersistentDataType.BYTE);
    }

    /** True se la posizione contiene un blocco lobby ancora in fase di auto-rimozione. */
    public boolean isTrackedBlock(Location loc) {
        return activeTasks.containsKey(normalize(loc));
    }

    public boolean isBlacklisted(Material material) {
        return plugin.getConfigLoader().getLobbyBlacklist().contains(material.name().toUpperCase());
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    public boolean hasProtocolLib() {
        return hasProtocolLib;
    }

    public void sendCrackPacket(Block block, int animationId, int stage) {
        ProtocolLibBridge.sendBlockCrack(block, animationId, stage);
    }

    private Location normalize(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}

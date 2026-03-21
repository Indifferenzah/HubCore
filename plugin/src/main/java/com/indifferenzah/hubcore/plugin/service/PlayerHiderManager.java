package com.indifferenzah.hubcore.plugin.service;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestisce il toggle per nascondere/mostrare gli altri giocatori.
 * L'item viene consegnato al join e ha due varianti: not_hidden e hidden.
 */
public class PlayerHiderManager {

    private final HubCorePlugin plugin;

    // PDC key: hubcore:player_hider → "1" (flag di identificazione)
    private final NamespacedKey hiderKey;

    private boolean enabled;
    private int slot;
    private boolean disableMovement;
    private int cooldownSec;

    private ItemStack notHiddenItem;
    private ItemStack hiddenItem;

    // UUID dei giocatori che hanno attivato il nascondi
    private final Set<UUID> hidingPlayers = ConcurrentHashMap.newKeySet();
    // Timestamp (ms) dell'ultimo toggle per cooldown
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public PlayerHiderManager(HubCorePlugin plugin) {
        this.plugin = plugin;
        this.hiderKey = new NamespacedKey(plugin, "player_hider");
        load();
    }

    /** Carica (o ricarica) la configurazione. */
    public void load() {
        enabled = plugin.getConfig().getBoolean("player_hider.enabled", true);
        slot = plugin.getConfig().getInt("player_hider.slot", 8);
        disableMovement = plugin.getConfig().getBoolean("player_hider.disable_inventory_movement", true);
        cooldownSec = plugin.getConfig().getInt("player_hider.cooldown", 3);

        ConfigurationSection nh = plugin.getConfig().getConfigurationSection("player_hider.not_hidden");
        ConfigurationSection h  = plugin.getConfig().getConfigurationSection("player_hider.hidden");
        notHiddenItem = buildItem(nh, "not_hidden");
        hiddenItem    = buildItem(h, "hidden");
    }

    /** Dà l'item appropriato in base allo stato attuale del giocatore. */
    public void giveItem(Player player) {
        if (!enabled) return;
        boolean hiding = hidingPlayers.contains(player.getUniqueId());
        player.getInventory().setItem(slot, hiding ? hiddenItem.clone() : notHiddenItem.clone());
    }

    /** Alterna lo stato nascondi/mostra per il giocatore. */
    public void toggle(Player player) {
        UUID uuid = player.getUniqueId();

        // Controlla cooldown
        Long last = cooldowns.get(uuid);
        if (last != null && System.currentTimeMillis() - last < cooldownSec * 1000L) return;
        cooldowns.put(uuid, System.currentTimeMillis());

        if (hidingPlayers.contains(uuid)) {
            // Mostra tutti i giocatori
            hidingPlayers.remove(uuid);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) player.showPlayer(plugin, other);
            }
        } else {
            // Nasconde tutti i giocatori
            hidingPlayers.add(uuid);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) player.hidePlayer(plugin, other);
            }
        }
        // Aggiorna l'item nell'inventario
        giveItem(player);
    }

    /**
     * Da chiamare quando un nuovo giocatore entra:
     * lo nasconde a chi ha il nascondi attivo.
     */
    public void onNewPlayerJoin(Player newPlayer) {
        for (UUID uuid : hidingPlayers) {
            Player hider = Bukkit.getPlayer(uuid);
            if (hider != null && hider.isOnline()) {
                hider.hidePlayer(plugin, newPlayer);
            }
        }
    }

    /** Da chiamare al quit: pulisce lo stato e ripristina la visibilità. */
    public void onQuit(Player player) {
        UUID uuid = player.getUniqueId();
        hidingPlayers.remove(uuid);
        cooldowns.remove(uuid);
    }

    /** True se l'item è l'item del player hider (verificato via PDC). */
    public boolean isHiderItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(hiderKey, PersistentDataType.BYTE);
    }

    public boolean isEnabled() { return enabled; }
    public boolean isDisableMovement() { return disableMovement; }

    // -------------------------------------------------------------------------

    private ItemStack buildItem(ConfigurationSection s, String fallbackId) {
        if (s == null) return new ItemStack(Material.PAPER);

        Material material;
        try {
            material = Material.valueOf(s.getString("material", "PAPER").toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[PlayerHider] Materiale non valido per '" + fallbackId + "', uso PAPER.");
            material = Material.PAPER;
        }

        ItemStack item = new ItemStack(material, Math.max(1, s.getInt("amount", 1)));
        ItemMeta meta = item.getItemMeta();

        String name = s.getString("display_name", "");
        if (!name.isEmpty()) meta.displayName(ColorUtil.itemName(name));

        List<String> loreList = s.getStringList("lore");
        if (!loreList.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreList) lore.add(ColorUtil.itemName(line));
            meta.lore(lore);
        }

        // PDC tag per identificazione
        meta.getPersistentDataContainer().set(hiderKey, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }
}

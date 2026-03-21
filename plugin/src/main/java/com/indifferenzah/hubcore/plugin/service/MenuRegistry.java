package com.indifferenzah.hubcore.plugin.service;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Registro dei menu apribili tramite l'azione [MENU] nomeMenu.
 * I menu vengono registrati programmaticamente con register().
 * Se il nome richiesto non è registrato, logga un warning.
 */
public class MenuRegistry {

    private final HubCorePlugin plugin;
    private final Map<String, Consumer<Player>> menus = new HashMap<>();

    public MenuRegistry(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Registra un menu per nome (case-insensitive). */
    public void register(String name, Consumer<Player> handler) {
        menus.put(name.toLowerCase(), handler);
    }

    /** Apre il menu con il nome specificato per il giocatore. */
    public void open(Player player, String name) {
        Consumer<Player> handler = menus.get(name.toLowerCase());
        if (handler != null) {
            handler.accept(player);
        } else {
            plugin.getLogger().warning("[MenuRegistry] Menu non registrato: '" + name + "'. Usa MenuRegistry.register() per aggiungerlo.");
        }
    }

    /** True se il menu è registrato. */
    public boolean isRegistered(String name) {
        return menus.containsKey(name.toLowerCase());
    }

    /** Rimuove tutti i menu registrati (usato prima di un reload). */
    public void clear() {
        menus.clear();
    }
}

package com.indifferenzah.hubcore.plugin.config;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;

/**
 * Carica e fornisce i messaggi tradotti da messages.yml.
 */
public class MessagesLoader {

    private final HubCorePlugin plugin;
    private YamlConfiguration messages;

    public MessagesLoader(HubCorePlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * Carica il file messages.yml dalla cartella dati del plugin.
     */
    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Restituisce un messaggio come Component colorato.
     *
     * @param path Percorso YAML del messaggio
     * @return Il Component risultante
     */
    public Component get(String path) {
        String raw = getRaw(path);
        return ColorUtil.colorize(raw);
    }

    /**
     * Restituisce un messaggio come Component colorato con segnaposto sostituiti.
     *
     * @param path         Percorso YAML del messaggio
     * @param placeholders Mappa dei segnaposto da sostituire
     * @return Il Component risultante
     */
    public Component get(String path, Map<String, String> placeholders) {
        String raw = getRaw(path);
        // Sostituisce i segnaposto prima della colorazione
        raw = ColorUtil.replacePlaceholders(raw, placeholders);
        return ColorUtil.colorize(raw);
    }

    /**
     * Restituisce un messaggio grezzo (non colorato) con segnaposto sostituiti.
     *
     * @param path         Percorso YAML del messaggio
     * @param placeholders Mappa dei segnaposto da sostituire
     * @return La stringa grezza
     */
    public String getRawWithPlaceholders(String path, Map<String, String> placeholders) {
        String raw = getRaw(path);
        return ColorUtil.replacePlaceholders(raw, placeholders);
    }

    /**
     * Restituisce la stringa grezza dal file YAML, sostituendo {prefix} con il prefisso configurato.
     *
     * @param path Percorso YAML del messaggio
     * @return La stringa grezza con {prefix} sostituito
     */
    private String getRaw(String path) {
        String value = messages.getString(path, "&cMessaggio non trovato: " + path);
        // Sostituisce automaticamente il segnaposto {prefix} con il prefisso del config
        String prefix = plugin.getConfigLoader().getPrefix();
        return value.replace("{prefix}", prefix);
    }
}

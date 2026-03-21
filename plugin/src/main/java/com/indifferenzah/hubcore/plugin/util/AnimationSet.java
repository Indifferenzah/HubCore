package com.indifferenzah.hubcore.plugin.util;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestisce un insieme di animazioni testuali (frame + intervallo ms).
 * Chiamare tick() ogni tick Bukkit per avanzare i frame.
 */
public class AnimationSet {

    /** Stato interno di una singola animazione. */
    private static class AnimState {
        final List<String> texts;   // frame dell'animazione
        final long intervalMs;      // ms tra un frame e l'altro
        int frame;                  // frame corrente (indice)
        long lastChangeMs;          // timestamp dell'ultimo cambio frame

        AnimState(List<String> texts, long intervalMs) {
            this.texts = texts;
            this.intervalMs = intervalMs;
            this.frame = 0;
            this.lastChangeMs = System.currentTimeMillis();
        }
    }

    /** Mappa nome animazione → stato. LinkedHashMap per mantenere l'ordine di inserimento. */
    private final Map<String, AnimState> animations = new LinkedHashMap<>();

    /**
     * Carica le animazioni da una sezione di config.
     * Formato atteso:
     * <pre>
     *   animations:
     *     NomeAnim:
     *       change-interval: 200   # ms
     *       texts:
     *         - "frame0"
     *         - "frame1"
     * </pre>
     *
     * @param section La sezione "animations" del file YAML, può essere null.
     */
    public void load(ConfigurationSection section) {
        animations.clear();
        if (section == null) return;

        for (String name : section.getKeys(false)) {
            ConfigurationSection anim = section.getConfigurationSection(name);
            if (anim == null) continue;

            long interval = anim.getLong("change-interval", 200);
            List<String> texts = anim.getStringList("texts");

            if (!texts.isEmpty()) {
                // Copia difensiva della lista
                animations.put(name, new AnimState(new ArrayList<>(texts), interval));
            }
        }
    }

    /**
     * Avanza i frame delle animazioni in base al tempo realmente trascorso.
     * Se dall'ultima chiamata sono passati più intervalli, avanza di conseguenza
     * (evita che frame siano "persi" quando tick() viene chiamato di rado).
     */
    public void tick() {
        long now = System.currentTimeMillis();
        for (AnimState state : animations.values()) {
            if (state.texts.size() <= 1) continue;
            long elapsed = now - state.lastChangeMs;
            if (elapsed >= state.intervalMs) {
                // Avanza di quanti frame sono passati nel tempo trascorso
                long steps = elapsed / state.intervalMs;
                state.frame = (int) ((state.frame + steps) % state.texts.size());
                // Mantieni il "resto" per non perdere frazioni di intervallo
                state.lastChangeMs = now - (elapsed % state.intervalMs);
            }
        }
    }

    /**
     * Restituisce il frame corrente dell'animazione come {@link Component} colorizzato.
     * Se l'animazione non esiste o non ha frame, restituisce {@link Component#empty()}.
     *
     * @param name Nome dell'animazione (chiave YAML)
     * @return Il Component colorizzato del frame corrente
     */
    public Component getCurrentFrame(String name) {
        AnimState state = animations.get(name);
        if (state == null || state.texts.isEmpty()) return Component.empty();
        return ColorUtil.colorize(state.texts.get(state.frame));
    }

    /**
     * Restituisce il testo grezzo del frame corrente (non colorizzato).
     * Utile per eseguire sostituzioni inline prima di colorizzare il risultato finale.
     *
     * @param name Nome dell'animazione
     * @return Stringa grezza del frame corrente, o stringa vuota se l'animazione non esiste
     */
    public String getCurrentFrameRaw(String name) {
        AnimState state = animations.get(name);
        if (state == null || state.texts.isEmpty()) return "";
        return state.texts.get(state.frame);
    }

    /**
     * Controlla se un'animazione con il nome dato esiste nel set caricato.
     *
     * @param name Nome dell'animazione
     * @return true se l'animazione esiste
     */
    public boolean has(String name) {
        return animations.containsKey(name);
    }
}

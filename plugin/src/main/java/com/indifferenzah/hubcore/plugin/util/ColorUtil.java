package com.indifferenzah.hubcore.plugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilita' per la colorazione e la formattazione dei testi.
 */
public final class ColorUtil {

    // Pattern per colori esadecimali in formato &#rrggbb
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9A-Fa-f]{6})");

    // Costruttore privato: classe di sola utilita'
    private ColorUtil() {}

    /**
     * Converte una stringa con codici colore in un Component di Adventure.
     * Supporta sia i codici legacy (&x) che i colori esadecimali (&#rrggbb).
     *
     * @param text Il testo da colorare
     * @return Il Component risultante
     */
    public static Component colorize(String text) {
        if (text == null) return Component.empty();
        // 1. Converte &#rrggbb nel formato &x&r&r&g&g&b&b accettato da LegacyComponentSerializer
        String converted = convertHexColors(text);
        // 2. Deserializza usando il serializer legacy con &
        return LegacyComponentSerializer.legacyAmpersand().deserialize(converted);
    }

    /**
     * Come {@link #colorize(String)} ma disabilita esplicitamente il corsivo.
     * Da usare per i nomi e la lore degli item: Minecraft li mostra in corsivo
     * per default quando hanno un nome custom.
     */
    public static Component itemName(String text) {
        return colorize(text).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Converte una stringa con codici colore in una stringa con caratteri § (sezione).
     * Utile per sistemi che accettano stringhe colorate ma non Component.
     *
     * @param text Il testo da colorare
     * @return La stringa con codici § applicati
     */
    public static String colorizeString(String text) {
        if (text == null) return "";
        // 1. Converte i codici esadecimali
        String converted = convertHexColors(text);
        // 2. Sostituisce & con § per i codici legacy standard
        return converted.replace('&', '\u00A7');
    }

    /**
     * Sostituisce i segnaposto nella forma {chiave} con i valori forniti.
     *
     * @param text         Il testo contenente i segnaposto
     * @param placeholders Mappa chiave → valore
     * @return Il testo con i segnaposto sostituiti
     */
    public static String replacePlaceholders(String text, Map<String, String> placeholders) {
        if (text == null) return "";
        if (placeholders == null || placeholders.isEmpty()) return text;

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            // Sostituisce {chiave} con il valore corrispondente
            text = text.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return text;
    }

    /**
     * Converte i colori esadecimali dal formato &#rrggbb al formato &x&r&r&g&g&b&b
     * compatibile con LegacyComponentSerializer.
     *
     * @param text Il testo da convertire
     * @return Il testo con i colori esadecimali convertiti
     */
    private static String convertHexColors(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder builder = new StringBuilder();

        while (matcher.find()) {
            String hex = matcher.group(1);
            // Costruisce la sequenza &x&r&r&g&g&b&b
            StringBuilder replacement = new StringBuilder("&x");
            for (char c : hex.toCharArray()) {
                replacement.append('&').append(c);
            }
            matcher.appendReplacement(builder, replacement.toString());
        }
        matcher.appendTail(builder);

        return builder.toString();
    }
}

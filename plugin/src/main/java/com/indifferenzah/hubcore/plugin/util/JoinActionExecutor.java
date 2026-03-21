package com.indifferenzah.hubcore.plugin.util;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.List;

/**
 * Esegue le azioni della lista join_events e il fuoco d'artificio di join.
 * Azioni supportate:
 *   [TITLE] testo;sottotitolo;fadeIn;stay;fadeOut  (valori in secondi)
 *   [SOUND] NOME_SUONO
 *   [GAMEMODE] survival|creative|adventure|spectator
 *   [EFFECT] NOME_EFFETTO;amplificatore  (amplificatore 1-based, es. 1 = Effetto I)
 */
public class JoinActionExecutor {

    private final HubCorePlugin plugin;

    public JoinActionExecutor(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Esegue tutte le azioni della lista per il giocatore dato. */
    public void executeAll(Player player, List<String> actions) {
        for (String action : actions) {
            try {
                execute(player, action);
            } catch (Exception e) {
                plugin.getLogger().warning("[JoinEvents] Errore nell'azione '" + action + "': " + e.getMessage());
            }
        }
    }

    private void execute(Player player, String action) {
        if (action.startsWith("[TITLE]")) {
            // [TITLE] testo;sottotitolo;fadeIn;stay;fadeOut  (secondi)
            String raw = action.substring(7).trim().replace("%player%", player.getName());
            String[] p = raw.split(";", 5);
            String title    = p.length > 0 ? p[0] : "";
            String subtitle = p.length > 1 ? p[1] : "";
            int fadeIn  = p.length > 2 ? parseIntSafe(p[2]) : 1;
            int stay    = p.length > 3 ? parseIntSafe(p[3]) : 2;
            int fadeOut = p.length > 4 ? parseIntSafe(p[4]) : 1;

            player.showTitle(Title.title(
                    ColorUtil.colorize(title),
                    ColorUtil.colorize(subtitle),
                    Title.Times.times(
                            Duration.ofSeconds(Math.max(0, fadeIn)),
                            Duration.ofSeconds(Math.max(1, stay)),
                            Duration.ofSeconds(Math.max(0, fadeOut))
                    )
            ));

        } else if (action.startsWith("[SOUND]")) {
            String soundName = action.substring(7).trim();
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[JoinEvents] Suono non valido: " + soundName);
            }

        } else if (action.startsWith("[GAMEMODE]")) {
            String gmName = action.substring(10).trim().toUpperCase();
            GameMode gm = switch (gmName) {
                case "SURVIVAL"   -> GameMode.SURVIVAL;
                case "CREATIVE"   -> GameMode.CREATIVE;
                case "ADVENTURE"  -> GameMode.ADVENTURE;
                case "SPECTATOR"  -> GameMode.SPECTATOR;
                default -> null;
            };
            if (gm == null) {
                plugin.getLogger().warning("[JoinEvents] Gamemode non valido: " + gmName);
                return;
            }
            player.setGameMode(gm);

        } else if (action.startsWith("[EFFECT]")) {
            // [EFFECT] NOME_EFFETTO;amplificatore  (1-based: 1 = Effetto I)
            String[] parts = action.substring(8).trim().split(";", 2);
            String effectName = parts[0].trim().toUpperCase();
            int amplifier = parts.length > 1 ? Math.max(0, parseIntSafe(parts[1]) - 1) : 0;

            PotionEffectType type;
            try {
                type = PotionEffectType.getByName(effectName);
            } catch (Exception e) {
                type = null;
            }
            if (type == null) {
                plugin.getLogger().warning("[JoinEvents] Effetto pozione non valido: " + effectName);
                return;
            }
            // Durata praticamente permanente per la sessione (Integer.MAX_VALUE tick)
            player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, amplifier, false, false, false));
        }
    }

    // -------------------------------------------------------------------------
    // Fuoco d'artificio di join
    // -------------------------------------------------------------------------

    /** Lancia il fuoco d'artificio di join, rispettando first_join_only. */
    public void launchFirework(Player player) {
        if (!plugin.getConfigLoader().isFireworkEnabled()) return;
        // hasPlayedBefore() = true se il giocatore si era già connesso in precedenza
        if (plugin.getConfigLoader().isFireworkFirstJoinOnly() && player.hasPlayedBefore()) return;

        FireworkEffect.Builder fwBuilder = FireworkEffect.builder();

        // Tipo (es. BALL_LARGE)
        try {
            fwBuilder.with(FireworkEffect.Type.valueOf(
                    plugin.getConfigLoader().getFireworkType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            fwBuilder.with(FireworkEffect.Type.BALL_LARGE);
        }

        fwBuilder.flicker(plugin.getConfigLoader().isFireworkFlicker());
        fwBuilder.trail(plugin.getConfigLoader().isFireworkTrail());

        // Colori — usa le costanti statiche di org.bukkit.Color tramite reflection
        boolean hasColor = false;
        for (String colorStr : plugin.getConfigLoader().getFireworkColors()) {
            try {
                Color c = (Color) Color.class.getField(colorStr.toUpperCase()).get(null);
                fwBuilder.withColor(c);
                hasColor = true;
            } catch (Exception e) {
                plugin.getLogger().warning("[JoinEvents] Colore fuoco d'artificio non valido: " + colorStr);
            }
        }
        if (!hasColor) fwBuilder.withColor(Color.AQUA); // fallback

        try {
            FireworkEffect effect = fwBuilder.build();
            Firework fw = player.getWorld().spawn(player.getLocation(), Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(effect);
            meta.setPower(plugin.getConfigLoader().getFireworkPower());
            fw.setFireworkMeta(meta);
        } catch (Exception e) {
            plugin.getLogger().warning("[JoinEvents] Errore nel lancio del fuoco d'artificio: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}

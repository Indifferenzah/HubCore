package com.indifferenzah.hubcore.plugin.util;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.List;

/**
 * Esecutore globale delle azioni configurabili.
 * Supportato ovunque nella config (custom items, join events, menu, ecc.).
 *
 * Azioni disponibili:
 *   [MESSAGE] testo
 *   [BROADCAST] testo
 *   [ACTIONBAR] testo
 *   [TITLE] Titolo;Sottotitolo;FadeIn;Stay;FadeOut  (secondi)
 *   [SOUND] NOME_SUONO
 *   [COMMAND] comando           (eseguito come il giocatore)
 *   [CONSOLE] comando           (eseguito dalla console)
 *   [GAMEMODE] 0/1/2/3 o SURVIVAL/CREATIVE/ADVENTURE/SPECTATOR
 *   [EFFECT] NOME_EFFETTO;LIVELLO  (livello 1-based)
 *   [MENU] nomeMenu
 *   [CLOSE]
 *   [PROXY] nomeServer          (BungeeCord Connect)
 *
 * Placeholder sempre sostituiti: %player% → nome del giocatore
 */
public class ActionExecutor {

    private final HubCorePlugin plugin;

    public ActionExecutor(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Esegue tutte le azioni della lista per il giocatore. */
    public void executeAll(Player player, List<String> actions) {
        for (String action : actions) {
            try {
                execute(player, action);
            } catch (Exception e) {
                plugin.getLogger().warning("[ActionExecutor] Errore nell'azione '" + action + "': " + e.getMessage());
            }
        }
    }

    /** Esegue una singola azione per il giocatore. */
    public void execute(Player player, String action) {
        if (action == null || action.isBlank()) return;
        String a = action.trim();

        if (a.startsWith("[MESSAGE]")) {
            String text = placeholder(a.substring(9).trim(), player);
            player.sendMessage(ColorUtil.colorize(text));

        } else if (a.startsWith("[BROADCAST]")) {
            String text = placeholder(a.substring(11).trim(), player);
            Component msg = ColorUtil.colorize(text);
            for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);

        } else if (a.startsWith("[ACTIONBAR]")) {
            String text = placeholder(a.substring(11).trim(), player);
            player.sendActionBar(ColorUtil.colorize(text));

        } else if (a.startsWith("[TITLE]")) {
            // [TITLE] Titolo;Sottotitolo;FadeIn;Stay;FadeOut  (secondi)
            String raw = placeholder(a.substring(7).trim(), player);
            String[] p = raw.split(";", 5);
            String title    = p.length > 0 ? p[0] : "";
            String subtitle = p.length > 1 ? p[1] : "";
            int fadeIn  = p.length > 2 ? safe(p[2]) : 1;
            int stay    = p.length > 3 ? safe(p[3]) : 2;
            int fadeOut = p.length > 4 ? safe(p[4]) : 1;
            player.showTitle(Title.title(
                    ColorUtil.colorize(title),
                    ColorUtil.colorize(subtitle),
                    Title.Times.times(
                            Duration.ofSeconds(Math.max(0, fadeIn)),
                            Duration.ofSeconds(Math.max(1, stay)),
                            Duration.ofSeconds(Math.max(0, fadeOut))
                    )
            ));

        } else if (a.startsWith("[SOUND]")) {
            String soundName = a.substring(7).trim().toUpperCase();
            try {
                player.playSound(player.getLocation(), Sound.valueOf(soundName), 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[ActionExecutor] Suono non valido: " + soundName);
            }

        } else if (a.startsWith("[COMMAND]")) {
            String cmd = placeholder(a.substring(9).trim(), player);
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            Bukkit.dispatchCommand(player, cmd);

        } else if (a.startsWith("[CONSOLE]")) {
            String cmd = placeholder(a.substring(9).trim(), player);
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

        } else if (a.startsWith("[GAMEMODE]")) {
            String gmStr = a.substring(10).trim().toUpperCase();
            GameMode gm = switch (gmStr) {
                case "0", "SURVIVAL"   -> GameMode.SURVIVAL;
                case "1", "CREATIVE"   -> GameMode.CREATIVE;
                case "2", "ADVENTURE"  -> GameMode.ADVENTURE;
                case "3", "SPECTATOR"  -> GameMode.SPECTATOR;
                default -> null;
            };
            if (gm == null) {
                plugin.getLogger().warning("[ActionExecutor] Gamemode non valido: " + gmStr);
                return;
            }
            player.setGameMode(gm);

        } else if (a.startsWith("[EFFECT]")) {
            // [EFFECT] NOME_EFFETTO;LIVELLO  (livello 1-based, es. 1 = Effetto I)
            String[] parts = a.substring(8).trim().split(";", 2);
            String effectName = parts[0].trim().toUpperCase();
            int amplifier = parts.length > 1 ? Math.max(0, safe(parts[1]) - 1) : 0;
            PotionEffectType type = PotionEffectType.getByName(effectName);
            if (type == null) {
                plugin.getLogger().warning("[ActionExecutor] Effetto non valido: " + effectName);
                return;
            }
            player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, amplifier, false, false, false));

        } else if (a.startsWith("[MENU]")) {
            String menuName = a.substring(6).trim();
            plugin.getMenuRegistry().open(player, menuName);

        } else if (a.startsWith("[CLOSE]")) {
            player.closeInventory();

        } else if (a.startsWith("[PROXY]")) {
            // BungeeCord Connect — richiede registerOutgoingPluginChannel("BungeeCord")
            String serverName = a.substring(7).trim();
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        }
    }

    // -------------------------------------------------------------------------

    private String placeholder(String text, Player player) {
        return text.replace("%player%", player.getName());
    }

    private int safe(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}

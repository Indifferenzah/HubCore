package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRegisterChannelEvent;

/**
 * Blocca il mod World Downloader (WDL) rilevando la registrazione
 * dei suoi canali plugin al momento del join.
 *
 * Canali rilevati:
 *  - wdl:init, wdl:request_chunk_data, wdl:control  (versioni moderne)
 *  - WDL|INIT, WDL|REQUEST, WDL|CONTROL             (versioni legacy)
 */
public class AntiWDLListener implements Listener {

    private final HubCorePlugin plugin;

    public AntiWDLListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChannelRegister(PlayerRegisterChannelEvent event) {
        // Controlla se la funzione anti-WDL e' abilitata in config
        if (!plugin.getConfig().getBoolean("anti_wdl.enabled", true)) return;

        // Normalizza il nome del canale in minuscolo per il confronto
        String channel = event.getChannel().toLowerCase();

        // Canali registrati dal mod WDL (tutte le versioni note)
        // Formato moderno: "wdl:*" — formato legacy: "wdl|*"
        if (!channel.startsWith("wdl:") && !channel.startsWith("wdl|")) return;

        Player player = event.getPlayer();

        // Kick del giocatore con messaggio colorizzato
        Component kickMsg = ColorUtil.colorize(
                "&cL'utilizzo di World Downloader non e' consentito su questo server.");
        player.kick(kickMsg);

        // Notifica agli amministratori online
        if (plugin.getConfig().getBoolean("anti_wdl.notify_admins", true)) {
            Component notify = ColorUtil.colorize(
                    "&8[&cAntiWDL&8] &e" + player.getName()
                    + " &7ha tentato di usare WDL ed e' stato kickato.");

            for (Player admin : plugin.getServer().getOnlinePlayers()) {
                if (admin.hasPermission("hubcore.admin")) {
                    admin.sendMessage(notify);
                }
            }

            // Log in console
            plugin.getLogger().warning(
                    "[AntiWDL] " + player.getName()
                    + " kickato (canale: " + event.getChannel() + ")");
        }
    }
}

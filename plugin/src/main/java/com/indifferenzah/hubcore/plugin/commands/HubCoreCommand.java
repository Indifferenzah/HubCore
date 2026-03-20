package com.indifferenzah.hubcore.plugin.commands;

import com.indifferenzah.hubcore.api.model.PlayerData;
import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.Map;

/**
 * Classe che contiene tutti i comandi di HubCore.
 * Usa Lamp v4 per la registrazione e l'esecuzione dei comandi.
 */
public class HubCoreCommand {

    private final HubCorePlugin plugin;

    public HubCoreCommand(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // /hubcore
    // =========================================================================

    /**
     * Comando principale: mostra informazioni sul plugin.
     */
    @Command("hubcore")
    public void hubcore(BukkitCommandActor actor) {
        actor.sender().sendMessage(ColorUtil.colorize(
                "&8[&bHubCore&8] &7Made with &c❤ &7by &cIndifferenzah&7."
        ));
    }

    // =========================================================================
    // /hubcore reload
    // =========================================================================

    /**
     * Ricarica la configurazione e i messaggi del plugin.
     */
    @Command("hubcore reload")
    @CommandPermission("hubcore.reload")
    public void reload(BukkitCommandActor actor) {
        plugin.reload();
        actor.sender().sendMessage(plugin.getMessagesLoader().get("admin.reloaded"));
    }

    // =========================================================================
    // /pvp stats [player]
    // =========================================================================

    /**
     * Mostra le statistiche PvP di un giocatore.
     * Se non viene specificato il giocatore, mostra le statistiche del mittente.
     *
     * @param actor  Chi esegue il comando
     * @param target Il giocatore di cui vedere le statistiche (opzionale)
     */
    @Command("pvp stats")
    @CommandPermission("hubcore.pvp.stats")
    public void pvpStats(BukkitCommandActor actor, @Optional Player target) {
        // Se target e' null, usa il sender (deve essere un player)
        Player targetPlayer;
        if (target == null) {
            if (!(actor.sender() instanceof Player senderPlayer)) {
                actor.sender().sendMessage(ColorUtil.colorize("&cDevi specificare un giocatore dalla console."));
                return;
            }
            targetPlayer = senderPlayer;
        } else {
            targetPlayer = target;
        }

        var statsOpt = plugin.getStatsService().getPlayer(targetPlayer.getUniqueId());
        if (statsOpt.isEmpty()) {
            actor.sender().sendMessage(plugin.getMessagesLoader().get("stats.not-found",
                    Map.of("player", targetPlayer.getName())));
            return;
        }

        PlayerData data = statsOpt.get();
        String kd = String.format("%.2f", data.getKD());

        // Invia le statistiche formattate
        actor.sender().sendMessage(plugin.getMessagesLoader().get("stats.header",
                Map.of("player", data.getName())));
        actor.sender().sendMessage(plugin.getMessagesLoader().get("stats.kills",
                Map.of("kills", String.valueOf(data.getKills()))));
        actor.sender().sendMessage(plugin.getMessagesLoader().get("stats.deaths",
                Map.of("deaths", String.valueOf(data.getDeaths()))));
        actor.sender().sendMessage(plugin.getMessagesLoader().get("stats.kd",
                Map.of("kd", kd)));
        actor.sender().sendMessage(plugin.getMessagesLoader().get("stats.killstreak",
                Map.of("killstreak", String.valueOf(data.getKillstreak()))));
        actor.sender().sendMessage(plugin.getMessagesLoader().get("stats.best-streak",
                Map.of("best", String.valueOf(data.getBestStreak()))));
        actor.sender().sendMessage(plugin.getMessagesLoader().get("stats.footer",
                Map.of("player", data.getName())));
    }

    // =========================================================================
    // /pvp reset <player>
    // =========================================================================

    /**
     * Azzera le statistiche PvP di un giocatore.
     *
     * @param actor  Chi esegue il comando
     * @param target Il giocatore di cui azzerare le statistiche
     */
    @Command("pvp reset")
    @CommandPermission("hubcore.pvp.reset")
    public void pvpReset(BukkitCommandActor actor, Player target) {
        plugin.getStatsService().resetStats(target.getUniqueId());
        actor.sender().sendMessage(plugin.getMessagesLoader().get("admin.stats-reset",
                Map.of("player", target.getName())));
        // Notifica anche il giocatore target se online
        target.sendMessage(plugin.getMessagesLoader().get("stats.reset-notify"));
    }
}

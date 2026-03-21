package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import com.indifferenzah.hubcore.plugin.util.JoinActionExecutor;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listener per la gestione dell'ingresso del giocatore nel server.
 */
public class PlayerJoinListener implements Listener {

    private final HubCorePlugin plugin;
    private final JoinActionExecutor actionExecutor;

    public PlayerJoinListener(HubCorePlugin plugin) {
        this.plugin = plugin;
        this.actionExecutor = new JoinActionExecutor(plugin);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        // 1. Carica le statistiche dal database
        plugin.getStatsService().loadPlayer(player.getUniqueId(), player.getName());

        // 2. Messaggio di join personalizzato (o soppresso)
        if (plugin.getConfigLoader().isJoinLeaveEnabled()) {
            String raw = plugin.getConfigLoader().getJoinMessage()
                    .replace("%player%", player.getName());
            if (player.hasPermission("hubcore.announce.join") && !raw.isBlank()) {
                // Broadcast a tutti
                event.joinMessage(ColorUtil.colorize(raw));
            } else {
                // Sopprime il messaggio vanilla senza inviare nulla
                event.joinMessage(null);
            }
        } else {
            // Feature disabilitata → sopprime il messaggio vanilla
            event.joinMessage(null);
        }

        // 3. Join settings — eseguiti subito (prima del delay dell'inventario)
        if (plugin.getConfigLoader().isJoinHeal()) {
            player.setHealth(player.getAttribute(
                    org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
        if (plugin.getConfigLoader().isJoinExtinguish()) {
            player.setFireTicks(0);
        }
        if (plugin.getConfigLoader().isJoinClearInventory()) {
            player.getInventory().clear();
        }

        // 4. Notifica di aggiornamento disponibile (solo agli admin)
        if (plugin.isUpdateAvailable() && player.hasPermission("hubcore.admin")) {
            Component msg = plugin.getUpdateMessage();
            if (msg != null) player.sendMessage(msg);
        }

        // 5. Abilita double jump con delay: il client deve essere pronto prima di ricevere allowFlight
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> { if (player.isOnline()) plugin.getDoubleJumpListener().enableFor(player); }, 10L);

        // 6. Tab e sidebar (eseguiti subito al join)
        plugin.getTabManager().onJoin(player);
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> { if (player.isOnline()) plugin.getScoreboardManager().onJoin(player); }, 1L);

        // 6. Delay di 2 tick: altri plugin potrebbero sovrascrivere l'inventario subito dopo il join
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            // Teletrasporto allo spawn
            if (plugin.getConfigLoader().isSpawnJoin()) {
                Location spawnLoc = plugin.getH2StatsService().getLobbyLocation();
                if (spawnLoc != null) player.teleport(spawnLoc);
            }

            // Spada PvP
            if (!plugin.getSwordManager().hasSword(player)) {
                plugin.getSwordManager().giveSword(player);
            }
            // Item lobby blocks
            plugin.getLobbyBlocksManager().giveItems(player);

            // Custom join items (server selector, ecc.)
            plugin.getCustomJoinItemsManager().giveItems(player);

            // Player hider item
            plugin.getPlayerHiderManager().giveItem(player);

            // Nasconde il giocatore ai player che hanno il nascondi attivo
            plugin.getPlayerHiderManager().onNewPlayerJoin(player);

            // Join events ([TITLE], [SOUND], [GAMEMODE], [EFFECT], ecc.)
            actionExecutor.executeAll(player, plugin.getConfigLoader().getJoinEvents());

            // Fuoco d'artificio
            actionExecutor.launchFirework(player);

        }, 2L);
    }
}

package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.api.enums.PvPState;
import com.indifferenzah.hubcore.api.model.PlayerData;
import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Listener che gestisce il cambio di slot selezionato dal giocatore.
 * Controlla se il giocatore impugna o rimuove la spada PvP e aggiorna lo stato di conseguenza.
 */
public class PlayerItemHeldListener implements Listener {

    private final HubCorePlugin plugin;

    public PlayerItemHeldListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        int newSlot = event.getNewSlot();
        int previousSlot = event.getPreviousSlot();
        int swordSlot = plugin.getConfigLoader().getSwordSlot();

        // -----------------------------------------------------------------------
        // Il giocatore sta impugnando la spada PvP
        // -----------------------------------------------------------------------
        if (newSlot == swordSlot) {
            ItemStack item = player.getInventory().getItem(newSlot);
            if (item != null && plugin.getSwordManager().isPvPSword(item)) {
                handleSwordEquipped(player);
            }
        }

        // -----------------------------------------------------------------------
        // Il giocatore sta rimuovendo la spada PvP dallo slot configurato
        // -----------------------------------------------------------------------
        if (previousSlot == swordSlot) {
            ItemStack item = player.getInventory().getItem(previousSlot);
            if (item != null && plugin.getSwordManager().isPvPSword(item)) {
                handleSwordUnequipped(player);
            }
        }
    }

    /**
     * Gestisce l'equip della spada PvP nello slot configurato.
     */
    private void handleSwordEquipped(Player player) {
        var uuid = player.getUniqueId();
        var pvpService = plugin.getPvpService();
        PvPState state = pvpService.getState(uuid);

        // Controlla il delay di rispawn
        if (pvpService.hasRespawnDelay(uuid)) {
            player.sendMessage(plugin.getMessagesLoader().get("pvp.respawn-delay"));
            return;
        }

        switch (state) {
            case INACTIVE -> {
                // Avvia il delay di attivazione
                pvpService.startEnableDelay(player);
            }
            case DISABLING -> {
                // Il giocatore ha ripreso la spada durante il delay di disattivazione: annulla
                pvpService.cancelDisableDelay(uuid);
            }
            case COMBAT_TAG -> {
                // Il giocatore ha ripreso la spada durante il combat tag: annulla il desiderio di disattivare
                Optional<PlayerData> dataOpt = plugin.getStatsService().getPlayer(uuid);
                dataOpt.ifPresent(data -> data.setWantsToDisable(false));
            }
            default -> {
                // Negli altri stati (ENABLING, ACTIVE) non fa nulla
            }
        }
    }

    /**
     * Gestisce la rimozione della spada PvP dallo slot configurato.
     */
    private void handleSwordUnequipped(Player player) {
        var uuid = player.getUniqueId();
        var pvpService = plugin.getPvpService();
        PvPState state = pvpService.getState(uuid);

        switch (state) {
            case ENABLING -> {
                // Il giocatore ha rimosso la spada durante il delay di attivazione: annulla
                pvpService.cancelEnableDelay(uuid);
            }
            case ACTIVE -> {
                // Il giocatore ha rimosso la spada con PvP attivo: avvia il delay di disattivazione
                pvpService.startDisableDelay(player);
            }
            case COMBAT_TAG -> {
                // Il giocatore ha rimosso la spada durante il combat tag:
                // segnala che vuole disattivare ma non puo' farlo ora
                Optional<PlayerData> dataOpt = plugin.getStatsService().getPlayer(uuid);
                dataOpt.ifPresent(data -> data.setWantsToDisable(true));
                player.sendMessage(plugin.getMessagesLoader().get("pvp.combat-tag-cannot-disable"));
            }
            default -> {
                // Negli altri stati (INACTIVE, DISABLING) non fa nulla
            }
        }
    }
}

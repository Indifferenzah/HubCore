package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.api.enums.PvPState;
import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Listener principale per la gestione del danno PvP.
 * Implementa la logica di controllo PvP, ignorando WorldGuard.
 */
public class EntityDamageListener implements Listener {

    private final HubCorePlugin plugin;

    public EntityDamageListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Controlla che la vittima sia un giocatore
        if (!(event.getEntity() instanceof Player victim)) return;

        // Risolve l'attaccante (gestisce frecce e altri proiettili)
        Player attacker = resolveAttacker(event);
        if (attacker == null) return; // L'attaccante non e' un giocatore, ignora

        // Impedisce l'autodanno
        if (attacker.equals(victim)) return;

        var pvpService = plugin.getPvpService();
        var messages = plugin.getMessagesLoader();
        boolean attackerInPvP = pvpService.isInPvP(attacker.getUniqueId());
        boolean victimInPvP = pvpService.isInPvP(victim.getUniqueId());

        // -----------------------------------------------------------------------
        // Nessuno dei due e' in PvP: cancella l'evento
        // -----------------------------------------------------------------------
        if (!attackerInPvP && !victimInPvP) {
            event.setCancelled(true);
            return;
        }

        // -----------------------------------------------------------------------
        // Solo uno dei due e' in PvP: cancella e notifica
        // -----------------------------------------------------------------------
        if (attackerInPvP && !victimInPvP) {
            event.setCancelled(true);
            attacker.sendMessage(messages.get("pvp.target-not-in-pvp",
                    java.util.Map.of("target", victim.getName())));
            return;
        }

        if (!attackerInPvP && victimInPvP) {
            event.setCancelled(true);
            // Notifica l'attaccante che non e' in PvP
            attacker.sendMessage(messages.get("pvp.not-in-pvp"));
            return;
        }

        // -----------------------------------------------------------------------
        // Entrambi sono in PvP: gestisce il combattimento
        // -----------------------------------------------------------------------

        // Aggiorna il combat tag per entrambi
        pvpService.updateCombatTag(attacker, victim);

        // Se cancel-on-damage e' attivo, interrompe il delay di attivazione
        if (plugin.getConfigLoader().isCancelOnDamage()) {
            if (pvpService.getState(attacker.getUniqueId()) == PvPState.ENABLING) {
                pvpService.cancelEnableDelay(attacker.getUniqueId());
            }
            if (pvpService.getState(victim.getUniqueId()) == PvPState.ENABLING) {
                pvpService.cancelEnableDelay(victim.getUniqueId());
            }
        }

        // Controlla se il danno uccide la vittima
        double finalDamage = event.getFinalDamage();
        if (finalDamage >= victim.getHealth()) {
            // Gestisce l'uccisione: le statistiche vengono aggiornate prima della morte
            pvpService.handleKill(attacker, victim);
        }
    }

    /**
     * Risolve l'attaccante reale dall'evento.
     * Gestisce i proiettili (frecce, snowball, ecc.) per trovare il lanciatore.
     *
     * @param event L'evento di danno
     * @return Il Player attaccante, o null se non e' un giocatore
     */
    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player directAttacker) {
            return directAttacker;
        }

        // Gestisce i proiettili
        if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                return shooter;
            }
        }

        return null;
    }
}

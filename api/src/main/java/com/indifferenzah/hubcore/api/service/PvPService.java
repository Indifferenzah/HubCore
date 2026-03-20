package com.indifferenzah.hubcore.api.service;

import com.indifferenzah.hubcore.api.enums.PvPState;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Interfaccia per la gestione dello stato PvP dei giocatori.
 */
public interface PvPService {

    /**
     * Avvia il delay di attivazione PvP per il giocatore.
     *
     * @param player Il giocatore
     */
    void startEnableDelay(Player player);

    /**
     * Annulla il delay di attivazione PvP se in corso.
     *
     * @param uuid UUID del giocatore
     */
    void cancelEnableDelay(UUID uuid);

    /**
     * Attiva immediatamente il PvP per il giocatore.
     *
     * @param player Il giocatore
     */
    void activatePvP(Player player);

    /**
     * Avvia il delay di disattivazione PvP per il giocatore.
     *
     * @param player Il giocatore
     */
    void startDisableDelay(Player player);

    /**
     * Annulla il delay di disattivazione PvP se in corso.
     *
     * @param uuid UUID del giocatore
     */
    void cancelDisableDelay(UUID uuid);

    /**
     * Disattiva immediatamente il PvP per il giocatore.
     *
     * @param player Il giocatore
     */
    void deactivatePvP(Player player);

    /**
     * Aggiorna il combat tag per attaccante e vittima.
     *
     * @param attacker Il giocatore attaccante
     * @param victim   Il giocatore vittima
     */
    void updateCombatTag(Player attacker, Player victim);

    /**
     * Gestisce l'uccisione di un giocatore da parte di un altro.
     *
     * @param killer Il giocatore che ha ucciso
     * @param victim Il giocatore ucciso
     */
    void handleKill(Player killer, Player victim);

    /**
     * Gestisce la morte di un giocatore (senza killer).
     *
     * @param victim Il giocatore morto
     */
    void handleDeath(Player victim);

    /**
     * Controlla se il giocatore e' in PvP (ACTIVE, COMBAT_TAG o DISABLING).
     *
     * @param uuid UUID del giocatore
     * @return true se in PvP
     */
    boolean isInPvP(UUID uuid);

    /**
     * Controlla se il giocatore e' in combat tag.
     *
     * @param uuid UUID del giocatore
     * @return true se in COMBAT_TAG
     */
    boolean isInCombat(UUID uuid);

    /**
     * Restituisce lo stato PvP corrente del giocatore.
     *
     * @param uuid UUID del giocatore
     * @return Lo stato PvP
     */
    PvPState getState(UUID uuid);

    /**
     * Forza l'attivazione del PvP (comando admin).
     *
     * @param player Il giocatore
     */
    void forceEnable(Player player);

    /**
     * Forza la disattivazione del PvP (comando admin).
     *
     * @param player Il giocatore
     */
    void forceDisable(Player player);

    /**
     * Rimuove completamente il giocatore dalla gestione PvP (es. al quit).
     *
     * @param uuid UUID del giocatore
     */
    void removePlayer(UUID uuid);

    /**
     * Applica un delay di rispawn durante il quale il PvP non puo' essere attivato.
     *
     * @param uuid UUID del giocatore
     */
    void applyRespawnDelay(UUID uuid);

    /**
     * Controlla se il giocatore ha un delay di rispawn attivo.
     *
     * @param uuid UUID del giocatore
     * @return true se il delay e' attivo
     */
    boolean hasRespawnDelay(UUID uuid);
}

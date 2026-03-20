package com.indifferenzah.hubcore.api.enums;

/**
 * Rappresenta lo stato PvP di un giocatore.
 */
public enum PvPState {
    INACTIVE,    // PvP disattivato
    ENABLING,    // Delay di attivazione in corso
    ACTIVE,      // PvP attivo
    DISABLING,   // Delay di disattivazione in corso
    COMBAT_TAG   // PvP attivo + in combattimento (non disattivabile)
}

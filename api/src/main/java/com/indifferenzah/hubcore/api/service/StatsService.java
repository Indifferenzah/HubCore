package com.indifferenzah.hubcore.api.service;

import com.indifferenzah.hubcore.api.model.PlayerData;

import java.util.Optional;
import java.util.UUID;

/**
 * Interfaccia per la gestione delle statistiche PvP dei giocatori.
 */
public interface StatsService {

    /**
     * Carica (o crea) il profilo del giocatore dal database nella cache in memoria.
     *
     * @param uuid UUID del giocatore
     * @param name Nome del giocatore
     */
    void loadPlayer(UUID uuid, String name);

    /**
     * Salva il profilo del giocatore dalla cache al database.
     *
     * @param uuid UUID del giocatore
     */
    void savePlayer(UUID uuid);

    /**
     * Salva tutti i profili in cache nel database.
     */
    void saveAll();

    /**
     * Restituisce i dati del giocatore dalla cache.
     *
     * @param uuid UUID del giocatore
     * @return Optional contenente i dati del giocatore, o vuoto se non presente
     */
    Optional<PlayerData> getPlayer(UUID uuid);

    /**
     * Azzera le statistiche del giocatore nel database e nella cache.
     *
     * @param uuid UUID del giocatore
     */
    void resetStats(UUID uuid);
}

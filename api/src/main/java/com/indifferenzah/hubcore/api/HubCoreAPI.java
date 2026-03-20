package com.indifferenzah.hubcore.api;

import com.indifferenzah.hubcore.api.service.PvPService;
import com.indifferenzah.hubcore.api.service.StatsService;

/**
 * Punto di accesso statico all'API di HubCore.
 * Consente ad altri plugin di interagire con i servizi di HubCore.
 */
public final class HubCoreAPI {

    // Servizio statistiche registrato dall'implementazione
    private static StatsService statsService;

    // Servizio PvP registrato dall'implementazione
    private static PvPService pvpService;

    // Costruttore privato: classe non istanziabile
    private HubCoreAPI() {}

    /**
     * Restituisce il servizio statistiche.
     *
     * @return L'istanza di StatsService
     * @throws IllegalStateException se HubCore non e' ancora stato inizializzato
     */
    public static StatsService getStatsService() {
        if (statsService == null) {
            throw new IllegalStateException("HubCore non e' ancora stato inizializzato.");
        }
        return statsService;
    }

    /**
     * Imposta il servizio statistiche (chiamato da HubCorePlugin).
     *
     * @param service Implementazione di StatsService
     */
    public static void setStatsService(StatsService service) {
        statsService = service;
    }

    /**
     * Restituisce il servizio PvP.
     *
     * @return L'istanza di PvPService
     * @throws IllegalStateException se HubCore non e' ancora stato inizializzato
     */
    public static PvPService getPvpService() {
        if (pvpService == null) {
            throw new IllegalStateException("HubCore non e' ancora stato inizializzato.");
        }
        return pvpService;
    }

    /**
     * Imposta il servizio PvP (chiamato da HubCorePlugin).
     *
     * @param service Implementazione di PvPService
     */
    public static void setPvpService(PvPService service) {
        pvpService = service;
    }
}

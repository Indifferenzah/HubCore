package com.indifferenzah.hubcore.api.model;

import com.indifferenzah.hubcore.api.enums.PvPState;

import java.util.UUID;

/**
 * Dati statistici e di stato PvP di un giocatore.
 */
public class PlayerData {

    private final UUID uuid;
    private String name;
    private int kills;
    private int deaths;
    private int killstreak;
    private int bestStreak;
    private PvPState pvpState;

    /**
     * Flag non persistito: indica che il giocatore vuole disattivare il PvP
     * ma si trova in stato COMBAT_TAG. Viene applicato alla scadenza del combat tag.
     */
    private boolean wantsToDisable;

    /**
     * Costruttore principale.
     *
     * @param uuid UUID del giocatore
     * @param name Nome del giocatore
     */
    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.kills = 0;
        this.deaths = 0;
        this.killstreak = 0;
        this.bestStreak = 0;
        this.pvpState = PvPState.INACTIVE;
        this.wantsToDisable = false;
    }

    // -------------------------------------------------------------------------
    // Getter / Setter
    // -------------------------------------------------------------------------

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }

    public int getKillstreak() {
        return killstreak;
    }

    public void setKillstreak(int killstreak) {
        this.killstreak = killstreak;
    }

    public int getBestStreak() {
        return bestStreak;
    }

    public void setBestStreak(int bestStreak) {
        this.bestStreak = bestStreak;
    }

    public PvPState getPvpState() {
        return pvpState;
    }

    public void setPvpState(PvPState pvpState) {
        this.pvpState = pvpState;
    }

    public boolean isWantsToDisable() {
        return wantsToDisable;
    }

    public void setWantsToDisable(boolean wantsToDisable) {
        this.wantsToDisable = wantsToDisable;
    }

    // -------------------------------------------------------------------------
    // Metodi di utilita'
    // -------------------------------------------------------------------------

    /**
     * Incrementa le kill e la killstreak. Aggiorna il best streak se necessario.
     */
    public void incrementKills() {
        this.kills++;
        this.killstreak++;
        if (this.killstreak > this.bestStreak) {
            this.bestStreak = this.killstreak;
        }
    }

    /**
     * Incrementa le morti e azzera la killstreak corrente.
     */
    public void incrementDeaths() {
        this.deaths++;
        this.killstreak = 0;
    }

    /**
     * Calcola il rapporto kill/death.
     *
     * @return K/D come double; 0.0 se non ci sono morti
     */
    public double getKD() {
        if (deaths == 0) {
            return 0.0;
        }
        return (double) kills / (double) deaths;
    }
}

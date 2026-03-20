package com.indifferenzah.hubcore.plugin.service;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.config.ConfigLoader;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Gestisce la riproduzione di effetti visivi e sonori legati al PvP.
 */
public class EffectManager {

    private final HubCorePlugin plugin;

    public EffectManager(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Effetti on-kill
    // -------------------------------------------------------------------------

    /**
     * Riproduce gli effetti di uccisione (particelle e suono) sulla vittima.
     *
     * @param killer Il giocatore che ha ucciso
     * @param victim Il giocatore ucciso
     */
    public void playOnKillEffects(Player killer, Player victim) {
        ConfigLoader config = plugin.getConfigLoader();

        // Particelle alla posizione della vittima
        if (config.isOnKillParticlesEnabled()) {
            Location loc = victim.getLocation().add(0, 1, 0);
            loc.getWorld().spawnParticle(
                    config.getOnKillParticleType(),
                    loc,
                    config.getOnKillParticleCount(),
                    config.getOnKillParticleOffsetX(),
                    config.getOnKillParticleOffsetY(),
                    config.getOnKillParticleOffsetZ()
            );
        }

        // Suono
        if (config.isOnKillSoundEnabled()) {
            playSound(
                    killer,
                    victim,
                    config.getOnKillSoundType(),
                    config.getOnKillSoundVolume(),
                    config.getOnKillSoundPitch(),
                    config.getOnKillSoundTarget(),
                    config.getOnKillSoundRadius()
            );
        }
    }

    /**
     * Riproduce il suono di attivazione PvP per il giocatore.
     *
     * @param player Il giocatore
     */
    public void playPvpEnableEffect(Player player) {
        ConfigLoader config = plugin.getConfigLoader();
        if (config.isOnPvpEnableSoundEnabled()) {
            player.playSound(player.getLocation(),
                    config.getOnPvpEnableSound(),
                    config.getOnPvpEnableSoundVolume(),
                    config.getOnPvpEnableSoundPitch());
        }
    }

    /**
     * Riproduce il suono di disattivazione PvP per il giocatore.
     *
     * @param player Il giocatore
     */
    public void playPvpDisableEffect(Player player) {
        ConfigLoader config = plugin.getConfigLoader();
        if (config.isOnPvpDisableSoundEnabled()) {
            player.playSound(player.getLocation(),
                    config.getOnPvpDisableSound(),
                    config.getOnPvpDisableSoundVolume(),
                    config.getOnPvpDisableSoundPitch());
        }
    }

    // -------------------------------------------------------------------------
    // Metodo generico per suoni
    // -------------------------------------------------------------------------

    /**
     * Riproduce un suono in base alla modalita' target specificata.
     *
     * @param killer     Il giocatore killer (puo' essere null per contesti diversi)
     * @param victim     Il giocatore vittima (puo' essere null per contesti diversi)
     * @param sound      Il suono da riprodurre
     * @param volume     Volume del suono
     * @param pitch      Pitch del suono
     * @param targetMode Modalita': KILLER, VICTIM, BOTH, BROADCAST_RADIUS
     * @param radius     Raggio (usato solo con BROADCAST_RADIUS)
     */
    public void playSound(Player killer, Player victim, Sound sound,
                           float volume, float pitch, String targetMode, double radius) {
        switch (targetMode) {
            case "KILLER" -> {
                if (killer != null) {
                    killer.playSound(killer.getLocation(), sound, volume, pitch);
                }
            }
            case "VICTIM" -> {
                if (victim != null) {
                    victim.playSound(victim.getLocation(), sound, volume, pitch);
                }
            }
            case "BOTH" -> {
                if (killer != null) killer.playSound(killer.getLocation(), sound, volume, pitch);
                if (victim != null) victim.playSound(victim.getLocation(), sound, volume, pitch);
            }
            case "BROADCAST_RADIUS" -> {
                // Riproduce il suono a tutti i giocatori nel raggio specificato dalla posizione della vittima
                if (victim != null) {
                    Location center = victim.getLocation();
                    double radiusSq = radius * radius;
                    for (Player p : victim.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(center) <= radiusSq) {
                            p.playSound(center, sound, volume, pitch);
                        }
                    }
                }
            }
            default -> {
                // Modalita' sconosciuta: riproduce solo al killer
                if (killer != null) {
                    killer.playSound(killer.getLocation(), sound, volume, pitch);
                }
            }
        }
    }
}

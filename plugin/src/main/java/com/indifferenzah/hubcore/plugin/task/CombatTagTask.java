package com.indifferenzah.hubcore.plugin.task;

import com.indifferenzah.hubcore.plugin.service.PvPServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Task che scade il combat tag di un giocatore.
 * Alla scadenza: se il giocatore vuole disattivare il PvP (wantsToDisable),
 * avvia il delay di disattivazione; altrimenti torna allo stato ACTIVE.
 */
public class CombatTagTask extends BukkitRunnable {

    private final UUID playerUuid;
    private final PvPServiceImpl pvpService;

    public CombatTagTask(UUID playerUuid, PvPServiceImpl pvpService) {
        this.playerUuid = playerUuid;
        this.pvpService = pvpService;
    }

    @Override
    public void run() {
        // Fa scadere il combat tag del giocatore
        pvpService.expireCombatTag(playerUuid);
    }
}

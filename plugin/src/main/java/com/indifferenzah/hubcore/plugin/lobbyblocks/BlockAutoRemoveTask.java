package com.indifferenzah.hubcore.plugin.lobbyblocks;

import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Task che mostra l'animazione di rottura progressiva (stage 0-9) su un blocco
 * prima della sua auto-rimozione. Invia i pacchetti ProtocolLib a intervalli regolari.
 */
public class BlockAutoRemoveTask extends BukkitRunnable {

    private static final int TOTAL_STAGES = 10;

    private final Block block;
    private final LobbyBlocksManager manager;
    private final int animationId;
    private int stage = 0;
    private boolean done = false;

    public BlockAutoRemoveTask(Block block, LobbyBlocksManager manager, int animationId) {
        this.block = block;
        this.manager = manager;
        this.animationId = animationId;
    }

    @Override
    public void run() {
        if (manager.hasProtocolLib()) {
            manager.sendCrackPacket(block, animationId, stage);
        }
        stage++;

        if (stage >= TOTAL_STAGES) {
            done = true;
            safeCancel();
            manager.finishAutoRemove(block, animationId);
        }
    }

    /** Resetta il crack visuale senza terminare il ciclo (usato per cancellazione esterna). */
    public void resetCrack() {
        if (!done && manager.hasProtocolLib()) {
            manager.sendCrackPacket(block, animationId, -1);
        }
    }

    private void safeCancel() {
        try { cancel(); } catch (IllegalStateException ignored) {}
    }
}

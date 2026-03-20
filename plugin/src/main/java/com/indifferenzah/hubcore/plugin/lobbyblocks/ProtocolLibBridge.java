package com.indifferenzah.hubcore.plugin.lobbyblocks;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Bridge per ProtocolLib: contiene TUTTI i riferimenti alle classi ProtocolLib.
 * Questa classe viene caricata solo se ProtocolLib è disponibile a runtime.
 * Chiamare i metodi solo dopo aver verificato che ProtocolLib sia presente.
 */
public class ProtocolLibBridge {

    private ProtocolLibBridge() {}

    /**
     * Invia il pacchetto BLOCK_BREAK_ANIMATION a tutti i giocatori nel raggio di 20 blocchi.
     *
     * @param block       Il blocco che si sta rompendo
     * @param animationId ID univoco per l'animazione (non deve corrispondere a un entity ID reale)
     * @param stage       Stage di crack 0-9, oppure -1 per rimuovere l'animazione
     */
    public static void sendBlockCrack(Block block, int animationId, int stage) {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();

        PacketContainer packet = manager.createPacket(PacketType.Play.Server.BLOCK_BREAK_ANIMATION);
        packet.getIntegers().write(0, animationId);
        packet.getBlockPositionModifier().write(0,
                new BlockPosition(block.getX(), block.getY(), block.getZ()));
        packet.getIntegers().write(1, stage);

        // Invia a tutti i giocatori entro 20 blocchi (20^2 = 400)
        for (Player nearby : block.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(
                    block.getLocation().add(0.5, 0.5, 0.5)) <= 400.0) {
                try {
                    manager.sendServerPacket(nearby, packet);
                } catch (Exception ignored) {}
            }
        }
    }
}

package com.indifferenzah.hubcore.plugin.service;

import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/**
 * Gestisce il blocco globale della chat tramite /lockchat.
 * I giocatori con hubcore.staff.lockbypass possono chattare anche con la chat bloccata.
 */
public class ChatLockManager {

    private boolean locked = false;

    /** Alterna lo stato della chat e notifica tutti i giocatori online. */
    public void toggle(CommandSender sender) {
        locked = !locked;
        if (locked) {
            Bukkit.broadcast(ColorUtil.colorize("&4[&cSERVER&4] &cLa chat è stata &lBLOCCATA&c dagli staff."));
        } else {
            Bukkit.broadcast(ColorUtil.colorize("&2[&aSERVER&2] &aLa chat è stata &lSBLOCCATA&a dagli staff."));
        }
    }

    public boolean isLocked() {
        return locked;
    }
}

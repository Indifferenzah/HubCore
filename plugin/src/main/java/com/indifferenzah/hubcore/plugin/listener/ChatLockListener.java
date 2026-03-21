package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Blocca i messaggi in chat quando la chat è bloccata da /lockchat.
 * I giocatori con hubcore.staff.lockbypass possono sempre chattare.
 */
public class ChatLockListener implements Listener {

    private final HubCorePlugin plugin;

    public ChatLockListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getChatLockManager().isLocked()) return;
        if (event.getPlayer().hasPermission("hubcore.staff.lockbypass")) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(ColorUtil.colorize("&cLa chat è attualmente bloccata dagli staff."));
    }
}

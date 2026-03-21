package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.service.CustomJoinItemsManager;
import com.indifferenzah.hubcore.plugin.service.PlayerHiderManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Gestisce i click sugli item custom (custom_join_items e player_hider).
 * Usa LOWEST per eseguirsi prima degli altri listener (es. WorldSettingsListener).
 */
public class CustomItemsListener implements Listener {

    private final HubCorePlugin plugin;

    public CustomItemsListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Tasto destro → esegue le azioni dell'item
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        // Solo click destro (in aria o su blocco)
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) return;

        CustomJoinItemsManager cjm = plugin.getCustomJoinItemsManager();
        PlayerHiderManager phm = plugin.getPlayerHiderManager();

        // Custom join item → esegui azioni
        if (cjm.isEnabled() && cjm.isCustomItem(item)) {
            event.setCancelled(true);
            String id = cjm.getItemId(item);
            if (id != null) {
                plugin.getActionExecutor().executeAll(player, cjm.getActions(id));
            }
            return;
        }

        // Player hider → toggle visibilità
        if (phm.isEnabled() && phm.isHiderItem(item)) {
            event.setCancelled(true);
            phm.toggle(player);
        }
    }

    // -------------------------------------------------------------------------
    // Blocca il movimento nell'inventario se disable_inventory_movement = true
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        CustomJoinItemsManager cjm = plugin.getCustomJoinItemsManager();
        PlayerHiderManager phm = plugin.getPlayerHiderManager();

        // Controlla sia l'item nella slot cliccata che quello sul cursore
        if (isProtected(event.getCurrentItem(), cjm, phm)
                || isProtected(event.getCursor(), cjm, phm)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        CustomJoinItemsManager cjm = plugin.getCustomJoinItemsManager();
        PlayerHiderManager phm = plugin.getPlayerHiderManager();
        if (isProtected(item, cjm, phm)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        CustomJoinItemsManager cjm = plugin.getCustomJoinItemsManager();
        PlayerHiderManager phm = plugin.getPlayerHiderManager();
        if (isProtected(event.getMainHandItem(), cjm, phm)
                || isProtected(event.getOffHandItem(), cjm, phm)) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------

    /** True se l'item è un custom item o hider item con disable_movement attivo. */
    private boolean isProtected(ItemStack item, CustomJoinItemsManager cjm, PlayerHiderManager phm) {
        if (item == null || item.getType().isAir()) return false;
        if (cjm.isEnabled() && cjm.isDisableMovement() && cjm.isCustomItem(item)) return true;
        if (phm.isEnabled() && phm.isDisableMovement() && phm.isHiderItem(item)) return true;
        return false;
    }
}

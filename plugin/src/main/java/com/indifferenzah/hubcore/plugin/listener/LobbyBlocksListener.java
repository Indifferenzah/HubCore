package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.lobbyblocks.BlockSelectorHolder;
import com.indifferenzah.hubcore.plugin.lobbyblocks.LobbyBlocksManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Listener per il sistema LobbyBlocks:
 *
 * Stack di blocchi (block-item):
 *  - Right-click su superficie → piazzamento vanilla di Minecraft
 *  - Left-click → nessun effetto (blocca rottura vanilla)
 *
 * BlockPlaceEvent: intercetta il piazzamento e avvia l'animazione di rottura.
 *  Dopo break-time secondi il blocco sparisce automaticamente.
 *  La quantità dello stack rimane sempre a 64.
 *
 * Item selettore (selector-item):
 *  - Right-click in aria → apre il menu blockselector.yml
 *  - Click su blocco nel menu → cambia il materiale dello stack in hotbar
 */
public class LobbyBlocksListener implements Listener {

    private final HubCorePlugin plugin;

    public LobbyBlocksListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.getConfigLoader().isLobbyBlocksEnabled()) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        LobbyBlocksManager manager = plugin.getLobbyBlocksManager();
        Action action = event.getAction();

        // Leggi l'item direttamente dall'inventario: più affidabile di event.getItem()
        // che può tornare null per RIGHT_CLICK_AIR in alcune versioni di Paper
        ItemStack item = player.getInventory().getItemInMainHand();

        // --- STACK DI BLOCCHI ---
        if (manager.isBlockItem(item)) {
            if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
                // Blocca il left-click: non serve rompere manualmente
                event.setCancelled(true);
            }
            // Right-click su superficie: Minecraft gestisce il piazzamento normalmente
            // → BlockPlaceEvent avvierà l'animazione
        }

        // --- ITEM SELETTORE ---
        if (manager.isSelectorItem(item)) {
            // Non cancellare PHYSICAL: calpestare piastre/launchpad deve restare funzionante
            // anche quando il selettore è in mano.
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK
                    || action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                // Apri il menu al tick successivo: aprire l'inventario dentro PlayerInteractEvent
                // nello stesso tick del pacchetto di interazione può causare la chiusura immediata
                // dell'inventario lato client in Paper 1.21+
                plugin.getServer().getScheduler().runTask(plugin, () -> manager.openSelector(player));
            }
        }
    }

    /**
     * Intercetta il piazzamento del blocco lobby:
     * 1. Ripristina la quantità a 64 (blocchi infiniti)
     * 2. Avvia l'animazione di rottura immediata (ProtocolLib)
     * 3. Previene la caduta per gravità (sabbia, ghiaia, ecc.)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfigLoader().isLobbyBlocksEnabled()) return;

        LobbyBlocksManager manager = plugin.getLobbyBlocksManager();
        ItemStack item = event.getItemInHand();
        if (!manager.isBlockItem(item)) return;

        // Controlla blacklist
        if (manager.isBlacklisted(event.getBlockPlaced().getType())) {
            event.setCancelled(true);
            return;
        }

        Block placed = event.getBlockPlaced();

        // Ripristina la quantità a 64 al tick successivo (Bukkit decrementa dopo l'evento)
        // e invia l'aggiornamento al client con updateInventory()
        if (event.getPlayer().getGameMode() == GameMode.SURVIVAL
                || event.getPlayer().getGameMode() == GameMode.ADVENTURE) {
            Player placer = event.getPlayer();
            int blockSlot = plugin.getConfigLoader().getLobbyBlockSlot();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ItemStack slotItem = placer.getInventory().getItem(blockSlot);
                if (slotItem != null && manager.isBlockItem(slotItem)) {
                    slotItem.setAmount(64);
                    placer.updateInventory(); // forza aggiornamento lato client
                }
            });
        }

        // Avvia l'animazione di rottura automatica sul blocco piazzato
        manager.trackPlacedBlock(placed, event.getBlockReplacedState().getBlockData());
    }

    /**
     * Prima linea di difesa contro la gravità: cancella l'aggiornamento fisico
     * per i blocchi lobby tracciati (funziona nella maggior parte dei casi in Paper).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (!plugin.getConfigLoader().isLobbyBlocksEnabled()) return;
        if (plugin.getLobbyBlocksManager().isTrackedBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * Seconda linea di difesa: se un FallingBlock sta per spawnare in una posizione
     * tracciata (la fisica è riuscita comunque), lo blocca e ripristina il blocco.
     * Quando la FallingBlock entity spawna, il blocco nel mondo è già diventato AIR:
     * usiamo il BlockData dell'entità per ripristinarlo senza physics (applyPhysics=false).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallingBlockSpawn(EntitySpawnEvent event) {
        if (!plugin.getConfigLoader().isLobbyBlocksEnabled()) return;
        if (!(event.getEntity() instanceof FallingBlock fallingBlock)) return;

        Location loc = event.getLocation();
        if (!plugin.getLobbyBlocksManager().isTrackedBlock(loc)) return;

        event.setCancelled(true);

        // Il blocco è già diventato AIR: ripristinalo con i dati della FallingBlock entity
        Block block = loc.getBlock();
        if (block.getType().isAir()) {
            block.setBlockData(fallingBlock.getBlockData(), false);
        }
    }

    /**
     * Blocca la rottura vanilla quando il giocatore tiene in mano il block-item.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfigLoader().isLobbyBlocksEnabled()) return;
        if (plugin.getLobbyBlocksManager().isBlockItem(
                event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            event.setDropItems(false);
            event.setExpToDrop(0);
        }
    }

    /**
     * Gestisce i click nel menu del selettore di blocchi.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BlockSelectorHolder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        LobbyBlocksManager manager = plugin.getLobbyBlocksManager();
        if (manager.isGUIFiller(clicked)) return;

        // Cambia il materiale dello stack in hotbar e chiudi
        manager.setSelectedBlock(player, clicked.getType());
        player.closeInventory();
    }
}

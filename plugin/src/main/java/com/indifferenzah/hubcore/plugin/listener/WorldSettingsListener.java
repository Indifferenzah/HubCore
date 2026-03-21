package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * Applica le protezioni configurate in world_settings.
 * Usa LOW priority per bloccare prima degli altri listener,
 * eccetto i blocchi lobby che vengono esplicitamente esclusi.
 */
public class WorldSettingsListener implements Listener {

    private final HubCorePlugin plugin;

    public WorldSettingsListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean ws(String key) {
        return plugin.getConfigLoader().ws(key);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!ws("disable_hunger_loss")) return;
        if (!(event.getEntity() instanceof Player)) return;
        event.setCancelled(true);
        ((Player) event.getEntity()).setFoodLevel(20);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        var cause = event.getCause();
        if (ws("disable_fall_damage") && cause == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true); return;
        }
        if (ws("disable_void_death") && cause == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true); return;
        }
        if (ws("disable_fire_damage") && (cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR)) {
            event.setCancelled(true); return;
        }
        if (ws("disable_drowning") && cause == EntityDamageEvent.DamageCause.DROWNING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerVsPlayer(EntityDamageByEntityEvent event) {
        if (!ws("disable_player_pvp")) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof Player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (!ws("disable_off_hand_swap")) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (!ws("disable_weather_change")) return;
        if (event.toWeatherState()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {
        if (!ws("disable_death_message")) return;
        event.deathMessage(null);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (!ws("disable_mob_spawning")) return;
        if (event.getEntity().getType() == EntityType.PLAYER) return;
        // Permetti spawn da comandi e uova
        var reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!ws("disable_item_drop")) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!ws("disable_item_pickup")) return;
        if (!(event.getEntity() instanceof Player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!ws("disable_block_break")) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!ws("disable_block_place")) return;
        // Permetti i blocchi lobby (gestiti da LobbyBlocksListener a MONITOR)
        if (plugin.getLobbyBlocksManager().isBlockItem(event.getItemInHand())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!ws("disable_block_interact")) return;
        var action = event.getAction();
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                && action != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) return;
        // Non cancellare se è il selettore o il blocco lobby
        var item = event.getPlayer().getInventory().getItemInMainHand();
        if (plugin.getLobbyBlocksManager().isBlockItem(item)
                || plugin.getLobbyBlocksManager().isSelectorItem(item)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!ws("disable_block_burn")) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFireSpread(BlockSpreadEvent event) {
        if (!ws("disable_block_fire_spread")) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeafDecay(LeavesDecayEvent event) {
        if (!ws("disable_block_leaf_decay")) return;
        event.setCancelled(true);
    }
}

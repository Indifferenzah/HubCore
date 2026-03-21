package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener per il sistema Double Jump:
 * doppio spazio in aria → boost di velocità.
 * allowFlight viene abilitato per tutti i giocatori non-creative così il client
 * può rilevare il doppio spazio → noi cancelliamo il volo e applichiamo il boost.
 */
public class DoubleJumpListener implements Listener {

    private final HubCorePlugin plugin;

    // Giocatori che hanno già usato il double jump (aspettano di atterrare)
    private final Set<UUID> usedJump = ConcurrentHashMap.newKeySet();
    // Timestamp ultimo double jump per cooldown
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public DoubleJumpListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Abilita il double jump (allowFlight) quando necessario
    // -------------------------------------------------------------------------

    /** Chiama questo metodo al join e al respawn per abilitare il double jump. */
    public void enableFor(Player player) {
        if (!plugin.getConfigLoader().isDoubleJumpEnabled()) return;
        if (isCreativeOrSpectator(player)) return;
        player.setAllowFlight(true);
    }

    // -------------------------------------------------------------------------
    // Intercetta il tentativo di volo (doppio spazio)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!event.isFlying()) return; // solo attivazione volo, non disattivazione
        if (!plugin.getConfigLoader().isDoubleJumpEnabled()) return;

        Player player = event.getPlayer();
        if (isCreativeOrSpectator(player)) return;

        // Cancella il volo reale — noi gestiamo noi il salto
        event.setCancelled(true);
        player.setFlying(false);

        // Controlla cooldown
        int cooldownSec = plugin.getConfigLoader().getDoubleJumpCooldown();
        if (cooldownSec > 0) {
            Long last = cooldowns.get(player.getUniqueId());
            if (last != null && System.currentTimeMillis() - last < cooldownSec * 1000L) return;
        }

        // Applica boost
        double power  = plugin.getConfigLoader().getDoubleJumpPower();
        double powerY = plugin.getConfigLoader().getDoubleJumpPowerY();

        Vector dir = player.getLocation().getDirection();
        dir.setY(0);
        if (dir.length() > 0) dir.normalize();
        dir.multiply(power).setY(powerY);
        player.setVelocity(dir);

        // Segna come usato e registra cooldown
        usedJump.add(player.getUniqueId());
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        player.setAllowFlight(false); // re-abilitato all'atterraggio

        executeActions(player, plugin.getConfigLoader().getDoubleJumpActions());

        // Polling ogni tick: re-abilita appena il giocatore tocca terra
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || !usedJump.contains(player.getUniqueId())) {
                task.cancel();
                return;
            }
            if (player.isOnGround()) {
                usedJump.remove(player.getUniqueId());
                player.setAllowFlight(true);
                task.cancel();
            }
        }, 5L, 1L);
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        usedJump.remove(uuid);
        cooldowns.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private boolean isCreativeOrSpectator(Player player) {
        GameMode gm = player.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
    }

    private void executeActions(Player player, List<String> actions) {
        for (String action : actions) {
            if (action.startsWith("[SOUND]")) {
                String[] parts = action.substring(7).trim().split("\\s+");
                try {
                    Sound sound = Sound.valueOf(parts[0].toUpperCase());
                    float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                    float pitch  = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (Exception e) {
                    plugin.getLogger().warning("Azione double_jump non valida: " + action);
                }
            }
        }
    }
}

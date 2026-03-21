package com.indifferenzah.hubcore.plugin.listener;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener per il sistema Launchpad:
 * quando un giocatore calpesta la combinazione top_block + bottom_block
 * configurata, viene lanciato nella direzione in cui guarda.
 */
public class LaunchpadListener implements Listener {

    private final HubCorePlugin plugin;
    // Cooldown per evitare lanci multipli mentre il giocatore è sulla piastra
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 1500;

    public LaunchpadListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) return;
        if (!plugin.getConfigLoader().isLaunchpadEnabled()) return;

        Block top = event.getClickedBlock();
        if (top == null) return;

        // Controlla blocco superiore (pressure plate)
        Material topMat;
        try {
            topMat = Material.valueOf(plugin.getConfigLoader().getLaunchpadTopBlock().toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }
        if (top.getType() != topMat) return;

        // Controlla blocco inferiore (base del launchpad)
        Material bottomMat;
        try {
            bottomMat = Material.valueOf(plugin.getConfigLoader().getLaunchpadBottomBlock().toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }
        if (top.getRelative(BlockFace.DOWN).getType() != bottomMat) return;

        Player player = event.getPlayer();

        // Evita lanci ripetuti mentre il giocatore rimane sulla piastra
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) return;
        cooldowns.put(player.getUniqueId(), now);

        double power  = plugin.getConfigLoader().getLaunchpadPower();
        double powerY = plugin.getConfigLoader().getLaunchpadPowerY();

        // Calcola vettore di lancio: direzione orizzontale * power, Y = powerY
        Vector dir = player.getLocation().getDirection();
        dir.setY(0);
        if (dir.length() > 0) dir.normalize();
        dir.multiply(power).setY(powerY);
        player.setVelocity(dir);

        executeActions(player, plugin.getConfigLoader().getLaunchpadActions());
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
                    plugin.getLogger().warning("Azione launchpad non valida: " + action);
                }
            }
        }
    }
}

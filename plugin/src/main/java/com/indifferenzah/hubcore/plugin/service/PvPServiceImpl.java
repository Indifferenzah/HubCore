package com.indifferenzah.hubcore.plugin.service;

import com.indifferenzah.hubcore.api.enums.PvPState;
import com.indifferenzah.hubcore.api.model.PlayerData;
import com.indifferenzah.hubcore.api.service.PvPService;
import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.config.ConfigLoader;
import com.indifferenzah.hubcore.plugin.config.MessagesLoader;
import com.indifferenzah.hubcore.plugin.task.CombatTagTask;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementazione principale del servizio PvP.
 * Gestisce la macchina a stati PvP per ogni giocatore.
 */
public class PvPServiceImpl implements PvPService {

    private final HubCorePlugin plugin;

    // Stato PvP corrente per ogni giocatore
    private final Map<UUID, PvPState> states = new ConcurrentHashMap<>();
    // Task del delay di attivazione
    private final Map<UUID, BukkitTask> enableTasks = new ConcurrentHashMap<>();
    // Task del delay di disattivazione
    private final Map<UUID, BukkitTask> disableTasks = new ConcurrentHashMap<>();
    // Task del combat tag
    private final Map<UUID, BukkitTask> combatTasks = new ConcurrentHashMap<>();
    // Task della durata massima del combattimento
    private final Map<UUID, BukkitTask> maxCombatTasks = new ConcurrentHashMap<>();
    // Giocatori con delay di rispawn attivo
    private final Set<UUID> respawnDelays = ConcurrentHashMap.newKeySet();
    // Task del delay di rispawn
    private final Map<UUID, BukkitTask> respawnTasks = new ConcurrentHashMap<>();

    public PvPServiceImpl(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // PvPService - Attivazione
    // =========================================================================

    @Override
    public void startEnableDelay(Player player) {
        UUID uuid = player.getUniqueId();
        PvPState current = getState(uuid);
        if (current != PvPState.INACTIVE) return;

        // Passa allo stato ENABLING
        setState(uuid, PvPState.ENABLING);

        ConfigLoader config = plugin.getConfigLoader();
        MessagesLoader messages = plugin.getMessagesLoader();
        long delayTicks = config.getDelayEnable();
        long delaySecs = delayTicks / 20;

        // Invia messaggio di inizio attivazione
        player.sendMessage(messages.get("pvp.enabling", Map.of("seconds", String.valueOf(delaySecs))));

        // Task principale: attiva il PvP dopo il delay
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            enableTasks.remove(uuid);
            // Controlla che sia ancora nello stato ENABLING
            if (getState(uuid) == PvPState.ENABLING) {
                activatePvP(player);
            }
        }, delayTicks);
        enableTasks.put(uuid, task);

        // Actionbar con countdown (se abilitata)
        if (config.isShowActionbar()) {
            scheduleActionbarCountdown(player, delaySecs);
        }
    }

    /**
     * Schedula il countdown dell'actionbar durante il delay di attivazione.
     */
    private void scheduleActionbarCountdown(Player player, long totalSeconds) {
        UUID uuid = player.getUniqueId();
        ConfigLoader config = plugin.getConfigLoader();

        // Aggiorna l'actionbar ogni secondo
        for (long i = 0; i < totalSeconds; i++) {
            final long secondsLeft = totalSeconds - i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Controlla che sia ancora in ENABLING prima di mostrare il countdown
                if (getState(uuid) == PvPState.ENABLING && player.isOnline()) {
                    String format = config.getActionbarFormat();
                    String withSeconds = ColorUtil.replacePlaceholders(format,
                            Map.of("seconds", String.valueOf(secondsLeft)));
                    player.sendActionBar(ColorUtil.colorize(withSeconds));
                }
            }, i * 20L);
        }
    }

    @Override
    public void cancelEnableDelay(UUID uuid) {
        if (getState(uuid) != PvPState.ENABLING) return;

        // Cancella il task di attivazione
        BukkitTask task = enableTasks.remove(uuid);
        if (task != null) task.cancel();

        // Torna allo stato INACTIVE
        setState(uuid, PvPState.INACTIVE);

        // Aggiorna aspetto della spada
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            plugin.getSwordManager().updateSwordAppearance(player, false);
            player.sendMessage(plugin.getMessagesLoader().get("pvp.enable-cancelled"));
        }
    }

    @Override
    public void activatePvP(Player player) {
        UUID uuid = player.getUniqueId();

        // Cancella eventuali task di attivazione pendenti
        BukkitTask enableTask = enableTasks.remove(uuid);
        if (enableTask != null) enableTask.cancel();

        // Imposta lo stato ACTIVE
        setState(uuid, PvPState.ACTIVE);

        // Aggiorna aspetto della spada (attiva)
        plugin.getSwordManager().updateSwordAppearance(player, true);

        // Equipaggia l'armatura PvP
        plugin.getArmorManager().giveArmor(player);

        // Suono di attivazione
        plugin.getEffectManager().playPvpEnableEffect(player);

        // Messaggio
        player.sendMessage(plugin.getMessagesLoader().get("pvp.enabled"));
    }

    // =========================================================================
    // PvPService - Disattivazione
    // =========================================================================

    @Override
    public void startDisableDelay(Player player) {
        UUID uuid = player.getUniqueId();
        PvPState current = getState(uuid);
        // Il delay di disattivazione e' avviabile solo da ACTIVE
        if (current != PvPState.ACTIVE) return;

        setState(uuid, PvPState.DISABLING);

        ConfigLoader config = plugin.getConfigLoader();
        MessagesLoader messages = plugin.getMessagesLoader();
        long delayTicks = config.getDelayDisable();
        long delaySecs = delayTicks / 20;

        player.sendMessage(messages.get("pvp.disabling", Map.of("seconds", String.valueOf(delaySecs))));

        // Task principale: disattiva dopo il delay
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            disableTasks.remove(uuid);
            if (getState(uuid) == PvPState.DISABLING) {
                deactivatePvP(player);
            }
        }, delayTicks);
        disableTasks.put(uuid, task);
    }

    @Override
    public void cancelDisableDelay(UUID uuid) {
        if (getState(uuid) != PvPState.DISABLING) return;

        BukkitTask task = disableTasks.remove(uuid);
        if (task != null) task.cancel();

        // Torna allo stato ACTIVE
        setState(uuid, PvPState.ACTIVE);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            plugin.getSwordManager().updateSwordAppearance(player, true);
            player.sendMessage(plugin.getMessagesLoader().get("pvp.disable-cancelled"));
        }
    }

    @Override
    public void deactivatePvP(Player player) {
        UUID uuid = player.getUniqueId();

        // Cancella tutti i task attivi per questo giocatore
        cancelAllTasks(uuid);

        // Imposta lo stato INACTIVE
        setState(uuid, PvPState.INACTIVE);

        // Azzera il flag wantsToDisable
        plugin.getStatsService().getPlayer(uuid).ifPresent(d -> d.setWantsToDisable(false));

        // Aggiorna aspetto della spada (inattiva)
        plugin.getSwordManager().updateSwordAppearance(player, false);

        // Rimuove l'armatura PvP
        plugin.getArmorManager().removeArmor(player);

        // Suono di disattivazione
        plugin.getEffectManager().playPvpDisableEffect(player);

        // Messaggio
        player.sendMessage(plugin.getMessagesLoader().get("pvp.disabled"));
    }

    // =========================================================================
    // PvPService - Combat tag
    // =========================================================================

    @Override
    public void updateCombatTag(Player attacker, Player victim) {
        // Aggiorna il combat tag per entrambi i giocatori
        tagPlayer(attacker);
        tagPlayer(victim);
    }

    /**
     * Applica o rinnova il combat tag a un singolo giocatore.
     */
    private void tagPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        PvPState current = getState(uuid);

        // Il combat tag si applica solo a giocatori in PvP attivo o gia' taggati
        if (current == PvPState.INACTIVE || current == PvPState.ENABLING) return;

        ConfigLoader config = plugin.getConfigLoader();

        // Cancella il combat tag precedente (reset del timer)
        BukkitTask oldCombatTask = combatTasks.remove(uuid);
        if (oldCombatTask != null) oldCombatTask.cancel();

        // Se era in DISABLING, cancella il delay di disattivazione
        if (current == PvPState.DISABLING) {
            BukkitTask disableTask = disableTasks.remove(uuid);
            if (disableTask != null) disableTask.cancel();
        }

        // Porta il giocatore in COMBAT_TAG
        setState(uuid, PvPState.COMBAT_TAG);
        plugin.getSwordManager().updateSwordAppearance(player, true);

        // Schedula la scadenza del combat tag
        CombatTagTask tagTask = new CombatTagTask(uuid, this);
        BukkitTask newTask = tagTask.runTaskLater(plugin, config.getCombatTagDuration());
        combatTasks.put(uuid, newTask);

        // Gestione durata massima del combattimento
        long maxDuration = config.getMaxCombatDuration();
        if (maxDuration > 0 && !maxCombatTasks.containsKey(uuid)) {
            BukkitTask maxTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                maxCombatTasks.remove(uuid);
                // Forza la fine del combattimento
                if (getState(uuid) == PvPState.COMBAT_TAG) {
                    expireCombatTag(uuid);
                }
            }, maxDuration);
            maxCombatTasks.put(uuid, maxTask);
        }
    }

    /**
     * Fa scadere il combat tag di un giocatore.
     * Chiamato da CombatTagTask quando il timer scade.
     *
     * @param uuid UUID del giocatore
     */
    public void expireCombatTag(UUID uuid) {
        if (getState(uuid) != PvPState.COMBAT_TAG) return;

        // Rimuove il combat task
        combatTasks.remove(uuid);

        Player player = Bukkit.getPlayer(uuid);
        Optional<PlayerData> dataOpt = plugin.getStatsService().getPlayer(uuid);

        boolean wantsToDisable = dataOpt.map(PlayerData::isWantsToDisable).orElse(false);

        if (wantsToDisable) {
            // Il giocatore aveva rimosso la spada durante il combat tag: avvia disattivazione
            setState(uuid, PvPState.ACTIVE);
            if (player != null) {
                // Controlla se non tiene ancora la spada nello slot configurato
                int swordSlot = plugin.getConfigLoader().getSwordSlot();
                int currentSlot = player.getInventory().getHeldItemSlot();
                if (currentSlot != swordSlot) {
                    startDisableDelay(player);
                } else {
                    // Ha ripreso la spada: annulla il desiderio di disattivare
                    dataOpt.ifPresent(d -> d.setWantsToDisable(false));
                }
            }
        } else {
            // Torna semplicemente ad ACTIVE
            setState(uuid, PvPState.ACTIVE);
            if (player != null) {
                plugin.getSwordManager().updateSwordAppearance(player, true);
            }
        }
    }

    // =========================================================================
    // PvPService - Kill/Death
    // =========================================================================

    @Override
    public void handleKill(Player killer, Player victim) {
        ConfigLoader config = plugin.getConfigLoader();
        MessagesLoader messages = plugin.getMessagesLoader();

        // Aggiorna le statistiche del killer
        plugin.getStatsService().getPlayer(killer.getUniqueId()).ifPresent(data -> {
            data.incrementKills();

            // Messaggio privato al killer
            killer.sendMessage(messages.get("pvp.kill", Map.of(
                    "victim", victim.getName(),
                    "kills", String.valueOf(data.getKills()),
                    "killstreak", String.valueOf(data.getKillstreak())
            )));

            // Kill-heal
            if (config.isKillHealEnabled()) {
                applyKillHeal(killer, config);
            }

            // Milestone killstreak
            Map<Integer, ConfigLoader.KillstreakMilestone> milestones = config.getKillstreakMilestones();
            ConfigLoader.KillstreakMilestone milestone = milestones.get(data.getKillstreak());
            if (milestone != null) {
                handleKillstreakMilestone(killer, milestone, data);
            }
        });

        // Effetti on-kill
        plugin.getEffectManager().playOnKillEffects(killer, victim);

        // Kill feed
        if (config.isKillFeedEnabled()) {
            sendKillFeed(killer, victim, config);
        }
    }

    /**
     * Applica la guarigione al killer dopo un'uccisione.
     */
    private void applyKillHeal(Player killer, ConfigLoader config) {
        String type = config.getKillHealType();
        if ("FULL".equals(type)) {
            // Guarigione completa
            // Recupera la vita massima tramite l'attributo generico (compatibile con Paper 1.21.1)
            var maxHealthAttr = killer.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
            double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
            killer.setHealth(maxHealth);
            killer.setFoodLevel(20);
            killer.setSaturation(20.0f);
        } else if ("REGENERATION".equals(type)) {
            // Effetto rigenerazione
            killer.addPotionEffect(new PotionEffect(
                    PotionEffectType.REGENERATION,
                    config.getRegenDuration(),
                    config.getRegenAmplifier(),
                    false, false, true
            ));
        }
    }

    /**
     * Gestisce un milestone di killstreak raggiunto.
     */
    private void handleKillstreakMilestone(Player killer, ConfigLoader.KillstreakMilestone milestone,
                                            PlayerData data) {
        String message = ColorUtil.replacePlaceholders(milestone.message(), Map.of(
                "player", killer.getName(),
                "killstreak", String.valueOf(data.getKillstreak())
        ));
        Component colorized = ColorUtil.colorize(message);

        if (milestone.broadcast()) {
            // Invia a tutti i giocatori online
            Bukkit.broadcast(colorized);
        } else {
            // Invia solo al killer
            killer.sendMessage(colorized);
        }

        // Suono del milestone
        if (milestone.sound() != null) {
            killer.playSound(killer.getLocation(), milestone.sound(), milestone.volume(), milestone.pitch());
        }
    }

    /**
     * Invia il messaggio di kill feed nella modalita' configurata.
     */
    private void sendKillFeed(Player killer, Player victim, ConfigLoader config) {
        String format = config.getKillFeedFormat();
        String message = ColorUtil.replacePlaceholders(format, Map.of(
                "killer", killer.getName(),
                "victim", victim.getName()
        ));
        Component component = ColorUtil.colorize(message);

        switch (config.getKillFeedTarget()) {
            case "CHAT" -> Bukkit.broadcast(component);
            case "ACTION_BAR" -> {
                // Actionbar a tutti i giocatori online
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendActionBar(component);
                }
            }
            case "BOSSBAR" -> {
                // Crea una BossBar temporanea visibile a tutti
                BossBar bossBar = BossBar.bossBar(
                        component,
                        1.0f,
                        BossBar.Color.RED,
                        BossBar.Overlay.PROGRESS
                );
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showBossBar(bossBar);
                }
                // Rimuove la BossBar dopo 3 secondi (60 ticks)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.hideBossBar(bossBar);
                    }
                }, 60L);
            }
        }
    }

    @Override
    public void handleDeath(Player victim) {
        // Incrementa le morti nella cache (handleKill si occupa gia' dei kills del killer)
        plugin.getStatsService().getPlayer(victim.getUniqueId()).ifPresent(PlayerData::incrementDeaths);
    }

    // =========================================================================
    // PvPService - Controlli stato
    // =========================================================================

    @Override
    public boolean isInPvP(UUID uuid) {
        PvPState state = getState(uuid);
        return state == PvPState.ACTIVE
                || state == PvPState.COMBAT_TAG
                || state == PvPState.DISABLING;
    }

    @Override
    public boolean isInCombat(UUID uuid) {
        return getState(uuid) == PvPState.COMBAT_TAG;
    }

    @Override
    public PvPState getState(UUID uuid) {
        return states.getOrDefault(uuid, PvPState.INACTIVE);
    }

    // =========================================================================
    // PvPService - Comandi admin
    // =========================================================================

    @Override
    public void forceEnable(Player player) {
        UUID uuid = player.getUniqueId();
        cancelAllTasks(uuid);
        setState(uuid, PvPState.ACTIVE);

        // Assicura che abbia la spada
        if (!plugin.getSwordManager().hasSword(player)) {
            plugin.getSwordManager().giveSword(player);
        }
        plugin.getSwordManager().updateSwordAppearance(player, true);

        // Equipaggia l'armatura PvP
        plugin.getArmorManager().giveArmor(player);

        player.sendMessage(plugin.getMessagesLoader().get("admin.force-enable",
                Map.of("player", player.getName())));
    }

    @Override
    public void forceDisable(Player player) {
        UUID uuid = player.getUniqueId();
        cancelAllTasks(uuid);
        setState(uuid, PvPState.INACTIVE);

        plugin.getSwordManager().updateSwordAppearance(player, false);
        plugin.getStatsService().getPlayer(uuid).ifPresent(d -> d.setWantsToDisable(false));

        // Rimuove l'armatura PvP
        plugin.getArmorManager().removeArmor(player);

        player.sendMessage(plugin.getMessagesLoader().get("admin.force-disable",
                Map.of("player", player.getName())));
    }

    // =========================================================================
    // PvPService - Gestione ciclo di vita giocatore
    // =========================================================================

    @Override
    public void removePlayer(UUID uuid) {
        cancelAllTasks(uuid);
        states.remove(uuid);
    }

    @Override
    public void applyRespawnDelay(UUID uuid) {
        respawnDelays.add(uuid);

        // Cancella eventuali delay di rispawn precedenti
        BukkitTask existing = respawnTasks.remove(uuid);
        if (existing != null) existing.cancel();

        // Schedula la rimozione del delay dopo il tempo configurato
        long delay = plugin.getConfigLoader().getDelayRespawn();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            respawnDelays.remove(uuid);
            respawnTasks.remove(uuid);
        }, delay);
        respawnTasks.put(uuid, task);
    }

    @Override
    public boolean hasRespawnDelay(UUID uuid) {
        return respawnDelays.contains(uuid);
    }

    // =========================================================================
    // Metodi privati di supporto
    // =========================================================================

    /**
     * Imposta lo stato PvP del giocatore. Se INACTIVE, rimuove dalla mappa per pulire la memoria.
     */
    private void setState(UUID uuid, PvPState state) {
        if (state == PvPState.INACTIVE) {
            states.remove(uuid);
        } else {
            states.put(uuid, state);
        }
    }

    /**
     * Cancella tutti i task schedulati per un giocatore.
     */
    public void cancelAllTasks(UUID uuid) {
        cancelTask(enableTasks, uuid);
        cancelTask(disableTasks, uuid);
        cancelTask(combatTasks, uuid);
        cancelTask(maxCombatTasks, uuid);
        cancelTask(respawnTasks, uuid);
        respawnDelays.remove(uuid);
    }

    /** Cancella e rimuove il task dalla mappa specificata. */
    private void cancelTask(Map<UUID, BukkitTask> map, UUID uuid) {
        BukkitTask task = map.remove(uuid);
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception ignored) {
                // Task gia' completato: ignora l'eccezione
            }
        }
    }
}

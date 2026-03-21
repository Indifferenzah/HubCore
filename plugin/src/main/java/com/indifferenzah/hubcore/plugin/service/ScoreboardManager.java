package com.indifferenzah.hubcore.plugin.service;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestisce la sidebar animata per ogni giocatore.
 *
 * Usa il {@link Scoreboard} fornito da {@link TabManager} (condiviso)
 * per aggiungere un {@link Objective} nella sidebar.
 * Gli ID delle righe sono fissi (codici §) per evitare flickering.
 */
public class ScoreboardManager {

    private final HubCorePlugin plugin;

    // Riferimento a TabManager per ottenere il board condiviso
    private final TabManager tabManager;

    // Configurazione caricata da scoreboard.yml
    private YamlConfiguration sbConfig;

    // Task ripetuto per l'aggiornamento della sidebar
    private BukkitTask task;

    // Se la sidebar e' abilitata in scoreboard.yml
    private boolean enabled;

    // Intervallo di aggiornamento in tick
    private long updateInterval = 20L;

    // Formato del titolo (raw, puo' contenere %animation:Nome%)
    private String titleRaw = "";

    // Righe della sidebar (raw, possono contenere placeholder e animazioni)
    private List<String> linesRaw = new java.util.ArrayList<>();

    // Mappa UUID → Objective della sidebar
    private final ConcurrentHashMap<UUID, Objective> playerObjectives = new ConcurrentHashMap<>();

    /**
     * ID di slot fissi basati su codici §: §0§r, §1§r, ..., §e§r (15 slot).
     * Questi stringhe non cambiano mai, impedendo il flickering durante l'aggiornamento.
     */
    private static final String[] SLOT_IDS;

    static {
        SLOT_IDS = new String[15];
        for (int i = 0; i < 15; i++) {
            // Integer.toHexString produce 0-9 poi a-f
            SLOT_IDS[i] = "\u00A7" + Integer.toHexString(i) + "\u00A7r";
        }
    }

    public ScoreboardManager(HubCorePlugin plugin, TabManager tabManager) {
        this.plugin = plugin;
        this.tabManager = tabManager;
        load();
    }

    // =========================================================================
    // Caricamento configurazione
    // =========================================================================

    /**
     * Carica (o ricarica) scoreboard.yml.
     */
    public void load() {
        File file = new File(plugin.getDataFolder(), "scoreboard.yml");
        if (!file.exists()) return;
        sbConfig = YamlConfiguration.loadConfiguration(file);

        enabled = sbConfig.getBoolean("enabled", true);
        updateInterval = sbConfig.getLong("update-interval", 20L);
        titleRaw = sbConfig.getString("title", "");
        linesRaw = sbConfig.getStringList("lines");

    }

    // =========================================================================
    // Start / Stop
    // =========================================================================

    // Contatore tick per throttling aggiornamenti ai client
    private long tickCounter = 0;

    /**
     * Avvia il task che gira OGNI TICK per animazioni accurate.
     * L'invio ai client avviene solo ogni updateInterval tick.
     */
    public void start() {
        if (!enabled) return;
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tickCounter++;
            if (tickCounter % updateInterval == 0) {
                updateAll();
            }
        }, 1L, 1L);
    }

    /**
     * Ferma il task ripetuto.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // =========================================================================
    // Aggiornamento ciclico
    // =========================================================================

    /**
     * Aggiorna la sidebar di tutti i giocatori online.
     * Chiamato ogni updateInterval tick.
     */
    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Objective obj = playerObjectives.get(player.getUniqueId());
            if (obj == null) continue;
            updateSidebar(player, obj);
        }
    }

    /**
     * Aggiorna titolo e righe della sidebar per un giocatore specifico.
     *
     * @param player Il giocatore
     * @param obj    Il suo Objective
     */
    private void updateSidebar(Player player, Objective obj) {
        // Aggiorna il titolo
        String resolvedTitle = resolveLine(titleRaw, player);
        obj.displayName(ColorUtil.colorize(resolvedTitle));

        // Aggiorna le righe
        int totalLines = Math.min(linesRaw.size(), SLOT_IDS.length);
        for (int i = 0; i < totalLines; i++) {
            String rawLine = linesRaw.get(i);
            String resolved = resolveLine(rawLine, player);
            Component lineComponent = ColorUtil.colorize(resolved);

            // Ottieni il punteggio per questo slot (lo score determina la posizione)
            // La riga 0 e' in cima → score = totalLines - 0 = totalLines
            Score score = obj.getScore(SLOT_IDS[i]);
            // Imposta lo score per posizionare la riga (maggiore = piu' in alto)
            score.setScore(totalLines - i);
            // Aggiorna il testo custom del punteggio (Paper 1.20.4+ API)
            score.customName(lineComponent);
        }
    }

    // =========================================================================
    // Join / Quit
    // =========================================================================

    /**
     * Chiamato al join di un giocatore (con un tick di delay per attendere
     * che TabManager abbia gia' creato e assegnato il board).
     * Crea l'Objective della sidebar sul board condiviso.
     *
     * @param player Il giocatore che entra
     */
    public void onJoin(Player player) {
        if (!enabled) return;

        UUID uuid = player.getUniqueId();

        // Recupera il board condiviso creato da TabManager
        Scoreboard board = tabManager.getScoreboard(uuid);
        if (board == null) {
            // Fallback: usa il main scoreboard se TabManager non ha ancora creato il board
            board = Bukkit.getScoreboardManager().getMainScoreboard();
        }

        // Rimuovi eventuale Objective residuo con lo stesso nome
        Objective existing = board.getObjective("hubcore_sb");
        if (existing != null) {
            existing.unregister();
        }

        // Crea il nuovo Objective per la sidebar
        // Usa Criteria.DUMMY perche' i valori sono gestiti manualmente
        Objective obj = board.registerNewObjective(
                "hubcore_sb",      // nome interno (max 16 caratteri)
                Criteria.DUMMY,    // criterio: nessuna metrica automatica
                Component.empty()  // titolo iniziale (aggiornato subito dopo)
        );

        // Imposta la slot di visualizzazione sulla sidebar
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Salva il riferimento
        playerObjectives.put(uuid, obj);

        // Aggiorna immediatamente per evitare il frame vuoto iniziale
        updateSidebar(player, obj);
    }

    /**
     * Chiamato al quit di un giocatore.
     * Rimuove l'Objective e pulisce la mappa.
     *
     * @param player Il giocatore che esce
     */
    public void onQuit(Player player) {
        UUID uuid = player.getUniqueId();
        Objective obj = playerObjectives.remove(uuid);
        if (obj != null) {
            try {
                // Unregister potrebbe lanciare eccezione se il board e' gia' stato GC'd
                obj.unregister();
            } catch (Exception ignored) {
                // Ignora silenziosamente: il board e' gia' stato rimosso
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Risolve una singola linea: espande %animation:Nome%, poi applica
     * PlaceholderAPI e i placeholder di fallback interni.
     *
     * @param line   La linea raw dal config
     * @param player Il giocatore
     * @return La linea risolta (stringa, da colorizzare separatamente)
     */
    private String resolveLine(String line, Player player) {
        // Risolvi i placeholder di animazione (%animation:Nome%) tramite AnimationsManager
        line = resolveAnimations(line);
        // Sostituzioni fallback interne
        line = line.replace("%player%", player.getName());
        line = line.replace("%online%", "" + Bukkit.getOnlinePlayers().size());
        line = line.replace("%ping%", "" + player.getPing());
        // PlaceholderAPI (se disponibile)
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            line = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, line);
        }
        return line;
    }

    /**
     * Sostituisce ogni occorrenza di %animation:Nome% con il frame raw corrente
     * dell'animazione omonima.
     *
     * @param text Il testo da elaborare
     * @return Il testo con le animazioni risolte
     */
    private String resolveAnimations(String text) {
        int start = text.indexOf("%animation:");
        while (start >= 0) {
            int end = text.indexOf('%', start + 1);
            if (end < 0) break;
            // Estrae il nome dell'animazione tra "%animation:" e il secondo "%"
            String animName = text.substring(start + "%animation:".length(), end);
            String frame = plugin.getAnimationsManager().getAnimations().getCurrentFrameRaw(animName);
            // Sostituisce il placeholder (usa + per evitare invokedynamic)
            text = text.substring(0, start) + frame + text.substring(end + 1);
            // Riprendi la ricerca dopo la sostituzione
            start = text.indexOf("%animation:", start + frame.length());
        }
        return text;
    }
}

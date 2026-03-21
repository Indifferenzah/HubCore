package com.indifferenzah.hubcore.plugin.service;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestisce header/footer della tablist, formattazione del nome tablist
 * e ordinamento per gruppo tramite team Bukkit.
 *
 * Ogni giocatore ha il proprio Scoreboard (condiviso con ScoreboardManager).
 */
public class TabManager {

    private final HubCorePlugin plugin;

    // Configurazione caricata da tab.yml
    private YamlConfiguration tabConfig;

    // Task ripetuto per aggiornare header/footer
    private BukkitTask task;

    // Se la funzione tablist e' abilitata in tab.yml
    private boolean enabled;

    // Scoreboard per giocatore — condiviso con ScoreboardManager per la sidebar
    private final Map<UUID, Scoreboard> playerScoreboards = new ConcurrentHashMap<>();

    // Nomi dei gruppi nell'ordine configurato (per l'ordinamento in tablist)
    private List<String> groupOrder = new ArrayList<>();

    // Se la formattazione del nome tablist e' abilitata
    private boolean nameFormattingEnabled;

    // Formato del nome tablist (es. "{lp_prefix}%player%{lp_suffix}")
    private String nameFormat;

    // Se l'ordinamento per gruppo e' abilitato
    private boolean groupSortingEnabled;

    // Se il nametag sopra la testa e' abilitato
    private boolean nametagEnabled;

    // Formato del prefisso del nametag (es. "{lp_prefix}")
    private String nametagPrefixFormat;

    // Formato del suffisso del nametag (es. "{lp_suffix}")
    private String nametagSuffixFormat;

    // Se header/footer sono abilitati
    private boolean headerFooterEnabled;

    // Linee dell'header (raw, possono contenere %animation:Nome%)
    private List<String> headerLines = new ArrayList<>();

    // Linee del footer (raw, possono contenere %animation:Nome%)
    private List<String> footerLines = new ArrayList<>();

    // Intervallo di aggiornamento in tick
    private long updateInterval = 20L;

    public TabManager(HubCorePlugin plugin) {
        this.plugin = plugin;
        load();
    }

    // =========================================================================
    // Caricamento configurazione
    // =========================================================================

    /**
     * Carica (o ricarica) tab.yml e reinizializza le animazioni.
     */
    public void load() {
        // Leggi tab.yml dalla cartella dati del plugin
        File file = new File(plugin.getDataFolder(), "tab.yml");
        if (!file.exists()) return;
        tabConfig = YamlConfiguration.loadConfiguration(file);

        enabled = tabConfig.getBoolean("enabled", true);
        updateInterval = tabConfig.getLong("update-interval", 20L);

        // Carica header/footer
        headerFooterEnabled = tabConfig.getBoolean("header-footer.enabled", true);
        headerLines = tabConfig.getStringList("header-footer.header");
        footerLines = tabConfig.getStringList("header-footer.footer");

        // Carica formattazione nome tablist
        nameFormattingEnabled = tabConfig.getBoolean("tablist-name-formatting.enabled", true);
        nameFormat = tabConfig.getString("tablist-name-formatting.format", "%player%");

        // Carica ordinamento gruppi
        groupSortingEnabled = tabConfig.getBoolean("group-sorting.enabled", true);
        groupOrder = tabConfig.getStringList("group-sorting.groups");

        // Carica nametag
        nametagEnabled = tabConfig.getBoolean("nametag.enabled", false);
        nametagPrefixFormat = tabConfig.getString("nametag.prefix", "{lp_prefix}");
        nametagSuffixFormat = tabConfig.getString("nametag.suffix", "{lp_suffix}");
    }

    // =========================================================================
    // Start / Stop
    // =========================================================================

    // Contatore tick per throttling degli aggiornamenti ai client
    private long tickCounter = 0;

    /**
     * Avvia il task che gira OGNI TICK per aggiornare i frame delle animazioni.
     * L'invio ai client avviene solo ogni updateInterval tick.
     */
    public void start() {
        if (!enabled) return;
        if (task != null) task.cancel();
        // Task ogni tick: animazioni accurate, invio ai client throttolato
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tickCounter++;
            if (tickCounter % updateInterval == 0) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (headerFooterEnabled) updateHeaderFooter(player);
                    if (nameFormattingEnabled) updateTablistName(player);
                }
                // Aggiorna i nametag di tutti i giocatori su tutti i board
                if (nametagEnabled) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        for (Scoreboard board : playerScoreboards.values()) {
                            setupNametagTeam(board, player);
                        }
                    }
                }
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


    // =========================================================================
    // Header / Footer
    // =========================================================================

    /**
     * Costruisce e invia header e footer al giocatore.
     *
     * @param player Il giocatore a cui inviare
     */
    private void updateHeaderFooter(Player player) {
        Component header = buildMultiline(headerLines, player);
        Component footer = buildMultiline(footerLines, player);
        player.sendPlayerListHeaderAndFooter(header, footer);
    }

    /**
     * Unisce una lista di linee in un unico Component separato da newline.
     * Risolve prima i placeholder di animazione, poi PlaceholderAPI, poi i fallback.
     *
     * @param lines  Lista di stringhe raw
     * @param player Giocatore per i placeholder
     * @return Component risultante
     */
    private Component buildMultiline(List<String> lines, Player player) {
        if (lines.isEmpty()) return Component.empty();

        // Costruisce il testo con \n tra le righe usando il + operator
        String joined = "";
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) joined = joined + "\n";
            joined = joined + resolveLine(lines.get(i), player);
        }

        return ColorUtil.colorize(joined);
    }

    // =========================================================================
    // Nome tablist
    // =========================================================================

    /**
     * Aggiorna il nome visualizzato nella tablist per il giocatore.
     *
     * @param player Il giocatore
     */
    private void updateTablistName(Player player) {
        String raw = nameFormat;

        // Sostituisce {lp_prefix} e {lp_suffix} con i valori LuckPerms
        raw = replaceLuckPermsPlaceholders(raw, player);

        // Sostituisce %player% con il nome del giocatore
        raw = raw.replace("%player%", player.getName());

        // Applica PlaceholderAPI se disponibile
        raw = applyPapi(player, raw);

        // Colorizza e imposta il nome tablist
        player.playerListName(ColorUtil.colorize(raw));
    }

    // =========================================================================
    // Join / Quit
    // =========================================================================

    /**
     * Chiamato al join di un giocatore.
     * Crea il suo Scoreboard, registra i team di ordinamento
     * e aggiunge tutti i giocatori online ai team corretti.
     *
     * @param player Il giocatore che entra
     */
    public void onJoin(Player player) {
        // Crea un nuovo Scoreboard dedicato al giocatore
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        playerScoreboards.put(player.getUniqueId(), board);

        // Registra tutti i team di ordinamento sul nuovo board
        if (groupSortingEnabled) {
            setupSortingTeams(board);
        }

        if (nametagEnabled) {
            // Nametag abilitato: usa team per-giocatore (gestiscono sia ordinamento sia nametag).
            // Crea il team del nuovo giocatore su TUTTI i board attivi (incluso il suo).
            for (Scoreboard b : playerScoreboards.values()) {
                setupNametagTeam(b, player);
            }
            // Aggiungi i team degli altri giocatori online al board del nuovo giocatore.
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(player)) continue;
                setupNametagTeam(board, other);
            }
        } else if (groupSortingEnabled) {
            // Comportamento originale: team condivisi per gruppo (solo ordinamento).
            String playerGroup = getPlayerGroup(player);
            for (Player other : Bukkit.getOnlinePlayers()) {
                Scoreboard otherBoard = playerScoreboards.get(other.getUniqueId());
                if (otherBoard == null) continue;
                addToTeam(otherBoard, player.getName(), playerGroup);
            }
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(player)) continue;
                String otherGroup = getPlayerGroup(other);
                addToTeam(board, other.getName(), otherGroup);
            }
            addToTeam(board, player.getName(), playerGroup);
        }

        // Imposta il board al giocatore (la sidebar viene impostata da ScoreboardManager)
        player.setScoreboard(board);

        // Primo aggiornamento immediato di header/footer e nome tablist
        if (enabled) {
            if (headerFooterEnabled) updateHeaderFooter(player);
            if (nameFormattingEnabled) updateTablistName(player);
        }
    }

    /**
     * Chiamato al quit di un giocatore.
     * Rimuove il giocatore dai team degli altri board.
     *
     * @param player Il giocatore che esce
     */
    public void onQuit(Player player) {
        String name = player.getName();
        UUID uuid = player.getUniqueId();

        if (nametagEnabled) {
            // Rimuovi il team nametag di questo giocatore da tutti i board attivi.
            // Cerchiamo qualsiasi team che abbia il giocatore come entry, poiche'
            // il nome del team potrebbe essere cambiato se il gruppo e' stato modificato.
            for (Scoreboard board : playerScoreboards.values()) {
                // Copia per evitare ConcurrentModificationException durante l'unregister
                for (Team team : new HashSet<>(board.getTeams())) {
                    if (team.hasEntry(name)) {
                        team.unregister();
                    }
                }
            }
        } else if (groupSortingEnabled) {
            // Comportamento originale: rimuovi dai team di gruppo degli altri board
            for (Map.Entry<UUID, Scoreboard> entry : playerScoreboards.entrySet()) {
                if (entry.getKey().equals(uuid)) continue;
                removeFromAllTeams(entry.getValue(), name);
            }
        }

        // Rimuovi il board del giocatore dalla mappa
        playerScoreboards.remove(uuid);
    }

    // =========================================================================
    // Team di ordinamento
    // =========================================================================

    /**
     * Registra sul board tutti i team di ordinamento basati su groupOrder.
     * Il nome del team e' "NN_nomeGruppo" dove NN e' l'indice con zero iniziale.
     *
     * @param board Il board su cui registrare i team
     */
    private void setupSortingTeams(Scoreboard board) {
        for (int i = 0; i < groupOrder.size(); i++) {
            String groupName = groupOrder.get(i);
            // Prefisso numerico per garantire l'ordinamento alfabetico corretto
            String teamName = formatTeamName(i, groupName);
            // Crea il team solo se non esiste gia'
            if (board.getTeam(teamName) == null) {
                board.registerNewTeam(teamName);
            }
        }
        // Team di fallback per i giocatori senza gruppo configurato
        String fallbackName = "zz_other";
        if (board.getTeam(fallbackName) == null) {
            board.registerNewTeam(fallbackName);
        }
    }

    /**
     * Genera il nome del team per un gruppo in base alla sua priorita'.
     *
     * @param priority   Indice del gruppo (0 = massima priorita')
     * @param groupName  Nome del gruppo LuckPerms
     * @return Nome del team (es. "00_megaop")
     */
    private String formatTeamName(int priority, String groupName) {
        // Zero-padding a due cifre per l'ordinamento corretto fino a 99 gruppi
        String idx = priority < 10 ? "0" + priority : "" + priority;
        return idx + "_" + groupName;
    }

    /**
     * Aggiunge un giocatore (per nome) al team corrispondente al suo gruppo sul board.
     *
     * @param board      Il board su cui operare
     * @param playerName Il nome del giocatore
     * @param group      Il nome del gruppo LuckPerms
     */
    private void addToTeam(Scoreboard board, String playerName, String group) {
        // Prima rimuovi da eventuali team precedenti
        removeFromAllTeams(board, playerName);

        // Cerca il team per il gruppo
        int groupIndex = groupOrder.indexOf(group);
        String teamName;
        if (groupIndex >= 0) {
            teamName = formatTeamName(groupIndex, group);
        } else {
            // Gruppo non configurato: team di fallback
            teamName = "zz_other";
        }

        Team team = board.getTeam(teamName);
        if (team != null) {
            team.addEntry(playerName);
        }
    }

    /**
     * Rimuove un giocatore (per nome) da tutti i team del board.
     *
     * @param board      Il board su cui operare
     * @param playerName Il nome del giocatore
     */
    private void removeFromAllTeams(Scoreboard board, String playerName) {
        for (Team team : board.getTeams()) {
            if (team.hasEntry(playerName)) {
                team.removeEntry(playerName);
            }
        }
    }

    // =========================================================================
    // Nametag (prefisso/suffisso sopra la testa)
    // =========================================================================

    /**
     * Costruisce il nome del team per-giocatore usato per i nametag.
     * Il nome codifica la priorita' di gruppo per garantire il corretto ordinamento
     * in tablist: formato "@NN_playerName" (max 16 chars).
     *
     * @param player Il giocatore
     * @return Il nome del team (es. "@00_Steve")
     */
    private String buildPlayerTeamName(Player player) {
        int priority = 98; // fallback per gruppi non configurati
        if (groupSortingEnabled) {
            String group = getPlayerGroup(player);
            int idx = groupOrder.indexOf(group);
            if (idx >= 0) priority = idx;
        }
        // Zero-padding a due cifre + "@" prefisso (per non collidere con i team di gruppo)
        String idx = priority < 10 ? "0" + priority : "" + priority;
        // "@" + "00_" = 4 chars → spazio per 12 chars di nome giocatore = 16 totali
        String name = player.getName().length() > 12
                ? player.getName().substring(0, 12)
                : player.getName();
        return "@" + idx + "_" + name;
    }

    /**
     * Crea o aggiorna il team nametag del giocatore target sul board dato.
     * Imposta prefix e suffix dal formato configurato (LuckPerms + PlaceholderAPI).
     *
     * @param board  Il board su cui operare
     * @param target Il giocatore di cui mostrare il nametag
     */
    public void setupNametagTeam(Scoreboard board, Player target) {
        String teamName = buildPlayerTeamName(target);
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }

        // Converte il testo in formato § (gestisce sia & che § in input da LuckPerms/PAPI)
        String sectionPrefix = ColorUtil.colorizeString(resolveNametagText(nametagPrefixFormat, target));
        String sectionSuffix = ColorUtil.colorizeString(resolveNametagText(nametagSuffixFormat, target));

        // Crea i Component da passare al team
        Component prefix = LegacyComponentSerializer.legacySection().deserialize(sectionPrefix);
        Component suffix = LegacyComponentSerializer.legacySection().deserialize(sectionSuffix);

        team.prefix(prefix);
        team.suffix(suffix);

        // Imposta il colore del nome del giocatore come l'ultimo colore del prefisso.
        // Replica il comportamento legacy: &7 alla fine del prefisso colora il nome in grigio.
        team.color(ColorUtil.extractLastColor(sectionPrefix));

        // Assicura che il giocatore sia nell'entry del team
        if (!team.hasEntry(target.getName())) {
            team.addEntry(target.getName());
        }
    }

    /**
     * Risolve il testo del nametag per un giocatore.
     * Applica i placeholder LuckPerms e PlaceholderAPI.
     *
     * @param format Il formato grezzo (es. "{lp_prefix}")
     * @param player Il giocatore
     * @return Il testo risolto (ancora grezzo, da colorizzare)
     */
    private String resolveNametagText(String format, Player player) {
        // Sostituisce {lp_prefix} e {lp_suffix} con i valori LuckPerms
        String text = replaceLuckPermsPlaceholders(format, player);
        // Sostituisce %player% con il nome del giocatore
        text = text.replace("%player%", player.getName());
        // Applica PlaceholderAPI se disponibile
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    // =========================================================================
    // Helpers LuckPerms e PlaceholderAPI
    // =========================================================================

    /**
     * Ottieni il gruppo primario del giocatore da LuckPerms.
     * Se LuckPerms non e' disponibile o il giocatore non ha dati, restituisce "default".
     *
     * @param player Il giocatore
     * @return Il nome del gruppo primario
     */
    private String getPlayerGroup(Player player) {
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                return user.getPrimaryGroup();
            }
        } catch (Exception e) {
            // LuckPerms non disponibile, ignora silenziosamente
        }
        return "default";
    }

    /**
     * Sostituisce {lp_prefix} e {lp_suffix} nella stringa con i valori da LuckPerms.
     *
     * @param text   Stringa con placeholder
     * @param player Giocatore
     * @return Stringa con placeholder sostituiti
     */
    private String replaceLuckPermsPlaceholders(String text, Player player) {
        String prefix = "";
        String suffix = "";
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                net.luckperms.api.cacheddata.CachedMetaData meta =
                        user.getCachedData().getMetaData();
                String rawPrefix = meta.getPrefix();
                String rawSuffix = meta.getSuffix();
                if (rawPrefix != null) prefix = rawPrefix;
                if (rawSuffix != null) suffix = rawSuffix;
            }
        } catch (Exception e) {
            // LuckPerms non disponibile, usa stringhe vuote
        }
        // Sostituisce i placeholder con i valori ottenuti (usa + per evitare invokedynamic)
        return text.replace("{lp_prefix}", prefix).replace("{lp_suffix}", suffix);
    }

    /**
     * Applica i placeholder di PlaceholderAPI al testo se il plugin e' disponibile.
     * Se non e' installato, applica i placeholder di fallback interni.
     *
     * @param player Il giocatore
     * @param text   Il testo con placeholder
     * @return Il testo con placeholder risolti
     */
    private String applyPapi(Player player, String text) {
        // Sostituzioni fallback interne (sempre applicate prima di PAPI)
        text = text.replace("%player%", player.getName());
        text = text.replace("%online%", "" + Bukkit.getOnlinePlayers().size());

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    /**
     * Risolve una singola linea: sostituisce %animation:Nome% con il frame corrente
     * dell'animazione, poi applica PlaceholderAPI e i fallback.
     *
     * @param line   La linea raw
     * @param player Il giocatore
     * @return La linea risolta (ancora come stringa, da colorizzare dopo)
     */
    private String resolveLine(String line, Player player) {
        // Risolvi i placeholder di animazione (%animation:Nome%)
        line = resolveAnimations(line);
        // Sostituzioni fallback + PAPI
        line = applyPapi(player, line);
        return line;
    }

    /**
     * Sostituisce ogni occorrenza di %animation:Nome% con il frame raw corrente.
     *
     * @param text Il testo da elaborare
     * @return Il testo con le animazioni risolte
     */
    private String resolveAnimations(String text) {
        // Cerca il pattern %animation:NomeAnimazione%
        int start = text.indexOf("%animation:");
        while (start >= 0) {
            int end = text.indexOf('%', start + 1);
            if (end < 0) break;
            // Estrae il nome dell'animazione
            String animName = text.substring(start + "%animation:".length(), end);
            String frame = plugin.getAnimationsManager().getAnimations().getCurrentFrameRaw(animName);
            // Sostituisce il placeholder con il frame (usa + per evitare invokedynamic)
            text = text.substring(0, start) + frame + text.substring(end + 1);
            // Continua la ricerca dopo la sostituzione appena effettuata
            start = text.indexOf("%animation:", start + frame.length());
        }
        return text;
    }

    // =========================================================================
    // Getter pubblici
    // =========================================================================

    /**
     * Restituisce il {@link Scoreboard} dedicato al giocatore con l'UUID dato.
     * Usato da {@link ScoreboardManager} per condividere lo stesso board.
     *
     * @param uuid UUID del giocatore
     * @return Il Scoreboard del giocatore, o null se non presente
     */
    public Scoreboard getScoreboard(UUID uuid) {
        return playerScoreboards.get(uuid);
    }
}

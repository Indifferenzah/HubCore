package com.indifferenzah.hubcore.plugin;

import com.indifferenzah.hubcore.api.HubCoreAPI;
import com.indifferenzah.hubcore.plugin.commands.HubCoreCommand;
import com.indifferenzah.hubcore.plugin.config.ConfigLoader;
import com.indifferenzah.hubcore.plugin.config.MessagesLoader;
import com.indifferenzah.hubcore.plugin.database.H2StatsService;
import com.indifferenzah.hubcore.plugin.listener.*;
import com.indifferenzah.hubcore.plugin.service.ArmorManager;
import com.indifferenzah.hubcore.plugin.service.EffectManager;
import com.indifferenzah.hubcore.plugin.lobbyblocks.LobbyBlocksManager;
import com.indifferenzah.hubcore.plugin.service.PvPServiceImpl;
import com.indifferenzah.hubcore.plugin.service.SwordManager;
import com.indifferenzah.hubcore.plugin.task.AutoSaveTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;
import revxrsal.commands.bukkit.BukkitLamp;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classe principale del plugin HubCore.
 * Gestisce il ciclo di vita del plugin: abilitazione, disabilitazione e ricarica.
 */
public class HubCorePlugin extends JavaPlugin {

    private static HubCorePlugin instance;

    private ConfigLoader configLoader;
    private MessagesLoader messagesLoader;
    private H2StatsService statsService;
    private PvPServiceImpl pvpService;
    private SwordManager swordManager;
    private ArmorManager armorManager;
    private EffectManager effectManager;
    private LobbyBlocksManager lobbyBlocksManager;

    // Versione letta da version.yml (classpath), usata nel log e nel version check
    private String localVersion = "unknown";
    // Flag che indica se e' disponibile un aggiornamento su GitHub
    private boolean updateAvailable = false;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Salva le configurazioni di default se non esistono
        saveDefaultConfig();
        saveResource("messages.yml", false);
        saveResource("version.yml", false);

        // 2. Crea la cartella dati del plugin se non esiste
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // 3. Carica i file di configurazione
        configLoader = new ConfigLoader(this);
        messagesLoader = new MessagesLoader(this);

        // Legge la versione da version.yml (incluso nel JAR) prima di qualsiasi log
        String v = readLocalVersion();
        if (v != null) localVersion = v;

        // 4. Inizializza il database H2
        statsService = new H2StatsService(this);

        // 5. Inizializza i servizi
        swordManager = new SwordManager(this);
        armorManager = new ArmorManager(this);
        effectManager = new EffectManager(this);
        pvpService = new PvPServiceImpl(this);
        lobbyBlocksManager = new LobbyBlocksManager(this);
        saveResource("menu/blockselector.yml", false);

        // 6. Registra i servizi nell'API pubblica
        HubCoreAPI.setStatsService(statsService);
        HubCoreAPI.setPvpService(pvpService);

        // 7. Registra i listener degli eventi
        registerListeners();

        // 8. Registra i comandi tramite Lamp v4
        registerCommands();

        // 9. Avvia il task di salvataggio automatico
        long saveInterval = configLoader.getSaveInterval();
        new AutoSaveTask(this).runTaskTimer(this, saveInterval, saveInterval);

        // 10. Verifica la versione in modo asincrono
        checkVersion();

        // 11. Messaggio di avvio nel log (versione letta da version.yml)
        getLogger().info("HubCore v" + localVersion + " abilitato con successo.");
        getLogger().info("Sviluppato da Indifferenzah con amore.");
    }

    @Override
    public void onDisable() {
        // Salva tutte le statistiche in cache prima di chiudere
        if (statsService != null) {
            statsService.saveAll();
            statsService.close();
        }

        getLogger().info("HubCore disabilitato. Statistiche salvate.");
    }

    // =========================================================================
    // Registrazione listener e comandi
    // =========================================================================

    /**
     * Registra tutti i listener degli eventi Bukkit.
     */
    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerJoinListener(this), this);
        pm.registerEvents(new PlayerRespawnListener(this), this);
        pm.registerEvents(new PlayerQuitListener(this), this);
        pm.registerEvents(new PlayerItemHeldListener(this), this);
        pm.registerEvents(new EntityDamageListener(this), this);
        pm.registerEvents(new PlayerDeathListener(this), this);
        pm.registerEvents(new PlayerDropItemListener(this), this);
        pm.registerEvents(new PlayerSwapHandListener(this), this);
        pm.registerEvents(new InventoryClickListener(this), this);
        pm.registerEvents(new LobbyBlocksListener(this), this);
    }

    /**
     * Registra i comandi tramite Lamp v4 BukkitLamp.
     */
    private void registerCommands() {
        var lamp = BukkitLamp.builder(this).build();
        lamp.register(new HubCoreCommand(this));
    }

    // =========================================================================
    // Controllo versione
    // =========================================================================

    /**
     * Controlla in modo asincrono se e' disponibile una versione piu' recente su GitHub.
     * Se la versione locale e' piu' nuova di quella remota, disabilita il plugin.
     * Se quella remota e' piu' nuova, imposta il flag updateAvailable = true.
     */
    private void checkVersion() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                // Effettua la richiesta HTTP all'API GitHub
                String remoteVersion = fetchLatestGitHubVersion();
                if (remoteVersion == null) {
                    getLogger().warning("[VersionCheck] Impossibile recuperare la versione da GitHub.");
                    return;
                }

                int comparison = compareSemVer(localVersion, remoteVersion);

                if (comparison > 0) {
                    // Versione locale più recente di GitHub: build di sviluppo non rilasciata
                    getLogger().warning("[VersionCheck] Versione locale (" + localVersion + ") "
                            + "> remota (" + remoteVersion + "): build di sviluppo rilevata.");
                    Bukkit.getScheduler().runTask(this, () -> {
                        getLogger().severe("[VersionCheck] Disabilitazione plugin per versione non autorizzata.");
                        getServer().getPluginManager().disablePlugin(this);
                    });
                } else if (comparison < 0) {
                    // Aggiornamento disponibile
                    updateAvailable = true;
                    getLogger().info("[VersionCheck] Aggiornamento disponibile: v" + remoteVersion
                            + " (corrente: v" + localVersion + ")");
                    Bukkit.getScheduler().runTask(this, () -> {
                        for (var player : Bukkit.getOnlinePlayers()) {
                            if (player.hasPermission("hubcore.admin")) {
                                player.sendMessage(messagesLoader.get("admin.update-available"));
                            }
                        }
                    });
                } else {
                    getLogger().info("[VersionCheck] Plugin aggiornato (v" + localVersion + ").");
                }

            } catch (Exception e) {
                getLogger().warning("[VersionCheck] Errore durante la verifica: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    /**
     * Legge la versione dal file version.yml nel classpath del JAR.
     *
     * @return La stringa di versione, o null in caso di errore
     */
    private String readLocalVersion() {
        try (InputStream is = getClass().getResourceAsStream("/version.yml")) {
            if (is == null) return null;
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(is);
            if (data == null) return null;
            Object version = data.get("version");
            return version != null ? version.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Recupera l'ultimo tag di release da GitHub tramite la sua API REST.
     *
     * @return La stringa della versione remota (es. "1.2.0"), o null in caso di errore
     */
    private String fetchLatestGitHubVersion() {
        try {
            URL url = URI.create("https://api.github.com/repos/Indifferenzah/HubCore/releases/latest").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setRequestProperty("User-Agent", "HubCore-Plugin/" + localVersion);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                getLogger().warning("[VersionCheck] GitHub ha risposto con HTTP " + responseCode
                        + " (nessuna release pubblicata?).");
                return null;
            }

            StringBuilder sb = new StringBuilder();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }

            // Estrae il tag_name — accetta sia "v1.0.0" che "1.0.0"
            Matcher matcher = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"").matcher(sb);
            if (matcher.find()) return matcher.group(1).trim();

            getLogger().warning("[VersionCheck] tag_name non trovato nella risposta GitHub.");
            return null;
        } catch (Exception e) {
            getLogger().warning("[VersionCheck] Fetch fallito: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Confronta due stringhe di versione in formato SemVer (MAJOR.MINOR.PATCH).
     *
     * @param v1 Prima versione
     * @param v2 Seconda versione
     * @return Negativo se v1 < v2, 0 se uguali, positivo se v1 > v2
     */
    private int compareSemVer(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int p2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (p1 != p2) return p1 - p2;
        }
        return 0;
    }

    /** Parsa una parte di versione, ignorando i suffissi non numerici (es. "1-SNAPSHOT" → 1). */
    private int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // =========================================================================
    // Reload
    // =========================================================================

    /**
     * Ricarica config.yml e messages.yml, poi ri-applica spada e armatura
     * a tutti i giocatori online in base al loro stato PvP corrente.
     */
    public void reload() {
        // 1. Ricarica i file di configurazione
        reloadConfig();
        messagesLoader.load();

        // Ricarica il menu del selettore blocchi (blockselector.yml)
        lobbyBlocksManager.reload();

        // 2. Ri-applica spada, armatura e item lobby a tutti i giocatori online
        for (var player : getServer().getOnlinePlayers()) {
            boolean inPvP = pvpService.isInPvP(player.getUniqueId());

            // --- Spada ---
            // Rimuovi la spada dalla posizione attuale e reimmettila nello slot configurato
            int currentSwordSlot = swordManager.getSwordSlot(player);
            if (currentSwordSlot != -1) {
                // Rimuovi dal vecchio slot
                player.getInventory().setItem(currentSwordSlot, null);
            }
            // Dai la spada nel nuovo slot configurato con l'aspetto corretto
            swordManager.giveSword(player);
            swordManager.updateSwordAppearance(player, inPvP);

            // --- Armatura ---
            if (inPvP) {
                // PvP attivo: rigenera i pezzi con la nuova config (rimuove i vecchi e dà i nuovi)
                armorManager.removeArmor(player);
                armorManager.giveArmor(player);
            } else {
                // PvP non attivo: assicura che non ci sia armatura PvP equipaggiata
                armorManager.removeArmor(player);
            }

            // Ri-consegna gli item lobby blocks con la nuova configurazione
            lobbyBlocksManager.giveItems(player);
        }

        getLogger().info("Configurazione ricaricata.");
    }

    // =========================================================================
    // Getter pubblici
    // =========================================================================

    public static HubCorePlugin getInstance() {
        return instance;
    }

    public ConfigLoader getConfigLoader() {
        return configLoader;
    }

    public MessagesLoader getMessagesLoader() {
        return messagesLoader;
    }

    public H2StatsService getStatsService() {
        return statsService;
    }

    public H2StatsService getH2StatsService() {
        return statsService;
    }

    public PvPServiceImpl getPvpService() {
        return pvpService;
    }

    public SwordManager getSwordManager() {
        return swordManager;
    }

    public ArmorManager getArmorManager() {
        return armorManager;
    }

    public EffectManager getEffectManager() {
        return effectManager;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public LobbyBlocksManager getLobbyBlocksManager() {
        return lobbyBlocksManager;
    }
}

package com.indifferenzah.hubcore.plugin;

import com.indifferenzah.hubcore.api.HubCoreAPI;
import com.indifferenzah.hubcore.plugin.commands.HubCoreCommand;
import com.indifferenzah.hubcore.plugin.commands.SpawnCommand;
import com.indifferenzah.hubcore.plugin.config.ConfigLoader;
import com.indifferenzah.hubcore.plugin.config.MessagesLoader;
import com.indifferenzah.hubcore.plugin.database.H2StatsService;
import com.indifferenzah.hubcore.plugin.listener.*;
import com.indifferenzah.hubcore.plugin.listener.DoubleJumpListener;
import com.indifferenzah.hubcore.plugin.listener.LaunchpadListener;
import com.indifferenzah.hubcore.plugin.service.ArmorManager;
import com.indifferenzah.hubcore.plugin.service.EffectManager;
import com.indifferenzah.hubcore.plugin.lobbyblocks.LobbyBlocksManager;
import com.indifferenzah.hubcore.plugin.service.PvPServiceImpl;
import com.indifferenzah.hubcore.plugin.commands.CustomCommandLoader;
import com.indifferenzah.hubcore.plugin.service.AnimationsManager;
import com.indifferenzah.hubcore.plugin.service.ChatLockManager;
import com.indifferenzah.hubcore.plugin.service.CustomJoinItemsManager;
import com.indifferenzah.hubcore.plugin.menu.MenuLoader;
import com.indifferenzah.hubcore.plugin.service.MenuRegistry;
import com.indifferenzah.hubcore.plugin.service.PlayerHiderManager;
import com.indifferenzah.hubcore.plugin.service.ScoreboardManager;
import com.indifferenzah.hubcore.plugin.service.SwordManager;
import com.indifferenzah.hubcore.plugin.service.TabManager;
import com.indifferenzah.hubcore.plugin.task.AutoSaveTask;
import com.indifferenzah.hubcore.plugin.util.ActionExecutor;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import revxrsal.commands.bukkit.BukkitLamp;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
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
    private DoubleJumpListener doubleJumpListener;
    private AnimationsManager animationsManager;
    private TabManager tabManager;
    private ScoreboardManager scoreboardManager;
    private ActionExecutor actionExecutor;
    private MenuRegistry menuRegistry;
    private MenuLoader menuLoader;
    private CustomJoinItemsManager customJoinItemsManager;
    private PlayerHiderManager playerHiderManager;
    private CustomCommandLoader customCommandLoader;
    private ChatLockManager chatLockManager;

    // Versione letta da version.yml (classpath), usata nel log e nel version check
    private String localVersion = "unknown";
    // Flag che indica se e' disponibile un aggiornamento su GitHub
    private boolean updateAvailable = false;
    // Ultima versione disponibile su GitHub (null se non ancora verificato)
    private String newestVersion = null;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Salva le configurazioni di default se non esistono
        saveDefaultConfig();
        saveResource("messages.yml", false);
        saveResource("version.yml", false); // crea solo se non esiste

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

        // Salva tab.yml, scoreboard.yml e animations.yml se non esistono ancora
        saveResource("tab.yml", false);
        saveResource("scoreboard.yml", false);
        saveResource("animations.yml", false);

        // Inizializza AnimationsManager (condiviso da Tab e Scoreboard) prima dei due manager
        animationsManager = new AnimationsManager(this);
        animationsManager.start();

        // Inizializza TabManager e ScoreboardManager
        tabManager = new TabManager(this);
        scoreboardManager = new ScoreboardManager(this, tabManager);
        tabManager.start();
        scoreboardManager.start();

        // Inizializza ActionExecutor, MenuRegistry e manager item custom
        actionExecutor = new ActionExecutor(this);
        menuRegistry = new MenuRegistry(this);
        menuLoader = new MenuLoader(this);
        menuLoader.loadAll();
        customJoinItemsManager = new CustomJoinItemsManager(this);
        playerHiderManager = new PlayerHiderManager(this);

        // Inizializza sistema comandi/alias dinamici e chat lock
        chatLockManager = new ChatLockManager();
        customCommandLoader = new CustomCommandLoader(this);
        customCommandLoader.loadAll();

        // Registra canale BungeeCord per azione [PROXY]
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

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
        // Ferma i task di animazioni, tab e sidebar
        if (animationsManager != null) animationsManager.stop();
        if (tabManager != null) tabManager.stop();
        if (scoreboardManager != null) scoreboardManager.stop();

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
        pm.registerEvents(new LaunchpadListener(this), this);
        pm.registerEvents(new WorldSettingsListener(this), this);
        doubleJumpListener = new DoubleJumpListener(this);
        pm.registerEvents(doubleJumpListener, this);
        pm.registerEvents(new AntiWDLListener(this), this);
        pm.registerEvents(new CustomItemsListener(this), this);
        pm.registerEvents(new MenuClickListener(this), this);
        pm.registerEvents(new ChatLockListener(this), this);
    }

    /**
     * Registra i comandi tramite Lamp v4 BukkitLamp.
     */
    private void registerCommands() {
        var lamp = BukkitLamp.builder(this).build();
        lamp.register(new HubCoreCommand(this));
        lamp.register(new SpawnCommand(this));
    }

    // =========================================================================
    // Controllo versione
    // =========================================================================

    /**
     * Controlla in modo asincrono se è disponibile una versione più recente su GitHub.
     * Se la versione locale è più nuova di quella remota, disabilita il plugin.
     * Se quella remota è più nuova, imposta il flag updateAvailable = true.
     */
    private void checkVersion() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String remoteVersion = fetchLatestGitHubVersion();
                if (remoteVersion == null) {
                    getLogger().warning("[VersionCheck] Impossibile recuperare la versione da GitHub.");
                    return;
                }

                int cmp = compareSemVer(localVersion, remoteVersion);
                if (cmp > 0) {
                    // version.yml ha una versione superiore all'ultima release: disabilita
                    getLogger().severe("[VersionCheck] Versione non valida: locale (" + localVersion
                            + ") > ultima release (" + remoteVersion + "). Plugin disabilitato.");
                    Bukkit.getScheduler().runTask(this,
                            () -> getServer().getPluginManager().disablePlugin(this));
                } else if (cmp < 0) {
                    // Aggiornamento disponibile
                    updateAvailable = true;
                    newestVersion = remoteVersion;
                    getLogger().info("[VersionCheck] Aggiornamento disponibile: v" + remoteVersion
                            + " (corrente: v" + localVersion + ")");
                    Component updateMsg = buildUpdateMessage(localVersion, remoteVersion);
                    Bukkit.getScheduler().runTask(this, () -> {
                        for (var player : Bukkit.getOnlinePlayers()) {
                            if (player.hasPermission("hubcore.admin")) {
                                player.sendMessage(updateMsg);
                            }
                        }
                    });
                } else {
                    getLogger().info("[VersionCheck] Plugin aggiornato (v" + localVersion + ").");
                }

            } catch (Exception e) {
                getLogger().warning("[VersionCheck] Errore: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private Component buildUpdateMessage(String current, String newest) {
        String url = "https://github.com/Indifferenzah/HubCore/releases";
        return Component.text()
                .append(ColorUtil.colorize("&8[&bHubCore&8] &6An update is available, current version: &c" + current
                        + " &6, newest version: &a" + newest + "&6."))
                .appendNewline()
                .append(ColorUtil.colorize("&6Download it now: "))
                .append(Component.text(url)
                        .color(NamedTextColor.WHITE)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(url)))
                .build();
    }

    /** Restituisce il messaggio di update pronto da inviare, o null se non disponibile. */
    public Component getUpdateMessage() {
        if (!updateAvailable || newestVersion == null) return null;
        return buildUpdateMessage(localVersion, newestVersion);
    }

    private int compareSemVer(String v1, String v2) {
        String[] p1 = v1.split("\\.");
        String[] p2 = v2.split("\\.");
        int len = Math.max(p1.length, p2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < p1.length ? parseIntSafe(p1[i]) : 0;
            int n2 = i < p2.length ? parseIntSafe(p2[i]) : 0;
            if (n1 != n2) return n1 - n2;
        }
        return 0;
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return 0; }
    }

    /**
     * Legge la versione dal file version.yml nel classpath del JAR.
     *
     * @return La stringa di versione, o null in caso di errore
     */
    private String readLocalVersion() {
        // Legge dal data folder (salvato da saveResource a ogni avvio → sempre uguale al JAR)
        java.io.File file = new java.io.File(getDataFolder(), "version.yml");
        if (!file.exists()) return null;
        try {
            org.bukkit.configuration.file.YamlConfiguration cfg =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            String v = cfg.getString("version");
            return (v != null && !v.isBlank()) ? v.trim() : null;
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

        // Ricarica animations.yml, tab.yml e scoreboard.yml
        if (animationsManager != null) animationsManager.load();
        if (tabManager != null) tabManager.load();
        if (scoreboardManager != null) scoreboardManager.load();
        if (customJoinItemsManager != null) customJoinItemsManager.load();
        if (playerHiderManager != null) playerHiderManager.load();
        if (menuLoader != null) menuLoader.loadAll();
        if (customCommandLoader != null) customCommandLoader.loadAll();

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

    public DoubleJumpListener getDoubleJumpListener() {
        return doubleJumpListener;
    }

    public AnimationsManager getAnimationsManager() {
        return animationsManager;
    }

    public TabManager getTabManager() {
        return tabManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public ActionExecutor getActionExecutor() {
        return actionExecutor;
    }

    public MenuRegistry getMenuRegistry() {
        return menuRegistry;
    }

    public CustomJoinItemsManager getCustomJoinItemsManager() {
        return customJoinItemsManager;
    }

    public PlayerHiderManager getPlayerHiderManager() {
        return playerHiderManager;
    }

    public CustomCommandLoader getCustomCommandLoader() {
        return customCommandLoader;
    }

    public ChatLockManager getChatLockManager() {
        return chatLockManager;
    }
}

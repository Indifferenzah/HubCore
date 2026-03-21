package com.indifferenzah.hubcore.plugin.database;

import com.indifferenzah.hubcore.api.model.PlayerData;
import com.indifferenzah.hubcore.api.service.StatsService;
import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.File;
import java.sql.*;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementazione di StatsService che usa H2 embedded tramite HikariCP.
 */
public class H2StatsService implements StatsService {

    private final HubCorePlugin plugin;
    private HikariDataSource dataSource;

    // Cache in memoria: UUID → PlayerData
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    // Lobby spawn location (cached, null se non ancora impostata)
    private Location lobbyLocation;

    public H2StatsService(HubCorePlugin plugin) {
        this.plugin = plugin;
        initDataSource();
        createTable();
        createLobbyTable();
        lobbyLocation = loadLobbyLocation();
    }

    /**
     * Inizializza HikariCP con il database H2 embedded.
     */
    private void initDataSource() {
        String dbFile = plugin.getConfigLoader().getDatabaseFile();
        // Percorso assoluto nella cartella dati del plugin
        File dataFolder = plugin.getDataFolder();
        String jdbcUrl = "jdbc:h2:file:" + new File(dataFolder, dbFile).getAbsolutePath()
                + ";MODE=MySQL;AUTO_RECONNECT=TRUE";

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("HubCore-H2");

        dataSource = new HikariDataSource(config);
    }

    /**
     * Crea la tabella pvp_stats se non esiste.
     */
    private void createTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS pvp_stats (
                    uuid        VARCHAR(36) NOT NULL PRIMARY KEY,
                    name        VARCHAR(16) NOT NULL,
                    kills       INT         NOT NULL DEFAULT 0,
                    deaths      INT         NOT NULL DEFAULT 0,
                    killstreak  INT         NOT NULL DEFAULT 0,
                    best_streak INT         NOT NULL DEFAULT 0,
                    last_seen   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nella creazione della tabella pvp_stats: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // StatsService
    // -------------------------------------------------------------------------

    @Override
    public void loadPlayer(UUID uuid, String name) {
        String sql = "SELECT kills, deaths, killstreak, best_streak FROM pvp_stats WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                PlayerData data = new PlayerData(uuid, name);

                if (rs.next()) {
                    // Carica i dati esistenti dal database
                    data.setKills(rs.getInt("kills"));
                    data.setDeaths(rs.getInt("deaths"));
                    data.setKillstreak(rs.getInt("killstreak"));
                    data.setBestStreak(rs.getInt("best_streak"));
                    // Aggiorna il nome (potrebbe essere cambiato)
                    updateName(conn, uuid, name);
                } else {
                    // Primo accesso: crea la riga nel database
                    insertPlayer(conn, uuid, name);
                }

                cache.put(uuid, data);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nel caricamento del giocatore " + name + ": " + e.getMessage());
            // Mette comunque i dati vuoti in cache per evitare NPE
            cache.put(uuid, new PlayerData(uuid, name));
        }
    }

    @Override
    public void savePlayer(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) return;

        String sql = """
                MERGE INTO pvp_stats (uuid, name, kills, deaths, killstreak, best_streak, last_seen)
                KEY(uuid)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setString(2, data.getName());
            ps.setInt(3, data.getKills());
            ps.setInt(4, data.getDeaths());
            // Salva 0 per la killstreak corrente (si azzera tra sessioni)
            ps.setInt(5, 0);
            ps.setInt(6, data.getBestStreak());
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nel salvataggio del giocatore " + data.getName() + ": " + e.getMessage());
        }
    }

    @Override
    public void saveAll() {
        // Salva tutti i giocatori in cache
        for (UUID uuid : cache.keySet()) {
            savePlayer(uuid);
        }
        plugin.getLogger().info("Statistiche salvate per " + cache.size() + " giocatori.");
    }

    @Override
    public Optional<PlayerData> getPlayer(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    @Override
    public void resetStats(UUID uuid) {
        // Azzera nel database
        String sql = "UPDATE pvp_stats SET kills = 0, deaths = 0, killstreak = 0, best_streak = 0 WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nel reset delle statistiche: " + e.getMessage());
        }

        // Azzera anche nella cache
        PlayerData data = cache.get(uuid);
        if (data != null) {
            data.setKills(0);
            data.setDeaths(0);
            data.setKillstreak(0);
            data.setBestStreak(0);
        }
    }

    /**
     * Chiude il pool di connessioni. Chiamato al disable del plugin.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Pool di connessioni H2 chiuso.");
        }
    }

    /**
     * Rimuove il giocatore dalla cache (al quit).
     *
     * @param uuid UUID del giocatore
     */
    public void unloadPlayer(UUID uuid) {
        cache.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Metodi privati di supporto
    // -------------------------------------------------------------------------

    /** Inserisce una nuova riga per il giocatore. */
    private void insertPlayer(Connection conn, UUID uuid, String name) throws SQLException {
        String sql = "INSERT INTO pvp_stats (uuid, name) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Lobby Location
    // -------------------------------------------------------------------------

    private void createLobbyTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS lobby_location (
                    id    INT          DEFAULT 1 NOT NULL PRIMARY KEY,
                    world VARCHAR(64)  NOT NULL,
                    x     DOUBLE       NOT NULL,
                    y     DOUBLE       NOT NULL,
                    z     DOUBLE       NOT NULL,
                    yaw   FLOAT        NOT NULL,
                    pitch FLOAT        NOT NULL
                )
                """;
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nella creazione della tabella lobby_location: " + e.getMessage());
        }
    }

    public void setLobbyLocation(Location loc) {
        String sql = """
                MERGE INTO lobby_location (id, world, x, y, z, yaw, pitch)
                KEY(id) VALUES (1, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, loc.getWorld().getName());
            ps.setDouble(2, loc.getX());
            ps.setDouble(3, loc.getY());
            ps.setDouble(4, loc.getZ());
            ps.setFloat(5, loc.getYaw());
            ps.setFloat(6, loc.getPitch());
            ps.executeUpdate();
            lobbyLocation = loc.clone();
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nel salvataggio della lobby location: " + e.getMessage());
        }
    }

    public Location getLobbyLocation() {
        return lobbyLocation;
    }

    private Location loadLobbyLocation() {
        String sql = "SELECT world, x, y, z, yaw, pitch FROM lobby_location WHERE id = 1";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    var world = Bukkit.getWorld(rs.getString("world"));
                    if (world == null) return null;
                    return new Location(world, rs.getDouble("x"), rs.getDouble("y"),
                            rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Impossibile caricare la lobby location: " + e.getMessage());
        }
        return null;
    }

    // -------------------------------------------------------------------------

    /** Aggiorna il nome del giocatore nel database. */
    private void updateName(Connection conn, UUID uuid, String name) throws SQLException {
        String sql = "UPDATE pvp_stats SET name = ?, last_seen = CURRENT_TIMESTAMP WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }
}

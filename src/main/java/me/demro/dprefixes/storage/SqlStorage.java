package me.demro.dprefixes.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.demro.dprefixes.DPrefixes;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Универсальный SQL-провайдер для H2 и MySQL/MariaDB.
 * <p>
 * Защита от race condition: сохранение одного игрока сериализуется
 * через {@code locks} (per-uuid lock). UPSERT ({@code MERGE} для H2,
 * {@code INSERT ... ON DUPLICATE KEY UPDATE} для MySQL) исключает
 * ошибки нарушения первичного ключа.
 */
public class SqlStorage implements StorageProvider {

    /** Варианты СУБД. */
    public enum Flavor { H2, MYSQL }

    private final DPrefixes plugin;
    private final Flavor flavor;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int poolSize;
    private final long connectionTimeoutMs;
    private final String tableOwnership;
    private final String tableEquipped;

    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();

    private HikariDataSource ds;

    public SqlStorage(@NotNull DPrefixes plugin,
                      @NotNull Flavor flavor,
                      @NotNull String jdbcUrl,
                      @NotNull String username,
                      @NotNull String password,
                      int poolSize,
                      long connectionTimeoutMs,
                      @NotNull String tablePrefix) {
        this.plugin = plugin;
        this.flavor = flavor;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.tableOwnership = tablePrefix + "ownership";
        this.tableEquipped = tablePrefix + "equipped";
    }

    @Override
    public void init() throws Exception {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(username);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(poolSize);
        cfg.setConnectionTimeout(connectionTimeoutMs);
        cfg.setPoolName("DPrefixes-Pool");
        cfg.setDriverClassName(flavor == Flavor.H2 ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver");

        this.ds = new HikariDataSource(cfg);
        createTables();
    }

    @Override
    public void shutdown() {
        if (ds != null && !ds.isClosed()) ds.close();
    }

    @Override
    public boolean isPreloadAll() {
        return StorageProvider.super.isPreloadAll();
    }

    private void createTables() throws SQLException {
        String ownershipSql;
        String equippedSql;

        if (flavor == Flavor.H2) {
            ownershipSql = "CREATE TABLE IF NOT EXISTS " + tableOwnership + " ("
                    + "uuid VARCHAR(36) NOT NULL, type VARCHAR(16) NOT NULL, "
                    + "item_key VARCHAR(255) NOT NULL, expire_at BIGINT NOT NULL, "
                    + "PRIMARY KEY (uuid, type, item_key))";
            equippedSql = "CREATE TABLE IF NOT EXISTS " + tableEquipped + " ("
                    + "uuid VARCHAR(36) NOT NULL, type VARCHAR(16) NOT NULL, "
                    + "item_key VARCHAR(255) NOT NULL, "
                    + "PRIMARY KEY (uuid, type))";
        } else {
            ownershipSql = "CREATE TABLE IF NOT EXISTS " + tableOwnership + " ("
                    + "uuid CHAR(36) NOT NULL, type VARCHAR(16) NOT NULL, "
                    + "item_key VARCHAR(255) NOT NULL, expire_at BIGINT NOT NULL, "
                    + "PRIMARY KEY (uuid, type, item_key), "
                    + "INDEX idx_uuid (uuid)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
            equippedSql = "CREATE TABLE IF NOT EXISTS " + tableEquipped + " ("
                    + "uuid CHAR(36) NOT NULL, type VARCHAR(16) NOT NULL, "
                    + "item_key VARCHAR(255) NOT NULL, "
                    + "PRIMARY KEY (uuid, type), "
                    + "INDEX idx_uuid (uuid)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        }

        try (Connection c = ds.getConnection();
             PreparedStatement ps1 = c.prepareStatement(ownershipSql);
             PreparedStatement ps2 = c.prepareStatement(equippedSql)) {
            ps1.executeUpdate();
            ps2.executeUpdate();
        }
    }

    @Override
    public @NotNull Map<UUID, PlayerData> loadAll() { return new HashMap<>(); }

    @Override
    public @NotNull PlayerData loadPlayer(@NotNull UUID uuid) {
        PlayerData data = new PlayerData();
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT type, item_key FROM " + tableEquipped + " WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String type = rs.getString("type");
                        String key = rs.getString("item_key");
                        if ("prefix".equals(type)) data.setEquippedPrefixKey(key);
                        else if ("suffix".equals(type)) data.setEquippedSuffixKey(key);
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT type, item_key, expire_at FROM " + tableOwnership + " WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String type = rs.getString("type");
                        String key = rs.getString("item_key");
                        long expire = rs.getLong("expire_at");
                        data.getOwnership().put(type + ":" + key, expire);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка загрузки игрока " + uuid + ": " + e.getMessage());
        }
        return data;
    }

    @Override
    public void savePlayer(@NotNull UUID uuid, @NotNull PlayerData data) {
        if (ds == null) return;
        Object lock = locks.computeIfAbsent(uuid, u -> new Object());
        synchronized (lock) {
            try (Connection c = ds.getConnection()) {
                c.setAutoCommit(false);
                try {
                    upsertOwnership(c, uuid, data);
                    upsertEquipped(c, uuid, data);
                    deleteOrphans(c, uuid, data);
                    c.commit();
                } catch (SQLException ex) {
                    c.rollback();
                    throw ex;
                } finally {
                    c.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Ошибка сохранения игрока " + uuid + ": " + e.getMessage());
            }
        }
    }

    private void upsertOwnership(@NotNull Connection c, @NotNull UUID uuid, @NotNull PlayerData data) throws SQLException {
        if (data.getOwnership().isEmpty()) return;
        String sql = flavor == Flavor.H2
                ? "MERGE INTO " + tableOwnership + " (uuid, type, item_key, expire_at) KEY (uuid, type, item_key) VALUES (?, ?, ?, ?)"
                : "INSERT INTO " + tableOwnership + " (uuid, type, item_key, expire_at) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE expire_at = VALUES(expire_at)";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Map.Entry<String, Long> e : data.getOwnership().entrySet()) {
                String fullKey = e.getKey();
                int idx = fullKey.indexOf(':');
                if (idx <= 0) continue;
                ps.setString(1, uuid.toString());
                ps.setString(2, fullKey.substring(0, idx));
                ps.setString(3, fullKey.substring(idx + 1));
                ps.setLong(4, e.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void upsertEquipped(@NotNull Connection c, @NotNull UUID uuid, @NotNull PlayerData data) throws SQLException {
        String sql = flavor == Flavor.H2
                ? "MERGE INTO " + tableEquipped + " (uuid, type, item_key) KEY (uuid, type) VALUES (?, ?, ?)"
                : "INSERT INTO " + tableEquipped + " (uuid, type, item_key) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE item_key = VALUES(item_key)";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (!data.getEquippedPrefixKey().isEmpty()) {
                ps.setString(1, uuid.toString());
                ps.setString(2, "prefix");
                ps.setString(3, data.getEquippedPrefixKey());
                ps.addBatch();
            }
            if (!data.getEquippedSuffixKey().isEmpty()) {
                ps.setString(1, uuid.toString());
                ps.setString(2, "suffix");
                ps.setString(3, data.getEquippedSuffixKey());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void deleteOrphans(@NotNull Connection c, @NotNull UUID uuid, @NotNull PlayerData data) throws SQLException {
        if (data.getOwnership().isEmpty()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + tableOwnership + " WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
        } else {
            StringBuilder sb = new StringBuilder("DELETE FROM " + tableOwnership + " WHERE uuid = ? AND (type, item_key) NOT IN (");
            for (int i = 0; i < data.getOwnership().size(); i++) sb.append(i == 0 ? "(?, ?)" : ", (?, ?)");
            sb.append(")");

            try (PreparedStatement ps = c.prepareStatement(sb.toString())) {
                ps.setString(1, uuid.toString());
                int p = 2;
                for (String fullKey : data.getOwnership().keySet()) {
                    int idx = fullKey.indexOf(':');
                    ps.setString(p++, idx > 0 ? fullKey.substring(0, idx) : "");
                    ps.setString(p++, idx > 0 ? fullKey.substring(idx + 1) : fullKey);
                }
                ps.executeUpdate();
            }
        }

        if (data.getEquippedPrefixKey().isEmpty()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + tableEquipped + " WHERE uuid = ? AND type = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, "prefix");
                ps.executeUpdate();
            }
        }
        if (data.getEquippedSuffixKey().isEmpty()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + tableEquipped + " WHERE uuid = ? AND type = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, "suffix");
                ps.executeUpdate();
            }
        }
    }

    @Override
    public void saveAll(@NotNull Map<UUID, PlayerData> data) {
        for (Map.Entry<UUID, PlayerData> e : data.entrySet()) savePlayer(e.getKey(), e.getValue());
    }
}
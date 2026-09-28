package me.demro.dprefixes.storage;

import me.demro.dprefixes.DPrefixes;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Фабрика хранилищ + миграция данных из {@code playerdata.yml}.
 */
public class DatabaseManager {

    private final @NotNull DPrefixes plugin;

    public DatabaseManager(@NotNull DPrefixes plugin) {
        this.plugin = plugin;
    }

    /**
     * Создаёт хранилище согласно настройке {@code database.type}.
     *
     * @return провайдер хранилища
     */
    public @NotNull StorageProvider create() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("database");
        String type = cfg == null ? "yaml" : cfg.getString("type", "yaml").toLowerCase();
        String tablePrefix = cfg == null ? "dp_" : cfg.getString("table-prefix", "dp_");

        switch (type) {
            case "h2" -> {
                String file = cfg.getString("h2.file", "storage");
                String user = cfg.getString("h2.username", "sa");
                String pass = cfg.getString("h2.password", "");
                File dbFile = new File(plugin.getDataFolder(), file);
                String url = "jdbc:h2:file:" + dbFile.getAbsolutePath() + ";AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
                plugin.getLogger().info("Хранилище: H2 (" + dbFile.getAbsolutePath() + ")");
                return new SqlStorage(plugin, SqlStorage.Flavor.H2, url, user, pass, 3, 5000L, tablePrefix);
            }
            case "mysql", "mariadb" -> {
                String host = cfg.getString("mysql.host", "localhost");
                int port = cfg.getInt("mysql.port", 3306);
                String db = cfg.getString("mysql.database", "dprefixes");
                String user = cfg.getString("mysql.username", "root");
                String pass = cfg.getString("mysql.password", "");
                boolean useSSL = cfg.getBoolean("mysql.useSSL", false);
                int poolSize = cfg.getInt("mysql.pool-size", 5);
                long timeout = cfg.getLong("mysql.connection-timeout-ms", 5000L);
                String extra = cfg.getString("mysql.extra-jdbc-params", "");
                if (extra.startsWith("?")) extra = extra.substring(1);

                StringBuilder url = new StringBuilder()
                        .append("jdbc:mysql://").append(host).append(":").append(port)
                        .append("/").append(db).append("?");
                if (!extra.isEmpty()) url.append(extra).append("&");
                url.append("useSSL=").append(useSSL);

                plugin.getLogger().info("Хранилище: MySQL (" + host + ":" + port + "/" + db + ")");
                return new SqlStorage(plugin, SqlStorage.Flavor.MYSQL, url.toString(), user, pass, poolSize, timeout, tablePrefix);
            }
            default -> {
                if (!type.equals("yaml")) plugin.getLogger().warning("Неизвестный тип '" + type + "', использую yaml.");
                else plugin.getLogger().info("Хранилище: YAML (playerdata.yml)");
                return new YamlStorage(plugin);
            }
        }
    }

    /**
     * Переносит данные из {@code playerdata.yml} в целевое хранилище (если включено).
     *
     * @param target целевое хранилище
     */
    public void migrateFromYamlIfNeeded(@NotNull StorageProvider target) {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("database");
        if (cfg == null || !cfg.getBoolean("migrate-from-yaml", true)) return;

        File yaml = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!yaml.exists() || target instanceof YamlStorage) return;

        plugin.getLogger().info("Обнаружен playerdata.yml — миграция данных...");
        try {
            YamlConfiguration oldCfg = YamlConfiguration.loadConfiguration(yaml);
            Map<UUID, PlayerData> migrated = new HashMap<>();

            ConfigurationSection eq = oldCfg.getConfigurationSection("equipped");
            if (eq != null) {
                for (String k : eq.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(k);
                        PlayerData d = migrated.computeIfAbsent(uuid, u -> new PlayerData());
                        d.setEquippedPrefixKey(oldCfg.getString("equipped." + k + ".prefix", ""));
                        d.setEquippedSuffixKey(oldCfg.getString("equipped." + k + ".suffix", ""));
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            ConfigurationSection ow = oldCfg.getConfigurationSection("owned");
            if (ow != null) {
                for (String uuidStr : ow.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        ConfigurationSection items = oldCfg.getConfigurationSection("owned." + uuidStr);
                        if (items == null) continue;
                        PlayerData d = migrated.computeIfAbsent(uuid, u -> new PlayerData());
                        for (String itemKey : items.getKeys(false)) d.getOwnership().put(itemKey, items.getLong(itemKey));
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            if (!migrated.isEmpty()) {
                target.saveAll(migrated);
                plugin.getLogger().info("Мигрировано игроков: " + migrated.size());
            }

            File backup = new File(plugin.getDataFolder(), "playerdata.yml.bak");
            if (backup.exists()) backup.delete();
            if (yaml.renameTo(backup)) plugin.getLogger().info("playerdata.yml → playerdata.yml.bak");
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка миграции: " + e.getMessage());
        }
    }
}
package me.demro.dprefixes.storage;

import me.demro.dprefixes.DPrefixes;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * YAML-хранилище. Все данные лежат в {@code playerdata.yml}.
 */
public class YamlStorage implements StorageProvider {

    private final DPrefixes plugin;
    private final File file;
    private FileConfiguration config;

    public YamlStorage(@NotNull DPrefixes plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playerdata.yml");
    }

    @Override
    public void init() throws Exception {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!file.exists()) file.createNewFile();
        config = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public void shutdown() {}

    @Override
    public boolean isPreloadAll() { return true; }

    @Override
    public @NotNull Map<UUID, PlayerData> loadAll() {
        Map<UUID, PlayerData> out = new HashMap<>();

        ConfigurationSection eq = config.getConfigurationSection("equipped");
        if (eq != null) {
            for (String k : eq.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(k);
                    PlayerData d = out.computeIfAbsent(uuid, u -> new PlayerData());
                    d.setEquippedPrefixKey(config.getString("equipped." + k + ".prefix", ""));
                    d.setEquippedSuffixKey(config.getString("equipped." + k + ".suffix", ""));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        ConfigurationSection ow = config.getConfigurationSection("owned");
        if (ow != null) {
            for (String uuidStr : ow.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection items = config.getConfigurationSection("owned." + uuidStr);
                    if (items == null) continue;
                    PlayerData d = out.computeIfAbsent(uuid, u -> new PlayerData());
                    for (String itemKey : items.getKeys(false)) {
                        d.getOwnership().put(itemKey, items.getLong(itemKey));
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return out;
    }

    @Override
    public @NotNull PlayerData loadPlayer(@NotNull UUID uuid) { return new PlayerData(); }

    @Override
    public void savePlayer(@NotNull UUID uuid, @NotNull PlayerData data) {}

    @Override
    public void saveAll(@NotNull Map<UUID, PlayerData> data) {
        config.set("prefixes", null);
        config.set("suffixes", null);
        config.set("equipped", null);
        config.set("owned", null);

        for (Map.Entry<UUID, PlayerData> e : data.entrySet()) {
            UUID uuid = e.getKey();
            PlayerData d = e.getValue();

            if (!d.getEquippedPrefixKey().isEmpty())
                config.set("equipped." + uuid + ".prefix", d.getEquippedPrefixKey());
            if (!d.getEquippedSuffixKey().isEmpty())
                config.set("equipped." + uuid + ".suffix", d.getEquippedSuffixKey());

            for (Map.Entry<String, Long> item : d.getOwnership().entrySet()) {
                config.set("owned." + uuid + "." + item.getKey(), item.getValue());
            }
        }
        try { config.save(file); } catch (IOException ex) { ex.printStackTrace(); }
    }
}
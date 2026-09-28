package me.demro.dprefixes.managers;

import me.demro.dprefixes.DPrefixes;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Менеджер паттернов — наборов ключевых слов для GigaChat.
 * Хранится в {@code patterns.yml}.
 */
public class PatternManager {

    /**
         * Иммутабельный паттерн.
         */
        public record Pattern(@NotNull String name, @NotNull String keywords, @NotNull String type) {
    }

    private final @NotNull DPrefixes plugin;
    private final @NotNull File file;
    private FileConfiguration config;
    private final Map<String, Pattern> patterns = new LinkedHashMap<>();

    public PatternManager(@NotNull DPrefixes plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "patterns.yml");
    }

    /** Загружает patterns.yml. */
    public void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        config = YamlConfiguration.loadConfiguration(file);
        patterns.clear();

        ConfigurationSection sec = config.getConfigurationSection("patterns");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            String kw = sec.getString(name + ".keywords", "");
            String type = sec.getString(name + ".type", "");
            patterns.put(name.toLowerCase(), new Pattern(name, kw, type));
        }
    }

    /** Сохраняет patterns.yml. */
    public void save() {
        config.set("patterns", null);
        for (Pattern p : patterns.values()) {
            config.set("patterns." + p.name() + ".keywords", p.keywords());
            config.set("patterns." + p.name() + ".type", p.type());
        }
        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * @param name имя паттерна
     * @return {@code true}, если паттерн существует
     */
    public boolean exists(@NotNull String name) { return patterns.containsKey(name.toLowerCase()); }

    /** @return паттерн или {@code null}. */
    public @Nullable Pattern get(@NotNull String name) { return patterns.get(name.toLowerCase()); }

    /**
     * @param name     имя
     * @param keywords ключевые слова
     * @param type     тип (prefix/suffix)
     */
    public void add(@NotNull String name, @NotNull String keywords, @NotNull String type) {
        patterns.put(name.toLowerCase(), new Pattern(name, keywords, type));
        save();
    }

    /** @param name имя паттерна. */
    public void remove(@NotNull String name) {
        patterns.remove(name.toLowerCase());
        save();
    }

    /** @return карта всех паттернов. */
    public @NotNull Map<String, Pattern> getAll() { return patterns; }

    /** @return количество паттернов. */
    public int size() { return patterns.size(); }
}
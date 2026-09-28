package me.demro.dprefixes.managers;

import me.demro.dprefixes.DPrefixes;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Менеджер звуков плагина.
 * <p>
 * Звуки настраиваются в {@code config.yml → sounds}. Каждый звук опционален
 * и имеет свой флаг {@code enabled}, имя {@link Sound}, громкость и тон.
 * <p>
 * Получение звука выполняется через {@link Registry#SOUNDS} — современный
 * способ, работающий на Paper 1.21.3+ (где {@code Sound} перестал быть enum).
 */
public class SoundManager {

    private final @NotNull DPrefixes plugin;

    private boolean enabled;
    private @Nullable SoundConfig menuOpen;
    private @Nullable SoundConfig purchase;
    private @Nullable SoundConfig purchaseFailed;

    public SoundManager(@NotNull DPrefixes plugin) {
        this.plugin = plugin;
    }

    /** Загружает настройки звуков из config.yml. */
    public void load() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("sounds");
        if (root == null) {
            enabled = false;
            return;
        }
        enabled = root.getBoolean("enabled", true);
        menuOpen = readSound(root.getConfigurationSection("menu-open"), "menu-open");
        purchase = readSound(root.getConfigurationSection("purchase"), "purchase");
        purchaseFailed = readSound(root.getConfigurationSection("purchase-failed"), "purchase-failed");
    }

    /**
     * Читает одну секцию звука.
     *
     * @param sec  секция в config.yml
     * @param path путь для логов (например, {@code "menu-open"})
     */
    private @Nullable SoundConfig readSound(@Nullable ConfigurationSection sec, @NotNull String path) {
        if (sec == null) return null;
        if (!sec.getBoolean("enabled", true)) return null;

        String soundName = sec.getString("sound", "");
        if (soundName.isEmpty()) return null;

        Sound sound = resolveSound(soundName);
        if (sound == null) {
            plugin.getLogger().warning("Неизвестный звук в config.yml (sounds." + path + "): " + soundName);
            return null;
        }

        float volume = (float) sec.getDouble("volume", 1.0);
        float pitch = (float) sec.getDouble("pitch", 1.0);
        return new SoundConfig(sound, volume, pitch);
    }

    /**
     * Преобразует строку в {@link Sound} через {@link Registry#SOUNDS}.
     * <p>
     * Поддерживаются форматы:
     * <ul>
     *   <li>Namespaced-ключ: {@code "block.note_block.harp"} или {@code "minecraft:block.note_block.harp"}</li>
     *   <li>Enum-стиль: {@code "BLOCK_NOTE_BLOCK_HARP"} (преобразуется в нижний регистр с точками)</li>
     * </ul>
     *
     * @param soundName имя звука из конфига
     * @return {@link Sound} или {@code null}, если звук не найден
     */
    private @Nullable Sound resolveSound(@NotNull String soundName) {
        NamespacedKey key = parseKey(soundName);
        if (key == null) return null;
        return Registry.SOUNDS.get(key);
    }

    /**
     * Разбирает строку в {@link NamespacedKey}.
     * <ul>
     *   <li>{@code "minecraft:block.note_block.harp"} → ключ как есть;</li>
     *   <li>{@code "block.note_block.harp"} → {@code minecraft:block.note_block.harp};</li>
     *   <li>{@code "BLOCK_NOTE_BLOCK_HARP"} → {@code minecraft:block.note_block.harp}.</li>
     * </ul>
     */
    private @Nullable NamespacedKey parseKey(@NotNull String soundName) {
        String trimmed = soundName.trim();
        if (trimmed.isEmpty()) return null;

        if (trimmed.contains(":")) {
            return NamespacedKey.fromString(trimmed.toLowerCase());
        }

        if (trimmed.contains(".")) {
            return NamespacedKey.minecraft(trimmed.toLowerCase());
        }

        String normalized = trimmed.toLowerCase().replace('_', '.');
        return NamespacedKey.minecraft(normalized);
    }

    /** Проигрывает звук открытия меню. */
    public void playMenuOpen(@NotNull Player player) {
        play(player, menuOpen);
    }

    /** Проигрывает звук успешной покупки. */
    public void playPurchase(@NotNull Player player) {
        play(player, purchase);
    }

    /** Проигрывает звук неудачной покупки. */
    public void playPurchaseFailed(@NotNull Player player) {
        play(player, purchaseFailed);
    }

    private void play(@NotNull Player player, @Nullable SoundConfig cfg) {
        if (!enabled || cfg == null) return;
        try {
            player.playSound(player.getLocation(), cfg.sound(), cfg.volume(), cfg.pitch());
        } catch (Exception ignored) {}
    }

    /** Внутренний контейнер настроек звука. */
    private record SoundConfig(@NotNull Sound sound, float volume, float pitch) {}
}
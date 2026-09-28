package me.demro.dprefixes.managers;

import me.demro.dprefixes.DPrefixes;
import me.demro.dprefixes.storage.DatabaseManager;
import me.demro.dprefixes.storage.PlayerData;
import me.demro.dprefixes.storage.StorageProvider;
import me.demro.dprefixes.storage.YamlStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер данных игроков. Хранит кэш в памяти и синхронизирует его с БД.
 */
public class PlayerDataManager {

    private final @NotNull DPrefixes plugin;
    private @Nullable StorageProvider storage;

    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(@NotNull DPrefixes plugin) {
        this.plugin = plugin;
    }

    /** Загружает хранилище и предзагружает данные (если нужно). */
    public void load() {
        DatabaseManager db = new DatabaseManager(plugin);
        storage = db.create();
        try {
            storage.init();
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка инициализации хранилища: " + e.getMessage());
            e.printStackTrace();
            if (!(storage instanceof YamlStorage)) {
                plugin.getLogger().warning("Откат на YAML-хранилище...");
                storage = new YamlStorage(plugin);
                try { storage.init(); } catch (Exception ignored) {}
            }
            return;
        }

        if (storage.isPreloadAll()) cache.putAll(storage.loadAll());
        db.migrateFromYamlIfNeeded(storage);
    }

    /** Финальное сохранение и закрытие соединений. */
    public void shutdown() {
        if (storage == null) return;
        try { storage.saveAll(cache); }
        catch (Exception e) { plugin.getLogger().warning("Ошибка финального сохранения: " + e.getMessage()); }
        storage.shutdown();
    }

    private @NotNull PlayerData ensureLoaded(@NotNull UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data != null) return data;
        PlayerData loaded = storage == null ? null : storage.loadPlayer(uuid);
        if (loaded == null) loaded = new PlayerData();
        cache.put(uuid, loaded);
        return loaded;
    }

    /** Асинхронно сохраняет игрока. */
    public void savePlayerAsync(@NotNull UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null || storage == null) return;
        PlayerData copy = data.copy();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try { storage.savePlayer(uuid, copy); }
            catch (Exception e) { plugin.getLogger().warning("Ошибка сохранения " + uuid + ": " + e.getMessage()); }
        });
    }

    /** Сохраняет всех закэшированных синхронно. */
    public void save() {
        if (storage == null) return;
        try { storage.saveAll(cache); }
        catch (Exception e) { plugin.getLogger().warning("Ошибка save(): " + e.getMessage()); }
    }

    /** Сохраняет и выгружает игрока из кэша. */
    public void unloadPlayer(@NotNull UUID uuid) {
        savePlayerAsync(uuid);
        cache.remove(uuid);
    }

    /** Асинхронно предзагружает данные игрока. */
    public void preloadAsync(@NotNull UUID uuid) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (cache.containsKey(uuid)) return;
            PlayerData data = storage == null ? null : storage.loadPlayer(uuid);
            if (data == null) data = new PlayerData();
            cache.put(uuid, data);
        });
    }

    public @NotNull String getEquippedPrefixKey(@NotNull UUID uuid) { return ensureLoaded(uuid).getEquippedPrefixKey(); }
    public @NotNull String getEquippedSuffixKey(@NotNull UUID uuid) { return ensureLoaded(uuid).getEquippedSuffixKey(); }

    public void setEquippedPrefix(@NotNull UUID uuid, @Nullable String itemKey) {
        PlayerData d = ensureLoaded(uuid);
        d.setEquippedPrefixKey(itemKey == null ? "" : itemKey);
        savePlayerAsync(uuid);
    }

    public void setEquippedSuffix(@NotNull UUID uuid, @Nullable String itemKey) {
        PlayerData d = ensureLoaded(uuid);
        d.setEquippedSuffixKey(itemKey == null ? "" : itemKey);
        savePlayerAsync(uuid);
    }

    public void clearEquippedPrefix(@NotNull UUID uuid) {
        ensureLoaded(uuid).setEquippedPrefixKey("");
        savePlayerAsync(uuid);
    }

    public void clearEquippedSuffix(@NotNull UUID uuid) {
        ensureLoaded(uuid).setEquippedSuffixKey("");
        savePlayerAsync(uuid);
    }

    /**
     * @param type    тип (prefix/suffix)
     * @param itemKey ключ предмета
     * @return составной ключ
     */
    public static @NotNull String fullKey(@NotNull String type, @NotNull String itemKey) {
        return type + ":" + itemKey;
    }

    public boolean hasOwned(@NotNull UUID uuid, @NotNull String type, @NotNull String itemKey) {
        Long expire = ensureLoaded(uuid).getOwnership().get(fullKey(type, itemKey));
        if (expire == null) return false;
        return expire == -1L || expire > System.currentTimeMillis();
    }

    public @Nullable Long getExpire(@NotNull UUID uuid, @NotNull String type, @NotNull String itemKey) {
        return ensureLoaded(uuid).getOwnership().get(fullKey(type, itemKey));
    }

    public boolean isExpired(@NotNull UUID uuid, @NotNull String type, @NotNull String itemKey) {
        Long expire = getExpire(uuid, type, itemKey);
        if (expire == null || expire == -1L) return false;
        return expire <= System.currentTimeMillis();
    }

    public void grant(@NotNull UUID uuid, @NotNull String type, @NotNull String itemKey, long expireAt) {
        ensureLoaded(uuid).getOwnership().put(fullKey(type, itemKey), expireAt);
        savePlayerAsync(uuid);
    }

    public void revoke(@NotNull UUID uuid, @NotNull String type, @NotNull String itemKey) {
        ensureLoaded(uuid).getOwnership().remove(fullKey(type, itemKey));
        savePlayerAsync(uuid);
    }

    /**
     * Полностью очищает владение и надетые предметы у игрока.
     * Используется административной командой {@code /dp pdel * / all}.
     *
     * @param uuid UUID игрока
     */
    public void clearAllOwnership(@NotNull UUID uuid) {
        PlayerData d = ensureLoaded(uuid);
        d.getOwnership().clear();
        d.setEquippedPrefixKey("");
        d.setEquippedSuffixKey("");
        savePlayerAsync(uuid);
    }

    public @NotNull Map<String, Long> getOwnedItems(@NotNull UUID uuid) {
        return ensureLoaded(uuid).getOwnership();
    }
}
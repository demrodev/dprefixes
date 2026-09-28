package me.demro.dprefixes.storage;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Абстракция хранилища данных игроков.
 */
public interface StorageProvider {

    /** Инициализация (открытие соединений, создание таблиц). */
    void init() throws Exception;

    /** Закрытие ресурсов. */
    void shutdown();

    /** @return {@code true}, если требуется загрузить всех игроков при старте. */
    default boolean isPreloadAll() { return false; }

    /** @return карта данных всех игроков (или пустая, если используется ленивая загрузка). */
    @NotNull Map<UUID, PlayerData> loadAll();

    /** @return данные конкретного игрока. */
    @NotNull PlayerData loadPlayer(@NotNull UUID uuid);

    /** Сохраняет данные одного игрока. */
    void savePlayer(@NotNull UUID uuid, @NotNull PlayerData data);

    /** Сохраняет всех игроков разом. */
    void saveAll(@NotNull Map<UUID, PlayerData> data);
}
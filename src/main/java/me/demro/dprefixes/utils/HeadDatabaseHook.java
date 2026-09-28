package me.demro.dprefixes.utils;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Безопасная интеграция с HeadDatabase через рефлексию.
 */
public final class HeadDatabaseHook {

    private static @Nullable Boolean enabled;
    private static @Nullable Object apiInstance;
    private static @Nullable Method mGetItemHead;

    private HeadDatabaseHook() {}

    public static boolean isAvailable() {
        if (enabled == null) {
            enabled = Bukkit.getPluginManager().getPlugin("HeadDatabase") != null;
        }
        return enabled;
    }

    /** Сбрасывает кэш (после reload). */
    public static void reset() {
        enabled = null;
        apiInstance = null;
        mGetItemHead = null;
    }

    private static boolean ensureInit() {
        if (!isAvailable()) return false;
        if (apiInstance != null && mGetItemHead != null) return true;
        try {
            Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
            apiInstance = apiClass.getDeclaredConstructor().newInstance();
            mGetItemHead = apiClass.getMethod("getItemHead", String.class);
            return true;
        } catch (Throwable t) {
            apiInstance = null;
            mGetItemHead = null;
            return false;
        }
    }

    /**
     * Возвращает голову из базы по её id.
     *
     * @param headId идентификатор головы (например, {@code "7129"})
     * @return {@link ItemStack} или {@code null}, если не найдено или HDB недоступен
     */
    public static @Nullable ItemStack getHead(@NotNull String headId) {
        if (!ensureInit()) return null;
        try {
            assert mGetItemHead != null;
            Object result = mGetItemHead.invoke(apiInstance, headId);
            return result instanceof ItemStack is ? is : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
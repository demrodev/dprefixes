package me.demro.dprefixes.utils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Утилита для работы с LuckPerms API.
 * <p>
 * Префиксы и суффиксы хранятся как узлы разрешений ({@link PrefixNode}/{@link SuffixNode})
 * прямо у пользователя. Это обеспечивает совместимость с любыми плагинами-форматтерами.
 */
public final class LuckPermsUtils {

    private static @Nullable LuckPerms luckPerms;

    private LuckPermsUtils() {}

    /** Инициализация — вызывается при старте плагина. */
    public static void init() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            luckPerms = null;
            return;
        }
        RegisteredServiceProvider<LuckPerms> provider =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) luckPerms = provider.getProvider();
    }

    /** @return {@code true}, если LuckPerms доступен. */
    public static boolean isAvailable() {
        return luckPerms != null;
    }

    /**
     * Устанавливает кастомный префикс игроку (заменяет существующие узлы префиксов).
     *
     * @param uuid   UUID игрока
     * @param prefix текст префикса
     * @param weight вес (приоритет)
     */
    public static void setPrefix(@NotNull UUID uuid, @NotNull String prefix, int weight) {
        if (luckPerms == null) return;
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) return;

        user.data().clear(node -> node instanceof PrefixNode);
        user.data().add(PrefixNode.builder(prefix, weight).build());
        luckPerms.getUserManager().saveUser(user);
    }

    /**
     * Устанавливает кастомный суффикс игроку.
     *
     * @param uuid   UUID игрока
     * @param suffix текст суффикса
     * @param weight вес (приоритет)
     */
    public static void setSuffix(@NotNull UUID uuid, @NotNull String suffix, int weight) {
        if (luckPerms == null) return;
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) return;

        user.data().clear(node -> node instanceof SuffixNode);
        user.data().add(SuffixNode.builder(suffix, weight).build());
        luckPerms.getUserManager().saveUser(user);
    }

    /**
     * Удаляет кастомный префикс — начнёт использоваться префикс группы.
     *
     * @param uuid UUID игрока
     */
    public static void clearPrefix(@NotNull UUID uuid) {
        if (luckPerms == null) return;
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) return;
        user.data().clear(node -> node instanceof PrefixNode);
        luckPerms.getUserManager().saveUser(user);
    }

    /**
     * Удаляет кастомный суффикс — начнёт использоваться суффикс группы.
     *
     * @param uuid UUID игрока
     */
    public static void clearSuffix(@NotNull UUID uuid) {
        if (luckPerms == null) return;
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) return;
        user.data().clear(node -> node instanceof SuffixNode);
        luckPerms.getUserManager().saveUser(user);
    }

    /**
     * @param uuid UUID игрока
     * @return {@code true}, если у игрока есть кастомный префикс
     */
    public static boolean hasCustomPrefix(@NotNull UUID uuid) {
        if (luckPerms == null) return false;
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) return false;
        for (Node node : user.data().toCollection()) if (node instanceof PrefixNode) return true;
        return false;
    }

    /**
     * @param uuid UUID игрока
     * @return {@code true}, если у игрока есть кастомный суффикс
     */
    public static boolean hasCustomSuffix(@NotNull UUID uuid) {
        if (luckPerms == null) return false;
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) return false;
        for (Node node : user.data().toCollection()) if (node instanceof SuffixNode) return true;
        return false;
    }

    /**
     * @param player игрок
     * @return эффективный префикс (кастомный или групповой)
     */
    public static @NotNull String getEffectivePrefix(@NotNull Player player) {
        if (luckPerms == null) return "";
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "";
        String s = user.getCachedData().getMetaData().getPrefix();
        return s == null ? "" : s;
    }

    /**
     * @param player игрок
     * @return эффективный суффикс (кастомный или групповой)
     */
    public static @NotNull String getEffectiveSuffix(@NotNull Player player) {
        if (luckPerms == null) return "";
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "";
        String s = user.getCachedData().getMetaData().getSuffix();
        return s == null ? "" : s;
    }
}
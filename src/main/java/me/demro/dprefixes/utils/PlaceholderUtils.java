package me.demro.dprefixes.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Утилита для работы с PlaceholderAPI и парсинга условий.
 * <p>
 * Доступ к PlaceholderAPI выполняется <b>через рефлексию</b>, чтобы класс
 * {@code me.clip.placeholderapi.PlaceholderAPI} не линковался при загрузке
 * класса. Это обеспечивает работу плагина на Spigot, Bukkit и Paper вне
 * зависимости от изоляции ClassLoader'ов.
 * <p>
 * Условие — строка вида {@code %placeholder% operator value}, где оператор
 * один из: {@code ==, !=, >, <, >=, <=, equals, equalsIgnoreCase, contains,
 * startsWith, endsWith}.
 */
public final class PlaceholderUtils {

    private static @Nullable Boolean papiEnabled;
    private static @Nullable Method mSetPlaceholdersPlayer;
    private static @Nullable Method mSetPlaceholdersOffline;
    private static @Nullable Method mSetPlaceholdersList;

    private PlaceholderUtils() {}

    /** @return {@code true}, если PlaceholderAPI установлен на сервере. */
    public static boolean isEnabled() {
        if (papiEnabled == null) {
            papiEnabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        }
        return papiEnabled;
    }

    /** Сбрасывает кэш (после {@code /dp reload}). */
    public static void reset() {
        papiEnabled = null;
        mSetPlaceholdersPlayer = null;
        mSetPlaceholdersOffline = null;
        mSetPlaceholdersList = null;
    }

    /**
     * Ленивая инициализация методов PlaceholderAPI через рефлексию.
     *
     * @return {@code true}, если методы найдены
     */
    private static boolean ensureReflection() {
        if (!isEnabled()) return true;
        if (mSetPlaceholdersPlayer != null) return false;

        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            mSetPlaceholdersPlayer = papi.getMethod("setPlaceholders", Player.class, String.class);
            mSetPlaceholdersOffline = papi.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            mSetPlaceholdersList = papi.getMethod("setPlaceholders", OfflinePlayer.class, List.class);
            return false;
        } catch (Throwable t) {
            mSetPlaceholdersPlayer = null;
            mSetPlaceholdersOffline = null;
            mSetPlaceholdersList = null;
            return true;
        }
    }

    /**
     * Парсит PAPI-плейсхолдеры в строке.
     *
     * @param player игрок
     * @param text   строка с плейсхолдерами
     * @return строка с подставленными значениями
     */
    public static @NotNull String parse(@NotNull Player player, @Nullable String text) {
        if (text == null || text.isEmpty()) return "";
        if (ensureReflection()) return text;
        try {
            Object result = null;
            if (mSetPlaceholdersPlayer != null) {
                result = mSetPlaceholdersPlayer.invoke(null, player, text);
            }
            return result instanceof String s ? s : text;
        } catch (Throwable t) {
            return text;
        }
    }

    /**
     * Парсит PAPI-плейсхолдеры в каждой строке списка.
     *
     * @param player игрок
     * @param list   список строк
     * @return новый список с подставленными значениями
     */
    @SuppressWarnings("unchecked")
    public static @NotNull List<String> parse(@NotNull Player player, @Nullable List<String> list) {
        if (list == null || list.isEmpty()) return list == null ? List.of() : list;
        if (ensureReflection()) return list;
        try {
            Object result = null;
            if (mSetPlaceholdersList != null) {
                result = mSetPlaceholdersList.invoke(null, player, list);
            }
            return result instanceof List<?> l ? (List<String>) l : list;
        } catch (Throwable t) {
            return list;
        }
    }

    /**
     * Резолвит одиночный плейсхолдер.
     *
     * @param player      игрок
     * @param placeholder строка вида {@code %vault_eco_balance%}
     * @return значение плейсхолдера
     */
    public static @NotNull String resolve(@NotNull OfflinePlayer player, @Nullable String placeholder) {
        if (placeholder == null || placeholder.isEmpty()) return "";
        if (ensureReflection()) return placeholder;
        try {
            Object result = null;
            if (mSetPlaceholdersOffline != null) {
                result = mSetPlaceholdersOffline.invoke(null, player, placeholder);
            }
            return result instanceof String s ? s : placeholder;
        } catch (Throwable t) {
            return placeholder;
        }
    }

    /**
     * Проверяет одно условие.
     *
     * @param player    игрок
     * @param condition строка условия
     * @return {@code true}, если условие выполнено
     */
    public static boolean checkCondition(@NotNull Player player, @Nullable String condition) {
        if (condition == null || condition.isBlank()) return false;

        String[] parts = splitCondition(condition.trim());
        if (parts == null || parts.length != 3) return false;

        String actual = resolve(player, parts[0]);
        return !compare(actual, parts[1], parts[2]);
    }

    /**
     * Проверяет список условий (логика AND).
     *
     * @param player     игрок
     * @param conditions список условий
     * @return {@code true}, если все условия выполнены (или список пуст)
     */
    public static boolean checkConditions(@NotNull Player player, @Nullable List<String> conditions) {
        if (conditions == null || conditions.isEmpty()) return true;
        for (String c : conditions) if (checkCondition(player, c)) return false;
        return true;
    }

    private static String @Nullable [] splitCondition(@NotNull String input) {
        String[] ops = {">=", "<=", "==", "!=", ">", "<",
                "equalsIgnoreCase", "equals", "contains", "startsWith", "endsWith"};
        for (String op : ops) {
            int idx = input.indexOf(op);
            if (idx > 0) {
                String left = input.substring(0, idx).trim();
                String right = input.substring(idx + op.length()).trim();
                if (!left.isEmpty() && !right.isEmpty()) return new String[]{left, op, right};
            }
        }
        return null;
    }

    private static boolean compare(@Nullable String actual, @NotNull String operator, @NotNull String expected) {
        if (actual == null) actual = "";
        String exp = expected;
        if (exp.length() >= 2
                && ((exp.startsWith("\"") && exp.endsWith("\""))
                || (exp.startsWith("'") && exp.endsWith("'")))) {
            exp = exp.substring(1, exp.length() - 1);
        }

        switch (operator) {
            case "==" -> {
                Double a = tryDouble(actual), b = tryDouble(exp);
                if (a != null && b != null) return a.doubleValue() == b.doubleValue();
                return actual.equals(exp);
            }
            case "!=" -> {
                Double a = tryDouble(actual), b = tryDouble(exp);
                if (a != null && b != null) return a.doubleValue() != b.doubleValue();
                return !actual.equals(exp);
            }
            case ">"  -> { Double a = tryDouble(actual), b = tryDouble(exp); return a != null && b != null && a > b; }
            case "<"  -> { Double a = tryDouble(actual), b = tryDouble(exp); return a != null && b != null && a < b; }
            case ">=" -> { Double a = tryDouble(actual), b = tryDouble(exp); return a != null && b != null && a >= b; }
            case "<=" -> { Double a = tryDouble(actual), b = tryDouble(exp); return a != null && b != null && a <= b; }
            case "equals" -> { return actual.equals(exp); }
            case "equalsIgnoreCase" -> { return actual.equalsIgnoreCase(exp); }
            case "contains" -> { return actual.contains(exp); }
            case "startsWith" -> { return actual.startsWith(exp); }
            case "endsWith" -> { return actual.endsWith(exp); }
            default -> { return false; }
        }
    }

    private static @Nullable Double tryDouble(@Nullable String s) {
        if (s == null) return null;
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException ex) { return null; }
    }
}
package me.demro.dprefixes.utils;

import net.md_5.bungee.api.ChatColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Общие утилиты: обработка цветовых кодов (включая HEX), парсинг длительностей,
 * форматирование дат.
 */
@SuppressWarnings("deprecation")
public final class Utils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([smhdw])");

    private Utils() {}

    /**
     * Преобразует цветовые коды (включая {@code &#RRGGBB}) в §-коды Minecraft.
     *
     * @param text исходный текст
     * @return окрашенный текст
     */
    public static @NotNull String color(@Nullable String text) {
        if (text == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, ChatColor.of("#" + matcher.group(1)).toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    /**
     * Окрашивает каждую строку списка.
     *
     * @param list исходный список
     * @return окрашенный список
     */
    public static @NotNull List<String> color(@Nullable List<String> list) {
        if (list == null) return new ArrayList<>();
        List<String> out = new ArrayList<>(list.size());
        for (String s : list) out.add(color(s));
        return out;
    }

    /**
     * Парсит длительность формата {@code 7d}, {@code 12h30m}, {@code 60s}.
     *
     * @param input строка длительности или {@code permanent}/{@code forever}/{@code навсегда}
     * @return количество миллисекунд или {@code -1} для «навсегда»
     */
    public static long parseDuration(@Nullable String input) {
        if (input == null) return -1;
        String s = input.trim().toLowerCase();
        if (s.isEmpty()
                || s.equals("permanent")
                || s.equals("forever")
                || s.equals("perm")
                || s.equals("навсегда")) {
            return -1L;
        }
        long total = 0;
        Matcher m = DURATION_PATTERN.matcher(s);
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            switch (m.group(2)) {
                case "s" -> total += n * 1000L;
                case "m" -> total += n * 60_000L;
                case "h" -> total += n * 3_600_000L;
                case "d" -> total += n * 86_400_000L;
                case "w" -> total += n * 604_800_000L;
            }
        }
        return total > 0 ? total : -1L;
    }

    /**
     * Форматирует epoch-время в читаемую дату.
     *
     * @param epochMs epoch миллисекунды
     * @return строка вида {@code 27.09.2026 18:30}
     */
    public static @NotNull String formatDate(long epochMs) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(epochMs));
    }

    /**
     * Человекочитаемое представление длительности.
     *
     * @param duration строка длительности
     * @return {@code 7 дн.}, {@code 12 ч.}, {@code навсегда} и т.п.
     */
    public static @NotNull String prettyDuration(@Nullable String duration) {
        long ms = parseDuration(duration);
        if (ms < 0) return "навсегда";
        long sec = ms / 1000;
        if (sec < 60) return sec + " сек.";
        long min = sec / 60;
        if (min < 60) return min + " мин.";
        long h = min / 60;
        if (h < 24) return h + " ч.";
        return (h / 24) + " дн.";
    }

    /**
     * Форматирует оставшееся время до истечения в компактный вид.
     * <p>
     * Примеры вывода:
     * <ul>
     *   <li>6 дн. 3 ч.</li>
     *   <li>2 ч. 15 мин.</li>
     *   <li>45 сек.</li>
     *   <li>&lt; 1 сек. (если истекло)</li>
     * </ul>
     *
     * @param expireAtMs момент истечения (epoch millis) или {@code -1} для навсегда
     * @return читаемая строка
     */
    public static @NotNull String formatTimeLeft(long expireAtMs) {
        if (expireAtMs <= 0) return "навсегда";

        long diffMs = expireAtMs - System.currentTimeMillis();
        if (diffMs <= 0) return "истёк";

        long totalSec = diffMs / 1000;
        long days = totalSec / 86_400;
        long hours = (totalSec % 86_400) / 3_600;
        long minutes = (totalSec % 3_600) / 60;
        long seconds = totalSec % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(" дн.");
        if (hours > 0) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(hours).append(" ч.");
        }
        if (minutes > 0 && days == 0) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(minutes).append(" мин.");
        }
        if (sb.isEmpty()) sb.append(seconds).append(" сек.");
        return sb.toString();
    }
}
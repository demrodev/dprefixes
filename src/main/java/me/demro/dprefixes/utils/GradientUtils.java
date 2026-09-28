package me.demro.dprefixes.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Генератор HEX-градиента для текста.
 * <p>
 * На каждый непробельный символ ставится свой цвет, распределяя переход
 * между несколькими HEX-цветами. Результат — строка с {@code &#RRGGBB}
 * перед каждым символом.
 */
public final class GradientUtils {

    private GradientUtils() {}

    /**
     * Применяет градиент к тексту.
     *
     * @param text   исходный текст без цветовых кодов
     * @param colors список HEX-цветов формата {@code #RRGGBB}
     * @return окрашенный текст
     */
    public static @NotNull String gradient(@Nullable String text, @Nullable List<String> colors) {
        if (text == null || text.isEmpty() || colors == null || colors.isEmpty()) return text == null ? "" : text;

        List<int[]> rgb = new ArrayList<>();
        for (String c : colors) {
            int[] parsed = hexToRgb(c);
            if (parsed != null) rgb.add(parsed);
        }
        if (rgb.isEmpty()) return text;

        if (rgb.size() == 1) {
            StringBuilder sb = new StringBuilder();
            String hex = toHex(rgb.getFirst());
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (Character.isWhitespace(ch)) sb.append(ch);
                else sb.append("&#").append(hex).append(ch);
            }
            return sb.toString();
        }

        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) idx.add(i);
        }
        if (idx.isEmpty()) return text;

        int total = idx.size();
        int segments = rgb.size() - 1;
        StringBuilder sb = new StringBuilder(text.length() * 10);
        int visibleIndex = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) { sb.append(ch); continue; }

            double t = total == 1 ? 0.0 : (double) visibleIndex / (total - 1);
            int seg = (int) Math.floor(t * segments);
            if (seg >= segments) seg = segments - 1;
            double localT = (t * segments) - seg;

            int[] a = rgb.get(seg);
            int[] b = rgb.get(seg + 1);
            int r = (int) Math.round(a[0] + (b[0] - a[0]) * localT);
            int g = (int) Math.round(a[1] + (b[1] - a[1]) * localT);
            int bl = (int) Math.round(a[2] + (b[2] - a[2]) * localT);

            sb.append("&#").append(toHex(new int[]{r, g, bl})).append(ch);
            visibleIndex++;
        }
        return sb.toString();
    }

    /**
     * Применяет случайный градиент из палитры.
     *
     * @param text    текст
     * @param palette список строк вида {@code "#A,#B,#C"}
     * @return окрашенный текст
     */
    public static @NotNull String gradientRandom(@Nullable String text, @Nullable List<String> palette) {
        if (text == null) return "";
        if (palette == null || palette.isEmpty()) return text;
        String pick = palette.get(ThreadLocalRandom.current().nextInt(palette.size()));
        return gradient(text, parsePaletteLine(pick));
    }

    /**
     * Разбирает строку палитры {@code "#A,#B,#C"} в список цветов.
     *
     * @param line строка палитры
     * @return список HEX-цветов
     */
    public static @NotNull List<String> parsePaletteLine(@Nullable String line) {
        List<String> out = new ArrayList<>();
        if (line == null || line.isEmpty()) return out;
        for (String s : line.split(",")) {
            String c = s.trim();
            if (!c.isEmpty()) out.add(c);
        }
        return out;
    }

    /**
     * Удаляет все символы, кроме букв, цифр и пробелов.
     *
     * @param input исходная строка
     * @return очищенная строка
     */
    public static @NotNull String stripNonText(@Nullable String input) {
        if (input == null) return "";
        String cleaned = input.replaceAll("[^\\p{L}\\p{N} ]", "").trim();
        return cleaned.replaceAll("\\s{2,}", " ");
    }

    private static int @Nullable [] hexToRgb(@Nullable String hex) {
        if (hex == null) return null;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() != 6) return null;
        try {
            int r = Integer.parseInt(h.substring(0, 2), 16);
            int g = Integer.parseInt(h.substring(2, 4), 16);
            int b = Integer.parseInt(h.substring(4, 6), 16);
            return new int[]{r, g, b};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static @NotNull String toHex(int @NotNull [] rgb) {
        return String.format("%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
    }
}
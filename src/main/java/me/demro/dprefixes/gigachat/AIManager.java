package me.demro.dprefixes.gigachat;

import me.demro.dprefixes.DPrefixes;
import me.demro.dprefixes.managers.PatternManager;
import me.demro.dprefixes.utils.GradientUtils;
import me.demro.dprefixes.utils.Utils;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Высокоуровневый менеджер AI-генерации префиксов и суффиксов.
 */
public class AIManager {

    private final @NotNull DPrefixes plugin;
    private @Nullable GigaChatClient client;

    public AIManager(@NotNull DPrefixes plugin) {
        this.plugin = plugin;
    }

    /**
     * Инициализация клиента по настройкам {@code gigachat}.
     * <p>
     * Пишет в консоль, что именно прочитано из конфига — чтобы диагностировать
     * проблемы с «выключено, хотя включено».
     */
    public void init() {
        FileConfiguration cfg = plugin.getConfig();
        boolean enabled = cfg.getBoolean("gigachat.enabled", false);

        if (!enabled) {
            client = null;
            plugin.getLogger().info("[GigaChat] Отключён в config.yml (gigachat.enabled=false).");
            return;
        }

        String key = cfg.getString("gigachat.authorization-key", "");
        try {
            client = new GigaChatClient(
                    plugin,
                    key,
                    cfg.getString("gigachat.scope", "GIGACHAT_API_PERS"),
                    cfg.getString("gigachat.model", "GigaChat"),
                    cfg.getString("gigachat.base-url", "https://api.giga.chat/v1"),
                    cfg.getString("gigachat.oauth-url", "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"),
                    cfg.getInt("gigachat.max-tokens", 500),
                    cfg.getDouble("gigachat.temperature", 0.9),
                    cfg.getInt("gigachat.timeout-seconds", 30)
            );
        } catch (Exception ex) {
            plugin.getLogger().warning("[GigaChat] Не удалось создать клиент: " + ex.getMessage());
            client = null;
            return;
        }

        if (client.isConfigured()) {
            plugin.getLogger().info("[GigaChat] Готов к работе.");
        } else {
            plugin.getLogger().warning("[GigaChat] Включён, но ключ авторизации не задан.");
        }
    }

    public boolean isEnabled() { return client != null; }

    public boolean isConfigured() { return client != null && client.isConfigured(); }

    /**
     * Асинхронно генерирует варианты и сохраняет их в {@code menus.yml}.
     *
     * @param type        {@code prefix} или {@code suffix}
     * @param patternName имя паттерна
     * @param count       количество
     * @return future со списком успешно добавленных вариантов
     */
    public @NotNull CompletableFuture<List<String>> generate(@NotNull String type,
                                                             @NotNull String patternName,
                                                             int count) {
        CompletableFuture<List<String>> result = new CompletableFuture<>();
        if (client == null) {
            result.completeExceptionally(new GigaChatException("GigaChat отключён."));
            return result;
        }

        PatternManager.Pattern pattern = plugin.getPatternManager().get(patternName);
        if (pattern == null) {
            result.completeExceptionally(new GigaChatException("Паттерн не найден: " + patternName));
            return result;
        }

        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        String prompt = cfg.getString("gigachat.prompt", "")
                .replace("%keywords%", pattern.keywords())
                .replace("%type%", type.equals("prefix") ? "префикс" : "суффикс")
                .replace("%count%", String.valueOf(count));

        client.generateAsync(prompt)
                .thenAccept(raw -> {
                    List<String> variants = GigaChatClient.parseVariants(raw);
                    if (variants.isEmpty()) {
                        result.completeExceptionally(new GigaChatException("Пустой ответ модели."));
                        return;
                    }
                    List<String> colored = applyGradients(variants);
                    List<String> added = addVariantsToMenu(type, colored);
                    if (added.isEmpty()) result.completeExceptionally(new GigaChatException("Не удалось добавить варианты."));
                    else result.complete(added);
                })
                .exceptionally(ex -> {
                    result.completeExceptionally(ex.getCause() != null ? ex.getCause() : ex);
                    return null;
                });

        return result;
    }

    private @NotNull List<String> applyGradients(@NotNull List<String> variants) {
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        if (!cfg.getBoolean("gigachat.gradient.enabled", true)) return variants;
        List<String> palette = cfg.getStringList("gigachat.gradient.palette");
        if (palette.isEmpty()) return variants;

        List<String> out = new ArrayList<>(variants.size());
        for (String v : variants) out.add(GradientUtils.gradientRandom(v, palette));
        return out;
    }

    private @NotNull List<String> addVariantsToMenu(@NotNull String type, @NotNull List<String> variants) {
        List<String> added = new ArrayList<>();
        FileConfiguration menus = plugin.getConfigManager().getMenusConfig();
        String menuKey = type.equals("prefix") ? "prefix" : "suffix";
        String base = "menus." + menuKey + ".items";
        if (menus.getConfigurationSection(base) == null) menus.createSection(base);

        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        String dGroup = cfg.getString("gigachat.defaults.group", "default");
        double dCost = cfg.getDouble("gigachat.defaults.cost", 0);
        String dPerm = cfg.getString("gigachat.defaults.permission", "");
        String dDuration = cfg.getString("gigachat.defaults.duration", "permanent");

        long baseId = System.currentTimeMillis();
        for (int i = 0; i < variants.size(); i++) {
            String value = variants.get(i);
            if (value == null || value.isEmpty()) continue;

            String path = base + ".ai_" + type + "_" + (baseId + i);
            menus.set(path + ".material", type.equals("prefix") ? "NAME_TAG" : "PAPER");
            menus.set(path + ".name", value);
            menus.set(path + ".lore", List.of(
                    "", "&7Значение: &r" + value,
                    "&7Группа: &f" + dGroup,
                    "&7Стоимость: &f" + dCost,
                    "&7Срок: &f" + Utils.prettyDuration(dDuration),
                    "", "%status%", ""));
            menus.set(path + (type.equals("prefix") ? ".prefix" : ".suffix"),
                    type.equals("prefix") ? value + " " : " " + value);
            menus.set(path + ".group", dGroup);
            menus.set(path + ".cost", dCost);
            menus.set(path + ".permission", dPerm.isEmpty() ? "" : dPerm);
            menus.set(path + ".duration", dDuration);
            added.add(value);
        }

        if (!added.isEmpty()) {
            plugin.getConfigManager().saveMenus();
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getConfigManager().reload());
        }
        return added;
    }

    public @Nullable GigaChatClient getClient() { return client; }
}
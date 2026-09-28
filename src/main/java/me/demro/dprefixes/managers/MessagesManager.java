package me.demro.dprefixes.managers;

import me.demro.dprefixes.DPrefixes;
import me.demro.dprefixes.utils.Utils;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Менеджер сообщений. Загружает {@code messages.yml}, подставляет префикс
 * плагина и плейсхолдеры, отправляет сообщения игрокам.
 * <p>
 * Поддерживает кликабельные ссылки: плейсхолдер {@code %url%} в шаблоне
 * может быть заменён на кликабельный текст, ведущий на URL.
 */
@SuppressWarnings("deprecation")
public class MessagesManager {

    private final @NotNull DPrefixes plugin;
    private FileConfiguration config;

    public MessagesManager(@NotNull DPrefixes plugin) {
        this.plugin = plugin;
    }

    /** Загружает messages.yml и дополняет его отсутствующими ключами. */
    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        config = YamlConfiguration.loadConfiguration(file);

        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                boolean changed = false;
                for (String key : defaults.getKeys(true)) {
                    if (defaults.isConfigurationSection(key)) continue;
                    if (!config.contains(key)) {
                        config.set(key, defaults.get(key));
                        changed = true;
                        plugin.getLogger().info("messages.yml: добавлен отсутствующий ключ " + key);
                    }
                }
                if (changed) {
                    try { config.save(file); }
                    catch (Exception e) { plugin.getLogger().warning("Не удалось сохранить messages.yml: " + e.getMessage()); }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка слияния messages.yml: " + e.getMessage());
        }
    }

    /** Перезагружает messages.yml. */
    public void reload() { load(); }

    private @NotNull String apply(@Nullable String msg, @NotNull String... replacements) {
        if (msg == null) return "";
        String prefix = config.getString("prefix", "");
        msg = msg.replace("%prefix%", prefix);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return msg;
    }

    public @NotNull String getRaw(@NotNull String path, @NotNull String... replacements) {
        return apply(config.getString(path, ""), replacements);
    }

    public @Nullable String getColoredOrNull(@NotNull String path, @NotNull String... replacements) {
        if (!config.contains(path)) return null;
        return Utils.color(getRaw(path, replacements));
    }

    public @NotNull String getColored(@NotNull String path, @NotNull String... replacements) {
        return Utils.color(getRaw(path, replacements));
    }

    public void send(@NotNull CommandSender sender, @NotNull String path, @NotNull String... replacements) {
        String msg = getColored(path, replacements);
        if (!msg.isEmpty()) sender.sendMessage(msg);
    }

    public @NotNull List<String> getColoredList(@NotNull String path, @NotNull String... replacements) {
        List<String> list = config.getStringList(path);
        List<String> out = new ArrayList<>(list.size());
        for (String s : list) out.add(Utils.color(apply(s, replacements)));
        return out;
    }

    /**
     * Отправляет игроку сообщение, в котором вместо плейсхолдера {@code %url%}
     * подставляется кликабельный текст:
     * <ul>
     *   <li>отображаемый текст — значение плейсхолдера {@code %url_text%}
     *       (или сам URL, если плейсхолдер не задан);</li>
     *   <li>клик по тексту открывает URL в браузере;</li>
     *   <li>при наведении показывается подсказка «Нажмите, чтобы открыть».</li>
     * </ul>
     *
     * @param player       получатель
     * @param path         путь к сообщению в messages.yml
     * @param url          URL для клика (может быть пустым)
     * @param urlText      отображаемый текст ссылки
     * @param replacements дополнительные пары плейсхолдер/значение
     */
    public void sendClickableUrl(@NotNull Player player,
                                 @NotNull String path,
                                 @Nullable String url,
                                 @NotNull String urlText,
                                 @NotNull String... replacements) {
        BaseComponent[] components = buildClickableUrl(path, url, urlText, replacements);
        if (components != null && components.length > 0) {
            player.spigot().sendMessage(components);
        }
    }

    /**
     * Собирает массив компонентов для сообщения с кликабельным URL.
     *
     * @return компоненты или {@code null}, если ключ отсутствует
     */
    public BaseComponent @Nullable [] buildClickableUrl(@NotNull String path,
                                                        @Nullable String url,
                                                        @NotNull String urlText,
                                                        @NotNull String... replacements) {
        if (!config.contains(path)) return null;

        String template = apply(config.getString(path, ""), replacements);

        String[] parts = template.split("%url%", -1);
        if (parts.length == 1) {
            return TextComponent.fromLegacyText(Utils.color(template));
        }

        ComponentBuilder builder = new ComponentBuilder();
        String prefix = config.getString("prefix", "");

        for (int i = 0; i < parts.length; i++) {
            String chunk = Utils.color(parts[i].replace("%prefix%", prefix));
            if (!chunk.isEmpty()) {
                builder.append(TextComponent.fromLegacyText(chunk), ComponentBuilder.FormatRetention.NONE);
            }

            if (i < parts.length - 1) {
                if (url != null && !url.isEmpty()) {
                    String display = Utils.color(urlText);
                    TextComponent link = new TextComponent(TextComponent.fromLegacyText(display));
                    link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
                    link.setHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new ComponentBuilder("Click to open " + url).create()
                    ));
                    builder.append(link, ComponentBuilder.FormatRetention.NONE);
                } else {
                    builder.append(new TextComponent(""), ComponentBuilder.FormatRetention.NONE);
                }
            }
        }

        return builder.create();
    }

    /** @return конфигурация messages.yml. */
    public @NotNull FileConfiguration getConfig() { return config; }
}
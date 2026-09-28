package me.demro.dprefixes.managers;

import me.demro.dlibs.DLibs;
import me.demro.dlibs.model.UpdateResult;
import me.demro.dprefixes.DPrefixes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Менеджер проверки обновлений через библиотеку dLibs.
 * <p>
 * Проверяет обновления:
 * <ul>
 *   <li>однократно при старте сервера;</li>
 *   <li>при каждом входе админа ({@code dprefixes.admin}) — с учётом кулдауна.</li>
 * </ul>
 * Уведомляет админов в чат и консоль (последняя версия или доступно обновление).
 */
@SuppressWarnings("deprecation")
public class UpdateManager {

    private final @NotNull DPrefixes plugin;

    private @Nullable DLibs dLibs;
    private @Nullable UpdateResult lastUpdateResult;
    private long lastUpdateCheckMillis = 0L;

    public UpdateManager(@NotNull DPrefixes plugin) {
        this.plugin = plugin;
    }

    /** @return результат последней проверки обновлений или {@code null}. */
    public @Nullable UpdateResult getLastUpdateResult() { return lastUpdateResult; }

    /**
     * Инициализация dLibs и однократная проверка при старте.
     */
    public void init() {
        if (!plugin.getConfig().getBoolean("update-check.enabled", true)) return;

        String repo = plugin.getConfig().getString("update-check.repo", "");
        if (repo.isEmpty()) {
            plugin.getLogger().warning("update-check.repo не задан — проверка обновлений отключена.");
            return;
        }

        dLibs = new DLibs(plugin);
        String currentVersion = plugin.getDescription().getVersion();

        dLibs.checkUpdatesAndLog(repo, currentVersion)
                .thenApply(result -> {
                    this.lastUpdateResult = result;
                    this.lastUpdateCheckMillis = System.currentTimeMillis();

                    if (result.hasError()) return result;

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.hasPermission("dprefixes.admin")) notifyAdmin(p, result);
                        }
                    });
                    return result;
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Ошибка при проверке обновлений: " + ex.getMessage());
                    return null;
                });
    }

    /**
     * Проверка обновлений при входе админа.
     * <p>
     * Если с последней проверки прошло меньше {@code cooldown-seconds} — используется
     * кэш без нового HTTP-запроса. В противном случае выполняется асинхронная проверка.
     * Админ получает сообщение: обновиться или что он на последней версии.
     *
     * @param player админ, которому нужно отправить уведомление
     */
    public void checkUpdatesForAdmin(@NotNull Player player) {
        if (!plugin.getConfig().getBoolean("update-check.enabled", true)) return;
        if (!plugin.getConfig().getBoolean("update-check.on-admin-join", true)) return;
        if (!player.hasPermission("dprefixes.admin")) return;
        if (!plugin.getConfig().getBoolean("update-check.notify-admins", true)) return;

        String repo = plugin.getConfig().getString("update-check.repo", "");
        if (repo.isEmpty() || dLibs == null) return;

        long cooldownMs = plugin.getConfig().getLong("update-check.cooldown-seconds", 300) * 1000L;
        long now = System.currentTimeMillis();

        if (now - lastUpdateCheckMillis < cooldownMs && lastUpdateResult != null) {
            notifyAdmin(player, lastUpdateResult);
            return;
        }

        String currentVersion = plugin.getDescription().getVersion();
        dLibs.checkUpdates(repo, currentVersion).thenAccept(result -> {
            this.lastUpdateResult = result;
            this.lastUpdateCheckMillis = System.currentTimeMillis();

            if (result.hasError()) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) notifyAdmin(player, result);
            });
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Ошибка при проверке обновлений для " + player.getName()
                    + ": " + ex.getMessage());
            return null;
        });
    }

    /**
     * Отправляет игроку уведомление о результате проверки обновлений.
     * <p>
     * Если доступно обновление — в сообщении {@code update.available} плейсхолдер
     * {@code %url%} заменяется на кликабельный текст (значение
     * {@code update-check.link-text}). Клик открывает страницу загрузки.
     * <p>
     * Если версия последняя — отправляется {@code update.latest}.
     */
    private void notifyAdmin(@NotNull Player player, @NotNull UpdateResult result) {
        MessagesManager messages = plugin.getMessagesManager();

        if (result.isUpdateAvailable()) {
            String url = result.getDownloadUrl() == null ? "" : result.getDownloadUrl();
            String linkText = plugin.getConfig().getString("update-check.link-text", "&a&n%latest%");
            linkText = linkText
                    .replace("%latest%", result.getLatestVersion() == null ? "" : result.getLatestVersion())
                    .replace("%current%", result.getCurrentVersion() == null ? "" : result.getCurrentVersion());

            messages.sendClickableUrl(player, "update.available", url, linkText,
                    "%current%", result.getCurrentVersion(),
                    "%latest%", result.getLatestVersion());
        } else {
            player.sendMessage(messages.getColored("update.latest",
                    "%current%", result.getCurrentVersion()));
        }
    }
}
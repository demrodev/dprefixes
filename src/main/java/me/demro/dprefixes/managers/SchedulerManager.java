package me.demro.dprefixes.managers;

import me.demro.dprefixes.DPrefixes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Менеджер планировщика плагина.
 * <p>
 * Отвечает за периодические задачи:
 * <ul>
 *   <li>проверку истечения срока владения префиксами/суффиксами.</li>
 * </ul>
 */
public class SchedulerManager {

    private final @NotNull DPrefixes plugin;

    public SchedulerManager(@NotNull DPrefixes plugin) {
        this.plugin = plugin;
    }

    /** Запускает все периодические задачи. */
    public void startAll() {
        startExpiryTask();
    }

    /** Задача проверки истечения срока у онлайн-игроков. */
    private void startExpiryTask() {
        long intervalTicks = Math.max(5L, plugin.getConfigManager().getExpiryCheckIntervalSeconds()) * 20L;

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    plugin.getMenuManager().checkExpiry(p);
                } catch (Exception ignored) {}
            }
        }, 20L * 30, intervalTicks);
    }
}
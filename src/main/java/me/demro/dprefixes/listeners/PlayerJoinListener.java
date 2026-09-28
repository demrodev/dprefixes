package me.demro.dprefixes.listeners;

import me.demro.dprefixes.DPrefixes;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Слушатель входа/выхода игроков.
 * <p>
 * При входе:
 * <ul>
 *   <li>асинхронно предзагружает данные игрока из БД;</li>
 *   <li>проверяет истечение срока префиксов/суффиксов;</li>
 *   <li>если игрок — админ ({@code dprefixes.admin}) — проверяет обновления
 *       плагина и уведомляет его в чат.</li>
 * </ul>
 * При выходе — сохраняет и выгружает данные игрока из кэша.
 */
public class PlayerJoinListener implements Listener {

    private final @NotNull DPrefixes plugin;

    public PlayerJoinListener(@NotNull DPrefixes plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        plugin.getPlayerDataManager().preloadAsync(event.getPlayer().getUniqueId());

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            plugin.getMenuManager().checkExpiry(event.getPlayer());
        }, 20L);

        if (event.getPlayer().hasPermission("dprefixes.admin")) {
            plugin.getUpdateManager().checkUpdatesForAdmin(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        plugin.getPlayerDataManager().unloadPlayer(event.getPlayer().getUniqueId());
    }
}
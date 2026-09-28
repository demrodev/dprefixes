package me.demro.dprefixes.listeners;

import me.demro.dprefixes.DPrefixes;
import me.demro.dprefixes.managers.MenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * Слушатель кликов по меню.
 */
public class MenuListener implements Listener {

    private final @NotNull DPrefixes plugin;

    public MenuListener(@NotNull DPrefixes plugin) {
        this.plugin = plugin;
    }

    /**
     * Обрабатывает клик по предмету в меню.
     *
     * @param event событие
     */
    @EventHandler
    public void onClick(@NotNull InventoryClickEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        if (!(topInv.getHolder() instanceof MenuHolder holder)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() != topInv) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        plugin.getMenuManager().handleClick(player, holder, event.getRawSlot());
    }

    /**
     * Отменяет перетаскивание предметов в меню.
     *
     * @param event событие
     */
    @EventHandler
    public void onDrag(@NotNull InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }
}
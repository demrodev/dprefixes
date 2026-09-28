package me.demro.dprefixes.managers;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Holder инвентаря меню. Хранит id меню, страницу и карту слот → предмет.
 */
@Getter
@RequiredArgsConstructor
public class MenuHolder implements InventoryHolder {

    private final @NotNull String menuId;
    private final int page;

    @Setter
    private @Nullable Inventory inventory;

    private final Map<Integer, MenuItem> slotItems = new HashMap<>();

    /**
     * @param slot слот
     * @param item предмет
     */
    public void setItem(int slot, @NotNull MenuItem item) { slotItems.put(slot, item); }

    /** @return предмет в слоте или {@code null}. */
    public @Nullable MenuItem getItem(int slot) { return slotItems.get(slot); }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("Inventory not set");
        return inventory;
    }
}
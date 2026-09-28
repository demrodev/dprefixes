package me.demro.dprefixes.managers;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Описание предмета меню.
 */
@Getter
@Setter
public class MenuItem {

    private String key = "";
    private int slot = -1;
    private List<Integer> slots = new ArrayList<>();
    private @Nullable String category = null;
    private @Nullable String head = null;

    private String material = "STONE";
    private String name = "";
    private List<String> lore = new ArrayList<>();
    private String action = "";
    private String prefix = "";
    private String suffix = "";
    private String group = "";
    private double cost = 0;
    private String permission = "";
    private String duration = "permanent";
    private List<String> conditions = new ArrayList<>();
    private boolean glow = false;
    private int customModelData = 0;

    public boolean isContent() { return !prefix.isEmpty() || !suffix.isEmpty(); }
    public boolean isDecorative() { return !isContent() && (action == null || action.isEmpty()); }
    public boolean isPrefix() { return !prefix.isEmpty(); }
    public boolean isSuffix() { return !suffix.isEmpty(); }
    public @NotNull String getKey() { return key; }

    public @NotNull List<Integer> getEffectiveSlots() {
        if (slots != null && !slots.isEmpty()) return slots;
        if (slot >= 0) return List.of(slot);
        return List.of();
    }
}
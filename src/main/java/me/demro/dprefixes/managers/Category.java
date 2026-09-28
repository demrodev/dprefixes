package me.demro.dprefixes.managers;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Категория префиксов/суффиксов внутри меню.
 */
@Getter
@Setter
public class Category {

    private final @NotNull String id;
    private String name = "";
    private String material = "CHEST";
    private @Nullable String head = null;
    private List<String> lore = new ArrayList<>();
    private int slot = -1;

    public Category(@NotNull String id) {
        this.id = id;
    }
}
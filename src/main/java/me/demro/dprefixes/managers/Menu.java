package me.demro.dprefixes.managers;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Описание меню: заголовок, размер, слоты контента, предметы, навигация, категории.
 */
@Getter
@Setter
@RequiredArgsConstructor
public class Menu {

    private final @NotNull String id;
    private String title = "";
    private String categoriesTitle = "";
    private int size = 27;
    private String type = "CHEST";
    private int itemsPerPage = 0;

    private final List<Integer> contentSlots = new ArrayList<>();
    private final List<MenuItem> items = new ArrayList<>();
    private final Map<String, MenuItem> navigation = new LinkedHashMap<>();
    private final Map<String, Category> categories = new LinkedHashMap<>();

    public boolean isPaginated() { return itemsPerPage > 0; }
    public boolean hasCategories() { return !categories.isEmpty(); }
    public void addItem(@NotNull MenuItem item) { items.add(item); }
    public void addNavItem(@NotNull String key, @NotNull MenuItem item) { navigation.put(key, item); }
    public void addCategory(@NotNull Category category) { categories.put(category.getId(), category); }
}
package me.demro.dprefixes.managers;

import me.demro.dprefixes.DPrefixes;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Менеджер конфигурации.
 * <p>
 * Для {@code config.yml} используется встроенный {@code getConfig()} из
 * родительского класса плагина — это гарантирует, что читается один и тот же
 * объект {@link FileConfiguration} во всех местах плагина.
 * Для {@code menus.yml} держим отдельный файл.
 */
public class ConfigManager {

    private final @NotNull DPrefixes plugin;
    private final @NotNull File menusFile;
    private final @NotNull FileConfiguration menusConfig;

    private final Map<String, Menu> menus = new LinkedHashMap<>();

    public ConfigManager(@NotNull DPrefixes plugin) {
        this.plugin = plugin;
        this.menusFile = new File(plugin.getDataFolder(), "menus.yml");
        if (!menusFile.exists()) plugin.saveResource("menus.yml", false);
        this.menusConfig = YamlConfiguration.loadConfiguration(menusFile);
    }

    /** Загружает конфигурацию. */
    public void load() {
        if (!new File(plugin.getDataFolder(), "config.yml").exists()) {
            plugin.saveDefaultConfig();
        }
        plugin.reloadConfig();
        loadMenus();
    }

    /** Перезагружает конфигурацию. */
    public void reload() {
        plugin.reloadConfig();

        FileConfiguration fresh = YamlConfiguration.loadConfiguration(menusFile);
        for (String key : fresh.getKeys(true)) {
            menusConfig.set(key, fresh.get(key));
        }
        menus.clear();
        loadMenus();
        plugin.getMessagesManager().reload();
    }

    private void loadMenus() {
        menus.clear();
        ConfigurationSection section = menusConfig.getConfigurationSection("menus");
        if (section == null) return;

        for (String menuKey : section.getKeys(false)) {
            ConfigurationSection mSec = section.getConfigurationSection(menuKey);
            if (mSec == null) continue;

            Menu menu = new Menu(menuKey);
            menu.setTitle(mSec.getString("title", "&0Меню"));
            menu.setCategoriesTitle(mSec.getString("categories-title", ""));
            menu.setSize(mSec.getInt("size", 27));
            menu.setType(mSec.getString("type", "CHEST"));
            menu.setItemsPerPage(mSec.getInt("items-per-page", 0));
            menu.getContentSlots().addAll(mSec.getIntegerList("content-slots"));

            ConfigurationSection catSec = mSec.getConfigurationSection("categories");
            if (catSec != null) {
                for (String catId : catSec.getKeys(false)) {
                    ConfigurationSection cSec = catSec.getConfigurationSection(catId);
                    if (cSec == null) continue;
                    Category cat = new Category(catId);
                    cat.setName(cSec.getString("name", catId));
                    cat.setMaterial(cSec.getString("material", "CHEST"));
                    cat.setHead(cSec.getString("head"));
                    cat.setLore(cSec.getStringList("lore"));
                    cat.setSlot(cSec.getInt("slot", -1));
                    menu.addCategory(cat);
                }
            }

            ConfigurationSection itemsSec = mSec.getConfigurationSection("items");
            if (itemsSec != null) {
                for (String itemKey : itemsSec.getKeys(false)) {
                    ConfigurationSection iSec = itemsSec.getConfigurationSection(itemKey);
                    if (iSec == null) continue;
                    menu.addItem(parseItem(itemKey, iSec));
                }
            }

            ConfigurationSection navSec = mSec.getConfigurationSection("navigation");
            if (navSec != null) {
                for (String navKey : navSec.getKeys(false)) {
                    ConfigurationSection nSec = navSec.getConfigurationSection(navKey);
                    if (nSec == null) continue;
                    MenuItem item = parseItem(navKey, nSec);
                    if (item.getAction() == null || item.getAction().isEmpty()) item.setAction(navKey);
                    menu.addNavItem(navKey, item);
                }
            }
            menus.put(menuKey, menu);
        }
    }

    private @NotNull MenuItem parseItem(@NotNull String key, @NotNull ConfigurationSection sec) {
        MenuItem item = new MenuItem();
        item.setKey(key);
        item.setCategory(sec.getString("category"));
        item.setHead(sec.getString("head"));

        if (sec.isList("slots")) {
            List<Integer> list = sec.getIntegerList("slots");
            item.setSlots(list);
            if (!list.isEmpty()) item.setSlot(list.getFirst());
        } else {
            int single = sec.getInt("slot", -1);
            item.setSlot(single);
            if (single >= 0) item.setSlots(List.of(single));
        }

        item.setMaterial(sec.getString("material", "STONE"));
        item.setName(sec.getString("name", "&f"));
        item.setLore(sec.getStringList("lore"));
        item.setAction(sec.getString("action", ""));
        item.setPrefix(sec.getString("prefix", ""));
        item.setSuffix(sec.getString("suffix", ""));
        item.setGroup(sec.getString("group", ""));
        item.setCost(sec.getDouble("cost", 0));
        item.setPermission(sec.getString("permission", ""));
        item.setDuration(sec.getString("duration", "permanent"));
        item.setConditions(sec.getStringList("conditions"));
        item.setGlow(sec.getBoolean("glow", false));
        item.setCustomModelData(sec.getInt("custom-model-data", 0));
        return item;
    }


    /**
     * Возвращает config.yml через встроенный метод плагина.
     *
     * @return конфигурация config.yml
     */
    public @NotNull FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public @NotNull FileConfiguration getMenusConfig() { return menusConfig; }
    public @NotNull File getMenusFile() { return menusFile; }

    public @Nullable Menu getMenu(@NotNull String id) { return menus.get(id); }
    public @NotNull Map<String, Menu> getMenus() { return menus; }

    public boolean isHighlightUnavailable() { return getConfig().getBoolean("settings.highlight-unavailable", true); }
    public boolean isShowRequirements() { return getConfig().getBoolean("settings.show-requirements", true); }
    public boolean isApplyOnJoin() { return getConfig().getBoolean("settings.apply-on-join", true); }
    public long getExpiryCheckIntervalSeconds() { return getConfig().getLong("settings.expiry-check-interval", 60); }
    public boolean isUsePlaceholderApi() { return getConfig().getBoolean("settings.use-placeholderapi", true); }
    public int getCustomPrefixWeight() { return getConfig().getInt("settings.custom-prefix-weight", 100); }

    public void saveMenus() {
        try { menusConfig.save(menusFile); } catch (Exception e) { e.printStackTrace(); }
    }
}
package me.demro.dprefixes.bootstrap;

import me.demro.dprefixes.DPrefixes;
import me.demro.dprefixes.commands.CommandHandler;
import me.demro.dprefixes.gigachat.AIManager;
import me.demro.dprefixes.listeners.MenuListener;
import me.demro.dprefixes.listeners.PlayerJoinListener;
import me.demro.dprefixes.managers.*;
import me.demro.dprefixes.utils.LuckPermsUtils;
import net.luckperms.api.LuckPerms;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Инициализатор плагина DPrefixes.
 * <p>
 * Вызывается из {@link DPrefixes#onEnable()}. Разбивает процесс на короткие
 * логические шаги: сохранение ресурсов, менеджеры, интеграции, команды,
 * слушатели, задачи, проверка обновлений.
 */
public final class PluginBootstrap {

    private final @NotNull DPrefixes plugin;

    public PluginBootstrap(@NotNull DPrefixes plugin) {
        this.plugin = plugin;
    }

    /** Полная инициализация плагина. */
    public void init() {
        saveResources();
        initManagers();
        initIntegrations();
        initCommands();
        initListeners();
        initTasks();
        initUpdates();

        plugin.getLogger().info("DPrefixes успешно включён!");
    }

    /** Сохраняет дефолтные ресурсы (messages.yml, menus.yml, patterns.yml). */
    private void saveResources() {
        plugin.saveDefaultConfig();
        for (String f : new String[]{"messages.yml", "menus.yml", "patterns.yml"}) {
            if (!new File(plugin.getDataFolder(), f).exists()) {
                plugin.saveResource(f, false);
            }
        }
    }

    /** Создаёт все менеджеры плагина и загружает данные. */
    private void initManagers() {
        plugin.setConfigManager(new ConfigManager(plugin));
        plugin.getConfigManager().load();

        plugin.setMessagesManager(new MessagesManager(plugin));
        plugin.getMessagesManager().load();

        plugin.setPlayerDataManager(new PlayerDataManager(plugin));
        plugin.getPlayerDataManager().load();

        plugin.setPatternManager(new PatternManager(plugin));
        plugin.getPatternManager().load();

        plugin.setMenuManager(new MenuManager(plugin));

        plugin.setSoundManager(new SoundManager(plugin));
        plugin.getSoundManager().load();

        plugin.setAiManager(new AIManager(plugin));
        plugin.getAiManager().init();
    }

    /** Подключает внешние сервисы: Vault, LuckPerms, PlaceholderAPI. */
    private void initIntegrations() {
        setupEconomy();
        setupLuckPerms();
        setupPlaceholderAPI();
        LuckPermsUtils.init();
    }

    /** Регистрирует команды /dp, /prefix, /suffix. */
    private void initCommands() {
        CommandHandler handler = new CommandHandler(plugin);

        PluginCommand dp = plugin.getCommand("dp");
        if (dp != null) {
            dp.setExecutor(handler);
            dp.setTabCompleter(handler);
        }
        PluginCommand prefix = plugin.getCommand("prefix");
        if (prefix != null) prefix.setExecutor(handler);
        PluginCommand suffix = plugin.getCommand("suffix");
        if (suffix != null) suffix.setExecutor(handler);
    }

    /** Регистрирует слушателей событий. */
    private void initListeners() {
        Bukkit.getPluginManager().registerEvents(new MenuListener(plugin), plugin);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(plugin), plugin);
    }

    /** Создаёт и запускает менеджер планировщика. */
    private void initTasks() {
        plugin.setSchedulerManager(new SchedulerManager(plugin));
        plugin.getSchedulerManager().startAll();
    }

    /** Создаёт и запускает менеджер обновлений (dLibs). */
    private void initUpdates() {
        plugin.setUpdateManager(new UpdateManager(plugin));
        plugin.getUpdateManager().init();
    }

    private void setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault не найден — экономика недоступна.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return;
        plugin.setEconomy(rsp.getProvider());
        assert plugin.getEconomy() != null;
        plugin.getLogger().info("Экономика подключена: " + plugin.getEconomy().getName());
    }

    private void setupLuckPerms() {
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger().warning("LuckPerms не найден.");
            return;
        }
        RegisteredServiceProvider<LuckPerms> rsp =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (rsp != null) {
            plugin.setLuckPerms(rsp.getProvider());
            plugin.getLogger().info("LuckPerms подключён.");
        }
    }

    private void setupPlaceholderAPI() {
        boolean enabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        plugin.setPlaceholderApiEnabled(enabled);
        if (enabled) plugin.getLogger().info("PlaceholderAPI подключён.");
    }
}
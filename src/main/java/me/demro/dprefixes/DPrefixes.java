package me.demro.dprefixes;

import lombok.Getter;
import lombok.Setter;
import me.demro.dprefixes.bootstrap.PluginBootstrap;
import me.demro.dprefixes.gigachat.AIManager;
import me.demro.dprefixes.managers.*;
import net.luckperms.api.LuckPerms;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Главный класс плагина DPrefixes.
 * <p>
 * Содержит только жизненный цикл плагина и ссылки на менеджеры.
 * Вся инициализация делегирована {@link PluginBootstrap}.
 */
@Getter
@Setter
public class DPrefixes extends JavaPlugin {

    private static DPrefixes instance;


    private SoundManager soundManager;
    private ConfigManager configManager;
    private MessagesManager messagesManager;
    private MenuManager menuManager;
    private PlayerDataManager playerDataManager;
    private PatternManager patternManager;
    private AIManager aiManager;
    private UpdateManager updateManager;
    private SchedulerManager schedulerManager;


    private @Nullable Economy economy;
    private @Nullable LuckPerms luckPerms;
    private boolean placeholderApiEnabled;

    @Override
    public void onEnable() {
        instance = this;
        new PluginBootstrap(this).init();
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) playerDataManager.shutdown();
        if (patternManager != null) patternManager.save();
        getLogger().info("DPrefixes выключен.");
    }

    /** @return singleton-экземпляр плагина. */
    public static @NotNull DPrefixes getInstance() { return instance; }
}
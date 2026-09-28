package me.demro.dprefixes.commands;

import me.demro.dprefixes.DPrefixes;
import me.demro.dprefixes.gigachat.AIManager;
import me.demro.dprefixes.managers.Menu;
import me.demro.dprefixes.managers.MenuItem;
import me.demro.dprefixes.managers.PatternManager;
import me.demro.dprefixes.utils.LuckPermsUtils;
import me.demro.dprefixes.utils.PlaceholderUtils;
import me.demro.dprefixes.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команд {@code /dp}, {@code /prefix}, {@code /suffix}.
 */
public class CommandHandler implements CommandExecutor, TabCompleter {

    private final @NotNull DPrefixes plugin;

    public CommandHandler(@NotNull DPrefixes plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        String name = command.getName().toLowerCase();

        switch (name) {
            case "prefix" -> {
                if (!(sender instanceof Player player)) {
                    plugin.getMessagesManager().send(sender, "general.player-only");
                    return true;
                }
                if (!player.hasPermission("dprefixes.use")) {
                    plugin.getMessagesManager().send(player, "general.no-permission");
                    return true;
                }
                openMenuSmart(player, "prefix");
                return true;
            }
            case "suffix" -> {
                if (!(sender instanceof Player player)) {
                    plugin.getMessagesManager().send(sender, "general.player-only");
                    return true;
                }
                if (!player.hasPermission("dprefixes.use")) {
                    plugin.getMessagesManager().send(player, "general.no-permission");
                    return true;
                }
                openMenuSmart(player, "suffix");
                return true;
            }
            case "dp" -> {
                if (args.length == 0) {
                    if (sender instanceof Player player) {
                        if (!player.hasPermission("dprefixes.use")) {
                            plugin.getMessagesManager().send(player, "general.no-permission");
                            return true;
                        }
                        openMenuSmart(player, "main");
                    } else sendHelp(sender);
                    return true;
                }

                switch (args[0].toLowerCase()) {
                    case "help" -> { sendHelp(sender); return true; }
                    case "reload" -> { handleReload(sender); return true; }
                    case "list" -> { handleList(sender, args); return true; }
                    case "add" -> { handleAdd(sender, args); return true; }
                    case "ai" -> { handleAi(sender, args); return true; }
                    case "padd" -> { handlePadd(sender, args); return true; }
                    case "pdel" -> { handlePdel(sender, args); return true; }
                    default -> { plugin.getMessagesManager().send(sender, "general.unknown-command"); return true; }
                }
            }
        }

        return false;
    }

    /**
     * Открывает меню: если у него есть категории — меню выбора категорий,
     * иначе — сразу список предметов.
     *
     * @param player игрок
     * @param menuId id меню
     */
    private void openMenuSmart(@NotNull Player player, @NotNull String menuId) {
        Menu menu = plugin.getConfigManager().getMenu(menuId);
        if (menu == null) return;
        if (menu.hasCategories()) {
            plugin.getMenuManager().openCategoryMenu(player, menuId);
        } else {
            plugin.getMenuManager().openMenu(player, menuId, 1, null);
        }
    }


    private void handlePadd(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("dprefixes.admin")) {
            plugin.getMessagesManager().send(sender, "general.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.getMessagesManager().send(sender, "commands.padd-usage");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.getMessagesManager().send(sender, "commands.player-not-found", "%player%", args[1]);
            return;
        }

        String itemId = args[2];
        MenuItem item = findMenuItem(itemId);
        if (item == null) {
            plugin.getMessagesManager().send(sender, "commands.padd-invalid-id", "%id%", itemId);
            return;
        }

        if (!LuckPermsUtils.isAvailable()) {
            plugin.getMessagesManager().send(sender, "menu.luckperms-missing");
            return;
        }

        boolean isPrefix = item.isPrefix();
        String type = isPrefix ? "prefix" : "suffix";
        var uuid = target.getUniqueId();

        long durationMs = Utils.parseDuration(item.getDuration());
        long expireAt = durationMs < 0 ? -1L : System.currentTimeMillis() + durationMs;
        plugin.getPlayerDataManager().grant(uuid, type, item.getKey(), expireAt);

        String parsedValue = PlaceholderUtils.parse(target, isPrefix ? item.getPrefix() : item.getSuffix());
        int weight = plugin.getConfigManager().getCustomPrefixWeight();

        if (isPrefix) {
            LuckPermsUtils.setPrefix(uuid, parsedValue, weight);
            plugin.getPlayerDataManager().setEquippedPrefix(uuid, item.getKey());
        } else {
            LuckPermsUtils.setSuffix(uuid, parsedValue, weight);
            plugin.getPlayerDataManager().setEquippedSuffix(uuid, item.getKey());
        }

        plugin.getMessagesManager().send(sender, "commands.padd-success",
                "%player%", target.getName(),
                "%type%", type,
                "%id%", item.getKey(),
                "%value%", parsedValue);

        if (!sender.equals(target)) {
            plugin.getMessagesManager().send(target, "commands.padd-received",
                    "%type%", type,
                    "%value%", parsedValue);
        }
    }

    private void handlePdel(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("dprefixes.admin")) {
            plugin.getMessagesManager().send(sender, "general.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.getMessagesManager().send(sender, "commands.pdel-usage");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.getMessagesManager().send(sender, "commands.player-not-found", "%player%", args[1]);
            return;
        }

        var uuid = target.getUniqueId();
        String itemId = args[2];

        if (itemId.equals("*") || itemId.equalsIgnoreCase("all")) {
            LuckPermsUtils.clearPrefix(uuid);
            LuckPermsUtils.clearSuffix(uuid);
            plugin.getPlayerDataManager().clearAllOwnership(uuid);

            plugin.getMessagesManager().send(sender, "commands.pdel-all-success",
                    "%player%", target.getName());

            if (!sender.equals(target)) {
                plugin.getMessagesManager().send(target, "commands.pdel-received-all");
            }
            return;
        }

        MenuItem item = findMenuItem(itemId);
        if (item == null) {
            plugin.getMessagesManager().send(sender, "commands.pdel-invalid-id", "%id%", itemId);
            return;
        }

        boolean isPrefix = item.isPrefix();
        String type = isPrefix ? "prefix" : "suffix";

        boolean owned = plugin.getPlayerDataManager().hasOwned(uuid, type, item.getKey());
        boolean equipped = isPrefix
                ? item.getKey().equals(plugin.getPlayerDataManager().getEquippedPrefixKey(uuid))
                : item.getKey().equals(plugin.getPlayerDataManager().getEquippedSuffixKey(uuid));

        if (!owned && !equipped) {
            plugin.getMessagesManager().send(sender, "commands.pdel-not-owned",
                    "%player%", target.getName(),
                    "%id%", item.getKey());
            return;
        }

        plugin.getPlayerDataManager().revoke(uuid, type, item.getKey());

        if (equipped) {
            if (isPrefix) {
                LuckPermsUtils.clearPrefix(uuid);
                plugin.getPlayerDataManager().clearEquippedPrefix(uuid);
            } else {
                LuckPermsUtils.clearSuffix(uuid);
                plugin.getPlayerDataManager().clearEquippedSuffix(uuid);
            }
        }

        plugin.getMessagesManager().send(sender, "commands.pdel-success",
                "%player%", target.getName(),
                "%type%", type,
                "%id%", item.getKey());

        if (!sender.equals(target)) {
            plugin.getMessagesManager().send(target, "commands.pdel-received",
                    "%type%", type);
        }
    }

    private @Nullable MenuItem findMenuItem(@NotNull String id) {
        Menu prefix = plugin.getConfigManager().getMenu("prefix");
        if (prefix != null) {
            for (MenuItem item : prefix.getItems()) {
                if (item.isPrefix() && item.getKey().equalsIgnoreCase(id)) return item;
            }
        }
        Menu suffix = plugin.getConfigManager().getMenu("suffix");
        if (suffix != null) {
            for (MenuItem item : suffix.getItems()) {
                if (item.isSuffix() && item.getKey().equalsIgnoreCase(id)) return item;
            }
        }
        return null;
    }

    private void aiStatus(@NotNull CommandSender sender) {
        FileConfiguration cfg = plugin.getConfig();
        boolean enabled = cfg.getBoolean("gigachat.enabled", false);
        String key = cfg.getString("gigachat.authorization-key", "");
        String scope = cfg.getString("gigachat.scope", "GIGACHAT_API_PERS");
        String model = cfg.getString("gigachat.model", "GigaChat");

        sender.sendMessage(Utils.color("&6--- GigaChat status ---"));
        sender.sendMessage(Utils.color("&7enabled: &f" + enabled));
        sender.sendMessage(Utils.color("&7authorization-key: &f"
                + (key.isEmpty() || key.equals("YOUR_AUTHORIZATION_KEY_HERE")
                ? "&cне задан"
                : "&aзадан (" + key.length() + " симв.)")));
        sender.sendMessage(Utils.color("&7scope: &f" + scope));
        sender.sendMessage(Utils.color("&7model: &f" + model));

        var ai = plugin.getAiManager();
        sender.sendMessage(Utils.color("&7AIManager enabled: &f" + ai.isEnabled()));
        sender.sendMessage(Utils.color("&7AIManager configured: &f" + ai.isConfigured()));
    }


    private void handleReload(@NotNull CommandSender sender) {
        if (!sender.hasPermission("dprefixes.admin")) { plugin.getMessagesManager().send(sender, "general.no-permission"); return; }
        PlaceholderUtils.reset();
        plugin.getConfigManager().reload();
        plugin.getPlayerDataManager().load();
        plugin.getPatternManager().load();
        plugin.getAiManager().init();
        plugin.getSoundManager().load();
        plugin.getUpdateManager().init();
        plugin.getMessagesManager().send(sender, "general.reloaded");
    }

    private void handleList(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("dprefixes.admin")) { plugin.getMessagesManager().send(sender, "general.no-permission"); return; }
        if (args.length < 2) { plugin.getMessagesManager().send(sender, "general.invalid-args"); return; }
        listEntries(sender, args[1].toLowerCase());
    }

    private void handleAi(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("dprefixes.admin")) { plugin.getMessagesManager().send(sender, "general.no-permission"); return; }
        if (args.length >= 2 && args[1].equalsIgnoreCase("status")) {
            aiStatus(sender);
            return;
        }
        if (!plugin.getAiManager().isEnabled()) {
            plugin.getMessagesManager().send(sender, "ai.disabled");
            sender.sendMessage(Utils.color("&7Проверьте: /dp ai status"));
            return;
        }
        if (!plugin.getAiManager().isConfigured()) { plugin.getMessagesManager().send(sender, "ai.no-key"); return; }
        if (args.length < 2) { plugin.getMessagesManager().send(sender, "ai.pattern-usage"); return; }

        switch (args[1].toLowerCase()) {
            case "pattern" -> aiCreatePattern(sender, args);
            case "generate" -> aiGenerate(sender, args);
            case "list" -> aiListPatterns(sender);
            case "remove" -> aiRemovePattern(sender, args);
            default -> plugin.getMessagesManager().send(sender, "general.unknown-command");
        }
    }

    private void aiCreatePattern(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        if (args.length < 4) { plugin.getMessagesManager().send(sender, "ai.pattern-usage"); return; }
        String name = args[2];
        String joined = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        String keywords = extractQuoted(joined);
        if (keywords == null) keywords = joined;
        if (keywords.isBlank()) { plugin.getMessagesManager().send(sender, "ai.pattern-usage"); return; }
        if (plugin.getPatternManager().exists(name)) { plugin.getMessagesManager().send(sender, "ai.pattern-exists"); return; }

        plugin.getPatternManager().add(name, keywords, "");
        plugin.getMessagesManager().send(sender, "ai.pattern-created", "%name%", name, "%keywords%", keywords);
    }

    private void aiGenerate(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        if (args.length < 4) { plugin.getMessagesManager().send(sender, "ai.generate-usage"); return; }
        String type = args[2].toLowerCase();
        if (!type.equals("prefix") && !type.equals("suffix")) { plugin.getMessagesManager().send(sender, "ai.generate-invalid-type"); return; }
        String patternName = args[3];
        if (!plugin.getPatternManager().exists(patternName)) {
            plugin.getMessagesManager().send(sender, "ai.pattern-not-found", "%name%", patternName);
            return;
        }
        int count = 5;
        if (args.length >= 5) {
            try { count = Math.max(1, Math.min(20, Integer.parseInt(args[4]))); } catch (NumberFormatException ignored) {}
        }

        plugin.getMessagesManager().send(sender, "ai.generate-start",
                "%count%", String.valueOf(count), "%type%", type, "%name%", patternName);

        AIManager ai = plugin.getAiManager();
        ai.generate(type, patternName, count)
                .thenAccept(added -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (added.isEmpty()) { plugin.getMessagesManager().send(sender, "ai.generate-empty"); return; }
                    plugin.getMessagesManager().send(sender, "ai.generate-success",
                            "%count%", String.valueOf(added.size()), "%type%", type);
                    sender.sendMessage(plugin.getMessagesManager().getColored("ai.generate-list-header"));
                    for (String v : added) {
                        sender.sendMessage(plugin.getMessagesManager().getColored("ai.generate-list-entry", "%value%", v));
                    }
                }))
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String msg = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            plugin.getMessagesManager().send(sender, "ai.error", "%error%", msg));
                    return null;
                });
    }

    private void aiListPatterns(@NotNull CommandSender sender) {
        var all = plugin.getPatternManager().getAll();
        if (all.isEmpty()) { plugin.getMessagesManager().send(sender, "ai.list-empty"); return; }
        sender.sendMessage(plugin.getMessagesManager().getColored("ai.list-header", "%count%", String.valueOf(all.size())));
        for (PatternManager.Pattern p : all.values()) {
            sender.sendMessage(plugin.getMessagesManager().getColored("ai.list-entry",
                    "%name%", p.name(), "%keywords%", p.keywords()));
        }
    }

    private void aiRemovePattern(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        if (args.length < 3) { plugin.getMessagesManager().send(sender, "ai.pattern-usage"); return; }
        String name = args[2];
        if (!plugin.getPatternManager().exists(name)) {
            plugin.getMessagesManager().send(sender, "ai.pattern-not-found", "%name%", name);
            return;
        }
        plugin.getPatternManager().remove(name);
        plugin.getMessagesManager().send(sender, "ai.pattern-removed", "%name%", name);
    }

    private @Nullable String extractQuoted(@NotNull String input) {
        int first = input.indexOf('"');
        if (first < 0) return null;
        int second = input.indexOf('"', first + 1);
        if (second < 0) return input.substring(first + 1).trim();
        return input.substring(first + 1, second);
    }

    private void handleAdd(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("dprefixes.admin")) { plugin.getMessagesManager().send(sender, "general.no-permission"); return; }
        if (args.length < 3) { plugin.getMessagesManager().send(sender, "commands.add-usage"); return; }

        String type = args[1].toLowerCase();
        if (!type.equals("prefix") && !type.equals("suffix")) { plugin.getMessagesManager().send(sender, "commands.add-invalid-type"); return; }

        String joined = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        String text;
        String paramsStr = "";

        int firstQuote = joined.indexOf('"');
        if (firstQuote >= 0) {
            int secondQuote = joined.indexOf('"', firstQuote + 1);
            if (secondQuote > firstQuote) {
                text = joined.substring(firstQuote + 1, secondQuote);
                paramsStr = joined.substring(secondQuote + 1).trim();
            } else text = joined.substring(firstQuote + 1).trim();
        } else {
            int space = joined.indexOf(' ');
            if (space > 0) { text = joined.substring(0, space); paramsStr = joined.substring(space + 1).trim(); }
            else text = joined;
        }

        if (text.isEmpty()) { plugin.getMessagesManager().send(sender, "commands.add-empty-text"); return; }

        Map<String, String> params = new HashMap<>();
        if (!paramsStr.isEmpty()) {
            for (String part : paramsStr.split(",")) {
                int idx = part.indexOf(':');
                if (idx > 0) {
                    String k = part.substring(0, idx).trim().toLowerCase();
                    String v = part.substring(idx + 1).trim();
                    if (!k.isEmpty() && !v.isEmpty()) params.put(k, v);
                }
            }
        }
        addEntry(sender, type, text, params);
    }

    private void sendHelp(@NotNull CommandSender sender) {
        for (String line : plugin.getMessagesManager().getColoredList("commands.help")) sender.sendMessage(line);
    }

    private void listEntries(@NotNull CommandSender sender, @NotNull String type) {
        String menuId;
        if (type.equals("prefixes") || type.equals("prefix")) menuId = "prefix";
        else if (type.equals("suffixes") || type.equals("suffix")) menuId = "suffix";
        else { plugin.getMessagesManager().send(sender, "general.invalid-args"); return; }

        Menu menu = plugin.getConfigManager().getMenu(menuId);
        if (menu == null) { plugin.getMessagesManager().send(sender, "commands.list-empty"); return; }

        boolean prefixType = type.startsWith("prefix");
        List<MenuItem> items = new ArrayList<>();
        for (MenuItem item : menu.getItems()) {
            if (prefixType && !item.isPrefix()) continue;
            if (!prefixType && !item.isSuffix()) continue;
            items.add(item);
        }
        if (items.isEmpty()) { plugin.getMessagesManager().send(sender, "commands.list-empty"); return; }

        sender.sendMessage(plugin.getMessagesManager().getColored("commands.list-header",
                "%type%", type, "%count%", String.valueOf(items.size())));

        for (MenuItem item : items) {
            sender.sendMessage(plugin.getMessagesManager().getColored("commands.list-entry",
                    "%name%", item.getKey(),
                    "%value%", prefixType ? item.getPrefix() : item.getSuffix(),
                    "%group%", item.getGroup().isEmpty() ? "-" : item.getGroup(),
                    "%cost%", String.valueOf(item.getCost()),
                    "%duration%", Utils.prettyDuration(item.getDuration())));
        }
    }

    private void addEntry(@NotNull CommandSender sender, @NotNull String type,
                          @NotNull String text, @NotNull Map<String, String> params) {
        FileConfiguration cfg = plugin.getConfigManager().getMenusConfig();
        String menuKey = type.equals("prefix") ? "prefix" : "suffix";
        String base = "menus." + menuKey + ".items";
        if (cfg.getConfigurationSection(base) == null) cfg.createSection(base);

        String path = base + "." + type + "_" + System.currentTimeMillis();
        String duration = params.getOrDefault("duration", "permanent");

        cfg.set(path + ".material", type.equals("prefix") ? "NAME_TAG" : "PAPER");
        cfg.set(path + ".name", text);
        cfg.set(path + ".lore", List.of(
                "", "&7Значение: &r" + text,
                "%group_line%", "%cost_line%", "%permission_line%", "%duration_line%",
                "", "%status%", ""));
        cfg.set(path + (type.equals("prefix") ? ".prefix" : ".suffix"),
                type.equals("prefix") ? text + " " : " " + text);

        cfg.set(path + ".group", params.getOrDefault("group", "default"));
        try { cfg.set(path + ".cost", Double.parseDouble(params.getOrDefault("cost", "0"))); }
        catch (NumberFormatException e) { cfg.set(path + ".cost", 0); }
        String perm = params.getOrDefault("permission", "none");
        cfg.set(path + ".permission", perm.equalsIgnoreCase("none") ? "" : perm);
        cfg.set(path + ".duration", duration);

        plugin.getConfigManager().saveMenus();
        plugin.getConfigManager().reload();
        plugin.getMessagesManager().send(sender, "commands.add-success", "%type%", type, "%value%", Utils.color(text));
    }


    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!command.getName().equalsIgnoreCase("dp")) return List.of();

        if (args.length == 1) {
            return filter(List.of("help", "reload", "list", "add", "ai", "padd", "pdel"), args[0]);
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("padd") || sub.equals("pdel")) {
            if (args.length == 2) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                return filter(names, args[1]);
            }
            if (args.length == 3) {
                return filter(getContentItemIds(sub.equals("pdel"), args[1]), args[2]);
            }
            return List.of();
        }

        if (args.length == 2) {
            return switch (sub) {
                case "add" -> filter(List.of("prefix", "suffix"), args[1]);
                case "list" -> filter(List.of("prefixes", "suffixes"), args[1]);
                case "ai" -> filter(List.of("pattern", "generate", "list", "remove", "status"), args[1]);
                default -> List.of();
            };
        }

        if (sub.equals("ai")) {
            String aiSub = args[1].toLowerCase();
            if (aiSub.equals("pattern")) {
                if (args.length == 3) return List.of("<имя>");
                if (args.length == 4) return filter(List.of("\"слово1, слово2, ...\""), args[3]);
                return List.of();
            }
            if (aiSub.equals("generate")) {
                if (args.length == 3) return filter(List.of("prefix", "suffix"), args[2]);
                if (args.length == 4) return filter(new ArrayList<>(plugin.getPatternManager().getAll().keySet()), args[3]);
                if (args.length == 5) return filter(List.of("3", "5", "10", "20"), args[4]);
                return List.of();
            }
            if (aiSub.equals("remove") && args.length == 3) {
                return filter(new ArrayList<>(plugin.getPatternManager().getAll().keySet()), args[2]);
            }
            return List.of();
        }

        if (!sub.equals("add")) return List.of();
        if (!args[1].equalsIgnoreCase("prefix") && !args[1].equalsIgnoreCase("suffix")) return List.of();

        if (args.length == 3) return filter(List.of("\"ваш текст\""), args[2]);
        String joined = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        long quotes = joined.chars().filter(c -> c == '"').count();
        if ((quotes % 2) == 1) return List.of();
        return filter(List.of("group:default", "cost:10", "permission:none",
                "duration:permanent", "duration:7d"), args[args.length - 1]);
    }

    private @NotNull List<String> getContentItemIds(boolean forPdel, @NotNull String nick) {
        List<String> ids = new ArrayList<>();

        if (forPdel) {
            Player target = Bukkit.getPlayerExact(nick);
            if (target != null) {
                for (String fullKey : plugin.getPlayerDataManager().getOwnedItems(target.getUniqueId()).keySet()) {
                    int idx = fullKey.indexOf(':');
                    if (idx > 0) ids.add(fullKey.substring(idx + 1));
                }
            }
            ids.add("*");
            ids.add("all");
        } else {
            Menu prefix = plugin.getConfigManager().getMenu("prefix");
            if (prefix != null) {
                for (MenuItem item : prefix.getItems()) if (item.isPrefix()) ids.add(item.getKey());
            }
            Menu suffix = plugin.getConfigManager().getMenu("suffix");
            if (suffix != null) {
                for (MenuItem item : suffix.getItems()) if (item.isSuffix()) ids.add(item.getKey());
            }
        }
        return ids;
    }

    private @NotNull List<String> filter(@NotNull List<String> options, @NotNull String prefix) {
        List<String> out = new ArrayList<>();
        for (String s : options) if (s.toLowerCase().startsWith(prefix.toLowerCase())) out.add(s);
        return out;
    }
}
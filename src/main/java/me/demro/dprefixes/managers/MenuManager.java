package me.demro.dprefixes.managers;

import lombok.Getter;
import me.demro.dprefixes.DPrefixes;
import me.demro.dprefixes.utils.HeadDatabaseHook;
import me.demro.dprefixes.utils.LuckPermsUtils;
import me.demro.dprefixes.utils.PlaceholderUtils;
import me.demro.dprefixes.utils.Utils;
import net.luckperms.api.model.user.User;
import net.milkbowl.vault.economy.Economy;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Менеджер меню: построение, обработка кликов, проверка условий, установка префиксов/суффиксов.
 * <p>
 * <b>Важно:</b> условия (permission, group, cost, PAPI) проверяются только
 * в момент <b>покупки</b> префикса/суффикса. Если предмет уже куплен —
 * условия игнорируются, и предмет можно свободно надеть повторно.
 * <p>
 * Поддерживаемые плейсхолдеры в name/lore:
 * <ul>
 *   <li>{@code %page%}, {@code %total%} — пагинация;</li>
 *   <li>{@code %status%} — статус предмета;</li>
 *   <li>{@code %expire%} — дата окончания владения;</li>
 *   <li>{@code %time_left%} — оставшееся время до истечения;</li>
 *   <li>{@code %cost_line%}, {@code %group_line%}, {@code %permission_line%}, {@code %duration_line%};</li>
 *   <li>любые PAPI-плейсхолдеры.</li>
 * </ul>
 * <p>
 * Купленные предметы автоматически подсвечиваются зачарованием (glow).
 * Если у предмета задан {@code head}, он берётся из HeadDatabase.
 * Меню с секцией {@code categories} открываются через {@link #openCategoryMenu}.
 */
@SuppressWarnings("deprecation")
public class MenuManager {

    private final @NotNull DPrefixes plugin;

    public MenuManager(@NotNull DPrefixes plugin) {
        this.plugin = plugin;
    }

    /**
     * Открывает меню игроку (без фильтра по категории).
     *
     * @param player игрок
     * @param menuId id меню
     * @param page   страница (с 1)
     */
    public void openMenu(@NotNull Player player, @NotNull String menuId, int page) {
        Menu menu = plugin.getConfigManager().getMenu(menuId);
        if (menu == null) return;

        if (menu.hasCategories()) {
            openCategoryMenu(player, menuId);
            return;
        }
        openMenu(player, menuId, page, null);
    }

    /**
     * Открывает меню выбора категорий.
     * <p>
     * Заголовок берётся из {@code categories-title}, если он задан в меню,
     * иначе — из {@code title} с удалением плейсхолдеров пагинации
     * ({@code [%page%/%total%]}, {@code %page%}, {@code %total%}).
     *
     * @param player игрок
     * @param menuId id меню
     */
    public void openCategoryMenu(@NotNull Player player, @NotNull String menuId) {
        Menu menu = plugin.getConfigManager().getMenu(menuId);
        if (menu == null) return;

        if (!menu.hasCategories()) {
            openMenu(player, menuId, 1, null);
            return;
        }

        String rawTitle = menu.getCategoriesTitle();
        if (rawTitle == null || rawTitle.isEmpty()) {
            rawTitle = stripPagePlaceholders(menu.getTitle());
        }
        String title = parsePapi(player, rawTitle);

        MenuHolder holder = new MenuHolder(menuId, 0);
        Inventory inv = Bukkit.createInventory(holder, menu.getSize(), Utils.color(title));
        holder.setInventory(inv);

        int idx = 0;
        for (Category cat : menu.getCategories().values()) {
            int slot = cat.getSlot() >= 0 ? cat.getSlot() : idx;
            if (slot < 0 || slot >= menu.getSize()) { idx++; continue; }

            inv.setItem(slot, buildCategoryIcon(cat));

            MenuItem proxy = new MenuItem();
            proxy.setKey("category:" + cat.getId());
            proxy.setAction("open-category:" + cat.getId());
            holder.setItem(slot, proxy);
            idx++;
        }

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta bm = back.getItemMeta();
        if (bm != null) {
            bm.setDisplayName(Utils.color("&cНазад"));
            bm.setLore(List.of(Utils.color("&7Вернуться в главное меню")));
            back.setItemMeta(bm);
        }
        int backSlot = menu.getSize() - 1;
        inv.setItem(backSlot, back);

        MenuItem backProxy = new MenuItem();
        backProxy.setKey("nav:back");
        backProxy.setAction("back");
        holder.setItem(backSlot, backProxy);

        player.openInventory(inv);
        plugin.getSoundManager().playMenuOpen(player);
    }

    /**
     * Убирает из заголовка плейсхолдеры пагинации:
     * {@code [%page%/%total%]}, {@code [%total%/%page%]}, {@code %page%}, {@code %total%}.
     * Также убирает лишние пробелы, оставшиеся после удаления.
     *
     * @param title исходный заголовок
     * @return очищенный заголовок
     */
    private @NotNull String stripPagePlaceholders(@Nullable String title) {
        if (title == null) return "";
        return title
                .replaceAll("\\s*\\[\\s*%page%\\s*/\\s*%total%\\s*]\\s*", " ")
                .replaceAll("\\s*\\[\\s*%total%\\s*/\\s*%page%\\s*]\\s*", " ")
                .replace("%page%", "")
                .replace("%total%", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    /**
     * Открывает меню с фильтром по категории.
     *
     * @param player     игрок
     * @param menuId     id меню
     * @param page       страница (с 1)
     * @param categoryId id категории или {@code null} для всех предметов
     */
    public void openMenu(@NotNull Player player, @NotNull String menuId, int page, @Nullable String categoryId) {
        Menu menu = plugin.getConfigManager().getMenu(menuId);
        if (menu == null) return;

        List<MenuItem> contentItems = new ArrayList<>();
        List<MenuItem> staticItems = new ArrayList<>();
        for (MenuItem item : menu.getItems()) {
            if (item.isContent()) {
                if (categoryId == null || categoryId.equals(item.getCategory())) {
                    contentItems.add(item);
                }
            } else {
                staticItems.add(item);
            }
        }

        int perPage = menu.isPaginated() ? menu.getItemsPerPage() : contentItems.size();
        if (perPage <= 0) perPage = Math.max(1, contentItems.size());
        int totalPages = menu.isPaginated()
                ? Math.max(1, (int) Math.ceil((double) contentItems.size() / perPage))
                : 1;
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        String rawTitle = menu.getTitle()
                .replace("%page%", String.valueOf(page))
                .replace("%total%", String.valueOf(totalPages));
        String title = parsePapi(player, rawTitle);

        MenuHolder holder = new MenuHolder(menuId, page);
        Inventory inv = Bukkit.createInventory(holder, menu.getSize(), Utils.color(title));
        holder.setInventory(inv);

        for (MenuItem item : staticItems) {
            for (int s : item.getEffectiveSlots()) {
                if (s < 0 || s >= menu.getSize()) continue;
                inv.setItem(s, buildItem(player, item, page, totalPages, true));
                holder.setItem(s, item);
            }
        }

        if (!contentItems.isEmpty()) {
            List<Integer> contentSlots = menu.getContentSlots();
            int start = menu.isPaginated() ? (page - 1) * perPage : 0;
            int end = Math.min(start + perPage, contentItems.size());
            for (int i = start; i < end; i++) {
                int idx = i - start;
                if (idx >= contentSlots.size()) break;
                int slot = contentSlots.get(idx);
                MenuItem item = contentItems.get(i);
                inv.setItem(slot, buildItem(player, item, page, totalPages, true));
                holder.setItem(slot, item);
            }
        }

        for (var entry : menu.getNavigation().entrySet()) {
            String navKey = entry.getKey();
            MenuItem navItem = entry.getValue();
            if (!shouldShowNav(menuId, navKey, navItem, page, totalPages, player)) continue;
            for (int s : navItem.getEffectiveSlots()) {
                if (s < 0 || s >= menu.getSize()) continue;
                inv.setItem(s, buildItem(player, navItem, page, totalPages, false));
                holder.setItem(s, navItem);
            }
        }

        if (menu.hasCategories() && categoryId != null) {
            ItemStack back = new ItemStack(Material.BOOK);
            ItemMeta bm = back.getItemMeta();
            if (bm != null) {
                bm.setDisplayName(Utils.color("&eК категориям"));
                bm.setLore(List.of(Utils.color("&7Выбор другой категории")));
                back.setItemMeta(bm);
            }
            int backSlot = menu.getSize() - 2;
            inv.setItem(backSlot, back);

            MenuItem backProxy = new MenuItem();
            backProxy.setKey("nav:categories");
            backProxy.setAction("back-to-categories");
            holder.setItem(backSlot, backProxy);
        }

        player.openInventory(inv);
        plugin.getSoundManager().playMenuOpen(player);
    }

    private boolean shouldShowNav(@NotNull String menuId, @NotNull String navKey, @NotNull MenuItem navItem,
                                  int page, int totalPages, @NotNull Player player) {
        String action = navItem.getAction();
        UUID uuid = player.getUniqueId();
        if ("remove-prefix".equals(action)) return LuckPermsUtils.hasCustomPrefix(uuid);
        if ("remove-suffix".equals(action)) return LuckPermsUtils.hasCustomSuffix(uuid);
        return switch (navKey) {
            case "prev-page" -> page > 1;
            case "next-page" -> page < totalPages;
            case "back" -> !menuId.equals("main");
            default -> true;
        };
    }

    private @NotNull ItemStack buildCategoryIcon(@NotNull Category cat) {
        ItemStack stack;
        if (cat.getHead() != null && !cat.getHead().isEmpty()) {
            ItemStack head = HeadDatabaseHook.getHead(cat.getHead());
            stack = head != null ? head : new ItemStack(Material.PLAYER_HEAD);
        } else {
            Material mat = Material.matchMaterial(cat.getMaterial());
            stack = new ItemStack(mat == null ? Material.CHEST : mat);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Utils.color(cat.getName()));
            if (!cat.getLore().isEmpty()) meta.setLore(Utils.color(cat.getLore()));
            stack.setItemMeta(meta);
        }
        return stack;
    }


    private @NotNull ItemStack buildItem(@NotNull Player player, @NotNull MenuItem item,
                                         int page, int totalPages, boolean checkAvailability) {
        ItemStack stack;
        if (item.getHead() != null && !item.getHead().isEmpty()) {
            ItemStack head = HeadDatabaseHook.getHead(item.getHead());
            stack = head != null ? head : new ItemStack(Material.PLAYER_HEAD);
        } else {
            Material mat = Material.matchMaterial(item.getMaterial());
            if (mat == null) mat = Material.STONE;
            stack = new ItemStack(mat);
        }

        boolean isContent = item.isContent();
        boolean owned = isContent && isOwned(player, item);

        boolean available = !checkAvailability
                || owned
                || checkConditions(player, item).isPass();

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        String rawName = replacePage(item.getName(), page, totalPages);
        meta.setDisplayName(Utils.color(parsePapi(player, rawName)));

        boolean highlight = plugin.getConfigManager().isHighlightUnavailable();
        boolean showReqs = plugin.getConfigManager().isShowRequirements();

        String status         = isContent ? buildStatus(player, item) : "";
        String expireStr      = isContent ? formatExpire(player, item) : "";
        String timeLeftStr    = isContent ? resolveTimeLeft(player, item) : "";
        String costLine       = isContent ? buildCostLine(item) : "";
        String groupLine      = isContent ? buildGroupLine(item) : "";
        String permissionLine = isContent ? buildPermissionLine(item) : "";
        String durationLine   = isContent ? buildDurationLine(item) : "";

        List<String> lore = new ArrayList<>();
        for (String line : item.getLore()) {
            if (line.contains("%time_left%")       && timeLeftStr.isEmpty())    continue;
            if (line.contains("%expire%")          && expireStr.isEmpty())       continue;
            if (line.contains("%group_line%")      && groupLine.isEmpty())       continue;
            if (line.contains("%permission_line%") && permissionLine.isEmpty())  continue;

            String substituted = replacePage(line, page, totalPages)
                    .replace("%status%", status)
                    .replace("%expire%", expireStr)
                    .replace("%time_left%", timeLeftStr)
                    .replace("%cost_line%", costLine)
                    .replace("%group_line%", groupLine)
                    .replace("%permission_line%", permissionLine)
                    .replace("%duration_line%", durationLine);
            String colored = Utils.color(parsePapi(player, substituted));

            if (!available && highlight) {
                String stripped = ChatColor.stripColor(colored);
                if (stripped.trim().isEmpty()) lore.add(colored);
                else lore.add(ChatColor.RED + stripped);
            } else {
                lore.add(colored);
            }
        }

        if (!available && showReqs) appendRequirements(player, item, lore);

        meta.setLore(lore);

        if (item.isGlow() || owned) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (item.getCustomModelData() > 0) meta.setCustomModelData(item.getCustomModelData());
        stack.setItemMeta(meta);
        return stack;
    }

    private void appendRequirements(@NotNull Player player, @NotNull MenuItem item, @NotNull List<String> lore) {
        lore.add("");
        lore.add(plugin.getMessagesManager().getColored("requirements.header"));
        if (!item.getGroup().isEmpty() && hasGroup(player, item.getGroup()))
            lore.add(plugin.getMessagesManager().getColored("requirements.group", "%group%", item.getGroup()));
        if (item.getCost() > 0 && hasFunds(player, item.getCost()))
            lore.add(plugin.getMessagesManager().getColored("requirements.cost", "%cost%", String.valueOf(item.getCost())));
        if (!item.getPermission().isEmpty() && !player.hasPermission(item.getPermission()))
            lore.add(plugin.getMessagesManager().getColored("requirements.permission", "%permission%", item.getPermission()));
        for (String condition : item.getConditions()) {
            if (PlaceholderUtils.checkCondition(player, condition)) {
                String[] parts = splitConditionForDisplay(condition);
                if (parts != null) {
                    lore.add(plugin.getMessagesManager().getColored("requirements.placeholder",
                            "%placeholder%", parts[0], "%operator%", parts[1], "%value%", parts[2]));
                }
            }
        }
    }

    private @NotNull String parsePapi(@NotNull Player player, @NotNull String text) {
        if (!plugin.getConfigManager().isUsePlaceholderApi()) return text;
        return PlaceholderUtils.parse(player, text);
    }

    private String @Nullable [] splitConditionForDisplay(@NotNull String condition) {
        String[] ops = {">=", "<=", "==", "!=", ">", "<", "equalsIgnoreCase", "equals", "contains", "startsWith", "endsWith"};
        for (String op : ops) {
            int idx = condition.indexOf(op);
            if (idx > 0) return new String[]{
                    condition.substring(0, idx).trim(),
                    op,
                    condition.substring(idx + op.length()).trim()
            };
        }
        return null;
    }

    private @NotNull String buildStatus(@NotNull Player player, @NotNull MenuItem item) {
        UUID uuid = player.getUniqueId();
        String type = item.isPrefix() ? "prefix" : "suffix";

        Long expire = plugin.getPlayerDataManager().getExpire(uuid, type, item.getKey());
        boolean owned = plugin.getPlayerDataManager().hasOwned(uuid, type, item.getKey());

        String equipped = item.isPrefix()
                ? plugin.getPlayerDataManager().getEquippedPrefixKey(uuid)
                : plugin.getPlayerDataManager().getEquippedSuffixKey(uuid);
        boolean active = !equipped.isEmpty() && equipped.equals(item.getKey());

        if (active && owned) {
            if (expire != null && expire == -1L) {
                return plugin.getMessagesManager().getColored("menu.status-active-permanent");
            }
            return plugin.getMessagesManager().getColored("menu.status-active-temporary",
                    "%expire%", Utils.formatDate(expire == null ? 0 : expire));
        }

        if (owned) {
            if (expire != null && expire == -1L) {
                return plugin.getMessagesManager().getColored("menu.status-permanent");
            }
            return plugin.getMessagesManager().getColored("menu.status-temporary",
                    "%expire%", Utils.formatDate(expire == null ? 0 : expire));
        }

        if (plugin.getPlayerDataManager().isExpired(uuid, type, item.getKey())) {
            return plugin.getMessagesManager().getColored("menu.status-expired");
        }
        return plugin.getMessagesManager().getColored("menu.status-not-owned");
    }

    private @NotNull String formatExpire(@NotNull Player player, @NotNull MenuItem item) {
        UUID uuid = player.getUniqueId();
        String type = item.isPrefix() ? "prefix" : "suffix";
        Long expire = plugin.getPlayerDataManager().getExpire(uuid, type, item.getKey());
        if (expire == null || expire == -1L) return "";
        return Utils.formatDate(expire);
    }

    private @NotNull String resolveTimeLeft(@NotNull Player player, @NotNull MenuItem item) {
        UUID uuid = player.getUniqueId();
        String type = item.isPrefix() ? "prefix" : "suffix";
        Long expire = plugin.getPlayerDataManager().getExpire(uuid, type, item.getKey());
        if (expire == null || expire == -1L) return "";
        return Utils.formatTimeLeft(expire);
    }

    private @NotNull String buildCostLine(@NotNull MenuItem item) {
        if (item.getCost() <= 0) {
            return plugin.getMessagesManager().getColored("menu.lines.cost-free");
        }
        return plugin.getMessagesManager().getColored("menu.lines.cost",
                "%cost%", formatCost(item.getCost()));
    }

    private @NotNull String buildGroupLine(@NotNull MenuItem item) {
        String group = item.getGroup();
        if (group.isEmpty() || group.equalsIgnoreCase("default")) return "";
        return plugin.getMessagesManager().getColored("menu.lines.group", "%group%", group);
    }

    private @NotNull String buildPermissionLine(@NotNull MenuItem item) {
        if (item.getPermission().isEmpty()) return "";
        return plugin.getMessagesManager().getColored("menu.lines.permission",
                "%permission%", item.getPermission());
    }

    private @NotNull String buildDurationLine(@NotNull MenuItem item) {
        return plugin.getMessagesManager().getColored("menu.lines.duration",
                "%duration%", Utils.prettyDuration(item.getDuration()));
    }

    private @NotNull String formatCost(double cost) {
        if (cost == Math.floor(cost) && !Double.isInfinite(cost)) {
            return String.valueOf((long) cost);
        }
        return String.valueOf(cost);
    }

    private @NotNull String replacePage(@Nullable String s, int page, int total) {
        if (s == null) return "";
        return s.replace("%page%", String.valueOf(page)).replace("%total%", String.valueOf(total));
    }


    /**
     * Обрабатывает клик игрока по предмету.
     * <p>
     * <b>Логика:</b>
     * <ol>
     *   <li>Если предмет — навигация или декорация, обрабатывается соответствующе.</li>
     *   <li>Если предмет <b>уже куплен</b> — условия <b>не проверяются</b>, предмет сразу надевается.</li>
     *   <li>Если предмет <b>не куплен</b> — проверяются условия. Если они проходят,
     *       списывается стоимость, выдаётся владение, затем предмет надевается.</li>
     * </ol>
     */
    public void handleClick(@NotNull Player player, @NotNull MenuHolder holder, int slot) {
        MenuItem item = holder.getItem(slot);
        if (item == null) return;

        String action = item.getAction();
        if (action != null && !action.isEmpty()) {
            switch (action) {
                case "prev-page" -> { openMenu(player, holder.getMenuId(), holder.getPage() - 1, null); return; }
                case "next-page" -> { openMenu(player, holder.getMenuId(), holder.getPage() + 1, null); return; }
                case "back" -> { openMenu(player, "main", 1); return; }
                case "close" -> { player.closeInventory(); return; }
                case "back-to-categories" -> {
                    openCategoryMenu(player, holder.getMenuId());
                    return;
                }
                case "remove-prefix" -> {
                    LuckPermsUtils.clearPrefix(player.getUniqueId());
                    plugin.getPlayerDataManager().clearEquippedPrefix(player.getUniqueId());
                    plugin.getMessagesManager().send(player, "menu.prefix-removed");
                    openMenu(player, holder.getMenuId(), holder.getPage());
                    return;
                }
                case "remove-suffix" -> {
                    LuckPermsUtils.clearSuffix(player.getUniqueId());
                    plugin.getPlayerDataManager().clearEquippedSuffix(player.getUniqueId());
                    plugin.getMessagesManager().send(player, "menu.suffix-removed");
                    openMenu(player, holder.getMenuId(), holder.getPage());
                    return;
                }
                default -> {
                    if (action.startsWith("open-category:")) {
                        String catId = action.substring("open-category:".length());
                        openMenu(player, holder.getMenuId(), 1, catId);
                        return;
                    }
                    if (action.startsWith("open-menu:")) {
                        String target = action.substring("open-menu:".length());
                        Menu targetMenu = plugin.getConfigManager().getMenu(target);
                        if (targetMenu != null && targetMenu.hasCategories()) {
                            openCategoryMenu(player, target);
                        } else {
                            openMenu(player, target, 1, null);
                        }
                        return;
                    }
                }
            }
        }

        if (!item.isContent()) return;

        boolean isPrefix = item.isPrefix();
        String type = isPrefix ? "prefix" : "suffix";
        UUID uuid = player.getUniqueId();
        boolean owned = plugin.getPlayerDataManager().hasOwned(uuid, type, item.getKey());

        if (!owned) {
            if (!checkConditions(player, item).isPass()) {
                plugin.getMessagesManager().send(player, "menu.unavailable");
                plugin.getSoundManager().playPurchaseFailed(player);
                return;
            }

            if (item.getCost() > 0 && plugin.getEconomy() != null) {
                if (!plugin.getEconomy().has(player, item.getCost())) {
                    plugin.getMessagesManager().send(player, "menu.insufficient-funds",
                            "%cost%", String.valueOf(item.getCost()));
                    plugin.getSoundManager().playPurchaseFailed(player);
                    return;
                }
                plugin.getEconomy().withdrawPlayer(player, item.getCost());
                plugin.getMessagesManager().send(player, "menu.payment-success",
                        "%cost%", String.valueOf(item.getCost()));
            }

            long durationMs = Utils.parseDuration(item.getDuration());
            long expireAt = durationMs < 0 ? -1L : System.currentTimeMillis() + durationMs;
            plugin.getPlayerDataManager().grant(uuid, type, item.getKey(), expireAt);
            plugin.getSoundManager().playPurchase(player);

            if (expireAt == -1L) {
                plugin.getMessagesManager().send(player, "menu.purchased-permanent");
            } else {
                plugin.getMessagesManager().send(player, "menu.purchased-temporary",
                        "%expire%", Utils.formatDate(expireAt));
            }
        }

        if (!LuckPermsUtils.isAvailable()) {
            plugin.getMessagesManager().send(player, "menu.luckperms-missing");
            return;
        }

        String parsedValue = parsePapi(player, isPrefix ? item.getPrefix() : item.getSuffix());
        int weight = plugin.getConfigManager().getCustomPrefixWeight();

        if (isPrefix) {
            LuckPermsUtils.setPrefix(uuid, parsedValue, weight);
            plugin.getPlayerDataManager().setEquippedPrefix(uuid, item.getKey());
            plugin.getMessagesManager().send(player, "menu.prefix-set", "%prefix_value%", parsedValue);
        } else {
            LuckPermsUtils.setSuffix(uuid, parsedValue, weight);
            plugin.getPlayerDataManager().setEquippedSuffix(uuid, item.getKey());
            plugin.getMessagesManager().send(player, "menu.suffix-set", "%suffix_value%", parsedValue);
        }
        player.closeInventory();
    }


    public @NotNull ConditionResult checkConditions(@NotNull Player player, @NotNull MenuItem item) {
        if (!item.getPermission().isEmpty() && !player.hasPermission(item.getPermission()))
            return ConditionResult.fail();
        if (!item.getGroup().isEmpty() && hasGroup(player, item.getGroup()))
            return ConditionResult.fail();
        if (item.getCost() > 0 && hasFunds(player, item.getCost()))
            return ConditionResult.fail();
        if (plugin.getConfigManager().isUsePlaceholderApi()
                && !PlaceholderUtils.checkConditions(player, item.getConditions()))
            return ConditionResult.fail();
        return ConditionResult.pass();
    }

    public boolean isOwned(@NotNull Player player, @NotNull MenuItem item) {
        if (!item.isContent()) return false;
        String type = item.isPrefix() ? "prefix" : "suffix";
        return plugin.getPlayerDataManager().hasOwned(player.getUniqueId(), type, item.getKey());
    }

    private boolean hasFunds(@NotNull Player player, double amount) {
        Economy eco = plugin.getEconomy();
        if (eco == null) return false;
        return !eco.has(player, amount);
    }

    private boolean hasGroup(@NotNull Player player, @NotNull String group) {
        if (group.isEmpty()) return false;
        if (player.hasPermission("dprefixes.bypass.group")) return false;
        var lp = plugin.getLuckPerms();
        if (lp == null) return false;
        User user = lp.getUserManager().getUser(player.getUniqueId());
        if (user == null) return true;
        if (user.getPrimaryGroup().equalsIgnoreCase(group)) return false;
        return user.getInheritedGroups(user.getQueryOptions()).stream()
                .noneMatch(g -> g.getName().equalsIgnoreCase(group));
    }


    public void checkExpiry(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        PlayerDataManager data = plugin.getPlayerDataManager();

        String pKey = data.getEquippedPrefixKey(uuid);
        if (!pKey.isEmpty() && data.isExpired(uuid, "prefix", pKey)) {
            LuckPermsUtils.clearPrefix(uuid);
            data.clearEquippedPrefix(uuid);
            plugin.getMessagesManager().send(player, "menu.prefix-expired");
        }

        String sKey = data.getEquippedSuffixKey(uuid);
        if (!sKey.isEmpty() && data.isExpired(uuid, "suffix", sKey)) {
            LuckPermsUtils.clearSuffix(uuid);
            data.clearEquippedSuffix(uuid);
            plugin.getMessagesManager().send(player, "menu.suffix-expired");
        }
    }

    /** Результат проверки условий. */
    @Getter
    public static class ConditionResult {
        private final boolean pass;

        private ConditionResult(boolean pass) { this.pass = pass; }

        public static @NotNull ConditionResult pass() { return new ConditionResult(true); }
        public static @NotNull ConditionResult fail() { return new ConditionResult(false); }
    }
}
package me.demro.dprefixes.storage;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Данные игрока: ключи надетых предметов и карта владения.
 * <p>
 * {@code ownership} хранит пары {@code "type:itemKey" → expireAt}
 * (epoch millis, {@code -1} = навсегда).
 */
@Getter
@Setter
public class PlayerData {

    private @NotNull String equippedPrefixKey = "";
    private @NotNull String equippedSuffixKey = "";
    private final @NotNull Map<String, Long> ownership = new HashMap<>();

    /** @return {@code true}, если нет ни префикса, ни суффикса, ни покупок. */
    public boolean isEmpty() {
        return equippedPrefixKey.isEmpty() && equippedSuffixKey.isEmpty() && ownership.isEmpty();
    }

    /** @return глубокая копия. */
    public @NotNull PlayerData copy() {
        PlayerData d = new PlayerData();
        d.setEquippedPrefixKey(this.equippedPrefixKey);
        d.setEquippedSuffixKey(this.equippedSuffixKey);
        d.getOwnership().putAll(this.ownership);
        return d;
    }
}
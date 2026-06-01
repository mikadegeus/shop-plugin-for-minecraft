package dev.mika.customshop.managers;

import dev.mika.customshop.CustomShop;
import dev.mika.customshop.models.ShopItem;
import dev.mika.customshop.utils.MessageUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;

/**
 * Centralises all buy/sell logic: balance checks, inventory mutations, economy
 * calls, transaction logging, feedback messages and sounds. Shared by the
 * confirmation GUI and the quick shift-click flow so the rules live in one place.
 */
public final class TransactionService {

    /** Outcome of a buy or sell attempt. */
    public enum Result {
        SUCCESS,
        NOT_ENOUGH_MONEY,
        NOT_ENOUGH_ITEMS,
        NOT_SELLABLE,
        INVENTORY_FULL,
        TRANSACTION_FAILED
    }

    private static final String BUY = "BUY";
    private static final String SELL = "SELL";

    private final CustomShop plugin;
    private final EconomyManager economy;
    private final DatabaseManager database;

    public TransactionService(@NotNull CustomShop plugin,
                              @NotNull EconomyManager economy,
                              @NotNull DatabaseManager database) {
        this.plugin = plugin;
        this.economy = economy;
        this.database = database;
    }

    /**
     * Attempt to buy {@code amount} of {@code item} for {@code player}. Handles
     * the money transfer, item delivery, logging, sound and feedback message.
     */
    @NotNull
    public Result buy(@NotNull Player player, @NotNull ShopItem item, int amount) {
        if (amount <= 0) {
            return Result.NOT_ENOUGH_ITEMS;
        }
        double total = item.getBuyPrice() * amount;

        if (!economy.has(player, total)) {
            sendMessage(player, "not-enough-money", item, amount, total);
            return Result.NOT_ENOUGH_MONEY;
        }
        if (!hasSpaceFor(player, item.getMaterial(), amount)) {
            sendMessage(player, "inventory-full", item, amount, total);
            return Result.INVENTORY_FULL;
        }
        if (!economy.withdraw(player, total)) {
            sendMessage(player, "not-enough-money", item, amount, total);
            return Result.NOT_ENOUGH_MONEY;
        }

        giveItems(player, item.getMaterial(), amount);
        logTransaction(player, item, amount, total, BUY);
        playSound(player, "shop.sound-on-purchase");
        sendMessage(player, "purchase-success", item, amount, total);
        return Result.SUCCESS;
    }

    /**
     * Attempt to sell {@code amount} of {@code item} from {@code player}'s inventory.
     */
    @NotNull
    public Result sell(@NotNull Player player, @NotNull ShopItem item, int amount) {
        if (!item.isSellable()) {
            sendMessage(player, "not-sellable", item, amount, 0);
            return Result.NOT_SELLABLE;
        }
        if (amount <= 0) {
            return Result.NOT_ENOUGH_ITEMS;
        }
        if (countItems(player, item.getMaterial()) < amount) {
            sendMessage(player, "not-enough-items", item, amount, 0);
            return Result.NOT_ENOUGH_ITEMS;
        }

        double total = item.getSellPrice() * amount;
        removeItems(player, item.getMaterial(), amount);
        if (!economy.deposit(player, total)) {
            // Deposit failed: restore the items so the player loses nothing.
            giveItems(player, item.getMaterial(), amount);
            sendMessage(player, "transaction-failed", item, amount, total);
            return Result.TRANSACTION_FAILED;
        }
        logTransaction(player, item, amount, total, SELL);
        playSound(player, "shop.sound-on-sell");
        sendMessage(player, "sell-success", item, amount, total);
        return Result.SUCCESS;
    }

    /**
     * @return how many of {@code material} the player currently carries.
     */
    public int countItems(@NotNull Player player, @NotNull Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    /**
     * @return {@code true} when {@code amount} items fit in the player's inventory.
     */
    public boolean hasSpaceFor(@NotNull Player player, @NotNull Material material, int amount) {
        int capacity = 0;
        int maxStack = material.getMaxStackSize();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                capacity += maxStack;
            } else if (stack.getType() == material && stack.getAmount() < maxStack) {
                capacity += maxStack - stack.getAmount();
            }
            if (capacity >= amount) {
                return true;
            }
        }
        return capacity >= amount;
    }

    /**
     * @return the total number of {@code material} the player's inventory can still accept.
     */
    public int freeSpaceFor(@NotNull Player player, @NotNull Material material) {
        int capacity = 0;
        int maxStack = material.getMaxStackSize();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                capacity += maxStack;
            } else if (stack.getType() == material && stack.getAmount() < maxStack) {
                capacity += maxStack - stack.getAmount();
            }
        }
        return capacity;
    }

    private void giveItems(@NotNull Player player, @NotNull Material material, int amount) {
        int maxStack = material.getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int give = Math.min(maxStack, remaining);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(material, give));
            remaining -= give;
            // Defensive: if anything did not fit, drop it at the player's feet
            // rather than silently deleting purchased items.
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    private void removeItems(@NotNull Player player, @NotNull Material material, int amount) {
        PlayerInventory inventory = player.getInventory();
        int remaining = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(stack.getAmount(), remaining);
            remaining -= take;
            int newAmount = stack.getAmount() - take;
            if (newAmount <= 0) {
                contents[i] = null;
            } else {
                // Write a fresh stack instead of mutating the live reference,
                // so behaviour is independent of how the server backs the array.
                ItemStack reduced = stack.clone();
                reduced.setAmount(newAmount);
                contents[i] = reduced;
            }
        }
        inventory.setStorageContents(contents);
    }

    private void logTransaction(@NotNull Player player, @NotNull ShopItem item,
                                int amount, double total, @NotNull String type) {
        database.logTransaction(
                player.getUniqueId(),
                player.getName(),
                item.getDisplayName(),
                amount,
                total,
                type,
                System.currentTimeMillis());
    }

    private void playSound(@NotNull Player player, @NotNull String configKey) {
        String soundName = plugin.getConfig().getString(configKey);
        if (soundName == null || soundName.isBlank()) {
            return;
        }
        try {
            // Modern, non-deprecated Adventure sound playback via a namespaced key,
            // e.g. "entity.experience_orb.pickup" -> minecraft:entity.experience_orb.pickup.
            Key key = Key.key(soundName.toLowerCase(Locale.ROOT));
            player.playSound(Sound.sound(key, Sound.Source.MASTER, 1.0f, 1.0f));
        } catch (Exception ignored) {
            // Misconfigured sound key; silently skip rather than spamming the log.
        }
    }

    private void sendMessage(@NotNull Player player, @NotNull String key,
                             @NotNull ShopItem item, int amount, double total) {
        FileConfiguration config = plugin.getConfig();
        String prefix = config.getString("messages.prefix", "");
        String raw = config.getString("messages." + key, "");
        if (raw.isEmpty()) {
            return;
        }
        String currency = config.getString("shop.currency-symbol", "$");
        String price = currency + formatPrice(total);
        String message = MessageUtil.replace(raw, "amount", String.valueOf(amount));
        message = MessageUtil.replace(message, "item", item.getDisplayName());
        message = MessageUtil.replace(message, "price", price);
        player.sendMessage(MessageUtil.color(prefix + message));
    }

    @NotNull
    public static String formatPrice(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.2f", value);
    }
}

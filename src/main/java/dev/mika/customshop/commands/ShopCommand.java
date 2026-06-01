package dev.mika.customshop.commands;

import dev.mika.customshop.CustomShop;
import dev.mika.customshop.managers.DatabaseManager;
import dev.mika.customshop.managers.TransactionService;
import dev.mika.customshop.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Handles {@code /shop} and its admin sub-commands: reload, log and give.
 */
public final class ShopCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "customshop.admin";
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final CustomShop plugin;

    public ShopCommand(@NotNull CustomShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            openMenu(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> handleReload(sender);
            case "log" -> handleLog(sender, args);
            case "give" -> handleGive(sender, args);
            default -> openMenu(sender);
        }
        return true;
    }

    private void openMenu(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg(sender, "player-only");
            return;
        }
        if (plugin.getShopManager().getCategoryCount() == 0) {
            player.sendMessage(MessageUtil.color(prefix() + "&cNo shops are configured."));
            return;
        }
        dev.mika.customshop.gui.ShopGUI.open(plugin, player, 0);
    }

    private void handleReload(@NotNull CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            msg(sender, "no-permission");
            return;
        }
        plugin.reloadConfig();
        plugin.getShopManager().loadShops();
        msg(sender, "reload-success");
    }

    private void handleLog(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            msg(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.color(prefix() + "&cUsage: /shop log <player>"));
            return;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            msg(sender, "player-not-found");
            return;
        }

        plugin.getDatabaseManager().getRecentTransactions(target.getUniqueId(), records -> {
            String name = target.getName() != null ? target.getName() : args[1];
            sender.sendMessage(MessageUtil.color("&8&m----------------------------------------"));
            sender.sendMessage(MessageUtil.color("&6Last transactions for &e" + name + "&6:"));
            if (records.isEmpty()) {
                sender.sendMessage(MessageUtil.color("&7No transactions found."));
            } else {
                String currency = plugin.getConfig().getString("shop.currency-symbol", "$");
                for (DatabaseManager.TransactionRecord record : records) {
                    String color = record.type().equalsIgnoreCase("BUY") ? "&a" : "&c";
                    String date = DATE_FORMAT.format(Instant.ofEpochMilli(record.timestamp()));
                    sender.sendMessage(MessageUtil.color(
                            "&8[" + date + "] " + color + record.type()
                                    + " &f" + record.amount() + "x " + record.item()
                                    + " &7for " + currency + TransactionService.formatPrice(record.price())));
                }
            }
            sender.sendMessage(MessageUtil.color("&8&m----------------------------------------"));
        });
    }

    private void handleGive(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            msg(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color(prefix() + "&cUsage: /shop give <player> <amount>"));
            return;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            msg(sender, "player-not-found");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            msg(sender, "invalid-amount");
            return;
        }
        if (amount <= 0) {
            msg(sender, "invalid-amount");
            return;
        }
        if (!plugin.getEconomyManager().isReady()) {
            sender.sendMessage(MessageUtil.color(prefix() + "&cEconomy is not available."));
            return;
        }

        plugin.getEconomyManager().deposit(target, amount);
        String currency = plugin.getConfig().getString("shop.currency-symbol", "$");
        String raw = plugin.getConfig().getString("messages.give-success", "&aGave {price} to {player}.");
        raw = MessageUtil.replace(raw, "price", currency + TransactionService.formatPrice(amount));
        raw = MessageUtil.replace(raw, "player", target.getName() != null ? target.getName() : args[1]);
        sender.sendMessage(MessageUtil.color(prefix() + raw));
    }

    @Nullable
    private OfflinePlayer resolvePlayer(@NotNull String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private String prefix() {
        return plugin.getConfig().getString("messages.prefix", "");
    }

    private void msg(@NotNull CommandSender sender, @NotNull String key) {
        String raw = plugin.getConfig().getString("messages." + key, "");
        if (!raw.isEmpty()) {
            sender.sendMessage(MessageUtil.color(prefix() + raw));
        }
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(List.of("reload", "log", "give"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("log") || args[0].equalsIgnoreCase("give"))) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(@NotNull List<String> options, @NotNull String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}

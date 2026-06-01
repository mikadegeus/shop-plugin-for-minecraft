package dev.mika.customshop.gui;

import dev.mika.customshop.CustomShop;
import dev.mika.customshop.managers.TransactionService;
import dev.mika.customshop.models.ShopCategory;
import dev.mika.customshop.models.ShopItem;
import dev.mika.customshop.utils.ItemBuilder;
import dev.mika.customshop.utils.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Confirmation screen for a single buy or sell action. The quantity can be
 * adjusted in steps (or maxed) and the total price updates live.
 */
public final class ConfirmGUI implements ShopMenu {

    /** Whether this confirmation buys or sells the item. */
    public enum Mode {
        BUY,
        SELL
    }

    private static final int SIZE = 27;
    private static final int DEC10_SLOT = 10;
    private static final int DEC1_SLOT = 11;
    private static final int INFO_SLOT = 13;
    private static final int INC1_SLOT = 15;
    private static final int INC10_SLOT = 16;
    private static final int CONFIRM_SLOT = 20;
    private static final int MAX_SLOT = 22;
    private static final int CANCEL_SLOT = 24;

    private static final int MIN_AMOUNT = 1;
    private static final int MAX_AMOUNT = 2304; // 36 inventory slots * 64

    private final CustomShop plugin;
    private final ShopCategory category;
    private final ShopItem item;
    private final Mode mode;
    private final Inventory inventory;
    private final String currency;

    private int amount = MIN_AMOUNT;

    public ConfirmGUI(@NotNull CustomShop plugin, @NotNull ShopCategory category,
                      @NotNull ShopItem item, @NotNull Mode mode) {
        this.plugin = plugin;
        this.category = category;
        this.item = item;
        this.mode = mode;
        this.currency = plugin.getConfig().getString("shop.currency-symbol", "$");

        String verb = mode == Mode.BUY ? "&aBuy" : "&cSell";
        Component title = MessageUtil.color("&8» " + verb + " &8«");
        this.inventory = Bukkit.createInventory(this, SIZE, title);
        buildStatic();
        refresh();
    }

    private void buildStatic() {
        ItemStack filler = new ItemBuilder(GuiLayout.FILLER_MATERIAL).name(" ").build();
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(DEC10_SLOT, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name("&c-10").build());
        inventory.setItem(DEC1_SLOT, new ItemBuilder(Material.PINK_STAINED_GLASS_PANE)
                .name("&c-1").build());
        inventory.setItem(INC1_SLOT, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name("&a+1").build());
        inventory.setItem(INC10_SLOT, new ItemBuilder(Material.GREEN_STAINED_GLASS_PANE)
                .name("&a+10").build());
        inventory.setItem(MAX_SLOT, new ItemBuilder(Material.HOPPER)
                .name("&eSet Max").build());
        inventory.setItem(CANCEL_SLOT, new ItemBuilder(Material.RED_WOOL)
                .name("&cCancel").lore(List.of("&7Return to the category")).build());
    }

    /**
     * Recompute the dynamic slots (item info + confirm button) for the current amount.
     */
    private void refresh() {
        double unit = mode == Mode.BUY ? item.getBuyPrice() : item.getSellPrice();
        double total = unit * amount;

        List<String> infoLore = new ArrayList<>();
        infoLore.add(mode == Mode.BUY ? "&7Action: &aBuying" : "&7Action: &cSelling");
        infoLore.add("&7Quantity: &f" + amount);
        infoLore.add("&7Unit price: &f" + currency + TransactionService.formatPrice(unit));
        infoLore.add("");
        infoLore.add("&6Total: &f" + currency + TransactionService.formatPrice(total));
        inventory.setItem(INFO_SLOT, new ItemBuilder(item.getMaterial())
                .name("&e" + item.getDisplayName())
                .amount(Math.min(amount, item.getMaterial().getMaxStackSize()))
                .lore(infoLore)
                .hideAttributes()
                .build());

        inventory.setItem(CONFIRM_SLOT, new ItemBuilder(Material.LIME_WOOL)
                .name("&aConfirm")
                .lore(List.of(
                        "&7" + (mode == Mode.BUY ? "Buy" : "Sell") + " &f" + amount + "x",
                        "&6Total: &f" + currency + TransactionService.formatPrice(total)))
                .build());
    }

    @Override
    public void handleClick(@NotNull InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        switch (slot) {
            case DEC10_SLOT -> changeAmount(-10);
            case DEC1_SLOT -> changeAmount(-1);
            case INC1_SLOT -> changeAmount(1);
            case INC10_SLOT -> changeAmount(10);
            case MAX_SLOT -> setMax(player);
            case CONFIRM_SLOT -> confirm(player);
            case CANCEL_SLOT -> new CategoryGUI(plugin, category, 0).open(player);
            default -> {
                // Decorative slot; nothing to do.
            }
        }
    }

    private void changeAmount(int delta) {
        amount = clamp(amount + delta);
        refresh();
    }

    private void setMax(@NotNull Player player) {
        TransactionService service = plugin.getTransactionService();
        if (mode == Mode.BUY) {
            int affordable = MAX_AMOUNT;
            if (item.getBuyPrice() > 0) {
                double balance = plugin.getEconomyManager().getBalance(player);
                affordable = (int) Math.floor(balance / item.getBuyPrice());
            }
            int space = service.freeSpaceFor(player, item.getMaterial());
            amount = clamp(Math.min(affordable, space));
        } else {
            amount = clamp(service.countItems(player, item.getMaterial()));
        }
        refresh();
    }

    private void confirm(@NotNull Player player) {
        TransactionService service = plugin.getTransactionService();
        TransactionService.Result result = mode == Mode.BUY
                ? service.buy(player, item, amount)
                : service.sell(player, item, amount);
        if (result == TransactionService.Result.SUCCESS) {
            new CategoryGUI(plugin, category, 0).open(player);
        }
        // On failure the service already messaged the player; keep the screen open.
    }

    private int clamp(int value) {
        return Math.max(MIN_AMOUNT, Math.min(value, MAX_AMOUNT));
    }

    public void open(@NotNull Player player) {
        player.openInventory(inventory);
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

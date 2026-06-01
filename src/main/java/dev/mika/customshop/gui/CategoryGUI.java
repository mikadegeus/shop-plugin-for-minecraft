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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shows the items inside a single category, paginated, with buy/sell controls.
 */
public final class CategoryGUI implements ShopMenu {

    private final CustomShop plugin;
    private final ShopCategory category;
    private final Inventory inventory;
    private final Map<Integer, ShopItem> slotToItem = new HashMap<>();
    private final int page;
    private final int totalPages;

    public CategoryGUI(@NotNull CustomShop plugin, @NotNull ShopCategory category, int requestedPage) {
        this.plugin = plugin;
        this.category = category;

        int capacity = GuiLayout.pageCapacity();
        this.totalPages = Math.max(1, (int) Math.ceil(category.getItems().size() / (double) capacity));
        this.page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        String suffix = totalPages > 1 ? " &7(" + (page + 1) + "/" + totalPages + ")" : "";
        Component title = MessageUtil.color("&8» &6" + category.getName() + " &8«" + suffix);

        this.inventory = Bukkit.createInventory(this, GuiLayout.SIZE, title);
        build();
    }

    private void build() {
        fillBorder();
        placeItems();
        addNavigation();
    }

    private void fillBorder() {
        Set<Integer> content = contentSlotSet();
        ItemStack border = new ItemBuilder(GuiLayout.BORDER_MATERIAL).name(" ").build();
        for (int slot = 0; slot < GuiLayout.SIZE; slot++) {
            if (!content.contains(slot)) {
                inventory.setItem(slot, border);
            }
        }
    }

    private void placeItems() {
        List<ShopItem> items = category.getItems();
        int capacity = GuiLayout.pageCapacity();
        int start = page * capacity;
        int end = Math.min(start + capacity, items.size());
        String currency = plugin.getConfig().getString("shop.currency-symbol", "$");

        for (int i = start; i < end; i++) {
            ShopItem item = items.get(i);
            int slot = GuiLayout.CONTENT_SLOTS[i - start];
            inventory.setItem(slot, buildItemIcon(item, currency));
            slotToItem.put(slot, item);
        }
    }

    private ItemStack buildItemIcon(@NotNull ShopItem item, @NotNull String currency) {
        List<String> lore = new ArrayList<>();
        lore.add("&aBuy: " + currency + TransactionService.formatPrice(item.getBuyPrice()));
        if (item.isSellable()) {
            lore.add("&cSell: " + currency + TransactionService.formatPrice(item.getSellPrice()));
        } else {
            lore.add("&cSell: Not sellable");
        }
        lore.add("");
        lore.add("&7Left-click to buy &8| &7Right-click to sell");
        lore.add("&7Shift-click for a full stack");
        return new ItemBuilder(item.getMaterial())
                .name("&e" + item.getDisplayName())
                .lore(lore)
                .hideAttributes()
                .build();
    }

    private void addNavigation() {
        if (page > 0) {
            inventory.setItem(GuiLayout.PREV_SLOT, new ItemBuilder(Material.ARROW)
                    .name("&ePrevious Page").build());
        }
        if (page < totalPages - 1) {
            inventory.setItem(GuiLayout.NEXT_SLOT, new ItemBuilder(Material.ARROW)
                    .name("&eNext Page").build());
        }
        inventory.setItem(GuiLayout.BACK_SLOT, new ItemBuilder(Material.BARRIER)
                .name("&cBack to Menu").build());
    }

    private Set<Integer> contentSlotSet() {
        Set<Integer> set = new HashSet<>();
        for (int slot : GuiLayout.CONTENT_SLOTS) {
            set.add(slot);
        }
        return set;
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

        if (slot == GuiLayout.PREV_SLOT && page > 0) {
            new CategoryGUI(plugin, category, page - 1).open(player);
            return;
        }
        if (slot == GuiLayout.NEXT_SLOT && page < totalPages - 1) {
            new CategoryGUI(plugin, category, page + 1).open(player);
            return;
        }
        if (slot == GuiLayout.BACK_SLOT) {
            ShopGUI.open(plugin, player, 0);
            return;
        }

        ShopItem item = slotToItem.get(slot);
        if (item == null) {
            return;
        }
        handleItemClick(player, item, event);
    }

    private void handleItemClick(@NotNull Player player, @NotNull ShopItem item, @NotNull InventoryClickEvent event) {
        TransactionService service = plugin.getTransactionService();
        boolean shift = event.isShiftClick();
        boolean left = event.isLeftClick();
        boolean right = event.isRightClick();

        if (shift && left) {
            service.buy(player, item, item.getMaterial().getMaxStackSize());
            return;
        }
        if (shift && right) {
            service.sell(player, item, item.getMaterial().getMaxStackSize());
            return;
        }
        if (left) {
            new ConfirmGUI(plugin, category, item, ConfirmGUI.Mode.BUY).open(player);
            return;
        }
        if (right) {
            new ConfirmGUI(plugin, category, item, ConfirmGUI.Mode.SELL).open(player);
        }
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

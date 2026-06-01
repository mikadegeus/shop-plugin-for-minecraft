package dev.mika.customshop.gui;

import dev.mika.customshop.CustomShop;
import dev.mika.customshop.models.ShopCategory;
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
 * The main shop menu: a paginated, glass-bordered overview of every category.
 */
public final class ShopGUI implements ShopMenu {

    private static final String DEFAULT_TITLE = "&8» &6CustomShop &8«";

    private final CustomShop plugin;
    private final Inventory inventory;
    private final Map<Integer, ShopCategory> slotToCategory = new HashMap<>();
    private final int page;
    private final int totalPages;

    public ShopGUI(@NotNull CustomShop plugin, int requestedPage) {
        this.plugin = plugin;

        List<ShopCategory> categories = plugin.getShopManager().getCategories();
        int capacity = GuiLayout.pageCapacity();
        this.totalPages = Math.max(1, (int) Math.ceil(categories.size() / (double) capacity));
        this.page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        String baseTitle = plugin.getConfig().getString("shop.menu-title", DEFAULT_TITLE);
        String suffix = totalPages > 1 ? " &7(" + (page + 1) + "/" + totalPages + ")" : "";
        Component title = MessageUtil.color(baseTitle + suffix);

        this.inventory = Bukkit.createInventory(this, GuiLayout.SIZE, title);
        build(categories);
    }

    private void build(@NotNull List<ShopCategory> categories) {
        fillBorder();

        if (totalPages == 1) {
            placeSinglePage(categories);
        } else {
            placePaginated(categories);
        }
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

    private void placeSinglePage(@NotNull List<ShopCategory> categories) {
        Set<Integer> contentSlots = contentSlotSet();
        Set<Integer> used = new HashSet<>();

        // First pass: honour valid explicit slots.
        List<ShopCategory> remaining = new ArrayList<>();
        for (ShopCategory category : categories) {
            int slot = category.getSlot();
            if (slot >= 0 && contentSlots.contains(slot) && !used.contains(slot)) {
                placeCategory(slot, category);
                used.add(slot);
            } else {
                remaining.add(category);
            }
        }

        // Second pass: auto-fill the rest into free content slots.
        int index = 0;
        for (ShopCategory category : remaining) {
            while (index < GuiLayout.CONTENT_SLOTS.length && used.contains(GuiLayout.CONTENT_SLOTS[index])) {
                index++;
            }
            if (index >= GuiLayout.CONTENT_SLOTS.length) {
                break;
            }
            int slot = GuiLayout.CONTENT_SLOTS[index];
            placeCategory(slot, category);
            used.add(slot);
        }
    }

    private void placePaginated(@NotNull List<ShopCategory> categories) {
        int capacity = GuiLayout.pageCapacity();
        int start = page * capacity;
        int end = Math.min(start + capacity, categories.size());
        for (int i = start; i < end; i++) {
            int slot = GuiLayout.CONTENT_SLOTS[i - start];
            placeCategory(slot, categories.get(i));
        }
    }

    private void placeCategory(int slot, @NotNull ShopCategory category) {
        List<String> lore = new ArrayList<>(category.getDescription());
        lore.add("");
        lore.add("&7Items: &f" + category.getItems().size());
        lore.add("&eClick to browse");
        ItemStack icon = new ItemBuilder(category.getIcon())
                .name("&6&l" + category.getName())
                .lore(lore)
                .hideAttributes()
                .build();
        inventory.setItem(slot, icon);
        slotToCategory.put(slot, category);
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
        inventory.setItem(GuiLayout.CLOSE_SLOT, new ItemBuilder(Material.BARRIER)
                .name("&cClose").build());
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

        if (totalPages > 1 && slot == GuiLayout.PREV_SLOT && page > 0) {
            open(plugin, player, page - 1);
            return;
        }
        if (totalPages > 1 && slot == GuiLayout.NEXT_SLOT && page < totalPages - 1) {
            open(plugin, player, page + 1);
            return;
        }
        if (slot == GuiLayout.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        ShopCategory category = slotToCategory.get(slot);
        if (category != null) {
            new CategoryGUI(plugin, category, 0).open(player);
        }
    }

    public void open(@NotNull Player player) {
        player.openInventory(inventory);
    }

    public static void open(@NotNull CustomShop plugin, @NotNull Player player, int page) {
        new ShopGUI(plugin, page).open(player);
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

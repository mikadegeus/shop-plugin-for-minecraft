package dev.mika.customshop.models;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Immutable representation of a shop category loaded from a YAML file.
 * Holds display metadata plus the ordered list of items it contains.
 */
public final class ShopCategory {

    private final String id;
    private final String name;
    private final Material icon;
    private final List<String> description;
    private final int slot;
    private final List<ShopItem> items;

    public ShopCategory(@NotNull String id,
                        @NotNull String name,
                        @NotNull Material icon,
                        @NotNull List<String> description,
                        int slot,
                        @NotNull List<ShopItem> items) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.description = List.copyOf(description);
        this.slot = slot;
        this.items = List.copyOf(items);
    }

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public Material getIcon() {
        return icon;
    }

    /**
     * @return an unmodifiable copy of the lore lines describing this category.
     */
    @NotNull
    public List<String> getDescription() {
        return Collections.unmodifiableList(description);
    }

    /**
     * @return the preferred main-menu slot, or {@code -1} when it should be auto-assigned.
     */
    public int getSlot() {
        return slot;
    }

    /**
     * @return an unmodifiable copy of the items in this category.
     */
    @NotNull
    public List<ShopItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}

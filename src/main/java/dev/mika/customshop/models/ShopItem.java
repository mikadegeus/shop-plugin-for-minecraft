package dev.mika.customshop.models;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable representation of a single buyable/sellable shop item.
 * A sell price of {@code 0} marks the item as not sellable.
 */
public final class ShopItem {

    private final Material material;
    private final double buyPrice;
    private final double sellPrice;
    private final String displayName;

    public ShopItem(@NotNull Material material, double buyPrice, double sellPrice, @NotNull String displayName) {
        this.material = material;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.displayName = displayName;
    }

    @NotNull
    public Material getMaterial() {
        return material;
    }

    public double getBuyPrice() {
        return buyPrice;
    }

    public double getSellPrice() {
        return sellPrice;
    }

    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return {@code true} when the item has a sell price greater than zero.
     */
    public boolean isSellable() {
        return sellPrice > 0;
    }
}

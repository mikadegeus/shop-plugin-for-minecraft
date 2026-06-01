package dev.mika.customshop.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Contract for every CustomShop inventory. Implemented as an {@link InventoryHolder}
 * so a clicked inventory can be identified reliably (no fragile title matching)
 * and asked to handle its own click logic.
 */
public interface ShopMenu extends InventoryHolder {

    /**
     * Handle a click inside this menu. Implementations are responsible for
     * cancelling the event where appropriate.
     */
    void handleClick(@NotNull InventoryClickEvent event);
}

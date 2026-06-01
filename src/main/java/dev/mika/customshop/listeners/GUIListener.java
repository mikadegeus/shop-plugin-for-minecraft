package dev.mika.customshop.listeners;

import dev.mika.customshop.gui.ShopMenu;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Routes inventory interactions to the relevant {@link ShopMenu}. Identifying
 * menus by their {@link InventoryHolder} avoids fragile title comparisons.
 */
public final class GUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof ShopMenu menu) {
            menu.handleClick(event);
        }
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof ShopMenu) {
            // Never allow dragging items into a shop menu.
            event.setCancelled(true);
        }
    }
}

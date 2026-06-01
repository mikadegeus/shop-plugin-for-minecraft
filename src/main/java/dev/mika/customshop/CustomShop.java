package dev.mika.customshop;

import dev.mika.customshop.commands.ShopCommand;
import dev.mika.customshop.listeners.GUIListener;
import dev.mika.customshop.managers.DatabaseManager;
import dev.mika.customshop.managers.EconomyManager;
import dev.mika.customshop.managers.ShopManager;
import dev.mika.customshop.managers.TransactionService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * CustomShop main plugin class. Wires together the configuration, economy hook,
 * database pool, shop definitions, command and GUI listener.
 */
public final class CustomShop extends JavaPlugin {

    private ShopManager shopManager;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private TransactionService transactionService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Economy is mandatory: disable the plugin if Vault has no provider.
        economyManager = new EconomyManager(this);
        if (!economyManager.setup()) {
            getLogger().severe("Vault economy provider not found. Disabling CustomShop.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Database: fall back gracefully but keep running if it cannot connect.
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect()) {
            getLogger().warning("Database unavailable: transactions will not be logged.");
        }

        shopManager = new ShopManager(this);
        shopManager.loadShops();

        transactionService = new TransactionService(this, economyManager, databaseManager);

        registerCommand();
        getServer().getPluginManager().registerEvents(new GUIListener(), this);

        getLogger().info("CustomShop enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("CustomShop disabled.");
    }

    private void registerCommand() {
        PluginCommand command = getCommand("shop");
        if (command == null) {
            getLogger().severe("Command 'shop' is missing from plugin.yml.");
            return;
        }
        ShopCommand executor = new ShopCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    @NotNull
    public ShopManager getShopManager() {
        return shopManager;
    }

    @NotNull
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    @NotNull
    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    @NotNull
    public TransactionService getTransactionService() {
        return transactionService;
    }
}

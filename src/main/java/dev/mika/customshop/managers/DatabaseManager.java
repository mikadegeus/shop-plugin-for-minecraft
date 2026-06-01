package dev.mika.customshop.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.mika.customshop.CustomShop;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Owns the HikariCP connection pool and all transaction-log persistence.
 * Supports MySQL and an SQLite fallback. Every database call runs off the
 * main server thread; result callbacks are posted back to the main thread.
 */
public final class DatabaseManager {

    private static final String TABLE = "transactions";
    private static final int DEFAULT_HISTORY_LIMIT = 10;

    private final CustomShop plugin;
    private HikariDataSource dataSource;
    private boolean mysql;

    public DatabaseManager(@NotNull CustomShop plugin) {
        this.plugin = plugin;
    }

    /**
     * Build the connection pool and ensure the schema exists.
     *
     * @return {@code true} when the pool connected and the table is ready.
     */
    public boolean connect() {
        FileConfiguration config = plugin.getConfig();
        String type = config.getString("database.type", "sqlite").toLowerCase();
        this.mysql = type.equals("mysql");

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("CustomShop-Pool");
        hikari.setMaximumPoolSize(config.getInt("database.pool-size", 10));
        hikari.setConnectionTimeout(config.getLong("database.connection-timeout-ms", 30000));

        if (mysql) {
            String host = config.getString("database.host", "localhost");
            int port = config.getInt("database.port", 3306);
            String database = config.getString("database.database", "customshop");
            String user = config.getString("database.username", "root");
            String password = config.getString("database.password", "");
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
            hikari.setUsername(user);
            hikari.setPassword(password);
            hikari.addDataSourceProperty("cachePrepStmts", "true");
            hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        } else {
            File dbFile = new File(plugin.getDataFolder(), "customshop.db");
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            // SQLite is single-writer; a small pool avoids lock contention.
            hikari.setMaximumPoolSize(1);
        }

        try {
            this.dataSource = new HikariDataSource(hikari);
            createTable();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialise the database: " + e.getMessage());
            return false;
        }
    }

    private void createTable() throws SQLException {
        String idColumn = mysql
                ? "id BIGINT AUTO_INCREMENT PRIMARY KEY"
                : "id INTEGER PRIMARY KEY AUTOINCREMENT";
        String ddl = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + idColumn + ", "
                + "player_uuid VARCHAR(36) NOT NULL, "
                + "player_name VARCHAR(16) NOT NULL, "
                + "item VARCHAR(64) NOT NULL, "
                + "amount INT NOT NULL, "
                + "price DOUBLE NOT NULL, "
                + "type VARCHAR(4) NOT NULL, "
                + "created_at BIGINT NOT NULL"
                + ")";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    /**
     * Persist a transaction asynchronously. Failures are logged, never thrown
     * onto the calling thread.
     */
    public void logTransaction(@NotNull UUID playerUuid,
                               @NotNull String playerName,
                               @NotNull String item,
                               int amount,
                               double price,
                               @NotNull String type,
                               long timestamp) {
        if (!isReady()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO " + TABLE
                    + " (player_uuid, player_name, item, amount, price, type, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, playerName);
                statement.setString(3, item);
                statement.setInt(4, amount);
                statement.setDouble(5, price);
                statement.setString(6, type);
                statement.setLong(7, timestamp);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to log transaction: " + e.getMessage());
            }
        });
    }

    /**
     * Fetch a player's most recent transactions asynchronously, then deliver the
     * result on the main server thread.
     */
    public void getRecentTransactions(@NotNull UUID playerUuid,
                                      @NotNull Consumer<List<TransactionRecord>> callback) {
        if (!isReady()) {
            callback.accept(new ArrayList<>());
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<TransactionRecord> records = new ArrayList<>();
            String sql = "SELECT item, amount, price, type, created_at FROM " + TABLE
                    + " WHERE player_uuid = ? ORDER BY created_at DESC LIMIT " + DEFAULT_HISTORY_LIMIT;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        records.add(new TransactionRecord(
                                rs.getString("item"),
                                rs.getInt("amount"),
                                rs.getDouble("price"),
                                rs.getString("type"),
                                rs.getLong("created_at")));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to read transactions: " + e.getMessage());
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(records));
        });
    }

    /**
     * @return {@code true} when the pool is connected and ready for queries.
     */
    public boolean isReady() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Close the pool. Safe to call multiple times.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * Immutable row from the transaction log used for the {@code /shop log} command.
     */
    public record TransactionRecord(String item, int amount, double price, String type, long timestamp) {
    }
}

package dev.mika.customshop.managers;

import dev.mika.customshop.CustomShop;
import dev.mika.customshop.models.ShopCategory;
import dev.mika.customshop.models.ShopItem;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads shop categories and their items from the {@code shops/} folder.
 * Each {@code .yml} file becomes one category, keyed by its file name.
 */
public final class ShopManager {

    private static final String SHOPS_FOLDER = "shops";
    private static final String[] BUNDLED_SHOPS = {"default.yml", "tools.yml", "food.yml", "armor.yml"};

    private final CustomShop plugin;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();

    public ShopManager(@NotNull CustomShop plugin) {
        this.plugin = plugin;
    }

    /**
     * Copy bundled example shops to disk on first run, then load every shop file.
     */
    public void loadShops() {
        categories.clear();

        File shopsDir = new File(plugin.getDataFolder(), SHOPS_FOLDER);
        if (!shopsDir.exists()) {
            shopsDir.mkdirs();
            for (String bundled : BUNDLED_SHOPS) {
                plugin.saveResource(SHOPS_FOLDER + "/" + bundled, false);
            }
        }

        File[] files = shopsDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("No shop files found in the shops folder.");
            return;
        }

        for (File file : files) {
            try {
                ShopCategory category = loadCategory(file);
                if (category != null) {
                    categories.put(category.getId(), category);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load shop '" + file.getName() + "': " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + categories.size() + " shop categor"
                + (categories.size() == 1 ? "y." : "ies."));
    }

    @Nullable
    private ShopCategory loadCategory(@NotNull File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String id = file.getName().substring(0, file.getName().length() - ".yml".length());
        String name = config.getString("category-name", id);
        Material icon = parseMaterial(config.getString("category-icon"), Material.CHEST);
        List<String> description = config.getStringList("description");
        if (description.isEmpty()) {
            description = Collections.singletonList("&7Click to browse.");
        }
        int slot = config.getInt("slot", -1);

        List<ShopItem> items = new ArrayList<>();
        List<Map<?, ?>> rawItems = config.getMapList("items");
        for (Map<?, ?> raw : rawItems) {
            ShopItem item = parseItem(raw, id);
            if (item != null) {
                items.add(item);
            }
        }

        if (items.isEmpty()) {
            plugin.getLogger().warning("Shop '" + id + "' has no valid items; skipping.");
            return null;
        }

        return new ShopCategory(id, name, icon, description, slot, items);
    }

    @Nullable
    private ShopItem parseItem(@NotNull Map<?, ?> raw, @NotNull String categoryId) {
        Object materialObj = raw.get("material");
        if (materialObj == null) {
            return null;
        }
        Material material = parseMaterial(materialObj.toString(), null);
        if (material == null) {
            plugin.getLogger().warning("Unknown material '" + materialObj + "' in shop '" + categoryId + "'.");
            return null;
        }
        double buyPrice = toDouble(raw.get("buy-price"), 0);
        double sellPrice = toDouble(raw.get("sell-price"), 0);
        Object nameObj = raw.get("display-name");
        String displayName = nameObj != null ? nameObj.toString() : prettify(material);
        return new ShopItem(material, buyPrice, sellPrice, displayName);
    }

    @Nullable
    private Material parseMaterial(@Nullable String name, @Nullable Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        return material != null ? material : fallback;
    }

    private double toDouble(@Nullable Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return fallback;
    }

    private String prettify(@NotNull Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                builder.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(' ');
            }
        }
        return builder.toString().trim();
    }

    /**
     * @return an immutable, insertion-ordered view of all loaded categories.
     */
    @NotNull
    public List<ShopCategory> getCategories() {
        return Collections.unmodifiableList(new ArrayList<>(categories.values()));
    }

    @Nullable
    public ShopCategory getCategory(@NotNull String id) {
        return categories.get(id);
    }

    public int getCategoryCount() {
        return categories.size();
    }
}

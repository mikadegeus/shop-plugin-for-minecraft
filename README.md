# CustomShop

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Paper](https://img.shields.io/badge/Paper-1.21+-blue?logo=minecraft&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green.svg)

A polished, production-ready GUI shop plugin for Paper 1.21+. Players browse
categories, buy and sell items through a clean menu flow, while every
transaction is logged to MySQL or SQLite for full admin visibility.

---

## Features

- ✅ Paginated main menu with a glass border and per-category icons
- ✅ Category menus showing live buy/sell prices and click instructions
- ✅ Confirmation screen with adjustable quantity (`-10 / -1 / +1 / +10 / max`) and a live total
- ✅ Shift-click to instantly buy or sell a full stack
- ✅ Vault economy integration with balance and inventory-space checks
- ✅ Transaction logging (buy/sell, amount, price, timestamp) to MySQL **or** SQLite
- ✅ `/shop log <player>` to review the last 10 transactions in chat
- ✅ Hot reload with `/shop reload`, no server restart required
- ✅ Fully configurable messages, sounds and currency symbol (MiniMessage and `&` colours)
- ✅ Async database access, main-thread-safe GUI updates, clean shutdown
- ✅ No deprecated API calls, `@NotNull` / `@Nullable` annotated

---

## Requirements

| Dependency | Version | Required |
|---|---|---|
| Paper | 1.21+ | Yes |
| Java | 21 | Yes |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | latest | Yes |
| An economy plugin (e.g. EssentialsX) | latest | Yes |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | latest | Optional |

---

## Installation

1. Build the plugin (see [Building](#building)) or grab `CustomShop-1.0.0.jar`.
2. Install **Vault** and an economy provider (such as EssentialsX) on your server.
3. Drop `CustomShop-1.0.0.jar` into your server's `plugins/` folder.
4. Start the server once. CustomShop generates `config.yml` and a `shops/` folder
   with four example categories (`default`, `tools`, `food`, `armor`).
5. Edit the configuration to taste, then run `/shop reload`.

---

## Configuration

### `config.yml`

```yaml
database:
  type: sqlite           # "sqlite" (zero setup) or "mysql"
  host: localhost
  port: 3306
  database: customshop
  username: root
  password: ""
  pool-size: 10
  connection-timeout-ms: 30000

shop:
  menu-title: "&8» &6CustomShop &8«"
  currency-symbol: "$"
  sound-on-purchase: "entity.experience_orb.pickup"
  sound-on-sell: "entity.villager.yes"

messages:
  prefix: "&8[&6Shop&8] "
  purchase-success: "&aPurchased {amount}x {item} for {price}."
  sell-success: "&aSold {amount}x {item} for {price}."
  not-enough-money: "&cYou don't have enough money."
  not-enough-items: "&cYou don't have enough items to sell."
```

> Sounds use Minecraft sound keys (e.g. `entity.experience_orb.pickup`), not the
> legacy enum names. This keeps the plugin free of deprecated API calls.

### Adding a shop category

Create a new `.yml` file in the `shops/` folder. The file name (without `.yml`)
becomes the category id.

```yaml
category-name: "Blocks"
category-icon: GRASS_BLOCK
description:
  - "&7Buy and sell building blocks."
slot: 10                 # optional: fixed slot in the main menu
items:
  - material: GRASS_BLOCK
    buy-price: 10
    sell-price: 5         # 0 means the item cannot be sold
    display-name: "Grass Block"
  - material: STONE
    buy-price: 5
    sell-price: 2
    display-name: "Stone"
```

Run `/shop reload` to apply changes without a restart.

---

## Commands

| Command | Permission | Description |
|---|---|---|
| `/shop` | `customshop.use` | Opens the shop main menu |
| `/shop reload` | `customshop.admin` | Reloads config and shops without a restart |
| `/shop log <player>` | `customshop.admin` | Shows a player's last 10 transactions |
| `/shop give <player> <amount>` | `customshop.admin` | Gives money to a player via Vault |

---

## Permissions

| Permission | Default | Description |
|---|---|---|
| `customshop.use` | `true` | Allows opening the shop menu |
| `customshop.admin` | `op` | Allows reload, log and give commands |

---

## Usage

- **Left-click** an item to open the buy confirmation screen.
- **Right-click** an item to open the sell confirmation screen.
- **Shift-left-click** to instantly buy a full stack.
- **Shift-right-click** to instantly sell a full stack.
- In the confirmation screen, adjust the amount with the `-10 / -1 / +1 / +10`
  buttons or **Set Max**, then click **Confirm**.

---

## Building

Requires JDK 21 and Maven.

```bash
mvn clean package
```

The shaded plugin jar is produced at `target/CustomShop-1.0.0.jar`. HikariCP, the
MySQL driver and the SQLite driver are bundled, so no extra libraries are needed.

---

## License

Released under the MIT License.

---

Built by **Mika** | Discord: **Mika#1031**

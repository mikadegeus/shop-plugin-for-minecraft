# Plugin 1: CustomShop

## Doel
Bouw een volledige, productierijpe Minecraft GUI-shop plugin in Java voor Paper 1.21+.
Het eindresultaat moet indrukwekkend zijn als portfolio-item: schone code, nette README,
werkende screenshots-klaar demo. Geen shortcuts, geen placeholder-code.

---

## Tech stack
- **Platform:** Paper 1.21.x (gebruik de Paper API, niet plain Spigot)
- **Java:** 21
- **Build tool:** Maven (`pom.xml`)
- **Database:** MySQL via HikariCP connection pool (ook SQLite fallback als MySQL niet beschikbaar)
- **Dependencies (via Maven):**
  - `io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT`
  - `com.zaxxer:HikariCP:5.1.0`
  - `mysql:mysql-connector-java:8.0.33`
  - PlaceholderAPI (soft-depend, optioneel)

---

## Projectstructuur

```
CustomShop/
├── pom.xml
├── CLAUDE.md
├── README.md
├── src/
│   └── main/
│       ├── java/
│       │   └── dev/mika/customshop/
│       │       ├── CustomShop.java          (main plugin class)
│       │       ├── commands/
│       │       │   └── ShopCommand.java     (/shop, /shop reload, /shop admin)
│       │       ├── gui/
│       │       │   ├── ShopGUI.java         (hoofdmenu met categorieën)
│       │       │   ├── CategoryGUI.java     (items per categorie)
│       │       │   └── ConfirmGUI.java      (koop/verkoop bevestiging)
│       │       ├── managers/
│       │       │   ├── ShopManager.java     (laadt categorieën + items uit config)
│       │       │   ├── DatabaseManager.java (MySQL/SQLite via HikariCP)
│       │       │   └── EconomyManager.java  (Vault economy wrapper)
│       │       ├── models/
│       │       │   ├── ShopCategory.java
│       │       │   └── ShopItem.java
│       │       ├── listeners/
│       │       │   └── GUIListener.java     (InventoryClickEvent, InventoryCloseEvent)
│       │       └── utils/
│       │           ├── ItemBuilder.java     (fluent API voor ItemStack bouwen)
│       │           └── MessageUtil.java     (kleur codes, MiniMessage support)
│       └── resources/
│           ├── plugin.yml
│           ├── config.yml
│           └── shops/
│               └── default.yml             (voorbeeld shop configuratie)
```

---

## Features (implementeer allemaal)

### Hoofd GUI
- Klikbaar categorieën-overzicht (max 45 slots, paginatie als >45 categorieën)
- Elke categorie heeft een eigen icon (configureerbaar), naam en beschrijving in de lore
- Nette border van glas-panes rondom het scherm
- Titel: `"&8» &6CustomShop &8«"` (gebruik MiniMessage)

### Categorie GUI
- Toont alle items in die categorie met paginatie (vorige/volgende knoppen)
- Elk item toont in de lore:
  - Koopprijs (groen): `"&aBuy: $<prijs>"`
  - Verkoopprijs (rood): `"&cSell: $<prijs>"` (kan 0 zijn = niet verkoopbaar)
  - Klik-instructies: `"&7Left-click to buy | Right-click to sell"`
- Shift+klik koopt/verkoopt de stack-hoeveelheid (64x)
- Terugknop naar het hoofdmenu

### Bevestigingsscherm (ConfirmGUI)
- Toont wat de speler koopt/verkoopt
- Hoeveelheid aanpasbaar met knoppen (-1, -10, +1, +10, max)
- Groene bevestig-knop, rode annuleer-knop
- Toont totaalprijs live bij elke hoeveelheidswijziging

### Economy
- Verplichte soft-depend op Vault
- Als Vault niet aanwezig is: duidelijke error in console en plugin disabled
- Checkt saldo voor aankoop, geeft nette foutmelding als te weinig geld

### Database (transactielogboek)
- Slaat elke transactie op: speler UUID, item, hoeveelheid, prijs, type (buy/sell), timestamp
- Tabel wordt automatisch aangemaakt bij eerste start
- Admin-commando `/shop log <speler>` toont laatste 10 transacties in chat

### Configuratie (`config.yml`)
```yaml
database:
  type: sqlite           # of mysql
  host: localhost
  port: 3306
  database: customshop
  username: root
  password: ""

shop:
  currency-symbol: "$"
  sound-on-purchase: ENTITY_EXPERIENCE_ORB_PICKUP
  sound-on-sell: ENTITY_VILLAGER_YES

messages:
  prefix: "&8[&6Shop&8] "
  purchase-success: "&aPurchased {amount}x {item} for {price}."
  sell-success: "&aSold {amount}x {item} for {price}."
  not-enough-money: "&cYou don't have enough money."
  not-enough-items: "&cYou don't have enough items to sell."
```

### Voorbeeld shop configuratie (`shops/default.yml`)
```yaml
category-name: "Blocks"
category-icon: GRASS_BLOCK
items:
  - material: GRASS_BLOCK
    buy-price: 10
    sell-price: 5
    display-name: "Grass Block"
  - material: STONE
    buy-price: 5
    sell-price: 2
    display-name: "Stone"
  - material: OAK_LOG
    buy-price: 15
    sell-price: 8
    display-name: "Oak Log"
```
Maak ook een `tools.yml`, `food.yml` en `armor.yml` categorie als extra voorbeelden.

### Commando's
| Commando | Permissie | Beschrijving |
|---|---|---|
| `/shop` | `customshop.use` | Opent het shop hoofdmenu |
| `/shop reload` | `customshop.admin` | Herlaadt config en shops zonder restart |
| `/shop log <speler>` | `customshop.admin` | Bekijk transactiehistorie |
| `/shop give <speler> <bedrag>` | `customshop.admin` | Test-commando: geef geld via Vault |

---

## Code kwaliteitseisen
- Geen deprecated API-calls
- Alle database-calls asynchroon (gebruik `Bukkit.getScheduler().runTaskAsynchronously`)
- GUI-updates altijd op de main thread (`runTask`)
- Geen memory leaks: verwijder listeners netjes, sluit database bij `onDisable`
- Gebruik `@NotNull` / `@Nullable` annotaties waar relevant
- Geen magic numbers: definieer constanten
- Elke class heeft een korte Javadoc-beschrijving

---

## README.md (genereer dit bestand ook)
De README moet bevatten:
- Badges: Java versie, Paper versie, License (MIT)
- Korte beschrijving (2 zinnen)
- Features lijst met checkmarks
- Installatie-instructies (stap voor stap)
- Configuratie-uitleg met code blocks
- Commando-tabel
- Permissions-tabel
- Sectie "Built by Mika | Discord: Mika#1031"

---

## Hoe te starten
```bash
# In de CustomShop map:
claude

# Geef daarna deze prompt:
# "Build the complete CustomShop plugin according to CLAUDE.md.
#  Start with pom.xml and plugin.yml, then work through each class.
#  Compile and fix all errors before finishing."
```

---

## Definition of Done
- [ ] `mvn clean package` slaagt zonder warnings
- [ ] Plugin laadt op een Paper 1.21 server zonder errors
- [ ] Alle GUI's openen en werken correct
- [ ] Kopen en verkopen werkt met Vault economy
- [ ] Database-tabel wordt aangemaakt en transacties worden gelogd
- [ ] `/shop reload` werkt zonder server restart
- [ ] README.md is volledig ingevuld
- [ ] Code bevat geen deprecated API-calls

<div align="center">

# ✨ dPrefixes

**Elegant prefixes and suffixes for your Minecraft server**

Customizable menus · LuckPerms-native · PlaceholderAPI conditions · HEX gradients · GigaChat AI generation

[![Java](https://img.shields.io/badge/Java-21+-orange?style=flat-square&logo=openjdk)](https://adoptium.net/)
[![Paper](https://img.shields.io/badge/Paper-1.21+-blue?style=flat-square)](https://papermc.io/)
[![Spigot](https://img.shields.io/badge/Spigot-1.19+-yellow?style=flat-square)](https://www.spigotmc.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![JAR size](https://img.shields.io/badge/JAR-~450%20KB-success?style=flat-square)]()
[![Tests](https://img.shields.io/badge/Tests-36%20passing-brightgreen?style=flat-square)]()

</div>

---

## 📖 About

**dPrefixes** is a premium prefix and suffix plugin that gives players a **fully customizable in-game menu** to purchase and equip cosmetic titles. Whether you're running a small survival server or a large network, dPrefixes scales with you.

Prefixes and suffixes are stored as **LuckPerms permission nodes** — they automatically appear in chat, tab, and above the head through any formatter plugin (TAB, ChatControl, EssentialsXChat, etc.).

Built for **Paper 1.21+** and **Spigot 1.19+**. JAR under **500 KB** — libraries (H2, MySQL, HikariCP) load on demand from Maven Central.

---

## ✨ Features

### 🎨 Menus
- **Unlimited menus** — define any number in `menus.yml`
- **Categories** — split prefixes by theme (VIP, Legend, Cosmic, Elemental, Seasonal, Rich...)
- **Pagination** — automatic page arrows, no manual math
- **Multi-slot decorations** — `slots: [0, 1, 2, 8, 9]` covers 5 slots in one entry
- **HeadDatabase support** — decorative player heads instead of vanilla items
- **Clickable links** — mask a URL behind the version number
- **Enchanted glow** on purchased items
- **HEX gradients** in titles, names, and lore (`&#RRGGBB`)

### 💰 Purchases
- **One-time payment** — no double charges on re-equipping
- **Vault economy** — works with EssentialsX, CMI, GemsEconomy, and any other provider
- **Requirements** — `permission`, `group`, `cost`, and PAPI conditions
- **Conditions apply only on purchase** — once bought, always equippable
- **Lifetime options** — `permanent`, `7d`, `30d`, `12h`, `1d12h30m`, `60s`
- **Auto-expiry** — expired items unequip automatically and revert to the group prefix

### 🔌 Integrations

| Plugin | Purpose |
|---|---|
| **LuckPerms** | Stores prefixes/suffixes as permission nodes |
| **Vault** | Any economy provider |
| **PlaceholderAPI** | Placeholders in menus + PAPI-based conditions |
| **HeadDatabase** | Decorative player heads |
| **GigaChat** | AI-generated prefixes with HEX gradients |
| **dLibs** | Automatic update checks |

### 📦 Storage
- **YAML** — simple, single-server
- **H2** — local database, no external server required
- **MySQL / MariaDB** — perfect for multi-server networks
- **Auto-migration** from YAML to H2/MySQL on first launch

### 🤖 GigaChat AI
Let the AI create unique prefixes for your server:

```
/dp ai pattern powerful "strong, mighty, legendary"
/dp ai generate prefix powerful 5
```

The plugin sends a prompt, receives raw text, strips all non-letter characters, applies a HEX gradient from your palette, and adds results straight to `menus.yml`. **Fully async** — no server lag.

### 🎧 Sounds
Separate sounds for **menu opening**, **successful purchase**, and **failed purchase**. Fully configurable (sound name, volume, pitch) — or disable per-sound or globally.

---

## 🚀 Installation

1. Download `dPrefixes-1.0.0.jar` and place it in `plugins/`.
2. Install **LuckPerms** 5.4+.
3. *(Optional)* Install **Vault** + any economy plugin, **PlaceholderAPI**, **HeadDatabase**.
4. Start the server. The plugin downloads H2/MySQL libraries on first launch.
5. Configure files in `plugins/dPrefixes/` and reload with `/dp reload`.

### Requirements

| Component | Version | Required |
|---|---|---|
| Paper / Spigot | 1.19+ | ✅ |
| Java | 21+ | ✅ |
| LuckPerms | 5.4+ | ✅ |
| Vault + Economy | any | ⚠️ optional |
| PlaceholderAPI | 2.11+ | ⚠️ optional |
| HeadDatabase | 1.3+ | ⚠️ optional |

---

## 📋 Commands

### Players

| Command | Description |
|---|---|
| `/dp` | Open main menu |
| `/prefix` | Open prefix menu |
| `/suffix` | Open suffix menu |
| `/dp help` | Show help |

### Admins

| Command | Description |
|---|---|
| `/dp reload` | Reload all configs |
| `/dp list prefixes` | List all prefixes |
| `/dp list suffixes` | List all suffixes |
| `/dp add prefix "text" params` | Add a new prefix |
| `/dp add suffix "text" params` | Add a new suffix |
| `/dp padd <nick> <id>` | Give a prefix in bypass mode |
| `/dp pdel <nick> <id\|*\|all>` | Revoke items from a player |
| `/dp ai pattern <name> "words"` | Create an AI pattern |
| `/dp ai generate <type> <pattern> [count]` | Generate via GigaChat |
| `/dp ai list` | List AI patterns |
| `/dp ai status` | Diagnose GigaChat integration |

Full **tab-completion** for every command.

---

## 🔑 Permissions

| Permission | Description | Default |
|---|---|---|
| `dprefixes.use` | Access to menus | ✅ everyone |
| `dprefixes.admin` | Access to admin commands | ❌ OP only |
| `dprefixes.bypass.group` | Ignore group checks | ❌ OP only |

Item-specific permissions can be set per prefix/suffix in `menus.yml` (e.g. `dprefixes.prefix.vip`).

---

## 🧩 Example configuration

### `menus.yml` — a single prefix

```yaml
menus:
  prefix:
    title: "&#00C9FF&lPREFIXES &8» &7[%page%/%total%]"
    size: 54
    items-per-page: 45
    content-slots: [0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44]

    categories:
      vip:
        name: "&6VIP"
        material: DIAMOND
        slot: 11

    navigation:
      back:      { slot: 49, material: BARRIER, name: "&cBack", action: "back" }
      remove:    { slot: 47, material: HOPPER, name: "&cRemove", action: "remove-prefix" }
      prev-page: { slot: 48, material: ARROW, name: "&e←" }
      next-page: { slot: 50, material: ARROW, name: "&e→" }

    items:
      vip:
        material: DIAMOND
        category: "vip"
        name: "&b[VIP]"
        lore:
          - ""
          - "&7Prefix: &b[VIP]"
          - "%group_line%"
          - "%cost_line%"
          - "%duration_line%"
          - ""
          - "%status%"
          - "&7Time left: %time_left%"
          - ""
        prefix: "&b[VIP] &r"
        group: "vip"
        cost: 1000
        permission: "dprefixes.prefix.vip"
        duration: "permanent"
        glow: true
```

### Placeholders

| Placeholder | Description | Auto-hides |
|---|---|---|
| `%status%` | "✓ Active" / "✓ Purchased until 01.01.2027" / "✗ Expired" / "✗ Not purchased" | ❌ |
| `%time_left%` | "3d 5h" | ✅ for permanent |
| `%expire%` | Expiration date | ✅ for permanent |
| `%cost_line%` | "Cost: 1000" or "Cost: Free" | ❌ |
| `%group_line%` | "Group: vip" | ✅ for default |
| `%permission_line%` | "Permission: dprefixes.prefix.vip" | ✅ if not set |
| `%duration_line%` | "Duration: 7d" | ❌ |
| `%balance%` | Current balance | ❌ |
| `%page%`, `%total%` | Pagination | ❌ |

### PAPI conditions

```yaml
conditions:
  - "%player_level% >= 30"
  - "%vault_eco_balance% >= 100000"
  - "%statistic_player_kills% > 100"
```

**Operators:** `==`, `!=`, `>`, `<`, `>=`, `<=`, `equals`, `equalsIgnoreCase`, `contains`, `startsWith`, `endsWith`.

---

## 🛠️ Building from source

```bash
git clone https://github.com/demrodev/dprefixes.git
cd dprefixes
mvn clean package
```

Output: `target/dPrefixes-1.0.0.jar`

### Running tests

```bash
mvn clean test
```

**36 tests** cover utilities, storage, and plugin lifecycle (using JUnit 5 + MockBukkit).

---

## 🏗️ Project structure

```
src/main/java/me/demro/dprefixes/
├── DPrefixes.java              # Lifecycle only
├── bootstrap/                  # Initialization
├── commands/                   # /dp /prefix /suffix
├── listeners/                  # Event listeners
├── managers/                   # Menu, Sound, Update, Scheduler, Pattern, PlayerData
├── storage/                    # YAML / H2 / MySQL
├── economy/                    # Vault bridge
├── gigachat/                   # GigaChat AI client
└── utils/                      # Utils, GradientUtils, PlaceholderUtils, LuckPermsUtils, HeadDatabaseHook
```

---

## 🤝 Contributing

Pull requests are welcome. For major changes, please open an issue first.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -m "feat: add my feature"`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the **Apache License 2.0** — see the [LICENSE](LICENSE) file for details.

---

## 💬 Support

- 🐛 **Bug reports:** [GitHub Issues](https://github.com/demrodev/dprefixes/issues)
- 💡 **Feature requests:** [GitHub Discussions](https://github.com/demrodev/dprefixes/discussions)
- 📦 **Releases:** [GitHub Releases](https://github.com/demrodev/dprefixes/releases)

---

<div align="center">

**Made with ❤️ by [DemRoDev](https://github.com/demrodev)**

If you like dPrefixes, please ⭐ the repository!

</div>

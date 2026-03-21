# <p align="center">HubCore</p>

<div align="center">
  <img src="https://i.imgur.com/ZP36KL5.png" alt="HubCore Logo"/>

**Hub & lobby management plugin for Paper 1.21.1**

![Version](https://img.shields.io/badge/version-2.0.0-blue?style=flat-square)
![Paper](https://img.shields.io/badge/Paper-1.21.1-cyan?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-blue?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-green?style=flat-square)

</div>

---

## 📖 Overview

**HubCore** is a fully-featured **hub & lobby management** plugin for Paper 1.21.1.
It provides a complete PvP system, interactive lobby utilities, animated tab/scoreboard, configurable GUI menus, a global action system, and a dynamic command/alias engine — all reloadable at runtime without restarting the server.

---

## ✨ Features

### ⚔️ PvP System
- **Sword-based activation** — PvP enables when the player holds the PvP sword, disables when they put it away
- **Configurable sword** — material, name, lore, enchants, flags, glow, unbreakable, active/inactive appearance
- **Combat tag** — PvP stays active for a configurable duration after the last hit dealt/received
- **Enable/disable delays** — configurable tick delays before PvP activates or deactivates
- **Respawn protection** — configurable delay after respawn before PvP can be re-enabled
- **Max combat timer** — optional forced deactivation after a maximum combat duration
- **Kill heal** — restore full health or apply Regeneration on kill (configurable)
- **Killstreak system** — tracks current and best killstreak; configurable milestone broadcasts with sounds
- **Kill feed** — customizable kill announcements in chat, action bar, or bossbar
- **Visual effects** — particles and sounds on kill, PvP enable, and PvP disable (all configurable)
- **WorldGuard bypass** — PvP works inside protected regions
- **Inventory keep on death** — no item or XP drops; sword is re-given on respawn
- **Indroppable sword** — players cannot drop or swap the PvP sword to offhand

### 📊 PvP Stats (H2 + HikariCP)
- Tracks kills, deaths, K/D ratio, current killstreak, best killstreak, and last seen timestamp
- Stats loaded into cache on join, saved on quit and on a configurable auto-save interval
- Public API (`StatsService`) exposed via `HubCoreAPI` for use by other plugins

### 🧱 Lobby Blocks
- **Hotbar block stack** — players always have 64 blocks in a dedicated hotbar slot
- **Place & auto-remove** — placed blocks disappear after a configurable timer
- **Break with animation** — smooth crack animation (gracefully disabled without ProtocolLib)
- **Block respawn** — broken blocks reappear automatically after a configurable delay
- **Blacklist** — prevent specific materials from being broken
- **Block Selector GUI** — right-click item to open a fully configurable inventory menu (`menu/blockselector.yml`)

### 🏃 Movement Utilities
- **Double jump** — configurable with sound, cooldown, and permission
- **Launchpad** — pressure-plate-based launch pads with configurable velocity
- **Lobby & spawn** — `/lobby` and `/spawn` commands with configurable locations (stored in H2)
- **Fly command** — `/fly` to toggle flight (`hubcore.alias.default.fly`)

### 📋 Animated Tab & Scoreboard
- **Centralized animations** — all animations defined once in `animations.yml`, shared and synchronized between tab and scoreboard
- **Animated header/footer** — multiline, supports `%animation:Name%`, PlaceholderAPI, and `%player%`/`%online%` fallbacks
- **Animated sidebar** — per-player scoreboard with animated title and up to 15 lines, flicker-free via fixed slot IDs
- **Tablist name formatting** — custom format with `{lp_prefix}`, `{lp_suffix}`, PlaceholderAPI
- **Group sorting** — LuckPerms-based tablist ordering via Scoreboard teams
- **Throttled updates** — animations tick every Bukkit tick; client sends are throttled to a configurable interval

### 🪟 Configurable Menus (`menu/`)
- Drop any `.yml` file in `plugins/HubCore/menu/` — it is auto-loaded as an openable GUI
- Per-slot items: material, amount, display name, lore, glow, actions
- Optional auto-refresh (re-renders item contents while the inventory is open)
- Open any menu from any action with `[MENU] menuName`
- Reload with `/hubcore reload` — no restart needed

### ⚡ Global Action System
Usable in menu slots, custom join items, join events, and YAML commands:

| Action | Description |
|---|---|
| `[MESSAGE] text` | Send a chat message to the player |
| `[BROADCAST] text` | Broadcast to all online players |
| `[ACTIONBAR] text` | Send an action bar message |
| `[TITLE] Title;Subtitle;FadeIn;Stay;FadeOut` | Show a title (times in seconds) |
| `[SOUND] SOUND_NAME` | Play a Bukkit sound |
| `[COMMAND] command` | Execute a command as the player |
| `[CONSOLE] command` | Execute a command as console |
| `[GAMEMODE] 0/1/2/3` | Change the player's gamemode |
| `[EFFECT] EFFECT;LEVEL` | Apply a potion effect (permanent) |
| `[MENU] menuName` | Open a configured GUI menu |
| `[CLOSE]` | Close the player's open inventory |
| `[PROXY] serverName` | Connect the player to a BungeeCord server |

All actions support `%player%` as a placeholder.

### 🎒 Custom Join Items
- Configurable items given to players on join (slot, material, name, lore, glow, actions)
- Right-click triggers any action list
- Movement protection — optionally prevent players from moving items out of the hotbar

### 👁️ Player Hider
- Toggle item to hide/show all other players
- Configurable slot, cooldown, separate item config for hidden/visible state
- New players joining are automatically hidden from those with hider active

### 🛡️ Anti-WDL (World Downloader)
- Detects and kicks players using World Downloader mods
- Optionally notifies online admins (`hubcore.admin`)

### 💬 Chat Lock
- `/lockchat` — toggle a global chat lock for all players
- Players with `hubcore.staff.lockbypass` can always chat regardless
- Broadcasts a server-wide message on lock and unlock

### 🔧 Dynamic Command Engine
- Drop `.yml` files in `plugins/HubCore/commands/` to create custom commands with actions
- Define aliases in `aliases.yml` — each alias redirects to a target command with all arguments
- All custom commands and aliases support `/hubcore reload` with no restart

**Builtin commands** (always registered, permission-gated):

| Command | Permission |
|---|---|
| `/gms` | `hubcore.alias.default.gms` |
| `/gmc` | `hubcore.alias.default.gmc` |
| `/gmsp` | `hubcore.alias.default.gmsp` |
| `/fly` | `hubcore.alias.default.fly` |
| `/lockchat` | `hubcore.alias.default.lockchat` |

### 🔄 Version Check
- Checks GitHub Releases API asynchronously on startup
- Notifies admins on join if an update is available
- Refuses to load if `version.yml` has been manually set above the latest release

---

## 🗂️ Commands

| Command | Description | Permission |
|---|---|---|
| `/hubcore` | Plugin info | — |
| `/hubcore reload` | Reload all config files and menus | `hubcore.admin` |
| `/hubcore force <on\|off> <player>` | Force PvP on/off for a player | `hubcore.admin` |
| `/pvp stats [player]` | Show PvP statistics | — |
| `/pvp reset <player>` | Reset a player's PvP stats | `hubcore.admin` |
| `/setlobby` | Set the lobby spawn point | `hubcore.set` |
| `/setspawn` | Set the world spawn point | `hubcore.set` |
| `/lobby` | Teleport to the lobby | — |
| `/spawn` | Teleport to the spawn | — |
| `/fly` | Toggle flight | `hubcore.alias.default.fly` |
| `/gms` / `/gmc` / `/gmsp` | Gamemode shortcuts | `hubcore.alias.default.*` |
| `/lockchat` | Toggle global chat lock | `hubcore.alias.default.lockchat` |

---

## 📁 File Structure

```
plugins/HubCore/
├── config.yml          — Main configuration (PvP, lobby blocks, effects, …)
├── messages.yml        — All player-facing messages
├── tab.yml             — Tab header/footer, name format, group sorting
├── scoreboard.yml      — Sidebar title and lines
├── animations.yml      — Shared animations (%animation:Name%) for tab & scoreboard
├── aliases.yml         — Custom command aliases
├── menu/
│   ├── blockselector.yml    — Block selector GUI (auto-generated)
│   └── *.yml                — Any custom menus (auto-loaded)
└── commands/
    └── *.yml                — Custom commands with action lists
```

---

## 📦 Installation

1. Download the latest `HubCore-2.x.x.jar` from [Releases](../../releases)
2. Drop it into your server's `plugins/` folder
3. *(Optional)* Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for extended placeholder support
4. *(Optional)* Install [LuckPerms](https://luckperms.net) for tab group sorting and prefix/suffix formatting
5. *(Optional)* Install [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) for lobby block-break animations
6. Start the server — all config files are generated automatically
7. Customize to your liking and run `/hubcore reload` to apply changes live

**Requirements:** Paper 1.21.1 · Java 21
**Soft dependencies:** PlaceholderAPI · LuckPerms · ProtocolLib

---

## ⚙️ Quick Config Reference

<details>
<summary>animations.yml — shared animations</summary>

```yaml
MyAnimation:
  change-interval: 200   # ms between frames
  texts:
    - "&cFrame 1"
    - "&aFrame 2"
    - "&bFrame 3"
```

Use in `tab.yml` or `scoreboard.yml` with `%animation:MyAnimation%`.

</details>

<details>
<summary>menu/example.yml — custom GUI</summary>

```yaml
title: "&8My Menu"
slots: 27
items:
  survival:
    material: GRASS_BLOCK
    slot: 11
    display_name: "&aSurvival"
    lore:
      - "&7Click to join"
    glow: true
    actions:
      - "[PROXY] survival"
  lobby:
    material: COMPASS
    slot: 13
    display_name: "&bLobby"
    actions:
      - "[CLOSE]"
```

</details>

<details>
<summary>commands/example.yml — custom command</summary>

```yaml
clearinventory:
  permission: hubcore.commands.clearinventory
  aliases:
    - ci
  actions:
    - '[CONSOLE] minecraft:clear %player%'
    - '[MESSAGE] &aInventory cleared!'
```

</details>

<details>
<summary>aliases.yml — command aliases</summary>

```yaml
gamemode:
  permission: hubcore.alias.gm
  enabled: true
  aliases:
    - gm
```

</details>

---

## 🏗️ Building from source

```bash
git clone https://github.com/Indifferenzah/HubCore
cd HubCore
./gradlew shadowJar
# Output: plugin/build/libs/HubCore-2.x.x.jar
```

---

## 📜 License

This project is licensed under the **MIT License** — see [`LICENSE`](LICENSE) for details.

---

<div align="center">

Made with ❤️ by **Indifferenzah**

</div>

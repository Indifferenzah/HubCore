# <p align="center">HubCore</p>

<div align="center">
  <img src="https://i.imgur.com/ZP36KL5.png" alt="HubCore Logo"/>

**Hub & lobby management plugin for Paper 1.21.1**

![Version](https://img.shields.io/badge/version-1.0.0-blue?style=flat-square)
![Paper](https://img.shields.io/badge/Paper-1.21.1-cyan?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-blue?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-green?style=flat-square)

</div>

---

## 📖 Overview

**HubCore** is a fully-featured **hub & lobby management** plugin for Paper 1.21.1.  
It provides interactive lobby blocks, a customizable block selector GUI, and smooth player experience utilities for your server hub.

---

## ✨ Features

### 🧱 Lobby Blocks
- **Hotbar block stack** — players always have 64 blocks in a dedicated hotbar slot
- **Place & auto-remove** — placed blocks disappear after a configurable timer (no permanent changes)
- **Break with animation** — breaking a block plays a smooth crack animation via ProtocolLib (gracefully disabled if ProtocolLib is absent)
- **Block respawn** — broken blocks reappear automatically after a configurable delay
- **Blacklist** — prevent specific materials from being broken
- **Physics-safe** — placed and restored blocks never trigger unintended block physics

### 🎨 Block Selector GUI
- **In-game GUI** — right-click the selector item to open a fully configurable inventory menu
- **Per-slot configuration** — define any material, name, and lore for each GUI slot via YAML
- **Filler support** — optional filler items for empty slots, PDC-tagged to avoid accidental selection
- **Live reload** — GUI layout reloads without restarting the server
- **Custom holder** — uses a typed `InventoryHolder` for safe event filtering

### 🛠️ Admin Tools
- All lobby settings configurable in `config.yml` (slots, timers, materials, blacklist)
- Selector item fully customizable (material, name, lore, flags, unbreakable)
- ProtocolLib detection at startup — plugin loads cleanly with or without it

---

## 🗂️ Commands

| Command | Description | Permission |
|---|---|---|
| `/hubcore` | Plugin info | — |
| `/hubcore help` | Full command list | — |
| `/hubcore reload` | Reload `config.yml` and GUI | `hubcore.admin` |

---

## ⚙️ Configuration

All settings live in `plugins/HubCore/config.yml`.  
The file is generated automatically on first run.

<details>
<summary>Key settings</summary>

```yaml
lobby-blocks:
  enabled: true

  # Hotbar slot for the block stack (0-8)
  block-slot: 0

  # Hotbar slot for the selector item (0-8)
  selector-slot: 8

  # Default block material
  default-block: STONE

  # Seconds before a placed block disappears
  place-time: 10

  # Seconds before a broken block respawns
  respawn-time: 5

  # Seconds to fully break a block (10 animation stages)
  break-time: 3

  # Materials players cannot break
  blacklist:
    - BEDROCK
    - BARRIER

selector:
  material: COMPASS
  name: "&b&lSeleziona Blocco"
  lore:
    - "&7Click destro per aprire il menu"
  unbreakable: true
  flags:
    - HIDE_ATTRIBUTES
```

</details>

<details>
<summary>Block selector GUI (menu/blockselector.yml)</summary>

```yaml
title: "&8Seleziona Blocco"
rows: 3

filler:
  enabled: true
  material: GRAY_STAINED_GLASS_PANE
  name: " "

slots:
  10:
    material: STONE
    name: "&fStone"
    lore:
      - "&7Click per selezionare"
  11:
    material: GRASS_BLOCK
    name: "&aGrass Block"
    lore:
      - "&7Click per selezionare"
  # ... add as many slots as you need
```

</details>

---

## 📦 Installation

1. Download the latest `HubCore-x.x.x.jar` from [Releases](../../releases)
2. Drop it into your server's `plugins/` folder
3. *(Optional)* Install [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) for block-break animations
4. Start the server — `config.yml` and `menu/blockselector.yml` are generated automatically
5. Customize slots, timers, and materials to your liking
6. Run `/hubcore reload` to apply changes live

**Requirements:** Paper 1.21.1 · Java 21  
**Optional:** ProtocolLib (block-break animation)

---

## 🏗️ Building from source

```bash
git clone https://github.com/Indifferenzah/HubCore
cd HubCore
./gradlew shadowJar
# Output: plugin/build/libs/HubCore-x.x.x.jar
```

---

## 📜 License

This project is licensed under the **MIT License** — see [`LICENSE`](LICENSE) for details.

---

<div align="center">

Made with ❤️ by **Indifferenzah**

</div>

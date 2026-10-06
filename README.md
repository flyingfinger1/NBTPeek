# NBT Peek

A small client-side Fabric mod that shows an item's **NBT / data components** directly in its tooltip, and **copies them to the clipboard** with a keypress. Handy for datapack, command and map makers.

This is an **original, clean implementation** (not derived from any other mod's source), so it carries a clear **MIT** license and can be freely redistributed.

## Features
- Shows an item's data components in the tooltip, with configurable **trigger**:
  advanced tooltips (**F3 + H**), always, while a key is held, or toggled by a key.
- Three **display styles**: *pretty* (coloured & indented), *plain*, or *compact* (raw SNBT).
- **Copy** the hovered item's NBT to the clipboard — as compact **SNBT** (paste into `/give …[…]`) or as **pretty-printed** multi-line text, selectable in the config.
- **Scroll** long data with the scroll keys instead of flooding the screen.
- Optionally **hide** the lore / custom-name components, and the header line.
- In-game config via **Mod Menu** (Cloth Config is bundled) — or edit `config/nbtpeek.json`.

## Keybinds (rebindable under Controls → *NBT Peek*)
| Action | Default |
|---|---|
| Copy NBT to clipboard | **C** |
| Scroll up / down | **↑ / ↓** |
| Show / toggle NBT (for the key-based triggers) | unbound |

## Requirements
- Minecraft **26.3**, **Fabric Loader** + **Fabric API**, **Java 25**.
- *Mod Menu* is optional (only for the config screen); *Cloth Config* is bundled.

## Building
`./gradlew build` (JDK 25) → `build/libs/nbtpeek-mc26.3-<version>.jar`.

## License
MIT — see [`LICENSE`](LICENSE).

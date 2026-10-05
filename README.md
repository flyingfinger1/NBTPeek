# NBT Peek

A small client-side Fabric mod that shows an item's **NBT / data components** directly in its tooltip, and **copies them to the clipboard** with a keypress. Handy for datapack, command and map makers.

This is an **original, clean implementation** (not derived from any other mod's source) — so it carries a clear **MIT** license and can be freely redistributed.

## Usage
- Enable **advanced tooltips** (press **F3 + H**) and hover an item — its data components are shown under the tooltip.
- Press **C** (rebindable under Controls → *NBT Peek*) while hovering an item to copy its NBT as SNBT to the clipboard — ready to paste into a `/give …[…]` command.

## Requirements
- Minecraft **26.3**, **Fabric Loader** + **Fabric API**, **Java 25**.

## Building
`./gradlew build` (JDK 25) → `build/libs/nbtpeek-mc26.3-<version>.jar`.

## License
MIT — see [`LICENSE`](LICENSE).

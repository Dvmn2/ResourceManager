# Resource Manager

Fabric client mod for Minecraft 1.21.11. Scans all currently loaded resource packs and displays the item variants they
define as browsable entries in the creative inventory.

This project is under active development.

## Requirements

- Fabric Loader
- Fabric API
- Minecraft 1.21.11
- Client-side only

## Features

The mod scans two kinds of resource pack content and sorts the results into four creative inventory tabs.

Item-definition JSON files (`items/*.json`):

- **Item Models** — plain model overrides with no select logic (a resource pack replacing an item's model directly)
- **Custom Model Data** — select-type variants keyed by the `custom_model_data` component
- **Custom Name** — select-type variants keyed by the `custom_name` component, i.e. renaming an item in an anvil to
  trigger a different model

Equipment-asset JSON files (`equipment/*.json`):

- **Equippable** — armor and elytra variants keyed by the equipment asset's `asset_id`

Each entry is labeled with the resource pack it came from. If a tab has no matching entries, it shows a single
placeholder item instead of an empty tab.

Entries are rescanned automatically on game startup and whenever resource packs are reloaded (for example, after
applying changes in the resource pack screen). If the player is already in a world when this happens, the open creative
inventory is refreshed immediately.

## Equipment asset naming convention

An `equipment/*.json` file has no reliable way to state which armor slot it belongs to: the `layers` field is shared
between multiple slots (helmet, chestplate, and boots all use the `humanoid` layer type). To resolve the slot and base
material without guessing, the mod requires the asset's file name to end with `_materials_types`:

```
<name>_<materials>_<types>
```

- `name` — arbitrary, may contain underscores
- `materials` — one or more material codes: `l` leather, `m` chainmail, `c` copper, `i` iron, `g` gold, `d` diamond,
  `n` netherite
- `types` — one or more slot codes: `h` helmet, `c` chestplate, `l` leggings, `b` boots, `e` elytra

One entry is generated per material/type combination. `e` (elytra) ignores the material list and always produces a
single entry on `minecraft:elytra`.

Examples:

- `anarch_i_c.json` → one entry: iron chestplate
- `chaps_lig_hl.json` → six entries: leather/iron/gold × helmet/leggings
- `coat_e.json` (no material segment before `e`) → one entry: elytra

Files whose name does not match this pattern (fewer than three `_`-separated segments, or a segment containing a
character outside its alphabet) are skipped and produce no entries.

## How it works

- Item-definition and equipment-asset files are parsed with Gson; malformed files are skipped without interrupting
  the scan.
- Item-definition handlers are tried in order of specificity: `custom_name` variants and `custom_model_data` variants
  are checked before falling back to plain model overrides.
- Equipment-asset icons are matched against already-scanned `items/*.json` entries: first by exact name match on the
  full asset name (including the `_materials_types` suffix), then by name-and-slot-keyword match. This lets a single
  base asset ship different icons per material/type combination, e.g. `anarch_i_h.json` and `anarch_i_c.json` as
  separate item-definition files. If no icon matches, the entry falls back to the base item's vanilla icon.
- Results are held in memory and rebuilt from scratch on every resource reload; nothing is persisted to disk.

## Installation

1. Install Fabric Loader and Fabric API for Minecraft 1.21.11.
2. Place the mod jar in the `mods` folder.
3. Open the creative inventory to see the mod's tabs.

## Notes

This mod is read-only: it does not modify any resource pack, item, or recipe. It only reads what is already loaded and
presents it for inspection.
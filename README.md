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

The mod scans item-definition JSON files (`items/*.json`) in every loaded resource pack and sorts the results into three
creative inventory tabs:

- **Item Models** — plain model overrides with no select logic (a resource pack replacing an item's model directly)
- **Custom Model Data** — select-type variants keyed by the `custom_model_data` component
- **Custom Name** — select-type variants keyed by the `custom_name` component, i.e. renaming an item in an anvil to
  trigger a different model

Each entry is labeled with the resource pack it came from. If a tab has no matching entries, it shows a single
placeholder item instead of an empty tab.

Entries are rescanned automatically on game startup and whenever resource packs are reloaded (for example, after
applying changes in the resource pack screen). If the player is already in a world when this happens, the open creative
inventory is refreshed immediately.

## How it works

- Item-definition files are parsed with Gson; malformed files are skipped without interrupting the scan.
- Handlers are tried in order of specificity: `custom_name` variants and `custom_model_data` variants are checked before
  falling back to plain model overrides.
- Results are held in memory and rebuilt from scratch on every resource reload; nothing is persisted to disk.

## Installation

1. Install Fabric Loader and Fabric API for Minecraft 1.21.11.
2. Place the mod jar in the `mods` folder.
3. Open the creative inventory to see the mod's tabs.

## Notes

This mod is read-only: it does not modify any resource pack, item, or recipe. It only reads what is already loaded and
presents it for inspection.
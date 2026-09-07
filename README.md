# Resource Manager

Клиентский мод-инструмент для Minecraft **1.21.11** на **Fabric**, который сканирует все подключённые ресурспаки и
показывает найденные предметы/модели/варианты в трёх отдельных вкладках творческого инвентаря. Никакой геймплейной
логики мод не добавляет — только просмотр содержимого установленных ресурспаков.

## Зачем это нужно

При разработке или отладке ресурспаков (особенно с item-definition JSON'ами в стиле 1.21+, использующими
`minecraft:select`) неудобно каждый раз вручную проверять, что модели корректно резолвятся в игре. Этот мод при каждой
перезагрузке ресурсов (`Done` в экране ресурспаков) пересканирует все `items/*.json` файлы и раскладывает найденное по
трём вкладкам:

| Вкладка               | Иконка            | Что показывает                                                                                 |
|-----------------------|-------------------|------------------------------------------------------------------------------------------------|
| **item_model**        | Картина           | Простые модели (`"type": "model"`), добавленные ресурспаками (ванильные предметы пропускаются) |
| **custom_model_data** | Блок редстоуна    | Select-варианты, переключаемые по строковому значению `custom_model_data`                      |
| **custom_name**       | Табличка с именем | Select-варианты, переключаемые по компоненту `custom_name` (имя, присвоенное в наковальне)     |

Если для вкладки не нашлось ни одной записи, вместо пустой вкладки показывается предмет-заглушка (лист бумаги с серым
курсивным текстом) — чтобы не выглядело так, будто мод сломан.

## Как это работает

```
ResourcePackModelScanner (SimpleSynchronousResourceReloadListener)
        │  reload()
        ▼
ScannedItemsRegistry.clear()
        │
        ▼
manager.findAllResources("items", *.json)
        │
        ▼
ItemDefinitionParser.parseEntry(...)         — по одному JSON-файлу за раз
        │
        ▼
   ItemDefinitionHandler (цепочка, порядок важен):
     1. CustomNameVariantHandler       — select по component=custom_name
     2. CustomModelDataVariantHandler  — select по property=custom_model_data
     3. ItemModelPreviewHandler        — обычный "model"-type
        │
        ▼
ScannedItemsRegistry.*_ENTRIES.add(ItemStack)
        │
        ▼
ModItemGroups (FabricItemGroup ×3)  →  вкладки в творческом инвентаре
```

Если игрок уже находится в мире в момент перезагрузки ресурсов, `ModItemGroups.refreshEntries(...)` принудительно
обновляет все три вкладки и ванильную вкладку "Search Items", чтобы не пришлось перезаходить в инвентарь.

### Дедупликация

`ScannedItemsRegistry` хранит наборы уже увиденных ключей (`Identifier` для моделей, составной `packId:itemId:value` для
select-вариантов), поэтому повторяющиеся записи из разных паков/файлов не дублируются в рамках одного сканирования.

## Структура пакетов

```
net.dvmn2.resourcemanager.client
├── ResourceManagerClient          — точка входа клиента (ClientModInitializer)
├── ResourceManagerDataGenerator   — заглушка datagen-точки входа
├── group/
│   └── ModItemGroups              — регистрация и обновление 3 вкладок
├── registry/
│   └── ScannedItemsRegistry       — общее хранилище найденных ItemStack + дедупликация
├── scanner/
│   ├── ResourcePackModelScanner   — reload-листенер, точка запуска сканирования
│   ├── ItemDefinitionParser       — разбор одного item-definition JSON
│   └── handler/
│       ├── ItemDefinitionHandler          — интерфейс обработчика
│       ├── ItemModelPreviewHandler        — "model"-type записи
│       ├── CustomModelDataVariantHandler  — select по custom_model_data
│       └── CustomNameVariantHandler       — select по custom_name
└── util/
    ├── ModConstants        — mod id и общие константы
    ├── NbtUtils            — List<String> → NbtList
    └── ResourcePackUtils   — человекочитаемое имя пака из packId
```

## Требования

- Minecraft **1.21.11**
- **Fabric Loader** + **Fabric API**
- Java 21+

## Установка

1. Скачать `.jar` из релизов (или собрать самостоятельно, см. ниже).
2. Убедиться, что установлены Fabric Loader и Fabric API нужной версии.
3. Положить `.jar` в папку `mods`.

## Сборка из исходников

```bash
./gradlew build
```

Собранный мод появится в `build/libs/`.

## Известные ограничения

- `ItemGroups.updateDisplayContext(...)` вызывается для пересборки поискового индекса крео-инвентаря, но по факту не
  оказывает эффекта (см. комментарий в `ModItemGroups.refreshEntries`) — это ограничение самого клиента, а не бага мода.
- Мод не проверяет реальную резолвимость модели (существование файла модели/текстуры) — только структуру item-definition
  JSON.

## Локализация

Названия вкладок и текст заглушек берутся из lang-файлов через переводимые ключи:

```
itemGroup.resourcemanager.item_model
itemGroup.resourcemanager.item_model.empty
itemGroup.resourcemanager.custom_model_data
itemGroup.resourcemanager.custom_model_data.empty
itemGroup.resourcemanager.custom_name
itemGroup.resourcemanager.custom_name.empty
```

## Лицензия

Укажите здесь лицензию проекта (например, MIT / CC0 / All Rights Reserved).
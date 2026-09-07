# ResourceManager

## Структура пакета

```
net.dvmn2.resourcemanager.client
├── ResourceManagerClient.java        — точка входа клиента (ClientModInitializer)
├── util/
│   ├── ModConstants.java           — MOD_ID и прочие константы
│   ├── NbtUtils.java               — List<String> → NbtList
│   └── ResourcePackUtils.java      — packId → человекочитаемое имя пака
├── registry/
│   └── ScannedItemsRegistry.java   — три списка ItemStack + дедупликация
├── group/
│   └── ModItemGroups.java          — регистрация и обновление трёх ItemGroup
└── scanner/
    ├── ResourcePackModelScanner.java  — SimpleSynchronousResourceReloadListener
    ├── ItemDefinitionParser.java      — читает один JSON, определяет id предмета
    └── handler/
        ├── ItemDefinitionHandler.java          — общий интерфейс обработчика
        ├── CustomNameVariantHandler.java        — select по custom_name
        ├── CustomModelDataVariantHandler.java   — select по custom_model_data
        └── ItemModelPreviewHandler.java         — обычная "model"-запись
```

## Как это работает

1. `ResourcePackModelScanner` подписывается на перезагрузку клиентских ресурсов и при каждом срабатывании сканирует
   папку `items/` во всех подключённых ресурспаках.
2. Каждый найденный JSON передаётся в `ItemDefinitionParser`, который вычисляет id предмета по пути файла и отдаёт
   объект `"model"` первому подходящему обработчику из `scanner.handler`:
    - **`CustomNameVariantHandler`** — если это select-модель по компоненту
      `custom_name` (обычно так ресурспаки подменяют внешний вид предмета, переименованного на наковальне). Каждый
      уникальный вариант становится отдельным `ItemStack` с уже присвоенным именем → вкладка `custom_name`.
    - **`CustomModelDataVariantHandler`** — если это select-модель по строковому значению `custom_model_data`. Каждый
      вариант получает соответствующий компонент `CUSTOM_MODEL_DATA` → вкладка
      `custom_model_data`.
    - **`ItemModelPreviewHandler`** — если это обычная запись модели без select-логики. Создаётся `ItemStack` на основе
      бумаги (`Items.PAPER`)
      с компонентом `ITEM_MODEL`, указывающим на найденную модель, чтобы показать её без реального предмета → вкладка
      `item_model`.
3. Результаты складываются в статические списки `ScannedItemsRegistry`, которые вкладки (`ModItemGroups`) просто
   перебирают в `entries(...)`.
4. Если игрок уже находится в мире во время перезагрузки — `ModItemGroups`
   принудительно обновляет содержимое вкладок, чтобы не нужно было перезаходить в книгу рецептов.

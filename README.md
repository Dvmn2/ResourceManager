# Resourseitems — новая структура мода

Тот же мод (Fabric, Minecraft 1.21.11), но разложенный из одного файла на логические классы. Идея: три вкладки
творческого инвентаря —
`item_model`, `custom_model_data`, `custom_name` — показывают содержимое подключённых ресурспаков.

## Структура пакетов

```
net.spogbot.resourseitems.client
├── ResourseitemsClient.java        — точка входа клиента (ClientModInitializer)
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

## Что изменено по сравнению с оригиналом (не только структура)

- Логика разбита по единой ответственности (SRP): парсинг JSON, поиск предметов, хранение результатов и UI-регистрация —
  отдельные классы.
- Убран неиспользуемый параметр `packsWithItems` и мёртвая ветка сканирования `models/item` (в исходнике вызывался
  только `scanAndProcess(..., "items", ...)`).
- `ItemModelPreviewHandler` теперь при наличии в JSON поля `"model"`
  (строки-ссылки на реальный путь модели) использует именно её, а не всегда id самого предмета — это точнее
  соответствует формату 1.21.x и подстрахует от несовпадения путей предмета/модели.
- Варианты `custom_model_data` теперь получают понятное имя вида
  `Предмет [значение]`, чтобы в вкладке не было визуально одинаковых записей без возможности их отличить (в оригинале
  имя не задавалось).
- Свои ключи локализации вместо переиспользования сторонних (`gui.title.items` → `itemGroup.resourceitems.item_model` и
  т. д.).

## Как подключить

1. Скопируйте `src/main/java/...` и `src/main/resources/...` в свой проект мода поверх текущих файлов (замените старый
   `ResourseitemsClient.java`).
2. Точка входа в `fabric.mod.json` остаётся прежней — класс не переехал в другой пакет:
   ```json
   "entrypoints": {
     "client": [
       "net.spogbot.resourseitems.client.ResourseitemsClient"
     ]
   }
   ```
3. Убедитесь, что зависимость `fabric-item-group-api-v1` и
   `fabric-resource-loader-v0` подключены (как и раньше — это те же API, которые использовал исходный файл).
4. Пересоберите мод (`./gradlew build`) — новых зависимостей не добавлено.

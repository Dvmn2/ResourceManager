package net.spogbot.resourseitems.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Клиентская точка входа мода "Resourseitems".
 * <p>
 * Мод предназначен только для просмотра (браузинга) содержимого установленных
 * ресурспаков в творческом режиме — для этого регистрируются две отдельные
 * вкладки инвентаря ("Models" и "Renamed"). Никакой геймплейной интеграции
 * (например, с наковальней) мод больше не содержит — это чисто
 * административный/отладочный инструмент.
 * <p>
 * Логика работы:
 * 1. При загрузке/перезагрузке ресурсов мод сканирует все JSON-файлы предметов
 * (item definitions, папка "items") и все модели предметов (папка
 * "models/item") во всех подключенных ресурспаках.
 * 2. Обычные модели превращаются в ItemStack на основе Items.PAPER с
 * компонентом ITEM_MODEL, указывающим на найденную модель — так игрок
 * может увидеть, как выглядит модель, не имея реального предмета.
 * 3. Если JSON описывает модель типа "select" по компоненту "custom_name"
 * (распространённый приём в ресурспаках для замены внешнего вида предмета
 * в зависимости от его имени, например "переименованные" вариации), такой
 * файл считается "renamed"-моделью и превращается в отдельный ItemStack
 * с уже присвоенным именем — чтобы его можно было найти и посмотреть в
 * отдельной вкладке "Renamed".
 */
@Environment(EnvType.CLIENT)
public class ResourseitemsClient implements ClientModInitializer {

    // Идентификатор вкладки с обычными моделями предметов ресурспака.
    private static final RegistryKey<ItemGroup> MODELS_TAB_KEY = RegistryKey.of(
            RegistryKeys.ITEM_GROUP,
            Identifier.of("resourcemodels", "models_tab")
    );

    // Идентификатор вкладки с "переименованными" вариациями предметов
    // (варианты модели, привязанные к конкретным альтернативным именам).
    private static final RegistryKey<ItemGroup> RENAMED_TAB_KEY = RegistryKey.of(
            RegistryKeys.ITEM_GROUP,
            Identifier.of("resourcemodels", "renamed_items_tab")
    );

    // Идентификатор вкладки с "ремоделенными" вариациями предметов
    // (варианты модели, привязанные к конкретным custom_model_data).
    private static final RegistryKey<ItemGroup> REMODELED_TAB_KEY = RegistryKey.of(
            RegistryKeys.ITEM_GROUP,
            Identifier.of("resourcemodels", "remodeled_items_tab")
    );

    // Списки предметов, которые будут отображены во вкладках. Заполняются
    // при (пере)загрузке ресурсов в reload-листенере ниже.
    public static final List<ItemStack> LOADED_MODELS = new ArrayList<>();
    public static final List<ItemStack> LOADED_RENAMED_ITEMS = new ArrayList<>();
    public static final List<ItemStack> LOADED_REMODELED_ITEMS = new ArrayList<>();

    // Наборы для дедупликации: не добавлять одну и ту же модель дважды.
    private static final Set<Identifier> SEEN_MODELS = new HashSet<>();
    private static final Set<String> SEEN_RENAMED = new HashSet<>();
    private static final Set<String> SEEN_REMODELED = new HashSet<>();

    @Override
    public void onInitializeClient() {
        // Регистрируем вкладку "Models" — витрина всех найденных моделей предметов.
        Registry.register(Registries.ITEM_GROUP, MODELS_TAB_KEY, FabricItemGroup.builder()
                .icon(() -> new ItemStack(Items.PAINTING))
                .displayName(Text.translatable("gui.title.items"))
                .entries((context, entries) -> {
                    for (ItemStack stack : LOADED_MODELS) {
                        entries.add(stack);
                    }
                })
                .build());

        // Регистрируем вкладку "Renamed" — витрина переименованных вариаций предметов.
        Registry.register(Registries.ITEM_GROUP, RENAMED_TAB_KEY, FabricItemGroup.builder()
                .icon(() -> new ItemStack(Items.NAME_TAG))
                .displayName(Text.translatable("gui.title.renames"))
                .entries((context, entries) -> {
                    for (ItemStack stack : LOADED_RENAMED_ITEMS) {
                        entries.add(stack);
                    }
                })
                .build());

        // Регистрируем вкладку "Remodeled" — витрина ремоделенных вариаций предметов.
        Registry.register(Registries.ITEM_GROUP, REMODELED_TAB_KEY, FabricItemGroup.builder()
                .icon(() -> new ItemStack(Items.REDSTONE_BLOCK))
                .displayName(Text.translatable("gui.title.remodels"))
                .entries((context, entries) -> {
                    for (ItemStack stack : LOADED_REMODELED_ITEMS) {
                        entries.add(stack);
                    }
                })
                .build());

        // Слушатель перезагрузки клиентских ресурсов: пересканирует ресурспаки
        // каждый раз, когда игрок открывает игру или нажимает F3+T / "Done"
        // в экране настройки ресурспаков.
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.of("resourcemodels", "model_scanner");
            }

            @Override
            public void reload(ResourceManager manager) {
                // Очищаем старые данные перед новым сканированием.
                LOADED_MODELS.clear();
                LOADED_RENAMED_ITEMS.clear();
                LOADED_REMODELED_ITEMS.clear();
                SEEN_MODELS.clear();
                SEEN_RENAMED.clear();
                SEEN_REMODELED.clear();

                // Собираем идентификаторы паков, в которых есть хотя бы один
                // файл в папке "items" — это нужно, чтобы не показывать модели
                // из "models/item", которые принадлежат пакам без собственных
                // item-определений (то есть, по сути, ванильным/чужим паком).
                Set<String> packsWithItems = new HashSet<>();
                Map<Identifier, List<Resource>> allItemsResources = manager.findAllResources("items", path -> true);
                for (List<Resource> resourcesList : allItemsResources.values()) {
                    for (Resource res : resourcesList) {
                        packsWithItems.add(res.getPackId());
                    }
                }

                // Сканируем источник моделей.
                scanAndProcess(manager, "items", packsWithItems);

                // Если игрок уже находится в игровом мире — принудительно
                // обновляем содержимое обеих вкладок, чтобы новые предметы
                // сразу появились без перезахода в творческую инвентарную книгу.
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.world != null && client.player != null && client.player.networkHandler != null) {
                    ItemGroup.DisplayContext context = new ItemGroup.DisplayContext(
                            client.player.networkHandler.getEnabledFeatures(),
                            client.options.getOperatorItemsTab().getValue(),
                            client.world.getRegistryManager()
                    );

                    ItemGroup modelsTab = Registries.ITEM_GROUP.get(MODELS_TAB_KEY);
                    if (modelsTab != null) modelsTab.updateEntries(context);

                    ItemGroup renamedTab = Registries.ITEM_GROUP.get(RENAMED_TAB_KEY);
                    if (renamedTab != null) renamedTab.updateEntries(context);

                    ItemGroup remodeledTab = Registries.ITEM_GROUP.get(REMODELED_TAB_KEY);
                    if (remodeledTab != null) remodeledTab.updateEntries(context);
                }
            }

            /**
             * Сканирует все JSON-ресурсы под указанным путём ("items" или
             * "models/item") и превращает их либо в обычную "модель для
             * просмотра" (Items.PAPER + ITEM_MODEL), либо, если файл описывает
             * select-модель по custom_name, в набор "переименованных"
             * вариаций с соответствующими альтернативными именами.
             */
            private void scanAndProcess(ResourceManager manager, String startingPath, Set<String> packsWithItems) {
                Map<Identifier, List<Resource>> resourcesMap = manager.findAllResources(
                        startingPath,
                        path -> path.getPath().endsWith(".json")
                );

                for (Map.Entry<Identifier, List<Resource>> entry : resourcesMap.entrySet()) {
                    Identifier resourceId = entry.getKey();
                    String fullPath = resourceId.getPath();
                    // Убираем расширение ".json".
                    String modelPath = fullPath.substring(0, fullPath.length() - 5);

                    // Вычисляем "чистый" идентификатор предмета/модели, убирая
                    // префикс каталога ("items/" или "models/item/").
                    String modelIdStr;
                    if (startingPath.equals("items")) {
                        modelIdStr = modelPath.substring(5); // убираем "items"
                    } else {
                        modelIdStr = modelPath.substring(11); // убираем "models/item"
                    }
                    if (modelIdStr.startsWith("/")) modelIdStr = modelIdStr.substring(1);
                    if (modelIdStr.isEmpty()) continue;

                    Identifier modelId = Identifier.of(resourceId.getNamespace(), modelIdStr);

                    for (Resource resource : entry.getValue()) {

                        // --- Пытаемся распознать renamed/remodeled-предмет (select по custom_name/custom_model_data) ---
                        try (InputStream is = resource.getInputStream();
                             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

                            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                            if (root.has("model") && root.get("model").isJsonObject()) {
                                JsonObject modelObj = root.getAsJsonObject("model");

                                // Признак renamed-предмета
                                if (modelObj.has("type") && modelObj.get("type").getAsString().endsWith("select") &&
                                        modelObj.has("property") && modelObj.get("property").getAsString().endsWith("component") &&
                                        modelObj.has("component") && modelObj.get("component").getAsString().endsWith("custom_name") &&
                                        modelObj.has("cases")) {

                                    Identifier baseItemId = Identifier.of(resourceId.getNamespace(), modelIdStr);
                                    Item baseItem = Registries.ITEM.get(baseItemId);

                                    // Игнорируем, если такого ванильного/модового
                                    // предмета не существует (Items.AIR = "не найден").
                                    if (baseItem != Items.AIR) {
                                        JsonArray cases = modelObj.getAsJsonArray("cases");
                                        for (JsonElement caseEl : cases) {
                                            if (!caseEl.isJsonObject()) continue;
                                            JsonObject caseObj = caseEl.getAsJsonObject();

                                            if (caseObj.has("when")) {
                                                JsonElement whenEl = caseObj.get("when");
                                                List<String> allNames = new ArrayList<>();

                                                // "when" может быть как одиночной строкой,
                                                // так и массивом синонимичных имён.
                                                if (whenEl.isJsonArray()) {
                                                    for (JsonElement e : whenEl.getAsJsonArray()) {
                                                        allNames.add(e.getAsString());
                                                    }
                                                } else if (whenEl.isJsonPrimitive()) {
                                                    allNames.add(whenEl.getAsString());
                                                }

                                                if (!allNames.isEmpty()) {
                                                    String primaryName = allNames.get(0);

                                                    // Уникальный ключ по паку+предмету+имени,
                                                    // чтобы не дублировать один и тот же вариант.
                                                    String uniqueKey = resource.getPackId() + ":" + baseItemId + ":" + primaryName;

                                                    if (SEEN_RENAMED.add(uniqueKey)) {
                                                        ItemStack renamedStack = new ItemStack(baseItem);

                                                        // Сохраняем все альтернативные имена в NBT,
                                                        // чтобы их можно было показать в тултипе
                                                        // (используется в UI просмотра, если он есть).
                                                        NbtList nameList = new NbtList();
                                                        for (String name : allNames) {
                                                            nameList.add(NbtString.of(name));
                                                        }
                                                        NbtCompound data = new NbtCompound();
                                                        data.put("alternative_names", nameList);

                                                        String packId = resource.getPackId();
                                                        String packName = packId.replace("file/", "").replace(".zip", "");
                                                        data.putString("pack_name", packName);

                                                        renamedStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
                                                        renamedStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(primaryName));

                                                        LOADED_RENAMED_ITEMS.add(renamedStack);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Признак remodeled-предмета
                                if (modelObj.has("type") && modelObj.get("type").getAsString().endsWith("select") &&
                                        modelObj.has("property") && modelObj.get("property").getAsString().endsWith("custom_model_data") &&
                                        modelObj.has("index") && modelObj.get("index").getAsInt() == 0 &&
                                        modelObj.has("cases")) {

                                    Identifier baseItemId = Identifier.of(resourceId.getNamespace(), modelIdStr);
                                    Item baseItem = Registries.ITEM.get(baseItemId);

                                    // Игнорируем, если такого ванильного/модового
                                    // предмета не существует (Items.AIR = "не найден").
                                    if (baseItem != Items.AIR) {
                                        JsonArray cases = modelObj.getAsJsonArray("cases");
                                        for (JsonElement caseEl : cases) {
                                            if (!caseEl.isJsonObject()) continue;
                                            JsonObject caseObj = caseEl.getAsJsonObject();

                                            if (caseObj.has("when")) {
                                                JsonElement whenEl = caseObj.get("when");
                                                List<String> allNames = new ArrayList<>();

                                                // "when" может быть как одиночной строкой,
                                                // так и массивом синонимичных имён.
                                                if (whenEl.isJsonArray()) {
                                                    for (JsonElement e : whenEl.getAsJsonArray()) {
                                                        allNames.add(e.getAsString());
                                                    }
                                                } else if (whenEl.isJsonPrimitive()) {
                                                    allNames.add(whenEl.getAsString());
                                                }

                                                if (!allNames.isEmpty()) {
                                                    String primaryName = allNames.get(0);

                                                    // Уникальный ключ по паку+предмету+имени,
                                                    // чтобы не дублировать один и тот же вариант.
                                                    String uniqueKey = resource.getPackId() + ":" + baseItemId + ":" + primaryName;

                                                    if (SEEN_REMODELED.add(uniqueKey)) {
                                                        ItemStack renamedStack = new ItemStack(baseItem);

                                                        // Сохраняем все альтернативные имена в NBT,
                                                        // чтобы их можно было показать в тултипе
                                                        // (используется в UI просмотра, если он есть).
                                                        NbtList nameList = new NbtList();
                                                        for (String name : allNames) {
                                                            nameList.add(NbtString.of(name));
                                                        }
                                                        NbtCompound data = new NbtCompound();
                                                        data.put("alternative_model_data", nameList);

                                                        String packId = resource.getPackId();
                                                        String packName = packId.replace("file/", "").replace(".zip", "");
                                                        data.putString("pack_name", packName);

                                                        List<String> strings = new ArrayList<>();

                                                        renamedStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
                                                        renamedStack.set(
                                                                DataComponentTypes.CUSTOM_MODEL_DATA,
                                                                new CustomModelDataComponent(
                                                                        List.of(),                // floats
                                                                        List.of(),                // flags
                                                                        List.of(primaryName),     // strings
                                                                        List.of()                 // colors
                                                                )
                                                        );

                                                        LOADED_REMODELED_ITEMS.add(renamedStack);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Признак remodeled-предмета
                                if (modelObj.has("type") && modelObj.get("type").getAsString().endsWith("model") &&
                                        modelObj.has("model")) {


                                    // --- Обычные модели (просто "витрина" для ресурспака) ---
                                    if (resourceId.getNamespace().equals("minecraft")) continue;
                                    if (!SEEN_MODELS.add(modelId)) continue;

                                    String fileName = modelId.getPath().substring(modelId.getPath().lastIndexOf('/') + 1)
                                            .replace('_', ' ');


                                    // Используем бумагу как "болванку", которой присваиваем
                                    // нужную модель через компонент ITEM_MODEL — так предмет
                                    // выглядит как модель из ресурспака, даже если реального
                                    // предмета с такой моделью не существует.
                                    ItemStack stack = new ItemStack(Items.PAPER);
                                    stack.set(DataComponentTypes.ITEM_MODEL, modelId);
                                    stack.set(DataComponentTypes.CUSTOM_NAME,
                                            Text.literal(fileName).styled(style -> style.withItalic(false)));

                                    LOADED_MODELS.add(stack);
                                }
                            }
                        } catch (Exception ignored) {
                            // Файл не является валидным JSON-описанием модели — пропускаем.
                        }
                    }
                }
            }
        });
    }
}
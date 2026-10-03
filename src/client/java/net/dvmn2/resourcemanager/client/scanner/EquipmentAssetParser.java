package net.dvmn2.resourcemanager.client.scanner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dvmn2.resourcemanager.client.registry.ScannedItemsRegistry;
import net.dvmn2.resourcemanager.client.util.ResourcePackUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentAssetKeys;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Разбирает один equipment-asset JSON файл (из папки {@code "equipment/"}
 * ресурспака, например {@code assets/armor/equipment/anarch_i_c.json}) и
 * регистрирует по нему предмет(ы)-превью.
 * <p>
 * <b>Единственный источник истины для слота и материала — имя файла</b>,
 * по схеме {@code name_materials_types}:
 * <pre>
 * name      — произвольное имя (может содержать "_")
 * materials — строка из букв: l-кожа, m-кольчуга, c-медь, i-железо,
 *             g-золото, d-алмаз, n-незерит
 * types     — строка из букв: h-шлем, c-нагрудник, l-поножи, b-ботинки,
 *             e-элитра (у элитры материалов не бывает)
 * </pre>
 * Пример: {@code anarch_i_c} → железная кираса. Пример:
 * {@code chaps_lig_hl} → 6 превью (кожа/железо/золото × шлем/поножи).
 * <p>
 * Валидны только последние два {@code "_"}-сегмента имени. Если суффикс
 * не проходит валидацию (не все символы из своего алфавита, или сегментов
 * меньше трёх), ассет считается не размеченным и пропускается.
 * <p>
 * <b>Звук надевания.</b> Если в загруженных ресурспаках есть
 * {@code sounds.json} с ключом, совпадающим с ПОЛНЫМ именем ассета
 * (включая суффикс {@code _materials_types}, например {@code anarch_i_c}),
 * этот звук используется как звук надевания вместо звука материала. Иначе
 * используется звук надевания реального базового предмета
 * (см. {@link #resolveEquipSound}).
 * <p>
 * <b>Иконки.</b> Если для этого equip-варианта нашлась своя иконка среди
 * записей вкладки {@code item_model} (см. {@link #matchIcon}), эта запись
 * удаляется из вкладки {@code item_model} — см.
 * {@link #removeMatchedIconsFromItemModelTab()} — чтобы одна и та же
 * модель не отображалась дважды: и как отдельное превью, и как иконка
 * брони.
 */
public final class EquipmentAssetParser {

    private static final String EQUIPMENT_DIR_PREFIX = "equipment";
    private static final String JSON_EXTENSION = ".json";
    private static final String VANILLA_PACK_ID = "vanilla";
    private static final String SOUNDS_MANIFEST_FILE = "sounds.json";

    private static final Map<Character, EquipmentSlot> TYPE_ALPHABET = Map.of(
            'h', EquipmentSlot.HEAD,
            'c', EquipmentSlot.CHEST,
            'l', EquipmentSlot.LEGS,
            'b', EquipmentSlot.FEET,
            'e', EquipmentSlot.CHEST // элитра физически занимает слот кирасы
    );

    private static final char ELYTRA_TYPE_CODE = 'e';

    // material-код -> слот -> реальный vanilla-предмет этого материала/слота.
    private static final Map<Character, Map<EquipmentSlot, Item>> MATERIAL_ITEMS = Map.of(
            'l', Map.of(EquipmentSlot.HEAD, Items.LEATHER_HELMET, EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE,
                    EquipmentSlot.LEGS, Items.LEATHER_LEGGINGS, EquipmentSlot.FEET, Items.LEATHER_BOOTS),
            'm', Map.of(EquipmentSlot.HEAD, Items.CHAINMAIL_HELMET, EquipmentSlot.CHEST, Items.CHAINMAIL_CHESTPLATE,
                    EquipmentSlot.LEGS, Items.CHAINMAIL_LEGGINGS, EquipmentSlot.FEET, Items.CHAINMAIL_BOOTS),
            'c', Map.of(EquipmentSlot.HEAD, Items.COPPER_HELMET, EquipmentSlot.CHEST, Items.COPPER_CHESTPLATE,
                    EquipmentSlot.LEGS, Items.COPPER_LEGGINGS, EquipmentSlot.FEET, Items.COPPER_BOOTS),
            'i', Map.of(EquipmentSlot.HEAD, Items.IRON_HELMET, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE,
                    EquipmentSlot.LEGS, Items.IRON_LEGGINGS, EquipmentSlot.FEET, Items.IRON_BOOTS),
            'g', Map.of(EquipmentSlot.HEAD, Items.GOLDEN_HELMET, EquipmentSlot.CHEST, Items.GOLDEN_CHESTPLATE,
                    EquipmentSlot.LEGS, Items.GOLDEN_LEGGINGS, EquipmentSlot.FEET, Items.GOLDEN_BOOTS),
            'd', Map.of(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE,
                    EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, EquipmentSlot.FEET, Items.DIAMOND_BOOTS),
            'n', Map.of(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE,
                    EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, EquipmentSlot.FEET, Items.NETHERITE_BOOTS)
    );

    // Метки слотов — нужны только для поиска иконки по ключевому слову.
    private static final Map<EquipmentSlot, String> SLOT_LABELS = Map.of(
            EquipmentSlot.HEAD, "helmet",
            EquipmentSlot.CHEST, "chestplate",
            EquipmentSlot.LEGS, "leggings",
            EquipmentSlot.FEET, "boots"
    );

    // Идентификаторы звуковых событий, объявленных в sounds.json загруженных
    // паков (namespace:key). Перечитывается на каждом reload.
    private final Set<Identifier> knownSoundEventIds = new HashSet<>();

    // Иконки, которые уже "забрал себе" какой-то equip-вариант в этом
    // reload'е — их нужно убрать из вкладки item_model после сканирования.
    private final Set<Identifier> usedIconIds = new HashSet<>();

    /**
     * Готовит парсер к новому reload'у: очищает список забранных иконок и
     * перечитывает {@code sounds.json} всех подключённых паков. Нужно
     * вызвать ДО {@link #parseEntry} — см. {@link ResourcePackModelScanner#reload}.
     */
    public void prepareReload(ResourceManager manager) {
        usedIconIds.clear();
        loadSoundEvents(manager);
    }

    private void loadSoundEvents(ResourceManager manager) {
        knownSoundEventIds.clear();
        for (String namespace : manager.getAllNamespaces()) {
            manager.getResource(Identifier.of(namespace, SOUNDS_MANIFEST_FILE)).ifPresent(resource -> {
                try (InputStream inputStream = resource.getInputStream();
                     InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    for (String key : root.keySet()) {
                        knownSoundEventIds.add(Identifier.of(namespace, key));
                    }
                } catch (Exception ignored) {
                    // sounds.json необязателен и может быть невалидным — не критично.
                }
            });
        }
    }

    /**
     * Удаляет из вкладки {@code item_model} все иконки, которые в этом
     * reload'е уже использованы как иконка какого-то equip-варианта.
     * Вызывается ПОСЛЕ того, как все {@code equipment/*.json} разобраны —
     * см. {@link ResourcePackModelScanner#reload}.
     */
    public void removeMatchedIconsFromItemModelTab() {
        ScannedItemsRegistry.ITEM_MODEL_ENTRIES.removeIf(stack -> {
            Identifier iconId = stack.get(DataComponentTypes.ITEM_MODEL);
            return iconId != null && usedIconIds.contains(iconId);
        });
    }

    /**
     * Разбирает все ресурсы, найденные под одним и тем же путём (переопределения из разных паков).
     */
    public void parseEntry(Identifier resourceId, List<Resource> resources) {
        Identifier assetId = toAssetId(resourceId);
        if (assetId == null) return;

        for (Resource resource : resources) {
            parseSingleResource(assetId, resource);
        }
    }

    private void parseSingleResource(Identifier assetId, Resource resource) {
        if (VANILLA_PACK_ID.equals(resource.getPackId())) return; // ванильная броня — не интересна

        List<NamedVariant> variants = parseNamingConvention(assetId);
        if (variants == null) return; // имя не размечено по схеме — пропускаем

        try (InputStream inputStream = resource.getInputStream();
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {

            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("layers")) return; // не похоже на equipment-asset

            String uniqueKey = resource.getPackId() + ":" + assetId;
            if (!ScannedItemsRegistry.markEquippableAssetSeen(uniqueKey)) return; // уже добавлен ранее

            String packName = ResourcePackUtils.displayName(resource);
            for (NamedVariant variant : variants) {
                registerVariant(assetId, variant.slot(), variant.baseItem(), packName);
            }
        } catch (Exception ignored) {
            // Осознанно широкий catch — см. аналогичный комментарий в ItemDefinitionParser.
        }
    }

    /**
     * Один разобранный по имени файла вариант: конкретный слот + конкретный
     * базовый предмет (материал), либо элитра.
     */
    private record NamedVariant(EquipmentSlot slot, Item baseItem) {
    }

    /**
     * Пытается разобрать имя ассета (последний path-сегмент identifier'а, без
     * расширения) по схеме {@code name_materials_types}. Возвращает
     * {@code null}, если имя не размечено — тогда ассет пропускается целиком.
     */
    private List<NamedVariant> parseNamingConvention(Identifier assetId) {
        String[] parts = assetId.getPath().split("_");
        if (parts.length < 3) return null;

        String materials = parts[parts.length - 2];
        String types = parts[parts.length - 1];

        if (materials.isEmpty() || types.isEmpty()) return null;
        for (char c : materials.toCharArray()) {
            if (!MATERIAL_ITEMS.containsKey(c)) return null;
        }
        for (char c : types.toCharArray()) {
            if (!TYPE_ALPHABET.containsKey(c)) return null;
        }

        List<NamedVariant> variants = new ArrayList<>();
        for (char typeChar : types.toCharArray()) {
            EquipmentSlot slot = TYPE_ALPHABET.get(typeChar);
            if (typeChar == ELYTRA_TYPE_CODE) {
                variants.add(new NamedVariant(slot, Items.ELYTRA));
                continue; // у элитры нет материальных вариантов
            }
            for (char materialChar : materials.toCharArray()) {
                variants.add(new NamedVariant(slot, MATERIAL_ITEMS.get(materialChar).get(slot)));
            }
        }
        return variants;
    }

    /**
     * Убирает суффикс {@code _materials_types} из пути ассета, оставляя
     * только "чистое" имя — для отображения в названии предмета (без
     * namespace, без материала и типа), аналогично тому, как
     * {@link net.dvmn2.resourcemanager.client.scanner.handler.CustomModelDataVariantHandler}
     * показывает только вариант, а не полный путь.
     */
    private String baseName(Identifier assetId) {
        String[] parts = assetId.getPath().split("_");
        return String.join("_", Arrays.copyOf(parts, parts.length - 2));
    }

    private void registerVariant(Identifier assetId, EquipmentSlot slot, Item baseItem, String packName) {
        RegistryKey<EquipmentAsset> assetKey =
                RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, assetId);

        EquippableComponent.Builder builder =
                EquippableComponent.builder(slot).model(assetKey);

        RegistryEntry<SoundEvent> equipSound = resolveEquipSound(assetId, baseItem);
        if (equipSound != null) {
            builder.equipSound(equipSound);
        }

        ItemStack stack = new ItemStack(baseItem);

        NbtCompound customData = new NbtCompound();
        customData.putString("pack_name", packName);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(customData));

        stack.set(DataComponentTypes.EQUIPPABLE, builder.build());

        // Поиск иконки — по ПОЛНОМУ имени ассета (с суффиксом materials_types),
        // чтобы разным типам одного и того же ассета можно было положить в
        // items/ разные иконки с такими же суффиксами.
        Identifier icon = matchIcon(assetId, slot);
        if (icon != null) {
            stack.set(DataComponentTypes.ITEM_MODEL, icon);
            usedIconIds.add(icon); // забираем иконку из вкладки item_model
        }

        // Формат имени — как у CustomModelDataVariantHandler: "<ванильное
        // имя базового предмета> [<имя ассета без namespace/материала/типа>]".
        stack.set(
                DataComponentTypes.CUSTOM_NAME,
                Text.literal(baseItem.getName().getString() + " [" + baseName(assetId) + "]")
                        .styled(style -> style.withItalic(false))
        );

        ScannedItemsRegistry.EQUIPPABLE_ENTRIES.add(stack);
    }

    /**
     * Звук надевания: если в подключённых паках есть {@code sounds.json} с
     * ключом, равным ПОЛНОМУ имени ассета (например {@code anarch_i_c}),
     * используется он — через "прямую" (не обязательно зарегистрированную)
     * ссылку на звуковое событие. Иначе используется звук надевания
     * реального базового предмета ({@code baseItem}).
     */
    private RegistryEntry<SoundEvent> resolveEquipSound(Identifier assetId, Item baseItem) {
        if (knownSoundEventIds.contains(assetId)) {
            return RegistryEntry.of(SoundEvent.of(assetId));
        }

        EquippableComponent baseComponent = baseItem.getComponents().get(DataComponentTypes.EQUIPPABLE);
        return baseComponent != null ? baseComponent.equipSound() : null;
    }

    /**
     * Ищет среди уже отсканированных записей вкладки {@code item_model}
     * (того же namespace, что и asset_id) подходящую кастомную иконку —
     * сначала по точному совпадению полного имени ассета (с суффиксом),
     * а если такого нет — по вхождению имени ассета И ключевого слова
     * слота. Если ничего не подошло — возвращает {@code null}, и предмет
     * останется с ванильной иконкой base item'а.
     */
    private Identifier matchIcon(Identifier assetId, EquipmentSlot slot) {
        String namespace = assetId.getNamespace();
        String assetName = assetId.getPath().toLowerCase(Locale.ROOT);
        String slotLabel = SLOT_LABELS.get(slot);

        Identifier exactMatch = null;
        Identifier byNameAndSlot = null;
        Identifier byNameOnly = null;
        int nameOnlyMatches = 0;

        for (ItemStack stack : ScannedItemsRegistry.ITEM_MODEL_ENTRIES) {
            Identifier modelId = stack.get(DataComponentTypes.ITEM_MODEL);
            if (modelId == null || !modelId.getNamespace().equals(namespace)) continue;

            String modelPath = modelId.getPath().toLowerCase(Locale.ROOT);

            if (modelPath.equals(assetName)) {
                exactMatch = modelId;
                continue;
            }
            if (!modelPath.contains(assetName)) continue;

            nameOnlyMatches++;
            byNameOnly = modelId;
            if (slotLabel != null && modelPath.contains(slotLabel)) {
                byNameAndSlot = modelId;
            }
        }

        if (exactMatch != null) return exactMatch;
        if (byNameAndSlot != null) return byNameAndSlot;
        return nameOnlyMatches == 1 ? byNameOnly : null;
    }

    /**
     * Превращает путь ресурса {@code "equipment/foo.json"} в asset_id {@code "ns:foo"}.
     */
    private Identifier toAssetId(Identifier resourceId) {
        String path = resourceId.getPath();
        if (!path.endsWith(JSON_EXTENSION)) return null;

        String withoutExtension = path.substring(0, path.length() - JSON_EXTENSION.length());
        if (!withoutExtension.startsWith(EQUIPMENT_DIR_PREFIX)) return null;

        String assetPath = withoutExtension.substring(EQUIPMENT_DIR_PREFIX.length());
        if (assetPath.startsWith("/")) assetPath = assetPath.substring(1);
        if (assetPath.isEmpty()) return null;

        return Identifier.of(resourceId.getNamespace(), assetPath);
    }
}
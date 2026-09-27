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
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Разбирает один equipment-asset JSON файл (из папки {@code "equipment/"}
 * ресурспака, например {@code assets/shadowmod/equipment/shadow.json}) и
 * регистрирует по нему 4 предмета-превью (шлем/нагрудник/поножи/ботинки),
 * каждый со своим {@link EquippableComponent}, ссылающимся на найденный
 * {@code asset_id}.
 * <p>
 * В отличие от {@link ItemDefinitionParser} (папка {@code "items/"}, влияет
 * на модель ПРЕДМЕТА в руке/инвентаре), asset_id из {@code "equipment/"}
 * влияет на текстуру брони НА ПЕРСОНАЖЕ и не привязан напрямую к какому-то
 * конкретному item id — поэтому это отдельный скан со своим списком в
 * {@link ScannedItemsRegistry}.
 * <p>
 * Иконка предмета в инвентаре при этом пытается матчиться с уже
 * найденными (на момент вызова) записями вкладки {@code item_model} по
 * совпадению namespace и вхождению имени asset_id в путь модели — см.
 * {@link #matchIcon(Identifier, EquipmentSlot)}. Из-за этого важно, чтобы
 * {@code items/} сканировались РАНЬШЕ {@code equipment/} в одном reload
 * (см. {@link ResourcePackModelScanner#reload}).
 */
public final class EquipmentAssetParser {

    private static final String EQUIPMENT_DIR_PREFIX = "equipment";
    private static final String JSON_EXTENSION = ".json";
    private static final String VANILLA_PACK_ID = "vanilla";

    private static final Map<EquipmentSlot, Item> SLOT_BASE_ITEMS = Map.of(
            EquipmentSlot.HEAD, Items.DIAMOND_HELMET,
            EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE,
            EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS,
            EquipmentSlot.FEET, Items.DIAMOND_BOOTS
    );

    // Ключевые слова для более точного матчинга иконки к слоту, когда под
    // один и тот же asset_id найдено несколько подходящих item_model.
    private static final Map<EquipmentSlot, String[]> SLOT_KEYWORDS = Map.of(
            EquipmentSlot.HEAD, new String[]{"helmet", "head", "hat", "cap"},
            EquipmentSlot.CHEST, new String[]{"chestplate", "chest", "body"},
            EquipmentSlot.LEGS, new String[]{"leggings", "legs", "pants"},
            EquipmentSlot.FEET, new String[]{"boots", "feet", "shoes"}
    );

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

        try (InputStream inputStream = resource.getInputStream();
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {

            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("layers")) return; // не похоже на equipment-asset

            String uniqueKey = resource.getPackId() + ":" + assetId;
            if (!ScannedItemsRegistry.markEquippableAssetSeen(uniqueKey)) return; // уже добавлен ранее

            String packName = ResourcePackUtils.displayName(resource);
            for (Map.Entry<EquipmentSlot, Item> slotEntry : SLOT_BASE_ITEMS.entrySet()) {
                registerVariant(assetId, slotEntry.getKey(), slotEntry.getValue(), packName);
            }
        } catch (Exception ignored) {
            // Осознанно широкий catch — см. аналогичный комментарий в ItemDefinitionParser.
        }
    }

    private void registerVariant(Identifier assetId, EquipmentSlot slot, Item baseItem, String packName) {
        RegistryKey<EquipmentAsset> assetKey =
                RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, assetId);

        EquippableComponent.Builder builder =
                EquippableComponent.builder(slot).model(assetKey);

        EquippableComponent baseComponent =
                baseItem.getComponents().get(DataComponentTypes.EQUIPPABLE);

        if (baseComponent != null) {
            builder.equipSound(baseComponent.equipSound());
        }

        ItemStack stack = new ItemStack(baseItem);

        NbtCompound customData = new NbtCompound();
        customData.putString("pack_name", packName);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(customData));

        stack.set(DataComponentTypes.EQUIPPABLE, builder.build());

        Identifier icon = matchIcon(assetId, slot);
        if (icon != null) {
            stack.set(DataComponentTypes.ITEM_MODEL, icon);
        }

        stack.set(
                DataComponentTypes.CUSTOM_NAME,
                Text.literal(assetId + " [" + slotLabel(slot) + "]")
                        .styled(style -> style.withItalic(false))
        );

        ScannedItemsRegistry.EQUIPPABLE_ENTRIES.add(stack);
    }

    /**
     * Пытается найти среди уже отсканированных записей вкладки
     * {@code item_model} (того же namespace, что и asset_id) подходящую
     * кастомную иконку — сначала по совпадению имени И ключевого слова
     * слота, а если такого нет и совпадение по имени единственное —
     * по одному только имени. Если ничего не подошло — возвращает
     * {@code null}, и предмет останется с ванильной иконкой base item'а.
     */
    private Identifier matchIcon(Identifier assetId, EquipmentSlot slot) {
        String namespace = assetId.getNamespace();
        String assetName = assetId.getPath().toLowerCase(Locale.ROOT);
        String[] slotKeywords = SLOT_KEYWORDS.get(slot);

        Identifier byNameAndSlot = null;
        Identifier byNameOnly = null;
        int nameOnlyMatches = 0;

        for (ItemStack stack : ScannedItemsRegistry.ITEM_MODEL_ENTRIES) {
            Identifier modelId = stack.get(DataComponentTypes.ITEM_MODEL);
            if (modelId == null || !modelId.getNamespace().equals(namespace)) continue;

            String modelPath = modelId.getPath().toLowerCase(Locale.ROOT);
            if (!modelPath.contains(assetName)) continue;

            nameOnlyMatches++;
            byNameOnly = modelId;

            for (String keyword : slotKeywords) {
                if (modelPath.contains(keyword)) {
                    byNameAndSlot = modelId;
                    break;
                }
            }
        }

        if (byNameAndSlot != null) return byNameAndSlot;
        return nameOnlyMatches == 1 ? byNameOnly : null;
    }

    private String slotLabel(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> slot.getName();
        };
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
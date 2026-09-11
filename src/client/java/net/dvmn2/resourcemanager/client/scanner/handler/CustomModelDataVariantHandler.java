package net.dvmn2.resourcemanager.client.scanner.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.dvmn2.resourcemanager.client.registry.ScannedItemsRegistry;
import net.dvmn2.resourcemanager.client.util.ResourcePackUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Обрабатывает select-модели, переключающиеся по строковому значению
 * компонента {@code custom_model_data} (индекс 0 среди строковых
 * значений) — приём ресурспаков для смены модели без наковальни/имени.
 * Каждый уникальный вариант превращается в отдельный {@link ItemStack} с
 * уже присвоенным CUSTOM_MODEL_DATA — для вкладки {@code custom_model_data}.
 */
public final class CustomModelDataVariantHandler implements ItemDefinitionHandler {

    @Override
    public boolean matches(JsonObject model) {
        return endsWith(model, "type", "select")
                && endsWith(model, "property", "custom_model_data")
                && model.has("cases");
    }

    @Override
    public void handle(Identifier itemId, JsonObject model, Resource resource) {
        Item baseItem = Registries.ITEM.get(itemId);
        if (baseItem == Items.AIR) return;

        for (JsonElement caseElement : model.getAsJsonArray("cases")) {
            if (!caseElement.isJsonObject()) continue;
            JsonObject caseObject = caseElement.getAsJsonObject();
            if (!caseObject.has("when")) continue;

            List<String> variantValues = readWhenAsList(caseObject.get("when"));
            if (!variantValues.isEmpty()) {
                registerVariant(itemId, baseItem, variantValues, resource);
            }
        }
    }

    private void registerVariant(Identifier itemId, Item baseItem, List<String> variantValues, Resource resource) {
        String primaryValue = variantValues.getFirst();
        String uniqueKey = resource.getPackId() + ":" + itemId + ":" + primaryValue;
        if (!ScannedItemsRegistry.markCustomModelDataSeen(uniqueKey)) return;

        ItemStack stack = new ItemStack(baseItem);

        NbtCompound customData = new NbtCompound();
        customData.putString("pack_name", ResourcePackUtils.displayName(resource));
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(customData));

        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(
                List.of(),               // floats
                List.of(),               // flags
                List.of(primaryValue),   // strings
                List.of()                // colors
        ));

        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(baseItem.getName().getString() + " [" + String.join(", ", variantValues) + "]")
                        .styled(style -> style.withItalic(false)));

        ScannedItemsRegistry.CUSTOM_MODEL_DATA_ENTRIES.add(stack);
    }

    /**
     * {@code "when"} может быть как одиночной строкой, так и массивом значений.
     */
    private List<String> readWhenAsList(JsonElement whenElement) {
        List<String> values = new ArrayList<>();
        if (whenElement.isJsonArray()) {
            for (JsonElement element : whenElement.getAsJsonArray()) {
                values.add(element.getAsString());
            }
        } else if (whenElement.isJsonPrimitive()) {
            values.add(whenElement.getAsString());
        }
        return values;
    }

    private boolean endsWith(JsonObject object, String key, String suffix) {
        return object.has(key) && object.get(key).getAsString().endsWith(suffix);
    }
}
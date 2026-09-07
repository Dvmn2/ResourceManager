package net.dvmn2.resourcemanager.client.scanner.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.dvmn2.resourcemanager.client.registry.ScannedItemsRegistry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Обрабатывает select-модели, переключающиеся по компоненту
 * {@code minecraft:custom_name} — распространённый в ресурспаках приём для
 * замены внешнего вида предмета в зависимости от имени, присвоенного через
 * наковальню. Каждый уникальный вариант превращается в отдельный
 * {@link ItemStack} с уже присвоенным именем — для вкладки {@code custom_name}.
 */
public final class CustomNameVariantHandler implements ItemDefinitionHandler {

    @Override
    public boolean matches(JsonObject model) {
        return endsWith(model, "type", "select")
                && endsWith(model, "property", "component")
                && endsWith(model, "component", "custom_name")
                && model.has("cases");
    }

    @Override
    public void handle(Identifier itemId, JsonObject model, Resource resource) {
        Item baseItem = Registries.ITEM.get(itemId);
        if (baseItem == Items.AIR) return; // предмета с таким id не существует

        for (JsonElement caseElement : model.getAsJsonArray("cases")) {
            if (!caseElement.isJsonObject()) continue;
            JsonObject caseObject = caseElement.getAsJsonObject();
            if (!caseObject.has("when")) continue;

            List<String> alternativeNames = readWhenAsList(caseObject.get("when"));
            if (!alternativeNames.isEmpty()) {
                registerVariant(itemId, baseItem, alternativeNames, resource);
            }
        }
    }

    private void registerVariant(Identifier itemId, Item baseItem, List<String> alternativeNames, Resource resource) {
        String primaryName = alternativeNames.get(0);
        String uniqueKey = resource.getPackId() + ":" + itemId + ":" + primaryName;
        if (!ScannedItemsRegistry.markCustomNameSeen(uniqueKey)) return; // уже добавлен ранее

        for (String name : alternativeNames) {
            ItemStack stack = new ItemStack(baseItem);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
            ScannedItemsRegistry.CUSTOM_NAME_ENTRIES.add(stack);
        }
    }

    /**
     * {@code "when"} может быть как одиночной строкой, так и массивом синонимов.
     */
    private List<String> readWhenAsList(JsonElement whenElement) {
        List<String> names = new ArrayList<>();
        if (whenElement.isJsonArray()) {
            for (JsonElement element : whenElement.getAsJsonArray()) {
                names.add(element.getAsString());
            }
        } else if (whenElement.isJsonPrimitive()) {
            names.add(whenElement.getAsString());
        }
        return names;
    }

    private boolean endsWith(JsonObject object, String key, String suffix) {
        return object.has(key) && object.get(key).getAsString().endsWith(suffix);
    }
}

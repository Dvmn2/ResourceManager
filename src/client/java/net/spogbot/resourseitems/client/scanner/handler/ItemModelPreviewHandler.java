package net.spogbot.resourseitems.client.scanner.handler;

import com.google.gson.JsonObject;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.spogbot.resourseitems.client.registry.ScannedItemsRegistry;

/**
 * Обрабатывает обычные ({@code "model"}-type) item definition'ы —
 * предметы, у которых просто указана конкретная модель без select-логики.
 * Для каждой уникальной модели создаётся {@link ItemStack} на базе
 * {@link Items#PAPER} с компонентом ITEM_MODEL — так можно увидеть модель
 * ресурспака, даже если реального предмета с ней не существует. Витрина —
 * вкладка {@code item_model}.
 */
public final class ItemModelPreviewHandler implements ItemDefinitionHandler {

    private static final String VANILLA_NAMESPACE = "minecraft";

    @Override
    public boolean matches(JsonObject model) {
        return endsWith(model, "type", "model")
                && model.has("model");
    }

    @Override
    public void handle(Identifier itemId, JsonObject model, Resource resource) {
        // Не показываем ванильные предметы — вкладка предназначена именно
        // для витрины моделей, добавленных ресурспаками.
        if (itemId.getNamespace().equals(VANILLA_NAMESPACE)) return;
        if (!ScannedItemsRegistry.markItemModelSeen(itemId)) return;

        // ВАЖНО: компонент ITEM_MODEL ссылается не на файл модели напрямую,
        // а на id item-definition'а из папки "items/" (то есть на itemId).
        // Именно этот id клиент ищет как assets/<ns>/items/<path>.json, а уже
        // внутри него самого лежит ссылка на реальную модель ("model" ->
        // "namespace:item/path"). Подставлять сюда вложенный путь модели
        // напрямую — ошибка: клиент не находит по нему item-definition,
        // и предмет отображается заглушкой (чёрно-фиолетовый квадрат).
        String displayName = fileNameOf(itemId).replace('_', ' ');

        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponentTypes.ITEM_MODEL, itemId);
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(displayName).styled(style -> style.withItalic(false)));

        ScannedItemsRegistry.ITEM_MODEL_ENTRIES.add(stack);
    }

    /**
     * Пытается прочитать реальный путь модели из поля {@code "model"}
     * (например {@code "mymod:item/thing"}). Если оно отсутствует или не
     * является строкой, используем как запасной вариант id самого предмета
     * — это соответствует распространённой конвенции ресурспаков "путь
     * предмета совпадает с путём модели".
     */
    private Identifier resolveModelId(Identifier itemId, JsonObject model) {
        if (model.has("model") && model.get("model").isJsonPrimitive()) {
            String reference = model.get("model").getAsString();
            return reference.contains(":")
                    ? Identifier.of(reference)
                    : Identifier.of(itemId.getNamespace(), reference);
        }
        return itemId;
    }

    private String fileNameOf(Identifier id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private boolean endsWith(JsonObject object, String key, String suffix) {
        return object.has(key) && object.get(key).getAsString().endsWith(suffix);
    }
}

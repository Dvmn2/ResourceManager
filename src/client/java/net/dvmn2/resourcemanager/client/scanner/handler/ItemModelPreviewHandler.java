package net.dvmn2.resourcemanager.client.scanner.handler;

import com.google.gson.JsonObject;
import net.dvmn2.resourcemanager.client.registry.ScannedItemsRegistry;
import net.dvmn2.resourcemanager.client.util.ResourcePackUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Обрабатывает обычные ({@code "model"}-type) item definition'ы —
 * предметы, у которых просто указана конкретная модель без select-логики.
 * Для каждой уникальной модели создаётся {@link ItemStack} на базе
 * {@link Items#PAPER} с компонентом ITEM_MODEL — так можно увидеть модель
 * ресурспака, даже если реального предмета с ней не существует. Витрина —
 * вкладка {@code item_model}.
 */
public final class ItemModelPreviewHandler implements ItemDefinitionHandler {

    /**
     * id встроенного "ресурспака" с базовыми ассетами самой игры. Именно под
     * этим id лежат item-definition'ы вида "items/stick.json" для КАЖДОГО
     * ванильного предмета, поэтому их нужно отсеивать — иначе вкладка
     * item_model showed бы ~1000 стандартных предметов даже без единого
     * установленного ресурспака.
     * <p>
     * Built-in "resource pack" id containing the game's base assets. Every
     * vanilla item ships a default item-definition (e.g. "items/stick.json")
     * under this pack id, so it needs to be filtered out — otherwise the
     * item_model tab would show ~1000 default entries even with zero
     * resource packs installed.
     */
    private static final String VANILLA_PACK_ID = "vanilla";

    @Override
    public boolean matches(JsonObject model) {
        return endsWith(model, "type", "model")
                && model.has("model");
    }

    @Override
    public void handle(Identifier itemId, JsonObject model, Resource resource) {
        if (VANILLA_PACK_ID.equals(resource.getPackId())) return;
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
        NbtCompound customData = new NbtCompound();
        customData.putString("pack_name", ResourcePackUtils.displayName(resource));
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(customData));
        stack.set(DataComponentTypes.ITEM_MODEL, itemId);
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(displayName).styled(style -> style.withItalic(false)));

        ScannedItemsRegistry.ITEM_MODEL_ENTRIES.add(stack);
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
package net.spogbot.resourseitems.client.group;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.spogbot.resourseitems.client.registry.ScannedItemsRegistry;
import net.spogbot.resourseitems.client.util.ModConstants;

import java.util.List;

/**
 * Регистрация трёх вкладок творческого инвентаря, отображающих результаты
 * сканирования подключённых ресурспаков: {@code item_model},
 * {@code custom_model_data} и {@code custom_name}.
 * <p>
 * Если для какой-то вкладки ресурспаки не дали ни одной записи, вместо
 * пустой вкладки показывается один предмет-заглушка с поясняющим именем
 * (см. {@link #createEmptyPlaceholder(Text)}), чтобы было понятно, что мод
 * работает, а не что вкладка сломана.
 */
public final class ModItemGroups {

    public static final RegistryKey<ItemGroup> ITEM_MODEL_TAB_KEY = RegistryKey.of(
            RegistryKeys.ITEM_GROUP,
            Identifier.of(ModConstants.MOD_ID, "item_model_tab")
    );

    public static final RegistryKey<ItemGroup> CUSTOM_MODEL_DATA_TAB_KEY = RegistryKey.of(
            RegistryKeys.ITEM_GROUP,
            Identifier.of(ModConstants.MOD_ID, "custom_model_data_tab")
    );

    public static final RegistryKey<ItemGroup> CUSTOM_NAME_TAB_KEY = RegistryKey.of(
            RegistryKeys.ITEM_GROUP,
            Identifier.of(ModConstants.MOD_ID, "custom_name_tab")
    );

    private ModItemGroups() {
    }

    /**
     * Регистрирует все три вкладки в реестре ItemGroup. Вызывается один раз при инициализации клиента.
     */
    public static void registerAll() {
        Registry.register(Registries.ITEM_GROUP, ITEM_MODEL_TAB_KEY, FabricItemGroup.builder()
                .icon(() -> new ItemStack(Items.PAINTING))
                .displayName(Text.translatable("itemGroup.resourceitems.item_model"))
                .entries((context, entries) -> addEntriesOrPlaceholder(
                        ScannedItemsRegistry.ITEM_MODEL_ENTRIES, entries,
                        Text.translatable("itemGroup.resourceitems.item_model.empty")))
                .build());

        Registry.register(Registries.ITEM_GROUP, CUSTOM_MODEL_DATA_TAB_KEY, FabricItemGroup.builder()
                .icon(() -> new ItemStack(Items.REDSTONE_BLOCK))
                .displayName(Text.translatable("itemGroup.resourceitems.custom_model_data"))
                .entries((context, entries) -> addEntriesOrPlaceholder(
                        ScannedItemsRegistry.CUSTOM_MODEL_DATA_ENTRIES, entries,
                        Text.translatable("itemGroup.resourceitems.custom_model_data.empty")))
                .build());

        Registry.register(Registries.ITEM_GROUP, CUSTOM_NAME_TAB_KEY, FabricItemGroup.builder()
                .icon(() -> new ItemStack(Items.NAME_TAG))
                .displayName(Text.translatable("itemGroup.resourceitems.custom_name"))
                .entries((context, entries) -> addEntriesOrPlaceholder(
                        ScannedItemsRegistry.CUSTOM_NAME_ENTRIES, entries,
                        Text.translatable("itemGroup.resourceitems.custom_name.empty")))
                .build());
    }

    /**
     * Принудительно обновляет содержимое всех трёх вкладок. Нужно, когда
     * игрок уже находится в мире во время перезагрузки ресурсов — иначе
     * новые предметы появились бы только после повторного открытия книги
     * творческого режима.
     * <p>
     * После своих трёх вкладок дополнительно обновляется ванильная вкладка
     * "Search Items" ({@link ItemGroups#SEARCH}). Она сама не сканирует
     * предметы, а лишь копирует уже готовый список из всех обычных вкладок
     * в момент своего собственного {@code updateEntries(...)} — поэтому её
     * нужно обновлять именно ПОСЛЕ наших трёх, иначе после удаления
     * ресурспака в поиске остаются "призрачные" старые записи, хотя сами
     * вкладки мода уже пусты.
     */
    public static void refreshEntries(ItemGroup.DisplayContext context) {
        ItemGroup itemModelTab = Registries.ITEM_GROUP.get(ITEM_MODEL_TAB_KEY);
        if (itemModelTab != null) itemModelTab.updateEntries(context);

        ItemGroup customModelDataTab = Registries.ITEM_GROUP.get(CUSTOM_MODEL_DATA_TAB_KEY);
        if (customModelDataTab != null) customModelDataTab.updateEntries(context);

        ItemGroup customNameTab = Registries.ITEM_GROUP.get(CUSTOM_NAME_TAB_KEY);
        if (customNameTab != null) customNameTab.updateEntries(context);

        ItemGroup searchTab = Registries.ITEM_GROUP.get(ItemGroups.SEARCH);
        if (searchTab != null) searchTab.updateEntries(context);
    }

    /**
     * Добавляет во вкладку либо реальные найденные предметы, либо (если
     * список пуст) один предмет-заглушку с поясняющим сообщением.
     */
    private static void addEntriesOrPlaceholder(List<ItemStack> source, ItemGroup.Entries target, Text emptyMessage) {
        if (source.isEmpty()) {
            target.add(createEmptyPlaceholder(emptyMessage));
        } else {
            source.forEach(target::add);
        }
    }

    /**
     * Создаёт предмет-заглушку для пустой вкладки: обычный лист бумаги с
     * серым курсивным названием, чтобы визуально отличаться от настоящих
     * найденных записей (у них имя всегда не курсивное).
     */
    private static ItemStack createEmptyPlaceholder(Text message) {
        ItemStack placeholder = new ItemStack(Items.PAPER);
        placeholder.set(DataComponentTypes.CUSTOM_NAME,
                message.copy().styled(style -> style.withColor(Formatting.GRAY).withItalic(true)));
        return placeholder;
    }
}
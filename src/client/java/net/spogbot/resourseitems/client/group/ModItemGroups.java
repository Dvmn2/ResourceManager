package net.spogbot.resourseitems.client.group;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.spogbot.resourseitems.client.registry.ScannedItemsRegistry;
import net.spogbot.resourseitems.client.util.ModConstants;

/**
 * Регистрация трёх вкладок творческого инвентаря, отображающих результаты
 * сканирования подключённых ресурспаков: {@code item_model},
 * {@code custom_model_data} и {@code custom_name}.
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
                .entries((context, entries) -> ScannedItemsRegistry.ITEM_MODEL_ENTRIES.forEach(entries::add))
                .build());

        Registry.register(Registries.ITEM_GROUP, CUSTOM_MODEL_DATA_TAB_KEY, FabricItemGroup.builder()
                .icon(() -> new ItemStack(Items.REDSTONE_BLOCK))
                .displayName(Text.translatable("itemGroup.resourceitems.custom_model_data"))
                .entries((context, entries) -> ScannedItemsRegistry.CUSTOM_MODEL_DATA_ENTRIES.forEach(entries::add))
                .build());

        Registry.register(Registries.ITEM_GROUP, CUSTOM_NAME_TAB_KEY, FabricItemGroup.builder()
                .icon(() -> new ItemStack(Items.NAME_TAG))
                .displayName(Text.translatable("itemGroup.resourceitems.custom_name"))
                .entries((context, entries) -> ScannedItemsRegistry.CUSTOM_NAME_ENTRIES.forEach(entries::add))
                .build());
    }

    /**
     * Принудительно обновляет содержимое всех трёх вкладок. Нужно, когда
     * игрок уже находится в мире во время перезагрузки ресурсов — иначе
     * новые предметы появились бы только после повторного открытия книги
     * творческого режима.
     */
    public static void refreshEntries(ItemGroup.DisplayContext context) {
        ItemGroup itemModelTab = Registries.ITEM_GROUP.get(ITEM_MODEL_TAB_KEY);
        if (itemModelTab != null) itemModelTab.updateEntries(context);

        ItemGroup customModelDataTab = Registries.ITEM_GROUP.get(CUSTOM_MODEL_DATA_TAB_KEY);
        if (customModelDataTab != null) customModelDataTab.updateEntries(context);

        ItemGroup customNameTab = Registries.ITEM_GROUP.get(CUSTOM_NAME_TAB_KEY);
        if (customNameTab != null) customNameTab.updateEntries(context);
    }
}

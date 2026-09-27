package net.dvmn2.resourcemanager.client.scanner;

import net.dvmn2.resourcemanager.client.group.ModItemGroups;
import net.dvmn2.resourcemanager.client.registry.ScannedItemsRegistry;
import net.dvmn2.resourcemanager.client.util.ModConstants;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemGroup;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Слушатель перезагрузки клиентских ресурсов. Срабатывает при запуске игры
 * и при каждом нажатии "Done" в экране настройки ресурспаков — заново
 * сканирует все item-definition'ы и equipment-asset'ы во всех подключённых
 * паках и распределяет их по четырём вкладкам через
 * {@link ItemDefinitionParser} и {@link EquipmentAssetParser}.
 * <p>
 * Порядок важен: сначала сканируется {@code items/}, и только потом —
 * {@code equipment/}, потому что {@link EquipmentAssetParser} пытается
 * подобрать кастомную иконку для брони среди уже найденных записей
 * вкладки {@code item_model}.
 * <p>
 * Реализует {@link SimpleSynchronousResourceReloadListener}, то есть
 * выполняется синхронно на клиентском потоке — поэтому внутри
 * {@link #reload(ResourceManager)} и {@link #refreshOpenInventoryIfNeeded()}
 * безопасно обращаться к {@link MinecraftClient#getInstance()} и изменять
 * общие статические списки в {@link ScannedItemsRegistry} без синхронизации.
 */
public final class ResourcePackModelScanner implements SimpleSynchronousResourceReloadListener {

    private static final String ITEMS_ROOT = "items";
    private static final String EQUIPMENT_ROOT = "equipment";

    private final ItemDefinitionParser itemParser = new ItemDefinitionParser();
    private final EquipmentAssetParser equipmentParser = new EquipmentAssetParser();

    /**
     * Регистрирует этот листенер в {@link ResourceManagerHelper} для клиентских ресурсов.
     */
    public static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new ResourcePackModelScanner());
    }

    @Override
    public Identifier getFabricId() {
        return Identifier.of(ModConstants.MOD_ID, "model_scanner");
    }

    @Override
    public void reload(ResourceManager manager) {
        ScannedItemsRegistry.clear();

        Map<Identifier, List<Resource>> itemDefinitions =
                manager.findAllResources(ITEMS_ROOT, path -> path.getPath().endsWith(".json"));

        for (Map.Entry<Identifier, List<Resource>> entry : itemDefinitions.entrySet()) {
            itemParser.parseEntry(entry.getKey(), entry.getValue());
        }

        // Сканируется ПОСЛЕ items/ — см. javadoc класса и EquipmentAssetParser.matchIcon.
        Map<Identifier, List<Resource>> equipmentAssets =
                manager.findAllResources(EQUIPMENT_ROOT, path -> path.getPath().endsWith(".json"));

        for (Map.Entry<Identifier, List<Resource>> entry : equipmentAssets.entrySet()) {
            equipmentParser.parseEntry(entry.getKey(), entry.getValue());
        }

        refreshOpenInventoryIfNeeded();
    }

    /**
     * Если игрок уже находится в игровом мире, немедленно обновляет
     * содержимое всех вкладок, чтобы новые предметы сразу появились
     * без перезахода в творческую инвентарную книгу.
     */
    private void refreshOpenInventoryIfNeeded() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null
                || client.player.networkHandler == null) {
            return;
        }

        ItemGroup.DisplayContext context = new ItemGroup.DisplayContext(
                client.player.networkHandler.getEnabledFeatures(),
                client.options.getOperatorItemsTab().getValue(),
                client.world.getRegistryManager()
        );

        ModItemGroups.refreshEntries(context);
    }
}
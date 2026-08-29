package net.spogbot.resourseitems.client.scanner;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemGroup;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.spogbot.resourseitems.client.group.ModItemGroups;
import net.spogbot.resourseitems.client.registry.ScannedItemsRegistry;
import net.spogbot.resourseitems.client.util.ModConstants;

import java.util.List;
import java.util.Map;

/**
 * Слушатель перезагрузки клиентских ресурсов. Срабатывает при запуске игры
 * и при каждом нажатии "Done" в экране настройки ресурспаков — заново
 * сканирует все item-definition'ы во всех подключённых паках и
 * распределяет их по трём вкладкам через {@link ItemDefinitionParser}.
 */
public final class ResourcePackModelScanner implements SimpleSynchronousResourceReloadListener {

    private static final String ITEMS_ROOT = "items";

    private final ItemDefinitionParser parser = new ItemDefinitionParser();

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
            parser.parseEntry(entry.getKey(), entry.getValue());
        }

        refreshOpenInventoryIfNeeded();
    }

    /**
     * Если игрок уже находится в игровом мире, немедленно обновляет
     * содержимое всех трёх вкладок, чтобы новые предметы сразу появились
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

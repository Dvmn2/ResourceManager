package net.dvmn2.resourcemanager.client;

import net.dvmn2.resourcemanager.client.group.ModItemGroups;
import net.dvmn2.resourcemanager.client.scanner.ResourcePackModelScanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Клиентская точка входа мода "Resourseitems".
 * <p>
 * Мод — административный/отладочный инструмент для просмотра содержимого
 * установленных ресурспаков в творческом режиме. Он не добавляет никакой
 * геймплейной логики (например, взаимодействия с наковальней) и работает
 * только на клиенте.
 * <p>
 * Результаты сканирования ресурспаков раскладываются по трём вкладкам
 * творческого инвентаря:
 * <ul>
 *     <li><b>item_model</b> — превью обычных моделей предметов, найденных
 *     в ресурспаках
 *     (см. {@link net.dvmn2.resourcemanager.client.scanner.handler.ItemModelPreviewHandler});</li>
 *     <li><b>custom_model_data</b> — варианты предметов, переключаемые по
 *     значению компонента custom_model_data
 *     (см. {@link net.dvmn2.resourcemanager.client.scanner.handler.CustomModelDataVariantHandler});</li>
 *     <li><b>custom_name</b> — варианты предметов, переключаемые по
 *     присвоенному игроком имени (custom_name)
 *     (см. {@link net.dvmn2.resourcemanager.client.scanner.handler.CustomNameVariantHandler}).</li>
 * </ul>
 * Логика сканирования и разбора JSON вынесена в пакет {@code scanner},
 * регистрация вкладок инвентаря — в пакет {@code group}, а общее хранилище
 * найденных предметов — в пакет {@code registry}.
 */
@Environment(EnvType.CLIENT)
public class ResourceManagerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModItemGroups.registerAll();
        ResourcePackModelScanner.register();
    }
}

package net.spogbot.resourseitems.client;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Точка входа генератора данных (Fabric Data Generation API).
 * <p>
 * Мод не регистрирует собственных предметов/блоков/рецептов — вся его
 * работа сводится к чтению уже существующих ресурспаков в рантайме,
 * поэтому провайдеры данных здесь не добавляются. Класс оставлен как
 * "заглушка", чтобы точка входа datagen существовала (указана в
 * fabric.mod.json) на случай, если в будущем понадобится генерировать,
 * например, локализацию для переводимых текстов ("gui.title.items" и т.п.).
 */
public class ResourseitemsDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        // Пак для будущих провайдеров данных (сейчас пустой — провайдеры не регистрируются).
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
    }
}
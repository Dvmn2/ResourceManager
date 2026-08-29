package net.spogbot.resourseitems.client.scanner.handler;

import com.google.gson.JsonObject;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

/**
 * Общий контракт для обработчиков одного варианта содержимого поля
 * {@code "model"} в item-definition JSON. Каждая реализация отвечает за
 * один из трёх типов записей, которые мод показывает во вкладках
 * творческого инвентаря.
 */
public interface ItemDefinitionHandler {

    /** Соответствует ли форма JSON-объекта {@code "model"} этому обработчику. */
    boolean matches(JsonObject model);

    /**
     * Обрабатывает совпавший объект {@code "model"}: извлекает нужные
     * данные и, при необходимости, добавляет новую запись в
     * соответствующий список {@link net.spogbot.resourseitems.client.registry.ScannedItemsRegistry}.
     *
     * @param itemId   идентификатор базового предмета (вычислен из пути файла в "items/")
     * @param model    JSON-объект поля {@code "model"}
     * @param resource ресурс, из которого был прочитан файл (нужен для packId)
     */
    void handle(Identifier itemId, JsonObject model, Resource resource);
}

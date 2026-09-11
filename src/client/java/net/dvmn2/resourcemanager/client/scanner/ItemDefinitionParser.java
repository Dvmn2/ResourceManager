package net.dvmn2.resourcemanager.client.scanner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dvmn2.resourcemanager.client.scanner.handler.CustomModelDataVariantHandler;
import net.dvmn2.resourcemanager.client.scanner.handler.CustomNameVariantHandler;
import net.dvmn2.resourcemanager.client.scanner.handler.ItemDefinitionHandler;
import net.dvmn2.resourcemanager.client.scanner.handler.ItemModelPreviewHandler;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Разбирает один item-definition JSON файл (из папки {@code "items/"}
 * ресурспака) и передаёт вложенный объект {@code "model"} первому
 * подходящему обработчику. Порядок обработчиков важен: сначала проверяются
 * более специфичные select-варианты (custom_name, custom_model_data), и
 * только затем — обычные модели (item_model).
 */
public final class ItemDefinitionParser {

    private static final String ITEMS_DIR_PREFIX = "items";
    private static final String JSON_EXTENSION = ".json";

    private final List<ItemDefinitionHandler> handlers = List.of(
            new CustomNameVariantHandler(),
            new CustomModelDataVariantHandler(),
            new ItemModelPreviewHandler()
    );

    /**
     * Разбирает все ресурсы, найденные под одним и тем же путём (переопределения из разных паков).
     */
    public void parseEntry(Identifier resourceId, List<Resource> resources) {
        Identifier itemId = toItemId(resourceId);
        if (itemId == null) return;

        for (Resource resource : resources) {
            parseSingleResource(itemId, resource);
        }
    }

    private void parseSingleResource(Identifier itemId, Resource resource) {
        try (InputStream inputStream = resource.getInputStream();
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {

            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("model") || !root.get("model").isJsonObject()) return;

            JsonObject model = root.getAsJsonObject("model");
            for (ItemDefinitionHandler handler : handlers) {
                if (handler.matches(model)) {
                    handler.handle(itemId, model, resource);
                    break;
                }
            }
        } catch (Exception ignored) {
            // Файл не является валидным JSON-описанием предмета — пропускаем.
            // Осознанно широкий catch: ресурспаки сторонних авторов могут
            // содержать невалидный или неожиданно структурированный JSON,
            // и падать здесь для одного файла не должно ронять весь reload.
            //
            // File is not a valid JSON item-definition — skip it.
            // Intentionally broad catch: third-party resource packs can
            // ship invalid or unexpectedly structured JSON, and failing on
            // a single file here should not crash the whole reload.
        }
    }

    /**
     * Превращает путь ресурса {@code "items/foo/bar.json"} в id предмета {@code "ns:foo/bar"}.
     */
    private Identifier toItemId(Identifier resourceId) {
        String path = resourceId.getPath();
        if (!path.endsWith(JSON_EXTENSION)) return null;

        String withoutExtension = path.substring(0, path.length() - JSON_EXTENSION.length());
        if (!withoutExtension.startsWith(ITEMS_DIR_PREFIX)) return null;

        String itemPath = withoutExtension.substring(ITEMS_DIR_PREFIX.length());
        if (itemPath.startsWith("/")) itemPath = itemPath.substring(1);
        if (itemPath.isEmpty()) return null;

        return Identifier.of(resourceId.getNamespace(), itemPath);
    }
}
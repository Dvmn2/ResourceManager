package net.spogbot.resourseitems.client.util;

import net.minecraft.resource.Resource;

/** Вспомогательные функции для работы с метаданными ресурспаков. */
public final class ResourcePackUtils {

    private ResourcePackUtils() {
    }

    /**
     * Превращает служебный packId ресурса (например {@code "file/MyPack.zip"})
     * в человекочитаемое имя пака ({@code "MyPack"}) для отображения в
     * тултипах найденных вариантов предметов.
     */
    public static String displayName(Resource resource) {
        return resource.getPackId()
                .replace("file/", "")
                .replace(".zip", "");
    }
}

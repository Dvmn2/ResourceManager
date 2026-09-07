package net.dvmn2.resourcemanager.client.util;

import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.List;

/**
 * Мелкие вспомогательные функции для работы с NBT.
 */
public final class NbtUtils {

    private NbtUtils() {
    }

    /**
     * Превращает список строк в {@link NbtList} из {@link NbtString}-элементов.
     */
    public static NbtList stringListToNbt(List<String> values) {
        NbtList list = new NbtList();
        for (String value : values) {
            list.add(NbtString.of(value));
        }
        return list;
    }
}

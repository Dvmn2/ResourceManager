package net.dvmn2.resourcemanager.client.registry;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Централизованное хранилище результатов сканирования ресурспаков.
 * <p>
 * Три отдельных списка соответствуют трём вкладкам творческого инвентаря:
 * превью моделей предметов ({@code item_model}), варианты по
 * custom_model_data ({@code custom_model_data}) и варианты по custom_name
 * ({@code custom_name}). Списки полностью пересобираются при каждой
 * перезагрузке ресурсов клиента — см. {@link #clear()}.
 */
public final class ScannedItemsRegistry {

    /**
     * Вкладка "item_model": простые превью моделей из ресурспаков.
     */
    public static final List<ItemStack> ITEM_MODEL_ENTRIES = new ArrayList<>();

    /**
     * Вкладка "custom_model_data": select-варианты по custom_model_data.
     */
    public static final List<ItemStack> CUSTOM_MODEL_DATA_ENTRIES = new ArrayList<>();

    /**
     * Вкладка "custom_name": select-варианты по custom_name.
     */
    public static final List<ItemStack> CUSTOM_NAME_ENTRIES = new ArrayList<>();

    // Наборы для дедупликации записей в рамках одного сканирования.
    private static final Set<Identifier> SEEN_ITEM_MODELS = new HashSet<>();
    private static final Set<String> SEEN_CUSTOM_MODEL_DATA = new HashSet<>();
    private static final Set<String> SEEN_CUSTOM_NAMES = new HashSet<>();

    private ScannedItemsRegistry() {
    }

    /**
     * Полностью очищает все списки и наборы дедупликации перед новым сканированием.
     */
    public static void clear() {
        ITEM_MODEL_ENTRIES.clear();
        CUSTOM_MODEL_DATA_ENTRIES.clear();
        CUSTOM_NAME_ENTRIES.clear();
        SEEN_ITEM_MODELS.clear();
        SEEN_CUSTOM_MODEL_DATA.clear();
        SEEN_CUSTOM_NAMES.clear();
    }

    /**
     * @return {@code true}, если эта модель ещё не встречалась в текущем сканировании.
     */
    public static boolean markItemModelSeen(Identifier modelId) {
        return SEEN_ITEM_MODELS.add(modelId);
    }

    /**
     * @return {@code true}, если этот вариант custom_model_data ещё не встречался.
     */
    public static boolean markCustomModelDataSeen(String uniqueKey) {
        return SEEN_CUSTOM_MODEL_DATA.add(uniqueKey);
    }

    /**
     * @return {@code true}, если этот вариант custom_name ещё не встречался.
     */
    public static boolean markCustomNameSeen(String uniqueKey) {
        return SEEN_CUSTOM_NAMES.add(uniqueKey);
    }
}
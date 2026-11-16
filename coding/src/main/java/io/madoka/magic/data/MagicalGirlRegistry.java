package io.madoka.magic.data;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 魔法少女定义的运行时注册表。
 *
 * <p>内容来自 {@code data/madoka_magic/magical_girls/*.json}，可随资源包热重载。</p>
 */
public final class MagicalGirlRegistry {

    public static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, MagicalGirl> GIRLS = new ConcurrentHashMap<>();

    private MagicalGirlRegistry() {
    }

    public static void replace(Map<String, MagicalGirl> loaded) {
        // 先放新值再删旧键：clear+putAll 会让并发读方看到空表
        GIRLS.putAll(loaded);
        GIRLS.keySet().retainAll(loaded.keySet());
    }

    /** 按 id 取一位魔法少女；没有则返回 null。 */
    public static MagicalGirl get(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        return GIRLS.get(id);
    }

    public static MagicalGirl getOrDefault(String id, MagicalGirl fallback) {
        MagicalGirl girl = get(id);
        return girl == null ? fallback : girl;
    }

    /** 全部已加载的魔法少女。 */
    public static Collection<MagicalGirl> all() {
        return new ArrayList<>(GIRLS.values());
    }

    /** 按 id 找契约材料对应的魔法少女（材料 id → 魔法少女）。 */
    public static MagicalGirl byContractMaterial(ResourceLocation material) {
        if (material == null) {
            return null;
        }
        for (MagicalGirl girl : GIRLS.values()) {
            if (material.equals(girl.getContractMaterial())) {
                return girl;
            }
        }
        return null;
    }

    /** 按契约材料的物品 id 找魔法少女。 */
    public static MagicalGirl byContractMaterialItem(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return byContractMaterial(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }
}

package io.madoka.magic.dimension;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.compat.CataclysmCompat;
import io.madoka.magic.data.MagicalGirl;
import io.madoka.magic.data.MagicalGirlRegistry;
import io.madoka.magic.entity.WitchTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 每位魔女一个专属维度。
 *
 * <p>维度用 Forge 的<b>数据驱动</b>方式定义（{@code data/madoka_magic/dimension/}），
 * 不走 Java 注册，避免维度 API 在不同小版本间变动带来的问题。</p>
 *
 * <p>维度 key 规则：{@code madoka_magic:witch_<themeId>}。</p>
 */
public final class MadokaDimensions {
    /** Only witches with implemented encounters can be rolled for a natural entrance. */
    public static final List<String> NATURAL_BARRIERS = List.of(WitchTypes.WITHER,
            WitchTypes.SALVATION, WitchTypes.NUTCRACKER, WitchTypes.MERMAID, WitchTypes.OPHELIA, WitchTypes.GLUTTONY,
            "candeloro");
    /** Existing and manually entered barriers also need dimension and exit handling. */
    public static final List<String> BARRIERS = List.of("sacrifice", "take", "atonement", WitchTypes.WITHER,
            WitchTypes.SALVATION, WitchTypes.NUTCRACKER, "candeloro", WitchTypes.MERMAID,
            WitchTypes.OPHELIA, WitchTypes.GLUTTONY);

    private MadokaDimensions() {
    }

    /** 某位魔女的维度 key（维度 id 写在 JSON 的 witch_dimension 字段里）。 */
    public static ResourceKey<Level> witchLevel(MagicalGirl girl) {
        String id = girl == null || girl.getWitchDimension() == null || girl.getWitchDimension().isEmpty()
                ? "madoka_magic:witch_sacrifice"
                : girl.getWitchDimension();
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(id));
    }

    public static ResourceKey<Level> witchLevel(String themeId) {
        MagicalGirl girl = MagicalGirlRegistry.get(themeId);
        return girl == null ? ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "witch_" + themeId)) : witchLevel(girl);
    }

    public static boolean hasBarrier(String themeId) {
        return BARRIERS.contains(themeId) || CataclysmCompat.isBarrierTheme(themeId) && CataclysmCompat.isPresent();
    }

    public static String barrierTheme(ResourceKey<Level> dimension) {
        for (String theme : BARRIERS) {
            if (dimension.equals(witchLevel(theme))) {
                return theme;
            }
        }
        if (CataclysmCompat.isPresent()) {
            for (String theme : CataclysmCompat.barrierThemes()) {
                if (dimension.equals(witchLevel(theme))) {
                    return theme;
                }
            }
        }
        return "";
    }
}

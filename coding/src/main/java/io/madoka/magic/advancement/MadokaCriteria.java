package io.madoka.magic.advancement;

import io.madoka.magic.MadokaMagic;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;

/**
 * 模组自己的进度触发器。数据包里的 advancement 用 {@code "trigger": "madoka_magic:<path>"} 引用。
 *
 * <p>类加载时注册，必须在数据包加载前完成，所以 {@link #bootstrap()} 在 mod 构造里调一次。</p>
 */
public final class MadokaCriteria {

    /** 签约后第一次变身。 */
    public static final SimpleTrigger BECOME_MAGICAL_GIRL = register("become_magical_girl");
    /** 第一次进入半魔女化。 */
    public static final SimpleTrigger HALF_WITCH = register("half_witch");
    /** 击杀由自己魔女化而来的魔女，转变身份。 */
    public static final SimpleTrigger DESPAIR = register("despair");
    /** 灵魂宝石纯度到顶。 */
    public static final SimpleTrigger FULL_PURITY = register("full_purity");
    /** 天赋星图全部点亮。 */
    public static final SimpleTrigger STAR_MAP = register("star_map");
    /** 全部种类的魔女都击败过。 */
    public static final SimpleTrigger WITCH_HUNTER = register("witch_hunter");

    private MadokaCriteria() {}

    private static SimpleTrigger register(String path) {
        return CriteriaTriggers.register(new SimpleTrigger(
                ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, path)));
    }

    /** 触发类加载，把六个触发器注册进 {@code CriteriaTriggers}。 */
    public static void bootstrap() {
        // 静态字段在类初始化时完成注册，这里只负责触发初始化。
    }
}

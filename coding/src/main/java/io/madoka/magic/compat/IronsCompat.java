package io.madoka.magic.compat;

import io.madoka.magic.data.MagicalGirl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * 与 Iron's Spells 'n Spellbooks 的可选兼容层。
 *
 * <p>本模组是独立模组，<b>没有安装铁魔法也必须能正常加载</b>。
 * 因此这里刻意不 import 任何铁魔法的类：只有真正需要访问铁魔法 API 时，
 * 才会通过 {@link IronsBridge} 触碰它的类，未安装时不会触发
 * {@code NoClassDefFoundError}。</p>
 */
public final class IronsCompat {

    public static final String IRONS_MODID = "irons_spellbooks";

    private IronsCompat() {
    }

    /** 玩家是否装了铁魔法。 */
    public static boolean isPresent() {
        return net.minecraftforge.fml.ModList.get().isLoaded(IRONS_MODID);
    }

    /**
     * 取铁魔法里对应的学派法强属性。
     * 只有在 {@link #isPresent()} 为 true 时才能调用。
     */
    public static Attribute ironsSpellPower(String school) {
        return IronsBridge.spellPowerAttribute(school);
    }

    /**
     * 联动法强：有灵魂宝石时，给对应学派的法强加属性。
     * <p>没装铁魔法时这个方法什么都不做——那个属性根本不存在。</p>
     */
    public static void applySchoolPower(LivingEntity player, MagicalGirl theme) {
        if (!isPresent()) {
            return;
        }
        IronsBridge.applySchoolPower(player, theme);
    }

    /** 移除联动法强。 */
    public static void removeSchoolPower(LivingEntity player) {
        if (!isPresent()) {
            return;
        }
        IronsBridge.removeSchoolPower(player);
    }
}

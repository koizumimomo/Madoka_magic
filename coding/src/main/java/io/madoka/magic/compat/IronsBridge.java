package io.madoka.magic.compat;

import io.madoka.magic.data.MagicalGirl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;

import java.util.UUID;

/**
 * 真正触碰 Iron's Spells 'n Spellbooks 类的桥接类。
 *
 * <p><b>本类只有在 {@link IronsCompat#isPresent()} 为 true 时才允许被加载</b>，
 * 否则 JVM 解析 {@code AttributeRegistry} 会抛 {@code NoClassDefFoundError}。</p>
 *
 * <p>学派法强是<b>联动内容</b>：有灵魂宝石时给对应学派的法强加属性，
 * 没装铁魔法时那个属性压根不存在，也就没有任何加成。</p>
 */
final class IronsBridge {

    /** 联动加成的修饰器 id。 */
    static final UUID SCHOOL_POWER_UUID = UUID.fromString("6f1d1f9e-1b0a-4a6e-9c1f-6b5a0d9e4c11");

    /** 联动给的法强加成（铁魔法的法强属性是加法型，基础值为 0）。 */
    static final double SCHOOL_POWER_BONUS = 0.25D;

    private IronsBridge() {
    }

    static Attribute spellPowerAttribute(String school) {
        switch (school) {
            case "nature" -> {
                return AttributeRegistry.NATURE_SPELL_POWER.get();
            }
            case "fire" -> {
                return AttributeRegistry.FIRE_SPELL_POWER.get();
            }
            case "ice" -> {
                return AttributeRegistry.ICE_SPELL_POWER.get();
            }
            case "lightning" -> {
                return AttributeRegistry.LIGHTNING_SPELL_POWER.get();
            }
            case "holy" -> {
                return AttributeRegistry.HOLY_SPELL_POWER.get();
            }
            case "ender" -> {
                return AttributeRegistry.ENDER_SPELL_POWER.get();
            }
            case "blood" -> {
                return AttributeRegistry.BLOOD_SPELL_POWER.get();
            }
            case "evocation" -> {
                return AttributeRegistry.EVOCATION_SPELL_POWER.get();
            }
            case "eldritch" -> {
                return AttributeRegistry.ELDRITCH_SPELL_POWER.get();
            }
            default -> {
                return null;
            }
        }
    }

    /** 给玩家加上联动法强。 */
    static void applySchoolPower(LivingEntity player, MagicalGirl theme) {
        if (theme == null) {
            return;
        }
        Attribute attribute = spellPowerAttribute(theme.getSchool());
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(SCHOOL_POWER_UUID);
        instance.addTransientModifier(new AttributeModifier(SCHOOL_POWER_UUID,
                "madoka_magic:school_power", SCHOOL_POWER_BONUS, AttributeModifier.Operation.ADDITION));
    }

    /** 移除联动法强。 */
    static void removeSchoolPower(LivingEntity player) {
        for (String school : new String[]{"nature", "fire", "ice", "lightning", "holy", "ender",
                "blood", "evocation", "eldritch"}) {
            Attribute attribute = spellPowerAttribute(school);
            if (attribute == null) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(SCHOOL_POWER_UUID);
            }
        }
    }
}

package io.madoka.magic.registry;

import io.madoka.magic.MadokaMagic;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组自己的属性注册表。
 *
 * <p>四个属性，分成两组：</p>
 *
 * <table>
 *   <tr><th>属性</th><th>作用</th></tr>
 *   <tr><td>{@link #GEM_PURITY} 灵魂宝石纯度</td><td>击杀积累，决定等级与基础数值成长</td></tr>
 *   <tr><td>{@link #MAGIC_MANA} 魔力值</td><td><b>施法资源</b>。每位魔法少女的魔力总量不同，耗尽后继续施法会叠加污染</td></tr>
 *   <tr><td>{@link #MAGIC_CAUSALITY} 因果值</td><td>影响少量（20%）数值，成神需要 ≥ 500</td></tr>
 *   <tr><td>{@link #MAGIC_POWER} 魔法强度</td><td>变身后所有攻击强度的额外加成</td></tr>
 * </table>
 *
 * <p><b>污染值不是属性</b>：它需要"满值即魔女化"这种状态语义，
 * 所以放在玩家数据里（{@link io.madoka.magic.data.PlayerMadokaData}）。</p>
 *
 * <p><b>学派法强不是这里的属性</b>：那是与 Iron's Spells 'n Spellbooks 的联动内容，
 * 装了铁魔法才存在，由 {@link io.madoka.magic.compat.IronsCompat} 处理。</p>
 */
public final class MadokaAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, MadokaMagic.MODID);

    /** 灵魂宝石纯度：击杀积累，纯度越高等级越高、数值越强。 */
    public static final RegistryObject<Attribute> GEM_PURITY = ATTRIBUTES.register(
            "gem_purity",
            () -> new RangedAttribute("attribute.madoka_magic.gem_purity", 0.0D, 0.0D, 100000.0D).setSyncable(true));

    /** 魔力值上限：施法资源的上限，每位魔法少女不同。 */
    public static final RegistryObject<Attribute> MAGIC_MANA = ATTRIBUTES.register(
            "magic_mana",
            () -> new RangedAttribute("attribute.madoka_magic.magic_mana", 0.0D, 0.0D, 10000.0D).setSyncable(true));

    /** 因果值：击杀魔女与眷属获得，影响少量（20%）数值，成神门槛 500。 */
    public static final RegistryObject<Attribute> MAGIC_CAUSALITY = ATTRIBUTES.register(
            "magic_causality",
            () -> new RangedAttribute("attribute.madoka_magic.magic_causality", 0.0D, 0.0D, 100000.0D).setSyncable(true));

    /** 魔法强度：变身后所有攻击强度的额外加成。 */
    public static final RegistryObject<Attribute> MAGIC_POWER = ATTRIBUTES.register(
            "magic_power",
            () -> new RangedAttribute("attribute.madoka_magic.magic_power", 0.0D, 0.0D, 1024.0D).setSyncable(true));

    private MadokaAttributes() {
    }

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        modBus.addListener(MadokaAttributes::addPlayerAttributes);
    }

    private static void addPlayerAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, GEM_PURITY.get());
        event.add(EntityType.PLAYER, MAGIC_MANA.get());
        event.add(EntityType.PLAYER, MAGIC_CAUSALITY.get());
        event.add(EntityType.PLAYER, MAGIC_POWER.get());
    }
}

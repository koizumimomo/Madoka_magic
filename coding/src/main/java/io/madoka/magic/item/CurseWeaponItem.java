package io.madoka.magic.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.madoka.magic.registry.MadokaAttributes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * 咒具：把一颗没净化过的悲叹之种炼成、装备在 Curios 咒具栏的饰品。
 *
 * <p>和净化共用同一颗种子：净化会掉耐久，满耐久才保留完整的诅咒，才能炼成咒具。</p>
 */
public final class CurseWeaponItem extends Item implements ICurioItem {

    /** Curios 槽位 id，与 {@code data/madoka_magic/curios/slots/curse.json} 对应。 */
    public static final String SLOT = "curse";

    private final String theme;
    private final double attack;
    private final double power;
    private final double armor;

    public CurseWeaponItem(String theme, double attack, double power, double armor) {
        super(new Properties().stacksTo(1));
        this.theme = theme;
        this.attack = attack;
        this.power = power;
        this.armor = armor;
    }

    public String theme() {
        return theme;
    }

    @Override
    public boolean canEquip(SlotContext slot, ItemStack stack) {
        return SLOT.equals(slot.identifier());
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slot, UUID uuid, ItemStack stack) {
        if (!canEquip(slot, stack)) {
            return ImmutableMultimap.of();
        }
        var builder = ImmutableMultimap.<Attribute, AttributeModifier>builder();
        add(builder, uuid, Attributes.ATTACK_DAMAGE, "attack", attack);
        add(builder, uuid, Attributes.ARMOR, "armor", armor);
        var magic = MadokaAttributes.MAGIC_POWER.get();
        if (magic != null && power != 0.0D) {
            builder.put(magic, new AttributeModifier(uuid, "curse_power", power,
                    AttributeModifier.Operation.ADDITION));
        }
        return builder.build();
    }

    private static void add(ImmutableMultimap.Builder<Attribute, AttributeModifier> builder, UUID uuid,
                            Attribute attribute, String name, double amount) {
        if (attribute != null && amount != 0.0D) {
            builder.put(attribute, new AttributeModifier(uuid, "curse_" + name, amount,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.madoka_magic.curse.slot"));
        tooltip.add(Component.translatable("tooltip.madoka_magic.curse.pristine"));
    }
}

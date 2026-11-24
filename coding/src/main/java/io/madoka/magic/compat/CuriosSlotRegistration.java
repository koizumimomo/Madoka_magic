package io.madoka.magic.compat;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.WitchTypes;
import io.madoka.magic.item.CurseWeaponItem;
import io.madoka.magic.item.WitchKissItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.SlotTypeMessage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Curios 可选前置：用 IMC 注册咒具栏。
 *
 * <p>数据包里的 {@code data/madoka_magic/curios/slots/curse.json} 在部分环境没有生效，
 * IMC 是 Curios 一直保留的注册通道，这里补一条，两边同 id 会互相覆盖但不会报错。</p>
 */
public final class CuriosSlotRegistration {

    private CuriosSlotRegistration() {}

    public static net.minecraft.world.item.ItemStack equippedCurse(net.minecraft.world.entity.LivingEntity wearer) {
        return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(wearer).resolve()
                .flatMap(inventory -> inventory.getStacksHandler(CurseWeaponItem.SLOT))
                .map(handler -> handler.getStacks().getSlots() == 0 ? net.minecraft.world.item.ItemStack.EMPTY
                        : handler.getStacks().getStackInSlot(0))
                .orElse(net.minecraft.world.item.ItemStack.EMPTY);
    }

    /** Keep ICurioItem implementations outside the base registry's class-loading path. */
    public static Map<String, RegistryObject<Item>> registerItems(DeferredRegister<Item> items) {
        Map<String, RegistryObject<Item>> curses = new LinkedHashMap<>();
        curses.put("sacrifice", items.register("curse_weapon_sacrifice",
                () -> new CurseWeaponItem("sacrifice", 4.0D, 0.0D, 0.0D)));
        curses.put("take", items.register("curse_weapon_take",
                () -> new CurseWeaponItem("take", 0.0D, 0.06D, 0.0D)));
        curses.put("atonement", items.register("curse_weapon_atonement",
                () -> new CurseWeaponItem("atonement", 0.0D, 0.0D, 3.0D)));
        for (String theme : WitchKissItem.THEMES) {
            if (!theme.equals(WitchTypes.WITHER) && !curses.containsKey(theme)) {
                curses.put(theme, items.register("curse_weapon_" + theme,
                        () -> new CurseWeaponItem(theme, 0.0D, 0.0D, 0.0D)));
            }
        }
        return Collections.unmodifiableMap(curses);
    }

    public static void register() {
        InterModComms.sendTo("curios", SlotTypeMessage.REGISTER_TYPE,
                () -> new SlotTypeMessage.Builder("curse")
                        .size(1)
                        .icon(ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "slot/curse"))
                        .build());
    }
}

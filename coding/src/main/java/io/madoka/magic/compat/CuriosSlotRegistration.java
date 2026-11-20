package io.madoka.magic.compat;

import io.madoka.magic.MadokaMagic;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.InterModComms;
import top.theillusivec4.curios.api.SlotTypeMessage;

/**
 * Curios 可选前置：用 IMC 注册咒具栏。
 *
 * <p>数据包里的 {@code data/madoka_magic/curios/slots/curse.json} 在部分环境没有生效，
 * IMC 是 Curios 一直保留的注册通道，这里补一条，两边同 id 会互相覆盖但不会报错。</p>
 */
public final class CuriosSlotRegistration {

    private CuriosSlotRegistration() {}

    public static void register() {
        InterModComms.sendTo("curios", SlotTypeMessage.REGISTER_TYPE,
                () -> new SlotTypeMessage.Builder("curse")
                        .size(1)
                        .icon(ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "slot/curse"))
                        .build());
    }
}

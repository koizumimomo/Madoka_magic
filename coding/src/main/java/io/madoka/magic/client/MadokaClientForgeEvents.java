package io.madoka.magic.client;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.client.hud.HudEditScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 总线上的客户端事件。
 *
 * <p>和 {@link MadokaClient}（MOD 总线：按键注册、模型注册）分开，
 * 两边事件类型不能混着放，否则启动就会报 IModBusEvent 类型错误。</p>
 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MadokaClientForgeEvents {

    private MadokaClientForgeEvents() {}

    /** J 键：打开 HUD 布局编辑。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return;
        }
        while (MadokaKeys.HUD_EDIT.consumeClick()) {
            minecraft.setScreen(new HudEditScreen());
        }
    }
}

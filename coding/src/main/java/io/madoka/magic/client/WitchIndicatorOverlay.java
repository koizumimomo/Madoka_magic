package io.madoka.magic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.madoka.magic.MadokaMagic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 经验条上的魔女指示器。
 *
 * <p>魔法少女化后，经验条上会出现一个指向最近魔女的小标记，
 * 让玩家不用满世界找她。角度由服务端算好（相对玩家朝向，0 = 正前方）。</p>
 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WitchIndicatorOverlay {

    private static final ResourceLocation MARKER =
            new ResourceLocation("madoka_magic", "textures/gui/witch_marker.png");

    private WitchIndicatorOverlay() {
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())) {
            return;
        }
        float direction = ClientMadokaState.getWitchDirection();
        if (Float.isNaN(direction)) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        // 经验条的位置：屏幕中央偏上的一条
        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        int centerX = window.getGuiScaledWidth() / 2;
        int y = window.getGuiScaledHeight() - 32 + 3 - 9;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, y + 4.5D, 0.0D);
        pose.mulPose(Axis.ZP.rotationDegrees(direction));
        graphics.blit(MARKER, -4, -4, 0.0F, 0.0F, 9, 9, 9, 9);
        pose.popPose();
    }
}

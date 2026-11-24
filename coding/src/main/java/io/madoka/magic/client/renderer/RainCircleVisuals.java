package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.network.MadokaNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 魔女泪雨的范围标注：服务端施法时同步范围，客户端在施法点贴地渲染
 * 一圈缓慢旋转的紫黑法阵，玩家一眼就能看出伤害覆盖到哪。雨结束（或客户端
 * 超时）后消失。
 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT)
public final class RainCircleVisuals {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/particle/witch_rain_circle.png");
    private static final double SPIN = -0.022D;

    private record State(double x, double y, double z, float radius, long endGameTime, ResourceKey<Level> dimension) {}

    private static volatile State state;

    private RainCircleVisuals() {}

    /** 网络线程之外（主线程 enqueueWork）调用：开始 / 结束一场泪雨的标注。 */
    public static void accept(MadokaNetwork.RainCircleState msg) {
        if (!msg.active()) {
            state = null;
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        state = new State(msg.x(), msg.y(), msg.z(), msg.radius(),
                level.getGameTime() + msg.durationTicks(), level.dimension());
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        State s = state;
        if (s == null) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(s.dimension()) || level.getGameTime() > s.endGameTime()) {
            state = null;
            return;
        }
        double time = level.getGameTime() + event.getPartialTick();
        var eye = event.getCamera().getPosition();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var pose = event.getPoseStack();
        var layer = RenderType.entityTranslucentEmissive(TEXTURE);
        double phase = time * SPIN;
        // 半透明呼吸：不喧宾夺主，但始终可见
        double pulse = 0.58D + 0.18D * Math.sin(time * 0.09D);
        pose.pushPose();
        pose.translate(s.x() - eye.x, s.y() + 0.06D - eye.y, s.z() - eye.z);
        circle(pose.last(), buffers.getBuffer(layer), s.radius(), phase, pulse);
        pose.popPose();
        buffers.endBatch(layer);
    }

    private static void circle(PoseStack.Pose pose, VertexConsumer out, double radius, double angle, double alpha) {
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        int opacity = (int) (255 * Mth.clamp(alpha, 0.0D, 1.0D));
        corner(pose, out, -radius, -radius, cosine, sine, 0.0F, 0.0F, opacity);
        corner(pose, out, -radius, radius, cosine, sine, 0.0F, 1.0F, opacity);
        corner(pose, out, radius, radius, cosine, sine, 1.0F, 1.0F, opacity);
        corner(pose, out, radius, -radius, cosine, sine, 1.0F, 0.0F, opacity);
    }

    private static void corner(PoseStack.Pose pose, VertexConsumer out, double x, double z,
                               double cos, double sin, float u, float v, int alpha) {
        out.vertex(pose.pose(), (float) (x * cos - z * sin), 0.0F, (float) (x * sin + z * cos))
                .color(255, 255, 255, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(), 0, 1, 0).endVertex();
    }
}

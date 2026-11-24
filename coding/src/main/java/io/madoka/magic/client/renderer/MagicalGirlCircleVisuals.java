package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Random;

/** 变身魔法少女脚下的常驻旋转法阵与上升光点，三系各有专属法阵与配色。 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT)
public final class MagicalGirlCircleVisuals {
    private record Style(RenderType layer, SimpleParticleType streak, SimpleParticleType motif) {}

    private static final double RADIUS = 1.18D;
    private static final double SPIN = -0.032D;
    private static final double VIEW_DISTANCE_SQR = 64.0D * 64.0D;

    /**
     * 献身=金色蝶纹法阵、赎罪=天蓝碎片法阵、索取=紫罗兰蜂窝法阵。
     * 不能在静态初始化器里取 RegistryObject：本类被 @Mod.EventBusSubscriber
     * 在 mod 构造阶段 Class.forName 提前加载，此时粒子注册表尚未注册，
     * 必须等首次渲染时（注册已完成）再惰性构建。
     */
    private static volatile Map<String, Style> styles;

    private static Map<String, Style> styles() {
        var map = styles;
        if (map == null) {
            map = Map.of(
                    "sacrifice", new Style(circleLayer("devotion_circle"),
                            ModParticles.CircleStreak.DEVOTION.get(), ModParticles.Devotion.BUTTERFLY.get()),
                    "atonement", new Style(circleLayer("atonement_circle"),
                            ModParticles.CircleStreak.ATONEMENT.get(), null),
                    "take", new Style(circleLayer("take_circle"),
                            ModParticles.CircleStreak.TAKE.get(), null));
            styles = map;
        }
        return map;
    }

    private static RenderType circleLayer(String texture) {
        return RenderType.entityTranslucentEmissive(ResourceLocation.fromNamespaceAndPath(
                MadokaMagic.MODID, "textures/particle/" + texture + ".png"));
    }

    private static Style styleOf(AbstractClientPlayer player) {
        return styles().getOrDefault(TransformationVisuals.magicalGirlTheme(player),
                styles().get("sacrifice"));
    }

    private static boolean showCircle(AbstractClientPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isInvisible()
                && !TransformationVisuals.magicalGirlTheme(player).isEmpty();
    }

    private MagicalGirlCircleVisuals() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        var client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null || client.player == null) {
            return;
        }
        var eye = event.getCamera().getPosition();
        var buffers = client.renderBuffers().bufferSource();
        var pose = event.getPoseStack();
        double time = level.getGameTime() + event.getPartialTick();
        for (Style style : styles().values()) {
            boolean drew = false;
            for (AbstractClientPlayer player : level.players()) {
                if (!showCircle(player) || styleOf(player) != style
                        || eye.distanceToSqr(player.getPosition(event.getPartialTick())) > VIEW_DISTANCE_SQR) {
                    continue;
                }
                var center = player.getPosition(event.getPartialTick()).subtract(eye);
                double phase = time * SPIN + player.getId() * 1.7D;
                double pulse = 0.72D + 0.14D * Math.sin(time * 0.085D + player.getId());
                pose.pushPose();
                pose.translate(center.x, center.y, center.z);
                circle(pose.last(), buffers.getBuffer(style.layer()), RADIUS, phase, pulse);
                pose.popPose();
                drew = true;
            }
            if (drew) {
                buffers.endBatch(style.layer());
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RandomSource random = level.random;
        for (AbstractClientPlayer player : level.players()) {
            if (!showCircle(player)) {
                continue;
            }
            Style style = styleOf(player);
            var feet = player.position();
            // 光点沿法阵内缓慢螺旋上升：对应颜色的单像素短线条
            if (random.nextFloat() < 0.45F) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double radius = 0.25D + random.nextDouble() * (RADIUS - 0.45D);
                level.addParticle(style.streak(),
                        feet.x + Math.cos(angle) * radius, feet.y + 0.08D, feet.z + Math.sin(angle) * radius,
                        0.0D, 0.028D + random.nextDouble() * 0.02D, 0.0D);
            }
            // 献身系偶尔一只金蝶随之飘起
            if (style.motif() != null && random.nextFloat() < 0.10F) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double radius = 0.30D + random.nextDouble() * (RADIUS - 0.55D);
                level.addParticle(style.motif(),
                        feet.x + Math.cos(angle) * radius, feet.y + 0.10D, feet.z + Math.sin(angle) * radius,
                        0.0D, 0.022D, 0.0D);
            }
        }
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
        out.vertex(pose.pose(), (float) (x * cos - z * sin), 0.05F, (float) (x * sin + z * cos))
                .color(255, 255, 255, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(), 0, 1, 0).endVertex();
    }
}

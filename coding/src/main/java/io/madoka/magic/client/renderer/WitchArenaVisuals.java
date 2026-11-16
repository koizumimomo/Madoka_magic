package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.MadokaWitchEntity;
import io.madoka.magic.entity.WitchCast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT)
public final class WitchArenaVisuals {
    static final RenderType LAYER = RenderType.entityTranslucentEmissive(
            ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "textures/particle/arena_mark.png"));
    private static final int ARCS_PER_SECTOR = 12;
    private static final double QUARTER = Math.PI / 2;

    private WitchArenaVisuals() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        var client = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || client.level == null || client.player == null) {
            return;
        }
        var camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        var witches = client.level.getEntitiesOfClass(MadokaWitchEntity.class, new AABB(eye, eye).inflate(72),
                witch -> witch.isAlive() && (witch.getRainVisual() != 0 || !witch.getArenaVisual().isEmpty()
                        || witch.getQuintetCast() != null || witch.getCurseRadius() > 0));
        if (witches.isEmpty()) {
            return;
        }
        var buffers = client.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        long now = client.level.getGameTime();
        for (var witch : witches) {
            if (!witch.isInvisibleTo(client.player) && !witch.isNoAi()) {
                double time = now + event.getPartialTick();
                if (witch.getCurseRadius() > 0) {
                    Vec3 origin = witch.getPosition(event.getPartialTick());
                    pose.pushPose();
                    pose.translate(origin.x - eye.x, origin.y - eye.y + 0.012D, origin.z - eye.z);
                    new QuintetWitchVisuals(pose.last(), buffers.getBuffer(LAYER)).curse(witch.getCurseRadius(), time);
                    pose.popPose();
                }
                var cast = witch.getQuintetCast();
                if (cast != null) {
                    Vec3 origin = cast.origin();
                    pose.pushPose();
                    pose.translate(origin.x - eye.x, origin.y - eye.y, origin.z - eye.z);
                    double runner = cast.kind() == WitchCast.Kind.OPHELIA_DASH
                            ? Mth.clamp(witch.getPosition(event.getPartialTick()).subtract(origin)
                                    .dot(cast.castPoint(0, 0, 1)), 0, cast.radius())
                            : 0;
                    new QuintetWitchVisuals(pose.last(), buffers.getBuffer(LAYER)).cast(witch, cast, time, runner);
                    if (cast.kind() == WitchCast.Kind.WHEEL_WAVE) {
                        MermaidWheelModel.render(cast, time, pose, buffers);
                    } else if (cast.kind() == WitchCast.Kind.PROCESSION) {
                        NutcrackerPuppetModel.render(cast, time, pose, buffers);
                    } else if (cast.kind() == WitchCast.Kind.OPHELIA_DASH) {
                        OpheliaDecoyModel.render(cast, time, runner, pose, buffers);
                    } else if (cast.kind() == WitchCast.Kind.ATONEMENT_SUMMON) {
                        AtonementVisuals.summons(pose, buffers, cast, time);
                    } else if (cast.kind() == WitchCast.Kind.CANDELORO_TEA) {
                        CandeloroVisuals.tea(pose, buffers, cast, time);
                    }
                    pose.popPose();
                }
            }
            float rain = witch.getRainVisual();
            if (witch.isAtonement() && (rain != 0 || witch.getQuintetCast() != null)) {
                Vec3 frame = witch.getPosition(event.getPartialTick());
                pose.pushPose();
                pose.translate(frame.x - eye.x, frame.y - eye.y, frame.z - eye.z);
                AtonementVisuals.beneathFrame(pose, buffers, now + event.getPartialTick());
                pose.popPose();
            }
            if (rain != 0) {
                Vec3 origin = witch.getRainCenter().map(Vec3::atBottomCenterOf).orElseGet(() -> witch.getPosition(event.getPartialTick()));
                pose.pushPose();
                pose.translate(origin.x - eye.x, origin.y - eye.y, origin.z - eye.z);
                var mark = buffers.getBuffer(LAYER);
                draw(pose.last(), mark, Math.abs(rain), 15, 0, 0, 0x529cef, false);
                var sprites = new RitualSprites(pose, buffers, camera, WitchSkillVisuals.LAYER);
                WitchSkillVisuals.drawRain(sprites, Math.abs(rain), rain < 0, now + event.getPartialTick());
                pose.popPose();
                continue;
            }
            var visual = witch.getArenaVisual();
            if (visual.isEmpty()) {
                continue;
            }
            Vec3 center = Vec3.atBottomCenterOf(BlockPos.of(visual.getLong("Center")));
            int flash = now < visual.getLong("FlashUntil") ? visual.getInt("Flash") : 0;
            int color = "take".equals(witch.getThemeId()) ? 0xfa6a9b : 0xffcb53;
            pose.pushPose();
            pose.translate(center.x - eye.x, center.y - eye.y, center.z - eye.z);
            draw(pose.last(), buffers.getBuffer(LAYER), visual.getFloat("Radius"),
                    visual.getInt("Pending"), visual.getInt("Warning"), flash, color, true);
            pose.popPose();
        }
        buffers.endBatch(LAYER);
        buffers.endBatch(WitchSkillVisuals.LAYER);
        buffers.endBatch(AtonementVisuals.CIRCLES);
        buffers.endBatch(MermaidWheelModel.LAYER);
        buffers.endBatch(CandeloroVisuals.TEA);
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer out, double radius, int pending,
                             int warning, int flash, int color, boolean sectors) {
        for (int sector = 0; sector < 4; sector++) {
            int bit = 1 << sector;
            if (((pending | flash) & bit) == 0) {
                continue;
            }
            double start = switch (sector) {
                case 1 -> QUARTER;
                case 3 -> QUARTER * 2;
                case 2 -> QUARTER * 3;
                default -> 0;
            };
            int fill = (flash & bit) != 0 ? 104 : (warning & bit) != 0 ? 56 : 23;
            int line = (warning & bit) != 0 ? 224 : 126;
            double inset = radius - 0.18D;
            for (int index = 0; index < ARCS_PER_SECTOR; index++) {
                double a = start + index * QUARTER / ARCS_PER_SECTOR;
                double b = start + (index + 1) * QUARTER / ARCS_PER_SECTOR;
                double ax = Math.cos(a), az = Math.sin(a);
                double bx = Math.cos(b), bz = Math.sin(b);
                quad(pose, out, 0, 0, bx * inset, bz * inset, ax * inset, az * inset, 0, 0, color, fill, 0.018F);
                quad(pose, out, ax * inset, az * inset, bx * inset, bz * inset,
                        bx * radius, bz * radius, ax * radius, az * radius, color, line, 0.022F);
            }
            if (sectors) {
                ray(pose, out, radius, start, color, line, 1.0D);
                ray(pose, out, radius, start + QUARTER, color, line, -1.0D);
            }
        }
    }

    private static void ray(PoseStack.Pose pose, VertexConsumer out, double radius, double angle, int color, int opacity, double side) {
        double x = Math.cos(angle), z = Math.sin(angle);
        double dx = -z * 0.085D * side, dz = x * 0.085D * side;
        quad(pose, out, 0, 0, dx, dz, x * radius + dx, z * radius + dz, x * radius, z * radius, color, opacity, 0.026F);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer out,
                             double ax, double az, double bx, double bz, double cx, double cz, double dx, double dz,
                             int color, int opacity, float y) {
        vertex(pose, out, ax, az, color, opacity, y);
        vertex(pose, out, bx, bz, color, opacity, y);
        vertex(pose, out, cx, cz, color, opacity, y);
        vertex(pose, out, dx, dz, color, opacity, y);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, double x, double z, int color, int opacity, float y) {
        out.vertex(pose.pose(), (float) x, y, (float) z).color(color >> 16 & 255, color >> 8 & 255, color & 255, opacity)
                .uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), 0, 1, 0).endVertex();
    }
}

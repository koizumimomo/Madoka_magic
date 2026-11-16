package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.entity.WitchTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Map;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT)
public final class WitchBarrierSky {
    private static final ResourceKey<Level> MERMAID_DIMENSION = MadokaDimensions.witchLevel(WitchTypes.MERMAID);
    private static final Map<ResourceKey<Level>, Sky> SKIES = Map.ofEntries(
            sky("sacrifice", 0x5a493d, 0x543319, 0x372619),
            sky("take", 0x58362a, 0x4d2b1c, 0x2b1712),
            sky("atonement", 0x3a4c60, 0x293d56, 0x1e2f48),
            sky(WitchTypes.WITHER, 0x3b3137, 0x2d252d, 0x211b22),
            sky(WitchTypes.SALVATION, 0x6f474e, 0x66404c, 0x4b2837),
            sky(WitchTypes.NUTCRACKER, 0x513643, 0x372133, 0x2b1929),
            sky("candeloro", 0x66422f, 0x6c3724, 0x4e2b21),
            sky(WitchTypes.MERMAID, 0x375f61, 0x112931, 0x071016),
            sky(WitchTypes.OPHELIA, 0x421f23, 0x3c1d20, 0x271417),
            sky(WitchTypes.GLUTTONY, 0x3c4327, 0x2c321e, 0x202618));
    private static final ResourceLocation STAFF = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/environment/oktavia_staff.png");
    private static final float RADIUS = 32.0F;
    private static final float TURN = (float) (Math.PI * 2.0);
    private static final int PANORAMA_COPIES = 2;
    private static final int COLUMNS = 48;
    private static final int ROWS = 20;
    private static final float LOW_ELEVATION = -4.0F;
    private static final float HIGH_ELEVATION = 48.0F;
    private static final float CAP_START = 56.0F;
    private static final float[] GRADIENT_ELEVATIONS = {-90.0F, 0.0F, 23.0F, 52.0F, 90.0F};

    private WitchBarrierSky() {}

    private static Map.Entry<ResourceKey<Level>, Sky> sky(String theme, int horizon, int mid, int zenith) {
        var texture = ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "textures/environment/" + theme + "_sky.png");
        return Map.entry(MadokaDimensions.witchLevel(theme), new Sky(texture, horizon, mid, zenith));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        Sky sky = visibleSky(event.getCamera());
        if (sky == null) {
            return;
        }

        Matrix4f view = event.getPoseStack().last().pose();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        try {
            drawGradient(view, sky);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            drawPanorama(view, sky.panorama());
            if (MERMAID_DIMENSION.equals(Minecraft.getInstance().level.dimension())) {
                double time = (Minecraft.getInstance().level.getGameTime() % 72000L + event.getPartialTick()) / 20.0;
                drawStaves(view, time);
            }
        } finally {
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
        }
    }

    @SubscribeEvent
    public static void colorFog(ViewportEvent.ComputeFogColor event) {
        Sky sky = visibleSky(event.getCamera());
        if (sky != null) {
            int horizon = sky.horizon();
            event.setRed((horizon >> 16 & 255) / 255.0F);
            event.setGreen((horizon >> 8 & 255) / 255.0F);
            event.setBlue((horizon & 255) / 255.0F);
        }
    }

    @SubscribeEvent
    public static void extendHorizonFog(ViewportEvent.RenderFog event) {
        float far = event.getFarPlaneDistance();
        if (event.getMode() == FogRenderer.FogMode.FOG_TERRAIN && far > 36.0F && visibleSky(event.getCamera()) != null) {
            event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), Math.max(26.0F, far * 0.56F)));
            event.setFarPlaneDistance(far * 0.90F);
            event.setCanceled(true);
        }
    }

    private static Sky visibleSky(Camera camera) {
        var level = Minecraft.getInstance().level;
        if (level == null || camera.getFluidInCamera() != FogType.NONE
                || camera.getEntity() instanceof LivingEntity viewer
                && (viewer.hasEffect(MobEffects.BLINDNESS) || viewer.hasEffect(MobEffects.DARKNESS))) {
            return null;
        }
        return SKIES.get(level.dimension());
    }

    private static void drawGradient(Matrix4f view, Sky sky) {
        int[] colors = {sky.horizon(), sky.horizon(), sky.mid(), sky.zenith(), sky.zenith()};
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int column = 0; column < COLUMNS; column++) {
            float start = TURN * column / COLUMNS;
            float end = TURN * (column + 1) / COLUMNS;
            for (int row = 0; row < GRADIENT_ELEVATIONS.length - 1; row++) {
                float bottom = GRADIENT_ELEVATIONS[row];
                float top = GRADIENT_ELEVATIONS[row + 1];
                int lowerColor = colors[row];
                int upperColor = colors[row + 1];
                gradientVertex(buffer, view, start, bottom, lowerColor);
                gradientVertex(buffer, view, end, bottom, lowerColor);
                gradientVertex(buffer, view, end, top, upperColor);
                gradientVertex(buffer, view, start, top, upperColor);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void drawPanorama(Matrix4f view, ResourceLocation panorama) {
        RenderSystem.setShaderTexture(0, panorama);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int panel = 0; panel < PANORAMA_COPIES; panel++) {
            float origin = TURN * (panel / (float) PANORAMA_COPIES - 0.25F);
            float span = TURN / PANORAMA_COPIES;
            // Adjacent panels share their edge pixels to keep the panorama continuous.
            boolean mirrored = panel % 2 != 0;
            for (int column = 0; column < COLUMNS; column++) {
                float left = column / (float) COLUMNS;
                float right = (column + 1) / (float) COLUMNS;
                float uLeft = mirrored ? 1 - left : left;
                float uRight = mirrored ? 1 - right : right;
                for (int row = 0; row < ROWS; row++) {
                    float lower = row / (float) ROWS;
                    float upper = (row + 1) / (float) ROWS;
                    float bottom = panoramaElevation(lower);
                    float top = panoramaElevation(upper);
                    int lowerAlpha = panoramaAlpha(lower);
                    int upperAlpha = panoramaAlpha(upper);
                    texturedVertex(buffer, view, origin + span * left, bottom, uLeft, 1 - lower, lowerAlpha);
                    texturedVertex(buffer, view, origin + span * right, bottom, uRight, 1 - lower, lowerAlpha);
                    texturedVertex(buffer, view, origin + span * right, top, uRight, 1 - upper, upperAlpha);
                    texturedVertex(buffer, view, origin + span * left, top, uLeft, 1 - upper, upperAlpha);
                }
            }
        }
        drawZenithCap(buffer, view);
        BufferUploader.drawWithShader(buffer.end());
    }

    private static float panoramaElevation(float height) {
        float dome = Mth.clamp((height - 0.65F) / 0.35F, 0, 1);
        return Mth.lerp(height, LOW_ELEVATION, HIGH_ELEVATION) + 20 * smooth(dome);
    }

    private static int panoramaAlpha(float height) {
        float vertical = Mth.clamp(Math.min(height / 0.24F, (1 - height) / 0.30F), 0, 1);
        return Math.round(255 * smooth(vertical));
    }

    private static float smooth(float value) {
        return value * value * (3 - 2 * value);
    }

    private static void drawZenithCap(BufferBuilder buffer, Matrix4f view) {
        // Project the center of the artwork onto the cap to avoid pinching the panorama at the pole.
        int rings = 8;
        for (int column = 0; column < COLUMNS; column++) {
            float start = TURN * column / COLUMNS;
            float end = TURN * (column + 1) / COLUMNS;
            for (int ring = 0; ring < rings; ring++) {
                float bottom = Mth.lerp(ring / (float) rings, CAP_START, 90.0F);
                float top = Mth.lerp((ring + 1) / (float) rings, CAP_START, 90.0F);
                capVertex(buffer, view, start, bottom);
                capVertex(buffer, view, end, bottom);
                capVertex(buffer, view, end, top);
                capVertex(buffer, view, start, top);
            }
        }
    }

    private static void capVertex(BufferBuilder buffer, Matrix4f view, float angle, float elevation) {
        float radial = Mth.cos(elevation * Mth.DEG_TO_RAD);
        float u = 0.5F - 0.28F * radial * Mth.sin(angle);
        float v = 0.25F + 0.32F * radial * Mth.cos(angle);
        int alpha = Math.round(255 * smooth(Mth.clamp((elevation - CAP_START) / 18.0F, 0, 1)));
        texturedVertex(buffer, view, angle, elevation, u, v, alpha);
    }

    private static void drawStaves(Matrix4f view, double time) {
        RenderSystem.setShaderTexture(0, STAFF);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int strips = 3;
        int segments = 16;
        float width = 62.0F * Mth.DEG_TO_RAD;
        for (int strip = 0; strip < strips; strip++) {
            float center = (float) (TURN * (strip / (double) strips + time / 180.0));
            float elevation = 23.0F + 3.5F * Mth.sin((float) (time * TURN / 30.0) + strip * 2.1F);
            for (int segment = 0; segment < segments; segment++) {
                float left = segment / (float) segments;
                float right = (segment + 1) / (float) segments;
                float start = center + width * (left - 0.5F);
                float end = center + width * (right - 0.5F);
                texturedVertex(buffer, view, start, elevation - 6, left, 1, 145);
                texturedVertex(buffer, view, end, elevation - 6, right, 1, 145);
                texturedVertex(buffer, view, end, elevation + 6, right, 0, 145);
                texturedVertex(buffer, view, start, elevation + 6, left, 0, 145);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void gradientVertex(BufferBuilder buffer, Matrix4f view, float angle, float elevation, int color) {
        skyVertex(buffer, view, angle, elevation).color(color >> 16 & 255, color >> 8 & 255, color & 255, 255).endVertex();
    }

    private static void texturedVertex(BufferBuilder buffer, Matrix4f view, float angle, float elevation,
                                       float u, float v, int alpha) {
        skyVertex(buffer, view, angle, elevation).uv(u, v).color(255, 255, 255, alpha).endVertex();
    }

    private static VertexConsumer skyVertex(BufferBuilder buffer, Matrix4f view, float angle, float elevation) {
        float radians = elevation * Mth.DEG_TO_RAD;
        float horizontal = RADIUS * Mth.cos(radians);
        return buffer.vertex(view, -horizontal * Mth.sin(angle), RADIUS * Mth.sin(radians), horizontal * Mth.cos(angle));
    }

    private record Sky(ResourceLocation panorama, int horizon, int mid, int zenith) {}
}

package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.madoka.magic.MadokaMagic;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The same animated PixelFX motifs are shared by external bosses and their ritual items. */
final class ExternalWitchParticles {
    static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/particle/external_witch_orbits.png");
    static final RenderType LAYER = RenderType.entityTranslucentEmissive(ATLAS);
    static final int COLUMNS = 8;
    static final int ROWS = 12;
    static final int SIDE = 32;
    private static final int FRAMES = 4;

    private ExternalWitchParticles() {}

    static int tile(RitualVisualStyle.Orbit orbit, int index, double ticks) {
        int frame = Math.floorMod(Mth.floor(ticks / orbit.frameTicks()) + index, FRAMES);
        return orbit.row() * COLUMNS + Math.floorMod(index, 2) * FRAMES + frame;
    }

    static void render(PoseStack pose, MultiBufferSource buffers, Camera camera, RitualVisualStyle style,
                       double ticks, double width, double height) {
        var orbit = style.orbit();
        if (orbit == null) return;
        var sprites = new RitualSprites(pose, buffers, camera, LAYER);
        double scale = Mth.clamp(Math.max(width, height) / 3.5, 1, 2.5);
        double edge = Math.max(0, width * 0.5);
        double spread = Math.max(0.4, height * orbit.heightSpread());
        for (int i = 0; i < orbit.count(); i++) {
            double phase = Mth.frac(ticks / 140 + (double) i / orbit.count());
            double angle = ticks * orbit.speed() + i * WitchAura.TURN / orbit.count();
            double radius = edge + orbit.radius() * scale;
            double y = height * 0.55 + Math.sin(angle * 1.7) * spread * 0.5;
            double roll = 0.15 * Math.sin(angle * 2);
            switch (style.motion()) {
                case FLING -> {
                    radius += phase * phase * scale * 0.7;
                    y += (phase - 0.5) * spread;
                    roll += (i % 2 == 0 ? 1 : -1) * phase * 0.45;
                }
                case GATHER -> {
                    radius = edge + orbit.radius() * scale * (1.3 - phase * 0.5);
                    y += (0.5 - phase) * spread * 0.5;
                }
                case BIND -> {
                    angle = (i % 2 == 0 ? 1 : -1) * ticks * orbit.speed() + i * WitchAura.TURN / orbit.count();
                    y = height * 0.5 + Math.sin(angle) * spread * 0.55;
                }
                case REWIND -> {
                    angle = Math.sin(Math.floor(ticks / 5) * orbit.speed() * 5) * 1.2 + i * WitchAura.TURN / orbit.count();
                    roll = angle * 0.35;
                }
                case RIPPLE -> y += Math.sin(angle * 2 - ticks * 0.025) * spread * 0.35;
                case PARADE -> {
                    angle = (i % 2 == 0 ? 1 : -1) * ticks * orbit.speed() + i * WitchAura.TURN / orbit.count();
                    radius += (i % 2) * scale * 0.25;
                }
                default -> { }
            }
            double size = orbit.size() * scale * (0.94 + Math.sin(angle + ticks * 0.04) * 0.06);
            sprites.drawTile(tile(orbit, i, ticks), COLUMNS, ROWS, SIDE, SIDE,
                    radius * Math.cos(angle), y, radius * Math.sin(angle), size, size, roll,
                    0xffffff, WitchAura.fade(phase) * 0.9);
        }
    }
}

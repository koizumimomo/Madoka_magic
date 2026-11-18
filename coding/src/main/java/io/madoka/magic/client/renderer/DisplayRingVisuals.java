package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.madoka.magic.item.RitualItemAppearance;
import io.madoka.magic.item.WitchKissItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Displays the contained witch's motifs around the rings and her star above them. */
public final class DisplayRingVisuals {
    private static final int MOTE_COUNT = 8;
    private static final double ORBIT_TICKS = 210.0;
    private static final double STAR_HEIGHT = 1.09;

    private DisplayRingVisuals() {}

    public static void render(ItemStack stack, PoseStack pose, MultiBufferSource buffers, double ticks) {
        if (!(stack.getItem() instanceof WitchKissItem) || WitchKissItem.modelIndex(stack) == 0) return;
        String theme = WitchKissItem.getThemeId(stack);
        var style = RitualVisualStyle.forTheme(theme);
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        var particles = new RitualItemParticles(new RitualItemAppearance(theme, true));
        var orbit = new RitualSprites(pose, buffers, camera, particles.layer());

        for (int i = 0; i < MOTE_COUNT; i++) {
            double phase = Mth.frac(ticks / ORBIT_TICKS + (double) i / MOTE_COUNT);
            double angle = phase * WitchAura.TURN;
            double radius = 1.05 + 0.10 * Math.sin(angle * 2 + i * Math.PI / 4);
            double height = 0.46 * Math.sin(angle + (i % 2) * Math.PI);
            double pulse = 0.72 + 0.18 * Math.cos(angle * 2);
            double size = i % 2 == 0 ? 0.17 : 0.21;
            particles.world(orbit, i, ticks, radius * Math.cos(angle), height, radius * Math.sin(angle),
                    size, 0.28 * Math.sin(angle), pulse);
        }

        var star = new RitualSprites(pose, buffers, camera);
        double shimmer = Math.sin(ticks * WitchAura.TURN / 140);
        star.draw(RitualVisualStyle.Sprite.STAR, 0, STAR_HEIGHT, 0, 0.25 + 0.018 * shimmer,
                0, style.crown(), 0.9 + 0.08 * shimmer);
    }

}

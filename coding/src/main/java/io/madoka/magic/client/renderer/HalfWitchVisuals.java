package io.madoka.magic.client.renderer;

import io.madoka.magic.MadokaMagic;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

final class HalfWitchVisuals {
    static final RenderType LAYER = RenderType.entityTranslucentEmissive(ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/particle/half_witch.png"));
    static final int BURST_TICKS = 23;
    private static final int FRAMES = 8;
    private static final int ROWS = 5;
    private static final int ORBIT_MOTES = 10;
    private static final int CROWN_STARS = 12;

    private HalfWitchVisuals() {
    }

    static void orbit(RitualSprites sprites, String theme, double height, double ticks, boolean firstPerson) {
        int motif = motif(theme);
        for (int i = 0; i < ORBIT_MOTES; i++) {
            boolean ink = i % 3 == 0;
            double phase = (ticks / (ink ? 58.0D : 46.0D) + i / (double) ORBIT_MOTES) % 1.0D;
            double angle = ticks * (ink ? -0.031D : 0.055D) + i * WitchAura.TURN / ORBIT_MOTES;
            double radius = (ink ? 0.68D : 0.91D) + 0.25D * WitchAura.smooth(phase);
            double y = height * (ink ? 0.28D + 0.13D * Math.sin(angle) : 0.26D + 0.48D * phase);
            double size = (ink ? 0.22D : 0.25D) - 0.08D * phase;
            draw(sprites, ink ? 0 : motif, ticks * 0.55D + i * 3, radius * Math.cos(angle), y,
                    radius * Math.sin(angle), size, Math.sin(angle) * 0.47D, WitchAura.fade(phase));
        }
        if (!firstPerson) {
            for (int i = 0; i < CROWN_STARS; i++) {
                double angle = ticks * 0.013D + i * WitchAura.TURN / CROWN_STARS;
                double z = Math.sin(angle) * 0.58D;
                double size = i % 2 == 0 ? 0.185D : 0.148D;
                draw(sprites, 4, ticks * 0.27D + i, Math.cos(angle) * 0.58D,
                        height + 0.15D + z * 0.14D, z, size, 0, 0.96D);
            }
        }
    }

    static void burst(RitualSprites sprites, String theme, double height, double phase, boolean firstPerson) {
        int motif = motif(theme);
        for (int i = 0; i < 12; i++) {
            double delay = i % 4 * 0.045D;
            double life = (phase - delay) / (1.0D - delay);
            if (life <= 0 || life >= 1) {
                continue;
            }
            double spread = 1.0D - (1.0D - life) * (1.0D - life);
            double angle = i * WitchAura.TURN / 12 + spread * 1.6D;
            double radius = 0.32D + spread * (0.87D + 0.11D * (i % 3));
            double y = height * (0.20D + (0.36D + i % 3 * 0.12D) * spread);
            if (firstPerson && y > height * 0.60D && radius < 1.0D) {
                continue;
            }
            draw(sprites, i % 3 == 0 ? 0 : motif, life * 19 + i * 2, radius * Math.cos(angle), y,
                    radius * Math.sin(angle), 0.26D - spread * 0.12D, angle * 0.3D, WitchAura.fade(life));
        }
    }

    private static int motif(String theme) {
        return switch (theme) {
            case "sacrifice" -> 1;
            case "take" -> 2;
            case "atonement" -> 3;
            default -> 0;
        };
    }

    private static void draw(RitualSprites sprites, int row, double time, double x, double y, double z,
                             double size, double roll, double alpha) {
        int frame = Mth.floor(time) % FRAMES;
        sprites.drawTile(row * FRAMES + frame, FRAMES, ROWS, x, y, z, size, roll, 0xffffff, alpha);
    }
}

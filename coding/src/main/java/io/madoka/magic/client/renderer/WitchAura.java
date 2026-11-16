package io.madoka.magic.client.renderer;

import io.madoka.magic.client.renderer.RitualVisualStyle.Motion;
import io.madoka.magic.client.renderer.RitualVisualStyle.Sprite;
import net.minecraft.util.Mth;

final class WitchAura {
    static final double TURN = 2.0D * Math.PI;
    private static final int CROWN_STARS = 12;
    private static final double CROWN_RADIUS = 0.58D;
    private static final double CROWN_TILT = 0.14D;
    private static final int ORBIT_MOTES = 7;
    private static final int WHITE = 0xffffff;

    private WitchAura() {
    }

    static void crown(RitualSprites sprites, RitualVisualStyle style, double x, double y, double z,
                      double ticks, double scale, double opacity) {
        stars(sprites, x, y, z, ticks * 0.013D, scale, opacity, style.crown());
        if (style.motion() == Motion.PARADE) {
            stars(sprites, x, y + 0.22D * scale, z, -ticks * 0.018D + 0.12D,
                    scale * 1.34D, opacity * 0.45D, style.secondary());
        }
    }

    private static void stars(RitualSprites sprites, double x, double y, double z, double rotation,
                              double scale, double opacity, int color) {
        for (int index = 0; index < CROWN_STARS; index++) {
            double angle = rotation + index * TURN / CROWN_STARS;
            double dx = Math.cos(angle) * CROWN_RADIUS * scale;
            double dz = Math.sin(angle) * CROWN_RADIUS * scale;
            double size = (index % 2 == 0 ? 0.18D : 0.145D) * scale;
            sprites.draw(Sprite.STAR, x + dx, y + dz * CROWN_TILT, z + dz, size, 0.0D, color, opacity);
        }
    }

    static void orbit(RitualSprites sprites, RitualVisualStyle style, double ticks, double height) {
        Motion motion = style.motion();
        double time = ticks * 0.035D;
        for (int index = 0; index < ORBIT_MOTES; index++) {
            double phase = fraction(ticks / 72.0D + (double) index / ORBIT_MOTES);
            double angle = time + index * TURN / ORBIT_MOTES;
            double radius = 0.86D;
            double y = height * 0.62D;
            double roll = 0.0D;
            double size = 0.13D;
            double alpha = fade(phase);
            Sprite sprite = style.motif();
            int color = style.primary();

            switch (motion) {
                case SHED -> {
                    double release = smooth((phase - 0.59D) / 0.41D);
                    radius += 0.60D * release;
                    y += Math.sin(angle * 2.0D) * 0.18D - 0.26D * release;
                    roll = Math.sin(angle * 2.0D) * 0.25D;
                    sprite = phase > 0.87D ? style.accent() : butterfly(ticks + index);
                    color = sprite == style.accent() ? style.secondary() : WHITE;
                    size = phase > 0.87D ? 0.09D : 0.20D;
                }
                case GATHER -> {
                    radius = Mth.lerp(smooth(phase), 1.31D, 0.62D);
                    y += 0.32D * (1.0D - phase);
                    if (phase > 0.71D) {
                        sprite = style.accent();
                        size = 0.17D;
                        color = style.secondary();
                    }
                }
                case BIND -> {
                    if (index % 2 == 0) {
                        radius -= 0.30D * smooth(phase);
                        y = height * 0.46D + 0.11D * Math.sin(angle * 2.0D);
                        sprite = style.accent();
                        color = style.secondary();
                        size = 0.18D;
                        roll = Math.sin(angle) * 0.45D;
                    } else {
                        y -= Math.sin(phase * Math.PI) * 0.73D;
                    }
                }
                case WEAVE -> {
                    radius += 0.63D * Math.sin(phase * Math.PI);
                    y += 0.34D * Math.sin(angle * 3.0D);
                    roll = angle;
                }
                case REWIND -> {
                    double step = Math.floor(ticks / 5.0D) * 5.0D;
                    double swing = Math.sin(step * 0.044D) * 1.20D;
                    angle = swing + index * TURN / ORBIT_MOTES;
                    y += Math.sin(angle * 2.0D) * 0.22D;
                    roll = swing * 1.8D;
                }
                case INVITE -> {
                    y += 0.29D * Math.sin(angle * 2.0D);
                    radius += 0.19D * Math.sin(angle * 2.0D);
                    roll = 0.42D * Math.cos(angle);
                }
                case RIPPLE -> {
                    y += 0.37D * Math.sin(angle * 2.0D - time * 2.0D);
                    roll = 0.27D * Math.cos(angle * 2.0D - time * 2.0D);
                }
                case FLING -> {
                    radius += phase * phase * 0.85D;
                    y += phase * 0.27D;
                    roll = -0.85D * Math.cos(angle);
                    size *= 1.0D - phase * 0.50D;
                }
                case PARADE -> {
                    angle = (index % 2 == 0 ? time : -time) + index * TURN / ORBIT_MOTES;
                    radius = index % 2 == 0 ? 1.40D : 1.07D;
                    y += 0.24D * Math.sin(angle * 2.0D);
                    roll = angle;
                    size = 0.19D;
                }
                case THREAD -> {
                    boolean link = index % 2 == 0;
                    angle = (link ? -1.0D : 1.4D) * time + index * TURN / ORBIT_MOTES;
                    radius = link ? 0.64D : 0.84D + 0.30D * smooth(phase);
                    y = height * (link ? 0.43D + 0.14D * Math.sin(angle) : 0.26D + 0.59D * phase);
                    roll = link ? 0.7D * Math.sin(angle) : -0.36D * Math.cos(angle);
                    size = link ? 0.18D : 0.21D - 0.10D * phase;
                    alpha = link ? 0.60D + 0.29D * Math.cos(angle) : fade(phase);
                    sprite = link ? style.accent() : style.motif();
                    color = link ? style.secondary() : style.primary();
                }
            }
            if (index % 3 == 2 && motion != Motion.GATHER && motion != Motion.BIND && motion != Motion.SHED
                    && motion != Motion.THREAD) {
                sprite = style.accent();
                color = style.secondary();
            }
            sprites.draw(sprite, radius * Math.cos(angle), y, radius * Math.sin(angle), size, roll, color, alpha);
        }
    }

    static Sprite butterfly(double ticks) {
        return Math.sin(ticks * 1.1D) > 0.0D ? Sprite.BUTTERFLY : Sprite.BUTTERFLY_FOLDED;
    }

    static double fade(double phase) {
        return Mth.clamp(Math.min(phase * 9.0D, (1.0D - phase) * 6.0D), 0.0D, 1.0D);
    }

    static double smooth(double value) {
        double t = Mth.clamp(value, 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    private static double fraction(double value) {
        return value - Math.floor(value);
    }
}

package io.madoka.magic.client;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

final class MagicResourceBar {
    static final int HEIGHT = 11;

    enum Palette {
        MANA(0xFF6423AF, 0xFF2B124F, 0xFFB554EF, 0xFFC884FF),
        TAINT(0xFF54234D, 0xFF211126, 0xFF994768, 0xFFF098B2);

        final int top;
        final int bottom;
        final int center;
        final int star;

        Palette(int top, int bottom, int center, int star) {
            this.top = top;
            this.bottom = bottom;
            this.center = center;
            this.star = star;
        }
    }

    private static final int STARS = 12;
    private static final double STAR_SPEED = 3.0D;
    private static final int TEXT = 0xFFF8EFFF;
    private static final int OUTLINE = 0xFF21122D;

    private MagicResourceBar() {}

    static void render(GuiGraphics graphics, Font font, int x, int y, int width, double progress,
                       Palette palette, String text) {
        int right = x + width;
        int bottom = y + HEIGHT;
        graphics.fill(x, y, right, bottom, 0xFF160F21);
        graphics.fill(x, y, right, y + 1, 0xFFAA766F);
        graphics.fill(x, bottom - 1, right, bottom, 0xFF78506C);
        graphics.fill(x, y + 1, x + 1, bottom - 1, 0xFF926473);
        graphics.fill(right - 1, y + 1, right, bottom - 1, 0xFF926473);

        int trackWidth = width - 2;
        int filled = (int) Math.round(trackWidth * Mth.clamp(progress, 0, 1));
        if (filled > 0) {
            int innerX = x + 1;
            graphics.fillGradient(innerX, y + 1, innerX + filled, y + 5, palette.top, palette.center);
            graphics.fillGradient(innerX, y + 5, innerX + filled, bottom - 1, palette.center, palette.bottom);
            drawStars(graphics, innerX, y + 1, trackWidth, filled, palette.star);
        }

        ItemTooltipBorder.drawBarFrame(graphics, x, y, width, HEIGHT);
        int textX = x + (width - font.width(text)) / 2;
        int textY = y + (HEIGHT - font.lineHeight) / 2 + 1;
        graphics.drawString(font, text, textX - 1, textY, OUTLINE, false);
        graphics.drawString(font, text, textX + 1, textY, OUTLINE, false);
        graphics.drawString(font, text, textX, textY - 1, OUTLINE, false);
        graphics.drawString(font, text, textX, textY + 1, OUTLINE, false);
        graphics.drawString(font, text, textX, textY, TEXT, false);
    }

    private static void drawStars(GuiGraphics graphics, int x, int y, int width, int filled, int color) {
        // Work at twice the GUI resolution so the stars can drift in half-pixel steps.
        double seconds = Util.getMillis() / 1000.0D;
        int span = width * 2;
        int clip = filled * 2;
        double offset = (seconds * STAR_SPEED * 2) % span;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(0.5F, 0.5F, 1.0F);
        for (int i = 0; i < STARS; i++) {
            int column = (int) ((span * ((i * 0.61803398875D) % 1) + offset) % span);
            if (column >= clip) {
                continue;
            }
            int row = 2 + i * 7 % 14;
            int pulse = (int) (23 * (1 + Math.sin(seconds * 1.65D + i * 2.1D)));
            boolean bright = i % 2 == 0;
            if (bright && column > 0 && column + 1 < clip) {
                int glow = ((132 + pulse) << 24) | (color & 0xFFFFFF);
                graphics.fill(column - 1, row, column + 2, row + 1, glow);
                graphics.fill(column, row - 1, column + 1, row + 2, glow);
            }
            int star = ((bright ? 208 : 114) + pulse) << 24 | (bright ? 0xFFF5FF : color & 0xFFFFFF);
            graphics.fill(column, row, column + 1, row + 1, star);
        }
        pose.popPose();
    }
}

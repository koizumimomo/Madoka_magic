package io.madoka.magic.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.joml.Matrix4f;

/**
 * 描边 tooltip 行。
 *
 * <p>用 {@link TooltipComponent} 走原版 tooltip 渲染管线：先沿 8 个方向画 1px 描边，
 * 再叠一圈半透明外发光，最后画填充色。不依赖 Mixin，也不改物品本身的数据。</p>
 *
 * <p>只保留一种配色：黑字 + 白色发光描边，文字统一斜体。</p>
 */
public record OutlinedTooltip(Component text, int fill, int outline) implements TooltipComponent {
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    public static OutlinedTooltip blackOnWhite(Component text) {
        return new OutlinedTooltip(text.copy().withStyle(ChatFormatting.ITALIC), BLACK, WHITE);
    }

    /** 客户端渲染器：{@link MadokaClient} 在 RegisterClientTooltipComponentFactoriesEvent 里登记。 */
    static final class Renderer implements ClientTooltipComponent {
        private static final int PAD = 3;
        private final OutlinedTooltip tooltip;

        Renderer(OutlinedTooltip tooltip) {
            this.tooltip = tooltip;
        }

        @Override
        public int getWidth(Font font) {
            return font.width(tooltip.text()) + PAD * 2;
        }

        @Override
        public int getHeight() {
            return 10;
        }

        @Override
        public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffer) {
            int tx = x + PAD;
            int glow = (tooltip.outline() & 0x00FFFFFF) | 0x55000000;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    int distance = Math.max(Math.abs(dx), Math.abs(dy));
                    if (distance == 0) {
                        continue;
                    }
                    draw(font, matrix, buffer, tx + dx, y + dy,
                            distance == 1 ? tooltip.outline() : glow);
                }
            }
            draw(font, matrix, buffer, tx, y, tooltip.fill());
        }

        private void draw(Font font, Matrix4f matrix, MultiBufferSource.BufferSource buffer,
                          int x, int y, int color) {
            font.drawInBatch(tooltip.text(), x, y, color, false, matrix, buffer,
                    Font.DisplayMode.NORMAL, 0, 0xF000F0);
        }
    }
}

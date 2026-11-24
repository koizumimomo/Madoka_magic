package io.madoka.magic.client.hud;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 一个可拖拽的 HUD 元素。
 *
 * <p>render 的坐标是元素左上角；width/height 只用于编辑框和命中测试，
 * 渲染内容可以比这个范围大（比如冷却列表向上堆叠）。</p>
 */
public interface HudElement {

    String id();

    HudAnchor defaultAnchor();

    int defaultOffsetX();

    int defaultOffsetY();

    int width();

    int height();

    /** 当前状态下是否应该显示。编辑界面里不受这个限制。 */
    boolean visible();

    void render(GuiGraphics graphics, int x, int y);

    default void renderPreview(GuiGraphics graphics, int x, int y) {
        render(graphics, x, y);
    }
}

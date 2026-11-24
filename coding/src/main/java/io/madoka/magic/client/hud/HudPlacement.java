package io.madoka.magic.client.hud;

/** 单个 HUD 元素的位置配置。 */
public record HudPlacement(HudAnchor anchor, int offsetX, int offsetY, float scale, boolean visible) {

    /** 按屏幕坐标增量移动，锚点方向决定偏移怎么加。 */
    public HudPlacement moved(int dx, int dy) {
        boolean right = anchor == HudAnchor.TOP_RIGHT || anchor == HudAnchor.BOTTOM_RIGHT;
        boolean bottom = anchor == HudAnchor.BOTTOM_LEFT || anchor == HudAnchor.BOTTOM_CENTER
                || anchor == HudAnchor.BOTTOM_RIGHT;
        return new HudPlacement(anchor,
                right ? offsetX - dx : offsetX + dx,
                bottom ? offsetY - dy : offsetY + dy,
                scale, visible);
    }

    public HudPlacement withVisible(boolean value) {
        return new HudPlacement(anchor, offsetX, offsetY, scale, value);
    }

    public HudPlacement withScale(float value) {
        return new HudPlacement(anchor, offsetX, offsetY, value, visible);
    }
}

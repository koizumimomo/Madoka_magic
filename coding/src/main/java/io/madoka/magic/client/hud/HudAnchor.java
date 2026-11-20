package io.madoka.magic.client.hud;

/** HUD 元素的锚点。偏移量按锚点方向解释，四角锚点都从屏幕边缘往里算。 */
public enum HudAnchor {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT, CENTER, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;

    public int x(int screenWidth, int width, int offsetX) {
        return switch (this) {
            case TOP_LEFT, CENTER, BOTTOM_LEFT -> offsetX;
            case TOP_CENTER, BOTTOM_CENTER -> screenWidth / 2 + offsetX - width / 2;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - offsetX - width;
        };
    }

    public int y(int screenHeight, int height, int offsetY) {
        return switch (this) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> offsetY;
            case CENTER -> screenHeight / 2 + offsetY - height / 2;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> screenHeight - offsetY - height;
        };
    }
}

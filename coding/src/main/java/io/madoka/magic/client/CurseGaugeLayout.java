package io.madoka.magic.client;

/** Geometry shared by the gauge renderer and its layout checks. */
public final class CurseGaugeLayout {
    public static final int WIDTH = 96, HEIGHT = 88;
    public static final float CENTER_X = 48, CENTER_Y = 45, RADIUS = 35;
    private static final double START = Math.toRadians(-160), END = Math.toRadians(-20);

    private CurseGaugeLayout() {}

    public static double angle(int index, int count) {
        if (count < 1 || count > 8 || index < 0 || index >= count) throw new IllegalArgumentException("Invalid gauge node");
        return count == 1 ? (START + END) / 2 : START + (END - START) * index / (count - 1);
    }

    public static float x(int index, int count) { return CENTER_X + (float) Math.cos(angle(index, count)) * RADIUS; }
    public static float y(int index, int count) { return CENTER_Y + (float) Math.sin(angle(index, count)) * RADIUS; }
}

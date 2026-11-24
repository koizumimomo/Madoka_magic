package io.madoka.magic.client;

/** Each axis wraps only after its own complete revolution, without a shared clock reset. */
public final class DisplayRingMotion {
    private DisplayRingMotion() {}

    public static float degrees(long worldTick, float partialTick, int offset, int signedPeriod) {
        if (signedPeriod == 0) throw new IllegalArgumentException("Rotation period cannot be zero");
        long period = Math.abs((long) signedPeriod);
        double phase = Math.floorMod(worldTick + offset, period) + Math.max(0, Math.min(1, partialTick));
        return (float) (phase * 360.0 / signedPeriod);
    }
}

package io.madoka.magic.item;

import io.madoka.magic.config.MadokaConfig;
import net.minecraft.world.level.Level;

/** Client tooltip values supplied by the server, with local defaults outside a world. */
public final class RitualTooltipValues {
    private static volatile double seedPurification = Double.NaN;

    private RitualTooltipValues() {}

    public static void accept(double value) {
        seedPurification = Double.isFinite(value) && value >= 0 && value <= 100 ? value : Double.NaN;
    }

    public static double seedPurification(Level level) {
        return level != null && level.isClientSide && Double.isFinite(seedPurification)
                ? seedPurification : MadokaConfig.SEED_TAINT_PER_DURABILITY.get();
    }
}

package io.madoka.magic.entity;

import io.madoka.magic.compat.AlexCavesCompat;
import io.madoka.magic.compat.CataclysmCompat;

/** Shared progression identity for boss attributes and portal presentation. */
public enum WitchStage {
    SIDE("side"), CONTRACT("stage_1"), QUINTET("stage_2"), CAVES("stage_3"),
    CATACLYSM("stage_4"), FINALE("stage_5");

    private final String key;

    WitchStage(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static WitchStage forTheme(String theme) {
        if (theme == null) return SIDE;
        if (CataclysmCompat.isBarrierTheme(theme)) return CATACLYSM;
        if (AlexCavesCompat.isBarrierTheme(theme)) return CAVES;
        return switch (theme) {
            case "sacrifice", "take", "atonement" -> CONTRACT;
            case WitchTypes.SALVATION, WitchTypes.NUTCRACKER, "candeloro",
                    WitchTypes.MERMAID, WitchTypes.OPHELIA -> QUINTET;
            case WitchTypes.NIGHT -> FINALE;
            default -> SIDE;
        };
    }
}

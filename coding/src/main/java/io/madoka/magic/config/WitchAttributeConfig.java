package io.madoka.magic.config;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.entity.WitchTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class WitchAttributeConfig {
    public static final String FILE_NAME = "madoka_magic-witches.toml";
    public static final ForgeConfigSpec SPEC;

    private static final Map<String, Stats> PROFILES = new LinkedHashMap<>();
    private static final Stats DEFAULTS;

    static {
        var builder = new ForgeConfigSpec.Builder();
        DEFAULTS = profile(builder, "defaults", "普通魔女的默认属性。", 0, 0,
                "填 0 沿用 madoka_magic-common.toml 中的 witch.witch_health。", "默认基础护甲。");
        for (String theme : MadokaDimensions.BARRIERS) {
            register(builder, theme);
        }
        register(builder, WitchTypes.NIGHT);
        SPEC = builder.build();
    }

    private WitchAttributeConfig() {}

    private static void register(ForgeConfigSpec.Builder builder, String theme) {
        boolean wither = WitchTypes.WITHER.equals(theme);
        PROFILES.put(theme, profile(builder, theme, displayName(theme), wither ? 300 : 0, wither ? 4 : -1,
                "填 0 使用默认生命值。", "填 -1 使用默认护甲，填 0 表示没有护甲。"));
    }

    private static Stats profile(ForgeConfigSpec.Builder builder, String key, String name, double health, double armor,
                                 String healthComment, String armorComment) {
        builder.comment(name).push(key);
        var maxHealth = builder.comment(healthComment).defineInRange("max_health", health, 0.0, 1024.0);
        var armorPoints = builder.comment(armorComment).defineInRange("armor", armor, "defaults".equals(key) ? 0.0 : -1.0, 30.0);
        builder.pop();
        return new Stats(maxHealth, armorPoints);
    }

    private static String displayName(String theme) {
        return switch (theme) {
            case "sacrifice" -> "蝴蝶的魔女";
            case "take" -> "索取的魔女";
            case "atonement" -> "不幸的罪责";
            case WitchTypes.WITHER -> "凋零的魔女";
            case WitchTypes.SALVATION -> "救济的魔女";
            case WitchTypes.NUTCRACKER -> "胡桃夹子的魔女";
            case "candeloro" -> "梳妆的魔女";
            case WitchTypes.MERMAID -> "人鱼的魔女";
            case WitchTypes.OPHELIA -> "武旦的魔女";
            case WitchTypes.GLUTTONY -> "变形的魔女";
            case WitchTypes.NIGHT -> "魔女之夜";
            default -> theme;
        };
    }

    @SubscribeEvent
    public static void onWitchJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity witch) || !witch.isAlive()) {
            return;
        }
        String theme = WitchTypes.bossTheme(witch);
        if (theme.isEmpty() || WitchTypes.WITHER.equals(theme)
                && !event.getLevel().dimension().equals(MadokaDimensions.witchLevel(theme))) {
            return;
        }
        var profile = PROFILES.getOrDefault(theme, DEFAULTS);
        double health = profile.health().get();
        if (health == 0.0) {
            double fallback = DEFAULTS.health().get();
            health = fallback == 0.0 ? MadokaConfig.WITCH_HEALTH.get() : fallback;
        }
        double armor = profile.armor().get();
        float healthFraction = witch.getHealth() / witch.getMaxHealth();
        var maxHealth = witch.getAttribute(Attributes.MAX_HEALTH);
        double targetHealth = Math.max(1.0, Math.min(1024.0, health));
        if (maxHealth.getBaseValue() != targetHealth) {
            maxHealth.setBaseValue(targetHealth);
            witch.setHealth(healthFraction * witch.getMaxHealth());
        }
        witch.getAttribute(Attributes.ARMOR).setBaseValue(armor < 0.0 ? DEFAULTS.armor().get() : armor);
    }

    private record Stats(ForgeConfigSpec.DoubleValue health, ForgeConfigSpec.DoubleValue armor) {}
}

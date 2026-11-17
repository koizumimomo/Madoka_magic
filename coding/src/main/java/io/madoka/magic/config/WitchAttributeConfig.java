package io.madoka.magic.config;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.data.MadokaSavedData;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.entity.WitchTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class WitchAttributeConfig {
    public static final String FILE_NAME = "madoka_magic-witches.toml";
    public static final ForgeConfigSpec SPEC;

    private static final Map<String, Stats> PROFILES = new LinkedHashMap<>();
    private static final Stats DEFAULTS;

    static {
        var builder = new ForgeConfigSpec.Builder();
        DEFAULTS = profile(builder, "defaults", "普通魔女的默认属性。", 0, 0,
                "填 0 按主人灵魂宝石纯度动态生成（0% → 200 血，每 1% +6 血）。", "默认基础护甲。");
        for (String theme : MadokaDimensions.BARRIERS) {
            register(builder, theme);
        }
        register(builder, WitchTypes.NIGHT);
        // Alex 的洞穴三位魔女（外部首领本体也吃这份属性）
        register(builder, "brunhilde", 500.0, 12.0);
        register(builder, "sieglinde", 400.0, 8.0);
        register(builder, "annegret", 300.0, 6.0);
        SPEC = builder.build();
    }

    private WitchAttributeConfig() {}

    private static void register(ForgeConfigSpec.Builder builder, String theme) {
        boolean wither = WitchTypes.WITHER.equals(theme);
        register(builder, theme, wither ? 300.0 : 0.0, wither ? 4.0 : -1.0);
    }

    private static void register(ForgeConfigSpec.Builder builder, String theme, double health, double armor) {
        PROFILES.put(theme, profile(builder, theme, displayName(theme), health, armor,
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
            case WitchTypes.WITHER -> "悼亡的魔女";
            case WitchTypes.SALVATION -> "救济的魔女";
            case WitchTypes.NUTCRACKER -> "胡桃夹子的魔女";
            case "candeloro" -> "梳妆的魔女";
            case WitchTypes.MERMAID -> "人鱼的魔女";
            case WitchTypes.OPHELIA -> "武旦的魔女";
            case WitchTypes.GLUTTONY -> "变形的魔女";
            case WitchTypes.NIGHT -> "魔女之夜";
            case "brunhilde" -> "蛮勇的魔女";
            case "sieglinde" -> "悔恨的魔女";
            case "annegret" -> "甘美的魔女";
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
        double armor = profile.armor().get();
        if (health == 0.0 && armor < 0.0) {
            // 未配置：按结界主人（进入结界的玩家的）灵魂宝石纯度动态生成。
            // 0% 纯度 → 200 血 0 甲；每 1% 纯度 → +6 血 +0.08 甲（100% → 800 血 8 甲）。
            double purity = ownerPurity(event.getLevel(), witch);
            health = 200.0D + purity * 6.0D;
            armor = purity * 0.08D;
        } else {
            if (health == 0.0) {
                double fallback = DEFAULTS.health().get();
                health = fallback == 0.0 ? MadokaConfig.WITCH_HEALTH.get() : fallback;
            }
            if (armor < 0.0) {
                armor = DEFAULTS.armor().get();
            }
        }
        float healthFraction = witch.getHealth() / witch.getMaxHealth();
        var maxHealth = witch.getAttribute(Attributes.MAX_HEALTH);
        double targetHealth = Math.max(1.0, Math.min(1024.0, health));
        if (maxHealth.getBaseValue() != targetHealth) {
            maxHealth.setBaseValue(targetHealth);
            witch.setHealth(healthFraction * witch.getMaxHealth());
        }
        witch.getAttribute(Attributes.ARMOR).setBaseValue(armor);
    }

    /** 结界主人的灵魂宝石纯度（0–100）；主人不在线/无法解析时按 0 处理。 */
    private static double ownerPurity(Level level, LivingEntity witch) {
        UUID owner = null;
        var state = witch.getPersistentData();
        if (state.hasUUID(io.madoka.magic.faction.WitchFamiliars.ENCOUNTER_TAG)) {
            var run = MadokaSavedData.get((ServerLevel) level)
                    .getEncounter(state.getUUID(io.madoka.magic.faction.WitchFamiliars.ENCOUNTER_TAG));
            if (run != null) {
                owner = run.owner();
            }
        }
        if (owner == null && witch instanceof io.madoka.magic.entity.MadokaWitchEntity named) {
            owner = named.getOwnerId();
        }
        if (owner == null) {
            return 0.0D;
        }
        var player = level.getServer().getPlayerList().getPlayer(owner);
        return player == null ? 0.0D
                : net.minecraft.util.Mth.clamp(io.madoka.magic.util.MadokaMath.purity(player), 0.0D,
                        io.madoka.magic.util.MadokaMath.PURITY_MAX);
    }

    private record Stats(ForgeConfigSpec.DoubleValue health, ForgeConfigSpec.DoubleValue armor) {}
}

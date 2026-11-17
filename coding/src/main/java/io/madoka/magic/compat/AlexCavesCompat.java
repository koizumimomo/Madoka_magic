package io.madoka.magic.compat;

import io.madoka.magic.MadokaMagic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Alex 的洞穴（Alex's Caves）首领的结界维度兼容层。
 *
 * <p>与灾变兼容层同构：不 import 洞穴模组的任何类，只按注册名查实体，
 * 未安装时所有入口返回空。三个首领各自对应一位魔女：</p>
 * <ul>
 *   <li>{@code luxtructosaurus} 暝煌龙 → <b>布伦希尔德</b>（蛮勇的魔女）</li>
 *   <li>{@code forsaken} 遗弃者 → <b>希格琳德</b>（悔恨的魔女）</li>
 *   <li>{@code licowitch} 甘草女巫 → <b>安娜格蕾特</b>（甘美的魔女）</li>
 * </ul>
 *
 * <p>入口与凋零一致：首领在非结界维度自然生成时被拦截，
 * 原地生成对应的结界入口传送门（见 {@code AlexCavesSpawnEvents}）。</p>
 */
public final class AlexCavesCompat {

    public static final String MODID = "alexscaves";

    /** 结界主题 → 首领注册名；每只首领一个独立维度。 */
    private static final Map<String, String> BARRIER_BOSSES = Map.of(
            "brunhilde", "luxtructosaurus",
            "sieglinde", "forsaken",
            "annegret", "licowitch");

    /** 首领注册名 → 结界主题（含可能的阶段变体时在此补充）。 */
    private static final Map<String, String> ENTITY_THEMES = Map.of(
            "luxtructosaurus", "brunhilde",
            "forsaken", "sieglinde",
            "licowitch", "annegret");

    /** 主题 → 使魔（注册名 + 数量 + 出现概率）。取自同生态的洞穴生物。 */
    private static final Map<String, List<Familiar>> FAMILIARS = Map.of(
            "brunhilde", List.of(new Familiar("relicheirus", 1), new Familiar("grottoceratops", 1),
                    new Familiar("vallumraptor", 2), new Familiar("subterranodon", 2),
                    new Familiar("gloomoth", 2), new Familiar("gammaroach", 2)),
            "sieglinde", List.of(new Familiar("underzealot", 2), new Familiar("watcher", 1),
                    new Familiar("vesper", 2), new Familiar("raycat", 1),
                    new Familiar("gammaroach", 1)),
            "annegret", List.of(new Familiar("gummy_bear", 1), new Familiar("candicorn", 1),
                    new Familiar("gingerbread_man", 2), new Familiar("caramel_cube", 2),
                    new Familiar("gumbeeper", 1), new Familiar("sweetish_fish", 1)));

    private AlexCavesCompat() {
    }

    public static boolean isPresent() {
        return net.minecraftforge.fml.ModList.get().isLoaded(MODID);
    }

    public static boolean isBarrierTheme(String theme) {
        return theme != null && BARRIER_BOSSES.containsKey(theme);
    }

    /** 已安装洞穴模组时可用作结界入口的主题；未安装返回空表。 */
    public static List<String> barrierThemes() {
        return isPresent() ? List.copyOf(BARRIER_BOSSES.keySet()) : List.of();
    }

    /** 该实体对应的结界主题；不是洞穴首领返回空串。 */
    public static String barrierTheme(Entity entity) {
        if (!isPresent() || entity == null) {
            return "";
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && MODID.equals(id.getNamespace()) ? themeForEntityPath(id.getPath()) : "";
    }

    /** 实体注册名（不含命名空间）→ 主题；给血条识别用。 */
    public static String themeForEntityPath(String path) {
        return path == null ? "" : ENTITY_THEMES.getOrDefault(path, "");
    }

    /** 在指定维度实例化对应首领；模组缺失或主题未知时返回 null。 */
    public static Mob createBoss(ServerLevel level, String theme) {
        String path = BARRIER_BOSSES.get(theme);
        if (!isPresent() || path == null) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(
                ResourceLocation.fromNamespaceAndPath(MODID, path)).orElse(null);
        return type != null && type.create(level) instanceof Mob mob ? mob : null;
    }

    /**
     * 该生物是否属于此洞穴魔女的结界维度。
     * <p>只放行该主题自己的首领与使魔，其它实体一律拦下。</p>
     */
    public static boolean belongsToBarrier(Entity entity, String theme) {
        if (!isPresent() || !isBarrierTheme(theme) || entity == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && MODID.equals(id.getNamespace())
                && theme.equals(themeForEntityPath(id.getPath()));
    }

    /**
     * 生成该首领的使魔：围绕竞技场散布，落在地面。
     * 每种按数量生成，概率不足时跳过。
     */
    public static void spawnFamiliars(ServerLevel level, BlockPos arena, String theme, UUID encounter) {
        List<Familiar> familiars = FAMILIARS.get(theme);
        if (familiars == null) {
            return;
        }
        for (Familiar familiar : familiars) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(
                    ResourceLocation.fromNamespaceAndPath(MODID, familiar.entityPath())).orElse(null);
            if (type == null) {
                continue;
            }
            for (int i = 0; i < familiar.count(); i++) {
                if (level.random.nextFloat() > familiar.chance()) {
                    continue;
                }
                if (!(type.create(level) instanceof Mob mob)) {
                    continue;
                }
                double angle = level.random.nextDouble() * Math.PI * 2.0D;
                double distance = 6.0D + level.random.nextDouble() * 10.0D;
                int x = arena.getX() + (int) Math.round(Math.cos(angle) * distance);
                int z = arena.getZ() + (int) Math.round(Math.sin(angle) * distance);
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                mob.moveTo(x + 0.5D, y, z + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
                mob.setPersistenceRequired();
                mob.getPersistentData().putUUID(io.madoka.magic.faction.WitchFamiliars.ENCOUNTER_TAG, encounter);
                level.addFreshEntity(mob);
            }
        }
    }

    /** 使魔配置：实体注册名 + 数量 + 出现概率。 */
    record Familiar(String entityPath, int count, float chance) {
        Familiar(String entityPath, int count) {
            this(entityPath, count, 1.0F);
        }
    }
}

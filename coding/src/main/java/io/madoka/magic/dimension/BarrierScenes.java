package io.madoka.magic.dimension;

import io.madoka.magic.entity.BarrierPropEntity;
import io.madoka.magic.entity.BarrierTreeEntity;
import io.madoka.magic.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * 结界场景生成（首个样张：蝴蝶的魔女 sacrifice，虚空悬浮风格）。
 *
 * <p>维度的底床只剩基岩，四周是虚空（蝴蝶的魔女 sacrifice 是纯粹的虚空悬浮风格：
 * 除了平台、主路、竞技场和枯树浮岛之外不生成任何地形）。固定骨架整体悬浮在高空：
 * 出生平台（13×13，表面 y=36，正下方一根细柱）→
 * 凹凸不平的悬浮主路（宽 5，长约 96 格，路面起伏 ±3 且逐列抖动）→
 * 竞技场（半径 20 圆形拼贴地板，矮墙留北向主路缺口，下方一根粗支撑柱）。
 * 主路两侧只保留散落的枯树浮岛。掉出甲板按「仁慈的虚空」处理，
 * 由 {@link io.madoka.magic.event.BarrierGuardEvents} 传回出生平台。</p>
 *
 * <p>布局相对各竞技场锚点独立放置；锚点间隔 512 格而骨架半径约 210 格，
 * 多场遭遇互不重叠。形状由世界 seed + 维度 id 派生，同一存档每次一致。
 * 构建幂等且总量小（约五千次放置），传送门进入时不再有明显卡顿。</p>
 */
public final class BarrierScenes {
    /** 已实现场景的主题。 */
    private static final String SAMPLE_THEME = "sacrifice";
    private static final String TAKE_THEME = "take";

    static final int SPAWN_OFFSET = 120;
    static final int ARENA_RADIUS = 20;
    static final int ROUTE_HALF_WIDTH = 2;
    static final int DECK_Y = 20;
    /** 出生平台比主路高出的格数：平台悬浮在大石柱顶端。 */
    private static final int SPAWN_RISE = 16;
    static final int SPAWN_Y = DECK_Y + SPAWN_RISE;
    /** 玩家掉到这个高度以下视为坠入虚空。 */
    public static final int VOID_FLOOR = 4;

    static final int MIN_X = -26;
    static final int MAX_X = 26;
    static final int MIN_Z = -SPAWN_OFFSET - 16;
    static final int MAX_Z = 28;

    private static final BlockState FILL = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState WALL_CAP = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();
    private static final BlockState STEP = Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState()
            .setValue(StairBlock.FACING, Direction.NORTH);

    private BarrierScenes() {}

    /** 该主题是否已有场景生成。 */
    public static boolean hasScene(String theme) {
        return SAMPLE_THEME.equals(theme) || TAKE_THEME.equals(theme);
    }

    /** 玩家进入点：有场景的主题落在出生平台中心，其余保持旧的竞技场北侧 16 格。 */
    public static BlockPos spawnPoint(String theme, BlockPos arena) {
        return hasScene(theme) ? arena.offset(0, 0, -SPAWN_OFFSET) : arena.offset(0, 0, -16);
    }

    /** 出生平台表面的 y：蝶之结界悬浮在高空，索取结界贴着蜜脾平原（高出 2 格）。 */
    static int spawnSurfaceY(String theme) {
        return TAKE_THEME.equals(theme) ? DECK_Y + 2 : SPAWN_Y;
    }

    /** 坠入虚空后的传回点：出生平台表面上方。 */
    public static BlockPos rescuePoint(String theme, BlockPos arena) {
        return arena.offset(0, spawnSurfaceY(theme) + 1, -SPAWN_OFFSET);
    }

    /** 幂等构建某场遭遇的结界场景；尚未实现场景的主题直接返回。 */
    public static void apply(ServerLevel level, BlockPos anchor, String theme) {
        if (!hasScene(theme)) {
            return;
        }
        long seed = level.getSeed() ^ level.dimension().location().hashCode() * 31L ^ 0x5A1F1CEL;
        if (TAKE_THEME.equals(theme)) {
            TakeBarrierScene.apply(level, anchor, seed);
            BlockPos spawn = anchor.offset(0, 0, -SPAWN_OFFSET);
            int surface = spawnSurfaceY(theme);
            ensureProp(level, spawn.offset(4, surface, 4), "signpost", 180.0F, 0.0F, 4);
            // 倾斜宴会桌：平原中段（平台与竞技场中间距离）歪着放
            ensureProp(level, anchor.offset(0, DECK_Y + 1, -SPAWN_OFFSET / 2 - 6),
                    "banquet_table", 30.0F, 16.0F, 3);
            // 巨大的勺：平原左侧、主路与竞技场之间的远景地标，倾斜插在地里
            ensureProp(level, anchor.offset(-16, DECK_Y + 1, -36),
                    "giant_spoon", 30.0F, -8.0F, 4);
            return;
        }
        BlockPos center = anchor.offset(0, 0, -SPAWN_OFFSET);
        BlockState floor = ModBlocks.BARRIER_FLOORS.get(theme).block().get().defaultBlockState();
        // 场景是否已建过：出生平台中央已是拼贴地板。判定必须在清理之前取，
        // 否则旧版本残骸（上一版贴地平台/断柱）永远清不掉。
        boolean fresh = !level.getBlockState(center.atY(SPAWN_Y)).is(floor.getBlock());
        // 出生空域无条件清理（旧版贴地平台、断柱残骸），再重建出生结构
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                for (int y = DECK_Y; y <= DECK_Y + 72; y++) {
                    level.setBlock(center.offset(dx, y, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        // 出生结构每次进门都重建，保证柱子/平台/灯柱在清理后完整
        buildSpawnPlatform(level, center, floor);
        buildSpawnTrim(level, center);
        ensureSpawnProps(level, center);
        level.setBlock(center.atY(SPAWN_Y - 1), WALL_CAP, Block.UPDATE_CLIENTS);
        if (!fresh) {
            // 旧存档补种近景枯树：以浮台方块为标记，已经种过就不再生成，避免重复实体
            if (!hasTreePlatform(level, anchor, seed)) {
                buildFloatingTrees(level, anchor, seed);
            }
            return;
        }

        for (int cz = (anchor.getZ() + MIN_Z) >> 4; cz <= (anchor.getZ() + MAX_Z) >> 4; cz++) {
            for (int cx = (anchor.getX() + MIN_X) >> 4; cx <= (anchor.getX() + MAX_X) >> 4; cx++) {
                level.getChunk(cx, cz);
            }
        }

        buildRoute(level, anchor, seed, floor);
        buildArena(level, anchor, floor);
        buildFloatingTrees(level, anchor, seed);
    }

    /** 出生平台：13×13 拼贴平台悬浮在 SPAWN_Y（比主路高 16 格），正下方一根 1×1 石柱通向虚空底。 */
    private static void buildSpawnPlatform(ServerLevel level, BlockPos center, BlockState floor) {
        for (int y = 1; y < SPAWN_Y - 1; y++) {
            level.setBlock(center.atY(y), FILL, Block.UPDATE_CLIENTS);
        }
        for (int dz = -6; dz <= 6; dz++) {
            for (int dx = -6; dx <= 6; dx++) {
                BlockPos pos = center.offset(dx, 0, dz);
                level.setBlock(pos.atY(SPAWN_Y - 1), FILL, Block.UPDATE_CLIENTS);
                level.setBlock(pos.atY(SPAWN_Y), floor, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** 悬浮主路：宽 5、长约 96 格。低频噪声连续起伏（整段同高的台阶式落差），
     *  北端从出生平台高度经大台阶降到主路高度。 */
    private static void buildRoute(ServerLevel level, BlockPos anchor, long seed, BlockState floor) {
        for (int dz = -(SPAWN_OFFSET - 6); dz <= -18; dz++) {
            float roll = noise(seed ^ 0xB0A7L, 0, dz / 12.0F) * 2.6F;
            // 北端 18 格坡道：从出生平台（SPAWN_Y-1）降到主路（DECK_Y±2）
            float dock = (dz + (SPAWN_OFFSET - 6)) / 18.0F;
            if (dock > 1.0F) {
                dock = 1.0F;
            } else if (dock < 0.0F) {
                dock = 0.0F;
            }
            int surface = Math.round(SPAWN_Y - 1 + dock * ((DECK_Y + roll) - (SPAWN_Y - 1)));
            for (int dx = -ROUTE_HALF_WIDTH; dx <= ROUTE_HALF_WIDTH; dx++) {
                BlockPos pos = anchor.offset(dx, 0, dz);
                level.setBlock(pos.atY(surface - 1), FILL, Block.UPDATE_CLIENTS);
                level.setBlock(pos.atY(surface), floor, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** 竞技场：半径 20 圆形拼贴地板 + 矮墙（北向缺口）+ 下方粗支撑柱。 */
    private static void buildArena(ServerLevel level, BlockPos anchor, BlockState floor) {
        for (int dz = -ARENA_RADIUS; dz <= ARENA_RADIUS; dz++) {
            for (int dx = -ARENA_RADIUS; dx <= ARENA_RADIUS; dx++) {
                double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (dist > ARENA_RADIUS) {
                    continue;
                }
                BlockPos pos = anchor.offset(dx, 0, dz);
                level.setBlock(pos.atY(DECK_Y - 1), FILL, Block.UPDATE_CLIENTS);
                level.setBlock(pos.atY(DECK_Y), floor, Block.UPDATE_CLIENTS);
            }
        }
        int outer = ARENA_RADIUS + 2;
        double inner = (ARENA_RADIUS - 0.7D) * (ARENA_RADIUS - 0.7D);
        double outerSq = (ARENA_RADIUS + 0.7D) * (ARENA_RADIUS + 0.7D);
        for (int dz = -outer; dz <= outer; dz++) {
            for (int dx = -outer; dx <= outer; dx++) {
                double d2 = (double) dx * dx + (double) dz * dz;
                if (d2 < inner || d2 > outerSq) {
                    continue;
                }
                if (Math.abs(dx) <= ROUTE_HALF_WIDTH + 1 && dz <= -(ARENA_RADIUS - ROUTE_HALF_WIDTH - 2)) {
                    continue;
                }
                BlockPos base = anchor.offset(dx, 0, dz);
                for (int y = DECK_Y + 1; y <= DECK_Y + 2; y++) {
                    level.setBlock(base.atY(y), FILL, Block.UPDATE_CLIENTS);
                }
                level.setBlock(base.atY(DECK_Y + 3), WALL_CAP, Block.UPDATE_CLIENTS);
            }
        }
        int support = 4;
        for (int dz = -support; dz <= support; dz++) {
            for (int dx = -support; dx <= support; dx++) {
                if ((double) dx * dx + (double) dz * dz > (double) support * support) {
                    continue;
                }
                for (int y = 1; y < DECK_Y - 1; y++) {
                    level.setBlock(anchor.offset(dx, y, dz), FILL, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** 主路两侧的枯树浮岛：虚空中只保留枯树（不再生成光秃断柱）。 */
    private static void buildFloatingTrees(ServerLevel level, BlockPos anchor, long seed) {
        for (int z = -(SPAWN_OFFSET + 4); z <= -14; z += 16) {
            for (int side = -1; side <= 1; side += 2) {
                int dz = z - (int) (hash(seed, z, side) * 4.0F);
                int dx = side * (8 + (int) (hash(seed ^ 0x71L, dz, side) * 9.0F));
                int top = 8 + (int) (hash(seed ^ 0x33L, dz, side) * 20.0F);
                int radius = hash(seed ^ 0x51L, dz, side) > 0.45F ? 3 : 2;
                for (int px = -radius; px <= radius; px++) {
                    for (int pz = -radius; pz <= radius; pz++) {
                        if (px * px + pz * pz > radius * radius + 1) {
                            continue;
                        }
                        level.setBlock(anchor.offset(dx + px, top, dz + pz), FILL, Block.UPDATE_CLIENTS);
                    }
                }
                BarrierTreeEntity.spawn(level, anchor.offset(dx, top + 1, dz),
                        hash(seed ^ 0xE8L, dz, side) * 360.0F);
            }
        }
    }

    /** 第一处枯树浮岛是否已经生成（旧存档补种时的幂等判定）。 */
    private static boolean hasTreePlatform(ServerLevel level, BlockPos anchor, long seed) {
        int z = -(SPAWN_OFFSET + 4);
        int side = -1;
        int dz = z - (int) (hash(seed, z, side) * 4.0F);
        int dx = side * (8 + (int) (hash(seed ^ 0x71L, dz, side) * 9.0F));
        int top = 8 + (int) (hash(seed ^ 0x33L, dz, side) * 20.0F);
        return level.getBlockState(anchor.offset(dx, top, dz)).is(FILL.getBlock());
    }

    /** 出生平台修饰：四角灯柱定界，南缘台阶 + 双灯柱指示主路方向（全部以 SPAWN_Y 为基准）。 */
    private static void buildSpawnTrim(ServerLevel level, BlockPos center) {
        int[][] corners = {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}};
        for (int[] c : corners) {
            BlockPos base = center.offset(c[0], 0, c[1]);
            level.setBlock(base.atY(SPAWN_Y + 1), FILL, Block.UPDATE_CLIENTS);
            level.setBlock(base.atY(SPAWN_Y + 2), LANTERN, Block.UPDATE_CLIENTS);
        }
        // 南缘门槛台阶（与主路坡道起点衔接）
        for (int dx = -2; dx <= 2; dx++) {
            level.setBlock(center.offset(dx, 0, 7).atY(SPAWN_Y - 1), STEP, Block.UPDATE_CLIENTS);
        }
        for (int dx : new int[]{-4, 4}) {
            BlockPos base = center.offset(dx, 0, 7);
            for (int y = SPAWN_Y - 1; y <= SPAWN_Y + 2; y++) {
                level.setBlock(base.atY(y), FILL, Block.UPDATE_CLIENTS);
            }
            level.setBlock(base.atY(SPAWN_Y + 3), LANTERN, Block.UPDATE_CLIENTS);
        }
    }

    /** 出生平台上的共用近景道具：东南角路标 + 出口蝴蝶拱门，已存在则不重复生成。 */
    private static void ensureSpawnProps(ServerLevel level, BlockPos center) {
        ensureProp(level, center.offset(4, SPAWN_Y, 4), "signpost", 180.0F, 0.0F, 4);
        ensureProp(level, center.offset(0, SPAWN_Y, 6), "butterfly_arch", 180.0F, 0.0F, 8);
    }

    /** 按 variant 幂等放置道具；height 用于查询现有实体的包围盒范围。 */
    private static void ensureProp(ServerLevel level, BlockPos pos, String variant,
                                   float yaw, float pitch, int height) {
        AABB area = new AABB(
                pos.getX() - 3.5D, pos.getY() - 1.0D, pos.getZ() - 3.5D,
                pos.getX() + 3.5D, pos.getY() + height, pos.getZ() + 3.5D);
        boolean exists = level.getEntitiesOfClass(BarrierPropEntity.class, area,
                prop -> variant.equals(prop.getVariant())).size() > 0;
        if (!exists) {
            BarrierPropEntity.spawn(level, pos, variant, yaw, pitch);
        }
    }

    /** 确定性 2D value noise，输出 [-1, 1]。 */
    private static float noise(long seed, float x, float z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        float fx = x - x0;
        float fz = z - z0;
        float sx = fx * fx * (3.0F - 2.0F * fx);
        float sz = fz * fz * (3.0F - 2.0F * fz);
        float v00 = hash(seed, x0, z0) * 2.0F - 1.0F;
        float v10 = hash(seed, x0 + 1, z0) * 2.0F - 1.0F;
        float v01 = hash(seed, x0, z0 + 1) * 2.0F - 1.0F;
        float v11 = hash(seed, x0 + 1, z0 + 1) * 2.0F - 1.0F;
        return (v00 + (v10 - v00) * sx) * (1.0F - sz) + (v01 + (v11 - v01) * sx) * sz;
    }

    /** 确定性坐标哈希，输出 [0, 1)。 */
    private static float hash(long seed, int x, int z) {
        long h = seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        h *= 0x27D4EB2F165667C5L;
        h ^= h >>> 32;
        h *= 0x165667B19E3779F9L;
        h ^= h >>> 29;
        return (h & 0xFFFFFFL) / (float) 0x1000000L;
    }
}

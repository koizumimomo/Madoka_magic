package io.madoka.magic.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 索取魔女（take）的结界场景：蜜脾平原。
 *
 * <p>按《结界场景分配》文档实现：基础地形是一整片蜜脾块平原（不再是虚空悬浮），
 * 出生平台只高出平原 2 格，黑白棋盘格砖主路直达平地上的竞技场。
 * 其余地形只有零散装饰柱与几处蜜池，远处仍交给虚空。</p>
 *
 * <ul>
 *   <li>平原：蜜脾块表层 + 夯泥蜂蜡垫层，覆盖主路与竞技场周边。</li>
 *   <li>出生平台：13×13，高出平原 2 格，四角蜡柱灯，南缘蜂巢门拱。</li>
 *   <li>主路：宽 5 的黑白棋盘格砖，从平台脚下直通竞技场北缺口。</li>
 *   <li>竞技场：平地，中心一小圈琥珀蜜池，矮墙用夯泥 + 蜜脾压顶。</li>
 *   <li>装饰：平原上零散的蜂巢柱、蜜池，以及中段倾斜的宴会桌（由 BarrierScenes 放置）。</li>
 * </ul>
 *
 * <p>蜂蜜块只用于蜜池装饰面，不铺在可行走路面上，避免黏住玩家。
 * 所有形状由世界 seed 派生，可重复构建。</p>
 */
public final class TakeBarrierScene {
    private static final BlockState COMB = Blocks.HONEYCOMB_BLOCK.defaultBlockState();
    private static final BlockState WAX = Blocks.PACKED_MUD.defaultBlockState();
    private static final BlockState HONEY = Blocks.HONEY_BLOCK.defaultBlockState();
    private static final BlockState AMBER = Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
    private static final BlockState LAMP = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
    private static final BlockState BRICK = Blocks.MUD_BRICKS.defaultBlockState();
    private static final BlockState BLACK = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState WHITE = Blocks.SMOOTH_QUARTZ.defaultBlockState();

    /** 平原表面高度（与主路、竞技场同高）。 */
    private static final int GROUND_Y = BarrierScenes.DECK_Y;
    /** 出生平台表面：高出平原 2 格。 */
    private static final int SPAWN_Y = BarrierScenes.DECK_Y + 2;
    private static final int PLAIN_X = 26;
    private static final int PLAIN_Z_MIN = -(BarrierScenes.SPAWN_OFFSET + 8);
    private static final int PLAIN_Z_MAX = 24;

    private TakeBarrierScene() {}

    public static void apply(ServerLevel level, BlockPos anchor, long seed) {
        int spawnOffset = BarrierScenes.SPAWN_OFFSET;
        int radius = BarrierScenes.ARENA_RADIUS;
        int halfWidth = BarrierScenes.ROUTE_HALF_WIDTH;
        BlockPos center = anchor.offset(0, 0, -spawnOffset);
        // 场景是否已建过：判定必须在清理之前取，和 sacrifice 保持一致（蜜脾地面为标记）
        boolean fresh = !level.getBlockState(center.atY(SPAWN_Y)).is(COMB.getBlock());
        // 只清理平台以上的空域（旧版高空平台/蜜囊残骸），平原与平台基座不受影响
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                for (int y = SPAWN_Y + 2; y <= GROUND_Y + 72; y++) {
                    level.setBlock(center.offset(dx, y, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        if (!fresh) {
            // 已建过的存档：只补齐平原与平台（旧版没有平原）
            buildPlain(level, anchor);
            buildSpawn(level, center);
            return;
        }

        for (int cz = (anchor.getZ() + BarrierScenes.MIN_Z) >> 4;
             cz <= (anchor.getZ() + BarrierScenes.MAX_Z) >> 4; cz++) {
            for (int cx = (anchor.getX() + BarrierScenes.MIN_X) >> 4;
                 cx <= (anchor.getX() + BarrierScenes.MAX_X) >> 4; cx++) {
                level.getChunk(cx, cz);
            }
        }

        buildPlain(level, anchor);
        buildRoute(level, anchor, halfWidth);
        buildArena(level, anchor, seed, radius, halfWidth);
        buildDecor(level, anchor, seed);
        buildSpawn(level, center);
    }

    /** 蜜脾平原：蜜脾块表层 + 一层蜂蜡垫层，覆盖主路与竞技场周边。 */
    private static void buildPlain(ServerLevel level, BlockPos anchor) {
        for (int dz = PLAIN_Z_MIN; dz <= PLAIN_Z_MAX; dz++) {
            for (int dx = -PLAIN_X; dx <= PLAIN_X; dx++) {
                BlockPos base = anchor.offset(dx, 0, dz);
                level.setBlock(base.atY(GROUND_Y - 1), COMB, Block.UPDATE_CLIENTS);
                level.setBlock(base.atY(GROUND_Y), COMB, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** 出生平台：13×13，高出平原 2 格；四角蜡柱灯，南缘蜂巢门拱正对主路。 */
    private static void buildSpawn(ServerLevel level, BlockPos center) {
        for (int dz = -6; dz <= 6; dz++) {
            for (int dx = -6; dx <= 6; dx++) {
                BlockPos pos = center.offset(dx, 0, dz);
                level.setBlock(pos.atY(SPAWN_Y - 1), WAX, Block.UPDATE_CLIENTS);
                level.setBlock(pos.atY(SPAWN_Y), COMB, Block.UPDATE_CLIENTS);
            }
        }
        for (int[] c : new int[][]{{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
            BlockPos base = center.offset(c[0], 0, c[1]);
            level.setBlock(base.atY(SPAWN_Y + 1), WAX, Block.UPDATE_CLIENTS);
            level.setBlock(base.atY(SPAWN_Y + 2), LAMP, Block.UPDATE_CLIENTS);
        }
        // 南缘：一级下行台阶 + 蜂巢门拱
        for (int dx = -2; dx <= 2; dx++) {
            level.setBlock(center.offset(dx, 0, 7).atY(SPAWN_Y - 1), COMB, Block.UPDATE_CLIENTS);
        }
        for (int dx : new int[]{-4, 4}) {
            BlockPos base = center.offset(dx, 0, 7);
            for (int y = SPAWN_Y - 1; y <= SPAWN_Y + 3; y++) {
                level.setBlock(base.atY(y), y == SPAWN_Y + 3 ? COMB : BRICK, Block.UPDATE_CLIENTS);
            }
        }
        for (int dx = -4; dx <= 4; dx++) {
            level.setBlock(center.offset(dx, 0, 7).atY(SPAWN_Y + 3), COMB, Block.UPDATE_CLIENTS);
        }
        level.setBlock(center.offset(0, 0, 7).atY(SPAWN_Y + 2), LAMP, Block.UPDATE_CLIENTS);
        level.setBlock(center.atY(SPAWN_Y - 1), COMB, Block.UPDATE_CLIENTS);
    }

    /** 主路：宽 5 的黑白棋盘格砖，从平台脚下直通竞技场北缺口。 */
    private static void buildRoute(ServerLevel level, BlockPos anchor, int halfWidth) {
        for (int dz = -(BarrierScenes.SPAWN_OFFSET - 6); dz <= -18; dz++) {
            for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                BlockState tile = ((dx + dz) & 1) == 0 ? BLACK : WHITE;
                level.setBlock(anchor.offset(dx, GROUND_Y, dz), tile, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** 竞技场：平地圆形拼贴 + 矮墙（北向主路缺口）+ 中心琥珀蜜池。 */
    private static void buildArena(ServerLevel level, BlockPos anchor, long seed,
                                   int radius, int halfWidth) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if ((double) dx * dx + (double) dz * dz > (double) radius * radius) {
                    continue;
                }
                level.setBlock(anchor.offset(dx, 0, dz).atY(GROUND_Y), COMB, Block.UPDATE_CLIENTS);
            }
        }
        // 中心蜜池：琥珀玻璃平面，不粘脚
        for (int dz = -4; dz <= 4; dz++) {
            for (int dx = -4; dx <= 4; dx++) {
                if (dx * dx + dz * dz > 16) {
                    continue;
                }
                level.setBlock(anchor.offset(dx, 0, dz).atY(GROUND_Y), AMBER, Block.UPDATE_CLIENTS);
            }
        }
        for (int dz = -4; dz <= 4; dz++) {
            for (int dx = -4; dx <= 4; dx++) {
                int d2 = dx * dx + dz * dz;
                if (d2 <= 16 || d2 > 30) {
                    continue;
                }
                level.setBlock(anchor.offset(dx, 0, dz).atY(GROUND_Y), COMB, Block.UPDATE_CLIENTS);
            }
        }
        int outer = radius + 2;
        double inner = (radius - 0.7D) * (radius - 0.7D);
        double outerSq = (radius + 0.7D) * (radius + 0.7D);
        for (int dz = -outer; dz <= outer; dz++) {
            for (int dx = -outer; dx <= outer; dx++) {
                double d2 = (double) dx * dx + (double) dz * dz;
                if (d2 < inner || d2 > outerSq) {
                    continue;
                }
                if (Math.abs(dx) <= halfWidth + 1 && dz <= -(radius - halfWidth - 2)) {
                    continue;
                }
                BlockPos base = anchor.offset(dx, 0, dz);
                level.setBlock(base.atY(GROUND_Y + 1), WAX, Block.UPDATE_CLIENTS);
                level.setBlock(base.atY(GROUND_Y + 2), COMB, Block.UPDATE_CLIENTS);
                if (hash(seed, dx, dz) > 0.82F) {
                    level.setBlock(base.atY(GROUND_Y + 3), LAMP, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** 平原装饰：零散蜂巢柱与几处蜜池，避开主路与竞技场。 */
    private static void buildDecor(ServerLevel level, BlockPos anchor, long seed) {
        for (int i = 0; i < 14; i++) {
            float r1 = hash(seed ^ 0xA11L, i, 1);
            float r2 = hash(seed ^ 0xA11L, i, 2);
            int dx = (int) ((r1 * 2.0F - 1.0F) * (PLAIN_X - 4));
            int dz = PLAIN_Z_MIN + 6 + (int) (r2 * (PLAIN_Z_MAX - PLAIN_Z_MIN - 12));
            if (Math.abs(dx) <= 4 || (dx * dx + dz * dz) < 30 * 30) {
                continue;
            }
            int height = 2 + (int) (hash(seed ^ 0xA12L, i, 3) * 5.0F);
            int radius = hash(seed ^ 0xA13L, i, 4) > 0.6F ? 1 : 0;
            for (int px = -radius; px <= radius; px++) {
                for (int pz = -radius; pz <= radius; pz++) {
                    for (int y = 1; y <= height; y++) {
                        BlockState st = y == height ? COMB : WAX;
                        level.setBlock(anchor.offset(dx + px, GROUND_Y + y, dz + pz), st, Block.UPDATE_CLIENTS);
                    }
                }
            }
            if (hash(seed ^ 0xA14L, i, 5) > 0.55F) {
                level.setBlock(anchor.offset(dx, GROUND_Y + height + 1, dz), LAMP, Block.UPDATE_CLIENTS);
            }
        }
        // 蜜池：凹陷一格并铺蜂蜜块，边上围蜂巢
        for (int i = 0; i < 3; i++) {
            float r1 = hash(seed ^ 0xB21L, i, 1);
            float r2 = hash(seed ^ 0xB21L, i, 2);
            int dx = (int) ((r1 * 2.0F - 1.0F) * 18.0F);
            int dz = PLAIN_Z_MIN + 20 + (int) (r2 * (PLAIN_Z_MAX - PLAIN_Z_MIN - 40));
            if (Math.abs(dx) <= 6 || (dx * dx + dz * dz) < 30 * 30) {
                continue;
            }
            int r = 2;
            for (int px = -r; px <= r; px++) {
                for (int pz = -r; pz <= r; pz++) {
                    if (px * px + pz * pz > r * r) {
                        continue;
                    }
                    level.setBlock(anchor.offset(dx + px, GROUND_Y, dz + pz), HONEY, Block.UPDATE_CLIENTS);
                }
            }
            for (int px = -r - 1; px <= r + 1; px++) {
                for (int pz = -r - 1; pz <= r + 1; pz++) {
                    int d2 = px * px + pz * pz;
                    if (d2 <= r * r || d2 > (r + 1) * (r + 1)) {
                        continue;
                    }
                    level.setBlock(anchor.offset(dx + px, GROUND_Y, dz + pz), COMB, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** 确定性坐标哈希，输出 [0, 1)。与 BarrierScenes 的私有实现保持同式。 */
    private static float hash(long seed, int x, int z) {
        long h = seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        h *= 0x27D4EB2F165667C5L;
        h ^= h >>> 32;
        h *= 0x165667B19E3779F9L;
        h ^= h >>> 29;
        return (h & 0xFFFFFFL) / (float) 0x1000000L;
    }
}

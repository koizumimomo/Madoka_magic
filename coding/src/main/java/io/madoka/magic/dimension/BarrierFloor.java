package io.madoka.magic.dimension;

import io.madoka.magic.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.FlatLevelSource;

public final class BarrierFloor {
    private static final int ARENA_RADIUS = 24;
    private static final int LEGACY_SURFACE_OFFSET = 3;

    private BarrierFloor() {}

    /** A newly opened arena may overlap chunks generated with the earlier, undecorated floor. */
    public static void refreshLegacyArena(ServerLevel level, BlockPos anchor, String theme) {
        var entry = ModBlocks.BARRIER_FLOORS.get(theme);
        if (entry == null || !(level.getChunkSource().getGenerator() instanceof FlatLevelSource)) {
            return;
        }
        int y = level.getMinBuildHeight() + LEGACY_SURFACE_OFFSET;
        BlockPos center = anchor.atY(y);
        Block floor = entry.block().get();
        if (level.getBlockState(center).is(floor) && level.getBlockState(center.offset(0, 0, -16)).is(floor)) {
            return;
        }
        var state = floor.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-ARENA_RADIUS, 0, -ARENA_RADIUS),
                center.offset(ARENA_RADIUS, 0, ARENA_RADIUS))) {
            var chunk = level.getChunkAt(pos);
            if (chunk.getBlockState(pos).is(entry.previous()) && chunk.getBlockState(pos.above()).isAir()
                    && chunk.getBlockState(pos.below()).is(entry.previous())
                    && chunk.getBlockState(pos.below(2)).is(entry.previous())
                    && chunk.getBlockState(pos.below(3)).is(Blocks.BEDROCK)) {
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            }
        }
    }
}

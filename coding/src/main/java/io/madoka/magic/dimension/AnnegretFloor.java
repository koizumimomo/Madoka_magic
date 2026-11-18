package io.madoka.magic.dimension;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.compat.AlexCavesCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class AnnegretFloor {
    private AnnegretFloor() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)
                || !"annegret".equals(MadokaDimensions.barrierTheme(level.dimension())) || !AlexCavesCompat.isPresent()) {
            return;
        }
        // Run after the chunk has entered the server cache. Never request neighboring chunks from a load callback.
        var server = level.getServer();
        var position = chunk.getPos();
        server.tell(new TickTask(server.getTickCount(), () -> {
            if (level.getChunkSource().getChunkNow(position.x, position.z) == chunk) paint(level, chunk);
        }));
    }

    private static void paint(ServerLevel level, LevelChunk chunk) {
        var frosting = List.of("block_of_frosting", "block_of_vanilla_frosting", "block_of_chocolate_frosting")
                .stream().map(AlexCavesCompat::block).flatMap(Optional::stream).map(Block::defaultBlockState).toList();
        if (frosting.isEmpty()) return;
        var chunkPos = chunk.getPos();
        var random = RandomSource.create(level.getSeed() ^ Mth.getSeed(chunkPos.x, 0, chunkPos.z));
        int y = level.getMinBuildHeight() + 3;
        for (BlockPos pos : BlockPos.betweenClosed(chunkPos.getMinBlockX(), y, chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX(), y, chunkPos.getMaxBlockZ())) {
            var replacement = frosting.get(random.nextInt(frosting.size()));
            var state = chunk.getBlockState(pos);
            if ((state.is(Blocks.CAKE) || state.is(Blocks.PINK_TERRACOTTA))
                    && chunk.getBlockState(pos.below()).is(Blocks.PINK_TERRACOTTA)
                    && chunk.getBlockState(pos.below(2)).is(Blocks.PINK_TERRACOTTA)
                    && chunk.getBlockState(pos.below(3)).is(Blocks.BEDROCK)) {
                level.setBlock(pos, replacement, Block.UPDATE_CLIENTS);
            }
        }
    }
}

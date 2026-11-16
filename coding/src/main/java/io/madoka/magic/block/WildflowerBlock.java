package io.madoka.magic.block;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class WildflowerBlock extends FlowerBlock implements BonemealableBlock {
    private static final TagKey<Biome> BLACK_TULIP_BIOMES = flowerBiomes("black_tulip");
    private static final TagKey<Biome> ERYNGIUM_BIOMES = flowerBiomes("eryngium");
    private static final int GRASS_BLOOM_CHANCE = 3;
    private static final int SPREAD_RADIUS = 2;

    public WildflowerBlock(MobEffect stewEffect, int duration, Properties properties) {
        super(() -> stewEffect, duration, properties);
    }

    private static TagKey<Biome> flowerBiomes(String flower) {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "has_" + flower));
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, boolean clientSide) {
        return findSpreadPosition(level, pos, state, null) != null;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos target = findSpreadPosition(level, pos, state, random);
        if (target != null) {
            level.setBlockAndUpdate(target, state);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGrassBonemeal(BonemealEvent event) {
        if (event.getResult() != Event.Result.DEFAULT || !(event.getLevel() instanceof ServerLevel level)
                || !event.getBlock().is(Blocks.GRASS_BLOCK) || !level.isEmptyBlock(event.getPos().above())) {
            return;
        }
        BlockPos origin = event.getPos().above();
        var biome = level.getBiome(origin);
        boolean tulip = biome.is(BLACK_TULIP_BIOMES);
        boolean eryngium = biome.is(ERYNGIUM_BIOMES);
        if ((!tulip && !eryngium) || level.random.nextInt(GRASS_BLOOM_CHANCE) != 0) {
            return;
        }
        BlockState flower = (eryngium && (!tulip || level.random.nextBoolean())
                ? ModBlocks.ERYNGIUM : ModBlocks.BLACK_TULIP).get().defaultBlockState();
        BlockPos target = findSpreadPosition(level, origin, flower, level.random);
        if (target != null) {
            // Leave the origin clear so vanilla can still grow its patch and consume the bone meal.
            level.setBlockAndUpdate(target, flower);
        }
    }

    @Nullable
    private static BlockPos findSpreadPosition(LevelReader level, BlockPos origin, BlockState flower,
                                               @Nullable RandomSource random) {
        BlockPos selected = null;
        int candidates = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SPREAD_RADIUS, -1, -SPREAD_RADIUS),
                origin.offset(SPREAD_RADIUS, 1, SPREAD_RADIUS))) {
            if (pos.equals(origin) || !level.hasChunkAt(pos) || !level.isEmptyBlock(pos) || !flower.canSurvive(level, pos)) {
                continue;
            }
            if (random == null) {
                return pos.immutable();
            }
            if (random.nextInt(++candidates) == 0) {
                selected = pos.immutable();
            }
        }
        return selected;
    }
}

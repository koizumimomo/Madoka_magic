package io.madoka.magic.event;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.entity.WitchPortalEntity;
import io.madoka.magic.entity.WitchTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 拦截凋零召唤：非结界维度里搭出凋零结构后，不再生成凋零 Boss。
 *
 * <p>四个灵魂沙与三颗凋灵骷髅头会被清掉，原地生成一道通往
 * 「悼亡的魔女」（theme {@code wither}）的结界入口传送门。
 * 结界维度内部的凋零（悼亡魔女本体）不受影响。</p>
 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class WitherSummonEvents {
    private WitherSummonEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWitherSummon(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.boss.wither.WitherBoss)
                || event.loadedFromDisk() || event.getLevel().isClientSide()
                || !MadokaDimensions.barrierTheme(event.getLevel().dimension()).isEmpty()) {
            return;
        }
        event.setCanceled(true);
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos center = event.getEntity().blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -3, -3), center.offset(3, 3, 3))) {
            var state = level.getBlockState(pos);
            if (state.is(Blocks.SOUL_SAND) || state.is(Blocks.WITHER_SKELETON_SKULL)
                    || state.is(Blocks.WITHER_SKELETON_WALL_SKULL)) {
                level.removeBlock(pos, false);
            }
        }
        // 灵魂沙清理后再取地表高度，避免传送门卡进地里
        BlockPos ground = new BlockPos(center.getX(),
                level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        center.getX(), center.getZ()),
                center.getZ());
        WitchPortalEntity.spawn(level, WitchPortalEntity.KIND_ENTRANCE, WitchTypes.WITHER, null, ground);
        level.playSound(null, center, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8F, 1.0F);
    }
}

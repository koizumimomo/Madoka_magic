package io.madoka.magic.event;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 双手紫水晶仪式：主手+副手都拿着紫水晶碎片，右键去皮原木，
 * 生成金色法阵粒子，40 tick 后把该方块替换为契约祭坛。
 *
 * <p>延迟任务用一个简易队列挂在 {@link TickEvent.ServerTickEvent} 上，
 * 每 5 tick 绕方块画一圈金色粉尘。</p>
 */
public class AltarCraftEvents {

    /** 去皮原木标签：{@code data/madoka_magic/tags/blocks/stripped_logs.json}。 */
    private static final TagKey<Block> STRIPPED_LOGS = TagKey.create(
            Registries.BLOCK, new ResourceLocation(MadokaMagic.MODID, "stripped_logs"));

    /** 金色粉尘粒子。 */
    private static final DustParticleOptions GOLD_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.3F), 1.0F);

    /** 仪式总时长（tick）。 */
    private static final int RITUAL_TICKS = 40;

    /** 等待转成祭坛的位置队列。 */
    private static final List<PendingAltar> PENDING = new ArrayList<>();

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // 只处理主手那一次，避免主副手各触发一遍
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player player = event.getEntity();
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (!main.is(Items.AMETHYST_SHARD) || !off.is(Items.AMETHYST_SHARD)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!serverLevel.getBlockState(pos).is(STRIPPED_LOGS)) {
            return;
        }
        // 同一根原木已经在仪式中就忽略
        for (PendingAltar p : PENDING) {
            if (p.dimension.equals(serverLevel.dimension()) && p.pos.equals(pos)) {
                return;
            }
        }
        // 消费 1 个紫水晶（主手）
        main.shrink(1);
        PENDING.add(new PendingAltar(serverLevel.dimension(), pos.immutable(), RITUAL_TICKS));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        Iterator<PendingAltar> it = PENDING.iterator();
        while (it.hasNext()) {
            PendingAltar pending = it.next();
            ServerLevel level = ServerLifecycleHooks.getCurrentServer().getLevel(pending.dimension);
            if (level == null) {
                it.remove();
                continue;
            }
            if (pending.ticksLeft % 5 == 0) {
                spawnRing(level, pending.pos);
            }
            pending.ticksLeft--;
            if (pending.ticksLeft <= 0) {
                // 替换前再确认那里仍是去皮原木（期间可能已被挖掉/改动）
                if (level.getBlockState(pending.pos).is(STRIPPED_LOGS)) {
                    level.setBlock(pending.pos, ModBlocks.ALTAR.get().defaultBlockState(), 3);
                }
                it.remove();
            }
        }
    }

    /** 绕方块一圈的金色粉尘。 */
    private static void spawnRing(ServerLevel level, BlockPos pos) {
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 0.6D;
        double cz = pos.getZ() + 0.5D;
        for (int i = 0; i < 16; i++) {
            double angle = i * (Math.PI * 2.0D) / 16.0D;
            level.sendParticles(GOLD_DUST,
                    cx + Math.cos(angle) * 1.1D, cy, cz + Math.sin(angle) * 1.1D,
                    1, 0.0D, 0.02D, 0.0D, 0.0D);
        }
    }

    /** 一个等待完成的双紫水晶仪式。 */
    private static final class PendingAltar {
        private final ResourceKey<Level> dimension;
        private final BlockPos pos;
        private int ticksLeft;

        private PendingAltar(ResourceKey<Level> dimension, BlockPos pos, int ticksLeft) {
            this.dimension = dimension;
            this.pos = pos;
            this.ticksLeft = ticksLeft;
        }
    }
}

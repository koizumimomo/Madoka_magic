package io.madoka.magic.event;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.item.WeaponEngraving;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 武器铭刻流程：
 * <ol>
 * <li>手持紫水晶碎片右击紫水晶簇方块，簇被取下并刻上玩家名字；</li>
 * <li>把武器与这簇水晶放进铁砧，合成出铭刻武器。</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class EngravingEvents {
    private EngravingEvents() {}

    /** 手持紫水晶碎片右击紫水晶簇：簇被取下，刻上自己的名字。 */
    @SubscribeEvent
    public static void markCluster(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (!held.is(Items.AMETHYST_SHARD)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(Blocks.AMETHYST_CLUSTER)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide) {
            return;
        }
        level.removeBlock(pos, false);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        player.getInventory().placeItemBackInInventory(WeaponEngraving.markedCluster(player));
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.35F);
        player.displayClientMessage(Component.translatable("message.madoka_magic.engrave.marked"), true);
    }

    /** 铁砧：武器 + 本人铭刻的紫水晶簇 → 铭刻武器。 */
    @SubscribeEvent
    public static void combine(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        Player player = event.getPlayer();
        if (player == null || left.getCount() != 1 || !WeaponEngraving.isWeapon(left)
                || WeaponEngraving.isEngraved(left) || !WeaponEngraving.isMarkedCluster(right)) {
            return;
        }
        UUID owner = WeaponEngraving.ownerId(right);
        if (owner == null || !owner.equals(player.getUUID())) {
            return;
        }
        ItemStack output = left.copy();
        WeaponEngraving.engrave(output, player);
        event.setOutput(output);
        event.setCost(1);
        event.setMaterialCost(1);
    }
}

package io.madoka.magic.item;

import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.network.MadokaNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 创造模式测试工具：按档位抬高污染。
 *
 * <p>右键第一次到 {@link PlayerMadokaData#HALF_WITCH_THRESHOLD}（半魔女线），
 * 第二次到 {@link PlayerMadokaData#TAINT_MAX}（魔女化线），满值后不再变化；
 * Shift+右键清空污染。只对魔法少女形态生效。</p>
 */
public final class TaintTestItem extends Item {

    public TaintTestItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isCreative()) {
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            apply(serverPlayer, player.isShiftKeyDown());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static void apply(ServerPlayer player, boolean clear) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (!data.isContracted() || data.isWitchPath() || data.isHalfWitch()) {
            player.displayClientMessage(Component.translatable("message.madoka_magic.test_taint.needs_girl"), true);
            return;
        }
        if (clear) {
            data.setTaint(0.0F);
            data.save(player);
            MadokaNetwork.syncTo(player);
            playSound(player, 1.4F);
            player.displayClientMessage(Component.translatable("item.madoka_magic.test_raise_taint.cleared"), true);
            return;
        }
        float taint = data.getTaint();
        if (taint >= PlayerMadokaData.TAINT_MAX) {
            player.displayClientMessage(Component.translatable("item.madoka_magic.test_raise_taint.full"), true);
            return;
        }
        float target = taint < PlayerMadokaData.HALF_WITCH_THRESHOLD
                ? PlayerMadokaData.HALF_WITCH_THRESHOLD
                : PlayerMadokaData.TAINT_MAX;
        data.setTaint(target);
        data.save(player);
        MadokaNetwork.syncTo(player);
        playSound(player, 0.9F);
        player.displayClientMessage(
                Component.translatable("item.madoka_magic.test_raise_taint.used", (int) target), true);
    }

    private static void playSound(ServerPlayer player, float pitch) {
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW,
                SoundSource.PLAYERS, 0.7F, pitch);
    }
}

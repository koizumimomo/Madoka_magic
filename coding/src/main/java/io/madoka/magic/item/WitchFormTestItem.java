package io.madoka.magic.item;

import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.data.PlayerTransformation;
import io.madoka.magic.event.PlayerEvents;
import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.network.PlayerFormState;
import io.madoka.magic.network.TransformationFx;
import io.madoka.magic.spell.SpellCasting;
import io.madoka.magic.util.MadokaMath;
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
 * 创造模式测试工具：在当前性质下，把玩家在魔法少女与魔女之间切换。
 *
 * <p>只改 {@link PlayerMadokaData#isWitchPath()}，主题（性质）保持不变；
 * 魔女形态的属性、技能与被动取 {@code magical_girls/<theme>.json} 里的
 * {@code witch_form}，与正常魔女化走的同一套数据。</p>
 */
public final class WitchFormTestItem extends Item {

    public WitchFormTestItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isCreative()) {
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            switchForm(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static void switchForm(ServerPlayer player) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (!data.isContracted() || data.getTheme() == null || data.isPendingWitchChoice()) {
            player.displayClientMessage(Component.translatable("message.madoka_magic.test_form.needs_contract"), true);
            return;
        }
        boolean toWitch = !data.isWitchPath();
        if (toWitch) {
            // 与正常魔女化一致：把当前纯度带过来当绝望值，污染清零。
            data.setDespair(MadokaMath.purity(player));
            data.setWitchPath(true);
            data.setTaint(0.0F);
            TransformationFx.send(player, data.getThemeId(), TransformationFx.Kind.WITCH);
        } else {
            data.setWitchPath(false);
            data.setDespair(-1.0D);
            data.setTaint(0.0F);
            TransformationFx.send(player, data.getThemeId(), TransformationFx.Kind.TRANSFORM);
        }
        data.setKeepIdentity(false);
        data.setPendingWitchChoice(false);
        // 两条路线的技能表不同，先清空再按当前形态重新解锁。
        data.getUnlockedSpells().clear();
        SpellCasting.clearCooldowns(player.getUUID());
        PlayerEvents.refreshUnlocks(player);
        PlayerTransformation.applyAttributes(player);
        data.save(player);
        PlayerFormState.broadcast(player);
        MadokaNetwork.syncTo(player);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.9F, toWitch ? 0.6F : 1.25F);
        player.displayClientMessage(Component.translatable(
                toWitch ? "item.madoka_magic.test_witch_form.witch" : "item.madoka_magic.test_witch_form.girl",
                Component.translatable(data.getTheme().getTranslationKey())), true);
    }
}

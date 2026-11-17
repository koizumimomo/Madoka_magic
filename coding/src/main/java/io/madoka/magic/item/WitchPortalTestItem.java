package io.madoka.magic.item;

import io.madoka.magic.client.MadokaClient;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 测试工具：右键打开魔女选择界面，生成所选魔女的结界入口传送门。 */
public final class WitchPortalTestItem extends Item {

    public WitchPortalTestItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isCreative()) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide()) {
            MadokaClient.openWitchPortalTest();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}

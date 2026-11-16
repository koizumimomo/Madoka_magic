package io.madoka.magic.item;

import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.data.PlayerTransformation;
import io.madoka.magic.event.PlayerEvents;
import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.network.PlayerFormState;
import io.madoka.magic.registry.MadokaAttributes;
import io.madoka.magic.spell.SpellCasting;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class CreativeTestItem extends Item {
    public enum Action { RELEASE_CONTRACT, MAX_PURITY }

    private final Action action;

    public CreativeTestItem(Action action) {
        super(new Properties().stacksTo(1));
        this.action = action;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isCreative()) {
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            var data = PlayerMadokaData.of(player);
            if (!data.isContracted() && !(data.isPendingWitchChoice() && action == Action.RELEASE_CONTRACT)) {
                player.displayClientMessage(Component.translatable("message.madoka_magic.test.needs_contract"), true);
                return InteractionResultHolder.fail(stack);
            }
            switch (action) {
                case RELEASE_CONTRACT -> {
                    PlayerTransformation.revert(serverPlayer);
                    data.setContracted(false);
                    data.setTheme("");
                    data.setWitchPath(false);
                    data.setKeepIdentity(false);
                    data.setPendingWitchChoice(false);
                    data.setWeakened(false);
                    data.setTaint(0.0F);
                    data.setObsessionStacks(0);
                    data.setLastKillTick(-1L);
                    data.clearCombat();
                    data.getUnlockedSpells().clear();
                    SpellCasting.clearCooldowns(player.getUUID());
                    PlayerFormState.broadcast(serverPlayer);
                }
                case MAX_PURITY -> {
                    var purity = player.getAttribute(MadokaAttributes.GEM_PURITY.get());
                    if (data.isWitchPath()) {
                        data.setDespair(MadokaMath.PURITY_MAX);
                    } else if (purity != null) {
                        purity.setBaseValue(MadokaMath.PURITY_MAX);
                    }
                    data.setLevel(MadokaMath.levelOf(player));
                    PlayerEvents.refreshUnlocks(serverPlayer);
                }
            }
            data.save(player);
            MadokaNetwork.syncTo(player);
            String message = action == Action.MAX_PURITY && data.isWitchPath()
                    ? "item.madoka_magic.test_max_purity.despair" : getDescriptionId() + ".used";
            player.displayClientMessage(Component.translatable(message), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}

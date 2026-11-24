package io.madoka.magic.data;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.item.CurseRelic;
import io.madoka.magic.network.MadokaNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/** Persisted amount supplied by the photograph's debt effect and synchronized to its numeric HUD. */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class CurseDebtState {
    private static final String KEY = "madoka_curse_debt";
    private static final String ID = "madoka_curse_identity";

    private CurseDebtState() {}

    public static boolean refresh(ServerPlayer player) {
        var data = PlayerMadokaData.of(player);
        var item = player.isAlive() && !player.isSpectator() && data.isTransformed()
                ? CurseStackState.equipped(player) : ItemStack.EMPTY;
        if (!(item.getItem() instanceof CurseRelic relic) || !relic.theme().equals("atonement")) {
            double pending = io.madoka.magic.spell.OriginalCurseEffects.pendingDebt(player);
            if (player.isAlive() && pending > 0) {
                var state = player.getPersistentData().getCompound(KEY);
                boolean changed = state.isEmpty() || state.getDouble("amount") != pending;
                if (state.getString("item").isEmpty())state.putString("item","madoka_magic:curse_weapon_atonement");
                state.putDouble("amount",pending);player.getPersistentData().put(KEY,state);return changed;
            }
            boolean had = player.getPersistentData().contains(KEY);
            player.getPersistentData().remove(KEY);
            return had;
        }
        if (!item.getOrCreateTag().hasUUID(ID)) item.getTag().putUUID(ID, UUID.randomUUID());
        var state = player.getPersistentData().getCompound(KEY);
        if (!state.hasUUID("identity") || !state.getUUID("identity").equals(item.getTag().getUUID(ID))) {
            state = new CompoundTag();
            state.putUUID("identity", item.getTag().getUUID(ID));
            state.putString("item", ForgeRegistries.ITEMS.getKey(item.getItem()).toString());
            state.putDouble("amount", io.madoka.magic.spell.OriginalCurseEffects.pendingDebt(player));
            player.getPersistentData().put(KEY, state);
            return true;
        }
        return false;
    }

    public static void setAmount(ServerPlayer player, double amount) {
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid relic debt amount");
        refresh(player);
        var state = player.getPersistentData().getCompound(KEY);
        if (!state.isEmpty()) state.putDouble("amount", amount);
        MadokaNetwork.syncTo(player);
    }

    public static CompoundTag snapshot(ServerPlayer player) {
        refresh(player);
        return player.getPersistentData().getCompound(KEY).copy();
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && player.tickCount % 5 == 0 && refresh(player)) MadokaNetwork.syncTo(player);
    }
}

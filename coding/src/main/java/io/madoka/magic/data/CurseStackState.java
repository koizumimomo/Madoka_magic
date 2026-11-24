package io.madoka.magic.data;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.compat.CuriosSlotRegistration;
import io.madoka.magic.item.CurseRelic;
import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/** Server-owned charge state; only the currently equipped, active relic can build or spend it. */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class CurseStackState {
    private static final String KEY = "madoka_curse_stacks";
    private static final String ITEM_ID = "madoka_curse_identity";

    private CurseStackState() {}

    public static ItemStack equipped(ServerPlayer player) {
        return ModList.get().isLoaded("curios") ? CuriosSlotRegistration.equippedCurse(player) : ItemStack.EMPTY;
    }

    public static boolean refresh(ServerPlayer player) {
        var data = PlayerMadokaData.of(player);
        ItemStack item = player.isAlive() && !player.isSpectator() && data.isTransformed()
                ? equipped(player) : ItemStack.EMPTY;
        if (!(item.getItem() instanceof CurseRelic relic) || CurseStackProfiles.get(relic.theme()) == null) {
            boolean had = player.getPersistentData().contains(KEY);
            player.getPersistentData().remove(KEY);
            return had;
        }
        var itemTag = item.getOrCreateTag();
        if (!itemTag.hasUUID(ITEM_ID)) itemTag.putUUID(ITEM_ID, UUID.randomUUID());
        var id = itemTag.getUUID(ITEM_ID);
        int level = CurseRelic.level(item);
        var state = player.getPersistentData().getCompound(KEY);
        var profile = CurseStackProfiles.get(relic.theme());
        if (!state.hasUUID("identity") || !id.equals(state.getUUID("identity"))
                || level != state.getInt("level") || !relic.theme().equals(state.getString("theme"))) {
            state = new CompoundTag();
            state.putUUID("identity", id);
            state.putString("theme", relic.theme());
            state.putString("item", ForgeRegistries.ITEMS.getKey(item.getItem()).toString());
            state.putInt("level", level);
            state.putInt("max", profile.threshold(level));
            state.putString("nameKey", profile.nameKey());
            player.getPersistentData().put(KEY, state);
            return true;
        }
        if (state.getInt("max") != profile.threshold(level) || !profile.nameKey().equals(state.getString("nameKey"))) {
            state.putInt("max", profile.threshold(level));
            state.putInt("stacks", Math.min(state.getInt("stacks"), profile.threshold(level)));
            state.putString("nameKey", profile.nameKey());
            return true;
        }
        return false;
    }

    public static CompoundTag snapshot(ServerPlayer player) {
        refresh(player);
        return player.getPersistentData().getCompound(KEY).copy();
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && player.tickCount % 5 == 0 && refresh(player)) {
            MadokaNetwork.syncTo(player);
        }
    }

    private static ServerPlayer owner(Entity source) {
        if (source instanceof ServerPlayer player) return player;
        return source instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer player ? player : null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void kill(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer dead) {
            dead.getPersistentData().remove(KEY);
            MadokaNetwork.syncTo(dead);
        }
        var player = owner(event.getSource().getEntity());
        if (player == null || event.getEntity() == player) return;
        refresh(player);
        var state = player.getPersistentData().getCompound(KEY);
        if (state.isEmpty()) return;
        int before = state.getInt("stacks");
        int next = Math.min(before + 1, state.getInt("max"));
        if (next == before) return;
        state.putInt("stacks", next);
        MadokaNetwork.syncTo(player);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void hit(LivingDamageEvent event) {
        if (io.madoka.magic.spell.OriginalCurseEffects.isSettlement(event.getSource())) return;
        var player = owner(event.getSource().getEntity());
        if (player == null || event.getEntity() == player || event.getAmount() <= 0) return;
        refresh(player);
        var state = player.getPersistentData().getCompound(KEY);
        if (state.isEmpty() || state.getInt("stacks") < state.getInt("max")) return;
        var profile = CurseStackProfiles.get(state.getString("theme"));
        if (profile == null) return;
        double matching = state.getString("theme").equals(PlayerMadokaData.of(player).getThemeId())
                ? profile.matchingMultiplier() : 1;
        double damage = profile.strikeDamage() * Math.pow(profile.levelMultiplier(), state.getInt("level") - 1)
                * (1 + MadokaMath.power(player)) * matching;
        event.setAmount(event.getAmount() + (float) damage);
        state.putInt("stacks", 0);
        var level = (ServerLevel) player.level();
        var target = event.getEntity();
        for (int i = 0; i < 9; i++) {
            level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + i * 0.35, target.getZ(),
                    1, 0.04, 0.02, 0.04, 0.005);
        }
        level.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + target.getBbHeight() * 0.5,
                target.getZ(), 12, 0.35, 0.4, 0.35, 0.1);
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.1f);
        MadokaNetwork.syncTo(player);
    }
}

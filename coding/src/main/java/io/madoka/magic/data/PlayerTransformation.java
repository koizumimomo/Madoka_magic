package io.madoka.magic.data;

import io.madoka.magic.compat.IronsCompat;
import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.entity.MadokaWitchEntity;
import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.network.PlayerFormState;
import io.madoka.magic.network.TransformationFx;
import io.madoka.magic.registry.MadokaAttributes;
import io.madoka.magic.spell.BlossomState;
import io.madoka.magic.spell.PollenTracker;
import io.madoka.magic.spell.PromiseState;
import io.madoka.magic.spell.SelfishEye;
import io.madoka.magic.spell.ThornTracker;
import io.madoka.magic.spell.WitchPlayerSpells;
import io.madoka.magic.event.PlayerEvents;
import io.madoka.magic.item.ContractWeapon;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class PlayerTransformation {
    private static final UUID FORM_MODIFIER = UUID.fromString("9877f422-b684-4d20-96bc-a7abc1fb4018");
    private static final UUID GROWTH_MODIFIER = UUID.fromString("727725f5-5afe-426b-8b96-e4e40363bc94");

    private PlayerTransformation() {
    }

    public static boolean toggle(ServerPlayer player) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        MagicalGirl theme = data.getTheme();
        if (!data.isContracted() || data.isPendingWitchChoice() || theme == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        if (data.isTransformed()) {
            revert(player);
        } else {
            data.setTransformed(true);
            giveWeapon(player, theme);
            applyAttributes(player);
            player.setHealth(player.getMaxHealth());
            data.setMana(MadokaMath.manaMax(player));
            data.save(player);
            TransformationFx.send(player, theme.getId(), TransformationFx.Kind.TRANSFORM);
        }

        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.9F, data.isTransformed() ? 1.25F : 0.85F);
        PlayerFormState.broadcast(player);
        MadokaNetwork.syncTo(player);
        return true;
    }

    public static void revert(ServerPlayer player) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        data.setTransformed(false);
        removeWeapon(player);
        BlossomState.stop(player);
        PromiseState.clear(player);
        PollenTracker.clear(player.getUUID());
        ThornTracker.clear(player.getUUID());
        WitchPlayerSpells.clear(player);
        applyAttributes(player);
        data.setMana(0.0D);
        data.save(player);
    }

    public static void beginHalfWitch(ServerPlayer player) {
        var data = PlayerMadokaData.of(player);
        if (!player.isAlive() || player.isSpectator() || !data.isTransformed() || data.isWitchPath()
                || data.isHalfWitch() || data.mustWitchify()) {
            return;
        }
        if (!data.isHalfWitchReady()) {
            player.displayClientMessage(Component.translatable("message.madoka_magic.barrier_need_taint",
                    (int) PlayerMadokaData.HALF_WITCH_THRESHOLD), true);
            return;
        }
        data.startHalfWitch();
        data.save(player);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.9F, 0.6F);
        PlayerFormState.broadcast(player);
        TransformationFx.send(player, data.getThemeId(), TransformationFx.Kind.HALF_WITCH);
        MadokaNetwork.syncTo(player);
    }

    public static void tickHalfWitch(ServerPlayer player, PlayerMadokaData data) {
        if (!data.isHalfWitch()) {
            return;
        }
        data.tickHalfWitch();
        data.save(player);
        if (!data.isHalfWitch()) {
            PlayerFormState.broadcast(player);
            MadokaNetwork.syncTo(player);
        }
    }

    public static void inheritWitchPower(ServerPlayer player, MadokaWitchEntity witch) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (!data.isContracted() || !data.isKeepIdentity() || data.isWitchPath() || witch.wasAssisted()
                || !witch.isOwnedBy(player) || !data.getThemeId().equals(witch.getThemeId())
                || !witch.level().dimension().equals(MadokaDimensions.witchLevel(witch.getThemeId()))) {
            return;
        }
        revert(player);
        data.setDespair(MadokaMath.purity(player));
        data.setWitchPath(true);
        data.setKeepIdentity(false);
        PlayerEvents.refreshUnlocks(player);
        data.save(player);
        PlayerFormState.broadcast(player);
        MadokaNetwork.syncTo(player);
        player.displayClientMessage(Component.translatable("message.madoka_magic.witch_power_inherited"), false);
    }

    /** 变身时回收旧武器再发放契约武器，避免背包里叠加多把。 */
    private static void giveWeapon(ServerPlayer player, MagicalGirl theme) {
        removeWeapon(player);
        var weapon = ContractWeapon.forTheme(theme.getId());
        if (weapon == null) {
            return;
        }
        var stack = new ItemStack(io.madoka.magic.registry.ModItems.CONTRACT_WEAPONS.get(weapon).get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** 解除变身时回收背包（含副手）里的契约武器。 */
    private static void removeWeapon(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (ContractWeapon.of(inventory.getItem(i)) != null) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    /** Rebuild temporary modifiers on login, respawn and form changes. */
    public static void applyAttributes(ServerPlayer player) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        MagicalGirl theme = data.getTheme();
        boolean active = data.isTransformed() && theme != null;
        if (data.isWitchPath() && data.getDespair() < 0.0D) {
            data.setDespair(MadokaMath.progression(player));
        }
        var witch = active && data.isWitchPath() ? theme.getWitchForm() : null;
        modifier(player, Attributes.MAX_HEALTH, witch != null ? witch.health() : active ? MadokaConfig.FORM_HEALTH_BONUS.get() : 0);
        modifier(player, Attributes.ATTACK_DAMAGE, witch != null ? witch.attack() : active ? MadokaConfig.FORM_ATTACK_BONUS.get() : 0);
        modifier(player, Attributes.ARMOR, witch != null ? witch.armor() : active ? MadokaConfig.FORM_ARMOR_BONUS.get() : 0);
        modifier(player, Attributes.MOVEMENT_SPEED, witch != null ? witch.speed() : 0);
        var mana = player.getAttribute(MadokaAttributes.MAGIC_MANA.get());
        if (mana != null) {
            // Old versions persisted the theme capacity as a base value.
            mana.setBaseValue(0.0D);
        }
        modifier(player, MadokaAttributes.MAGIC_MANA.get(), active ? theme.getManaMax() : 0.0D);
        updateGrowth(player, data);
        IronsCompat.removeSchoolPower(player);
        if (active) {
            IronsCompat.applySchoolPower(player, theme);
            BlossomState.applyWeaken(player, !data.isWitchPath() && data.isWeakened());
        } else {
            BlossomState.applyWeaken(player, false);
        }
        SelfishEye.refreshForm(player);
        clampResources(player, data);
        data.save(player);
    }

    public static void refreshGrowth(ServerPlayer player, PlayerMadokaData data) {
        if (updateGrowth(player, data)) {
            clampResources(player, data);
            data.save(player);
        }
    }

    private static boolean updateGrowth(ServerPlayer player, PlayerMadokaData data) {
        int steps = MadokaMath.growthSteps(player, data);
        var bias = MadokaMath.growthBias(data);
        double health = steps * MadokaConfig.GROWTH_HEALTH.get()
                * (data.isWitchPath() ? MadokaConfig.WITCH_GROWTH_HEALTH_MULTIPLIER.get() : 1.0D);
        boolean changed = growthModifier(player, Attributes.MAX_HEALTH, health, AttributeModifier.Operation.MULTIPLY_TOTAL);
        changed |= growthModifier(player, MadokaAttributes.MAGIC_POWER.get(), steps * MadokaConfig.GROWTH_MAGIC_POWER.get(),
                AttributeModifier.Operation.ADDITION);
        changed |= growthModifier(player, MadokaAttributes.MAGIC_MANA.get(), steps * MadokaConfig.GROWTH_MANA.get(),
                AttributeModifier.Operation.ADDITION);
        changed |= growthModifier(player, Attributes.MOVEMENT_SPEED, steps * bias.speedPerStep(),
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        changed |= growthModifier(player, Attributes.ARMOR, steps * bias.armorPerStep(), AttributeModifier.Operation.ADDITION);
        return changed;
    }

    private static boolean growthModifier(ServerPlayer player, Attribute attribute, double bonus, AttributeModifier.Operation operation) {
        var instance = player.getAttribute(attribute);
        if (instance == null) {
            return false;
        }
        var current = instance.getModifier(GROWTH_MODIFIER);
        if (current == null ? bonus == 0.0D : current.getAmount() == bonus) {
            return false;
        }
        instance.removeModifier(GROWTH_MODIFIER);
        if (bonus != 0.0D) {
            instance.addTransientModifier(new AttributeModifier(GROWTH_MODIFIER, "madoka_magic:growth", bonus, operation));
        }
        return true;
    }

    private static void clampResources(ServerPlayer player, PlayerMadokaData data) {
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        data.setMana(Math.min(data.getMana(), MadokaMath.manaMax(player)));
    }

    private static void modifier(ServerPlayer player, Attribute attribute, double bonus) {
        var instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(FORM_MODIFIER);
        if (bonus != 0.0D) {
            instance.addTransientModifier(new AttributeModifier(FORM_MODIFIER, "madoka_magic:transformation", bonus,
                    AttributeModifier.Operation.ADDITION));
        }
    }
}

package io.madoka.magic.event;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.item.ContractWeapon;
import io.madoka.magic.spell.SpellCasting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class ContractWeaponEvents {
    public enum Input { START, RELEASE, CANCEL }

    private static final UUID WEAPON_MODIFIER = UUID.fromString("be0c82ad-e76d-43c3-9fa1-68f965eb6dd9");
    private static final Map<ServerPlayer, Use> USES = new WeakHashMap<>();

    private static final class Use {
        final ContractWeapon weapon;
        final ServerLevel level;
        final int slot;
        final long startedAt;
        boolean guarding = true;
        boolean blocked;
        long counterUntil;
        int attackTarget;
        long attackTick;

        Use(ServerPlayer player, ContractWeapon weapon) {
            this.weapon = weapon;
            level = player.serverLevel();
            slot = player.getInventory().selected;
            startedAt = level.getGameTime();
        }

        boolean stillHeld(ServerPlayer player, ContractWeapon held) {
            return held == weapon && player.getOffhandItem().isEmpty()
                    && slot == player.getInventory().selected && level == player.serverLevel();
        }
    }

    private ContractWeaponEvents() {}

    private static ContractWeapon held(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || !player.getMainHandItem().isEmpty()) {
            return null;
        }
        var data = PlayerMadokaData.of(player);
        return data.isContracted() && data.isTransformed() && !data.isWitchPath()
                ? ContractWeapon.forTheme(data.getThemeId()) : null;
    }

    public static void handle(ServerPlayer player, Input input) {
        ContractWeapon weapon = held(player);
        Use use = USES.get(player);
        if (input == Input.CANCEL) {
            USES.remove(player);
            return;
        }
        if (weapon == null || !player.getOffhandItem().isEmpty() || player.isUsingItem()
                || player.containerMenu != player.inventoryMenu) {
            USES.remove(player);
            return;
        }
        if (input == Input.START) {
            if (use == null || !use.guarding || !use.stillHeld(player, weapon)) {
                USES.put(player, new Use(player, weapon));
            }
            return;
        }
        if (use == null || !use.guarding || !use.stillHeld(player, weapon)) {
            return;
        }
        if (weapon == ContractWeapon.THORN && use.blocked) {
            use.guarding = false;
            use.counterUntil = use.level.getGameTime() + MadokaConfig.WEAPON_COUNTER_TICKS.get();
            player.displayClientMessage(Component.translatable("message.madoka_magic.weapon.counter_ready"), true);
            return;
        }
        USES.remove(player);
        if (weapon.spell != null && use.level.getGameTime() - use.startedAt >= MadokaConfig.weapon(weapon).chargeTicks().get()) {
            if (!PlayerMadokaData.of(player).hasSpell(weapon.spell)) {
                player.displayClientMessage(Component.translatable("message.madoka_magic.weapon.skill_locked"), true);
                return;
            }
            SpellCasting.castFromClient(player, weapon.spell, false);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        ContractWeapon weapon = held(player);
        updateAttributes(player, weapon);
        Use use = USES.get(player);
        if (use != null && (!use.stillHeld(player, weapon) || player.containerMenu != player.inventoryMenu
                || !use.guarding && use.level.getGameTime() >= use.counterUntil)) {
            USES.remove(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ContractWeapon weapon = held(player);
        updateAttributes(player, weapon);
        Use use = USES.get(player);
        if (use != null && use.stillHeld(player, weapon) && weapon == ContractWeapon.THORN) {
            if (use.guarding) {
                event.setCanceled(true);
            } else if (use.level.getGameTime() < use.counterUntil) {
                use.attackTarget = event.getTarget().getId();
                use.attackTick = use.level.getGameTime();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void guard(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0
                || event.getSource().is(DamageTypeTags.BYPASSES_SHIELD)
                || event.getSource().getSourcePosition() == null) {
            return;
        }
        Use use = USES.get(player);
        if (use == null || !use.guarding || use.weapon != ContractWeapon.THORN || !use.stillHeld(player, held(player))) {
            return;
        }
        Vec3 source = event.getSource().getSourcePosition().subtract(player.position()).multiply(1, 0, 1);
        Vec3 facing = player.getViewVector(1).multiply(1, 0, 1);
        if (source.dot(facing) > 0) {
            event.setAmount(event.getAmount() * (1.0F - MadokaConfig.WEAPON_GUARD_REDUCTION.get().floatValue()));
            use.blocked = true;
            player.playSound(SoundEvents.SHIELD_BLOCK, 0.75F, 1.22F);
        }
    }

    @SubscribeEvent
    public static void counter(LivingHurtEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (!(direct instanceof ServerPlayer player) || !event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            return;
        }
        Use use = USES.get(player);
        if (use != null && !use.guarding && use.stillHeld(player, held(player))
                && use.attackTarget == event.getEntity().getId() && use.attackTick == use.level.getGameTime()
                && use.attackTick < use.counterUntil) {
            event.setAmount(event.getAmount() * MadokaConfig.WEAPON_COUNTER_MULTIPLIER.get().floatValue());
            player.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 0.85F, 0.9F);
            USES.remove(player);
        }
    }

    private static void updateAttributes(ServerPlayer player, ContractWeapon weapon) {
        var tuning = weapon == null ? null : MadokaConfig.weapon(weapon);
        replace(player.getAttribute(Attributes.ATTACK_DAMAGE), tuning == null ? 0.0D : tuning.damageBonus().get());
        replace(player.getAttribute(Attributes.ATTACK_SPEED), tuning == null ? 0.0D : tuning.speedBonus().get());
    }

    private static void replace(AttributeInstance attribute, double value) {
        if (attribute == null) {
            return;
        }
        var previous = attribute.getModifier(WEAPON_MODIFIER);
        if (previous == null ? value == 0.0D : previous.getAmount() == value) {
            return;
        }
        attribute.removeModifier(WEAPON_MODIFIER);
        if (value != 0.0D) {
            attribute.addTransientModifier(new AttributeModifier(WEAPON_MODIFIER, "madoka_magic:contract_weapon",
                    value, AttributeModifier.Operation.ADDITION));
        }
    }
}

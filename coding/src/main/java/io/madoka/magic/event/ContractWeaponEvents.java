package io.madoka.magic.event;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.item.ContractWeapon;
import io.madoka.magic.item.WeaponEngraving;
import io.madoka.magic.spell.SpellCasting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.ForgeMod;
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
    public enum Input { START, RELEASE, CANCEL, CAST }

    private static final UUID WEAPON_MODIFIER = UUID.fromString("be0c82ad-e76d-43c3-9fa1-68f965eb6dd9");
    private static final Map<ServerPlayer, Use> USES = new WeakHashMap<>();
    /** 最近一次近战挥击的游戏刻，供 restrict() 区分近战与技能伤害。 */
    private static final Map<ServerPlayer, Long> MELEE_TICKS = new WeakHashMap<>();
    /** 铭刻提示的冷却，避免连续攻击时刷屏。 */
    private static final Map<ServerPlayer, Long> ENGAGE_HINTS = new WeakHashMap<>();
    private static final int ENGAGE_HINT_COOLDOWN = 1200;

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
        if (!player.isAlive() || player.isSpectator()) {
            return null;
        }
        ContractWeapon held = ContractWeapon.of(player.getMainHandItem());
        if (held == null) {
            return null;
        }
        var data = PlayerMadokaData.of(player);
        return data.isContracted() && data.isTransformed()
                && held == ContractWeapon.forTheme(data.getThemeId()) ? held : null;
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
        var data = PlayerMadokaData.of(player);
        String spell = weapon.spell(data.getTheme(), data.isWitchPath());
        if (input == Input.CAST || data.isWitchPath() && input == Input.START) {
            // 折页/魔女途：右键直接施放当前形态技能列表首个技能（可无目标空放），不需要蓄力
            USES.remove(player);
            if (spell == null) {
                return;
            }
            if (!data.hasSpell(spell)) {
                player.displayClientMessage(Component.translatable("message.madoka_magic.weapon.skill_locked"), true);
                return;
            }
            SpellCasting.castFromClient(player, spell, false);
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
        if (spell != null && use.level.getGameTime() - use.startedAt >= MadokaConfig.weapon(weapon).chargeTicks().get()) {
            if (!data.hasSpell(spell)) {
                player.displayClientMessage(Component.translatable("message.madoka_magic.weapon.skill_locked"), true);
                return;
            }
            SpellCasting.castFromClient(player, spell, false);
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

    /**
     * 契约武器左键必横扫：原版横扫要求满蓄力、在地面、未疾跑、未暴击，
     * 条件不满足时（跳劈、疾跑、连点）由这里补一段横扫，效果与原版一致
     * （1 点范围伤害 + 横扫粒子/音效），命中主目标时不再重复结算。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void sweep(AttackEntityEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof LivingEntity target) || held(player) == null) {
            return;
        }
        boolean vanillaWillSweep = player.onGround() && !player.isSprinting() && !player.isPassenger()
                && !player.isFallFlying() && player.fallDistance <= 0.0F
                && !player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                && player.getAttackStrengthScale(0.5F) > 0.9F;
        if (vanillaWillSweep) {
            return;
        }
        boolean swept = false;
        for (LivingEntity neighbor : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(1.0D, 0.25D, 1.0D),
                e -> e != player && e != target && e.isAlive() && !e.isSpectator()
                        && !(e instanceof net.minecraft.world.entity.player.Player other
                                && (other.isCreative() || other.isSpectator()))
                        && !player.isAlliedTo(e))) {
            neighbor.hurt(player.damageSources().playerAttack(player), 1.0F);
            swept = true;
        }
        if (swept) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void guard(LivingDamageEvent event) {
        if (io.madoka.magic.spell.OriginalCurseEffects.isSettlement(event.getSource())) return;
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

    @SubscribeEvent
    public static void markMelee(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MELEE_TICKS.put(player, player.serverLevel().getGameTime());
        }
    }

    /**
     * 变身态玩家（魔法少女/魔女）用非伴生、非铭刻的武器攻击时，
     * 近战与原版弹射物一律只造成 1 点强制伤害。技能伤害与自定义技能
     * 射弹（mod 命名空间）不经过此限制。
     *
     * <p>放在 LOWEST：TaintEvents 等倍率结算都在 NORMAL，先让它们乘完，
     * 最后再把结果压回 1 点，否则 1 点会被伤害倍率重新放大。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void restrict(LivingHurtEvent event) {
        if (restricted(event.getSource())) {
            event.setAmount(1.0F);
            if (event.getSource().getEntity() instanceof ServerPlayer player) {
                engageHint(player);
            }
        }
    }

    /** 用未铭刻武器打出强制 1 点时，按三系各给一句提示（60 秒冷却）。 */
    private static void engageHint(ServerPlayer player) {
        String theme = PlayerMadokaData.of(player).getThemeId();
        if (!"sacrifice".equals(theme) && !"take".equals(theme) && !"atonement".equals(theme)) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        Long last = ENGAGE_HINTS.get(player);
        if (last != null && now - last < ENGAGE_HINT_COOLDOWN) {
            return;
        }
        ENGAGE_HINTS.put(player, now);
        // 走聊天栏：actionbar 会被试验假人的 DPS 显示顶掉
        player.displayClientMessage(
                Component.translatable("message.madoka_magic.engage.hint." + theme), false);
    }

    /** 护甲/附魔减伤在 LivingHurtEvent 之后结算，这里再兜底一次保证最终就是 1 点。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void restrictFinal(LivingDamageEvent event) {
        if (restricted(event.getSource())) {
            event.setAmount(1.0F);
        }
    }

    private static boolean restricted(net.minecraft.world.damagesource.DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !PlayerMadokaData.of(player).isTransformed()) {
            return false;
        }
        Entity direct = source.getDirectEntity();
        // 近战 playerAttack 的直接来源就是玩家自己（不是 null）
        boolean melee = direct == player && source.is(DamageTypes.PLAYER_ATTACK)
                && Long.valueOf(player.serverLevel().getGameTime()).equals(MELEE_TICKS.get(player));
        boolean vanillaProjectile = direct != null && "minecraft".equals(
                BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getNamespace());
        if (!melee && !vanillaProjectile) {
            return false;
        }
        if (melee) {
            return ContractWeapon.of(player.getMainHandItem()) == null
                    && !WeaponEngraving.isEngraved(player.getMainHandItem());
        }
        return ContractWeapon.of(player.getMainHandItem()) == null
                && ContractWeapon.of(player.getOffhandItem()) == null
                && !WeaponEngraving.isEngraved(player.getMainHandItem())
                && !WeaponEngraving.isEngraved(player.getOffhandItem());
    }

    private static void updateAttributes(ServerPlayer player, ContractWeapon weapon) {
        var tuning = weapon == null ? null : MadokaConfig.weapon(weapon);
        replace(player.getAttribute(Attributes.ATTACK_DAMAGE), tuning == null ? 0.0D : tuning.damageBonus().get());
        replace(player.getAttribute(Attributes.ATTACK_SPEED), tuning == null ? 0.0D : tuning.speedBonus().get());
        replace(player.getAttribute(ForgeMod.ENTITY_REACH.get()), tuning == null ? 0.0D : tuning.reachBonus().get());
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

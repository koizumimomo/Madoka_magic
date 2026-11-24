package io.madoka.magic.spell;

import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class WitchPlayerSpells {
    private static final Map<ServerPlayer, Rain> RAIN = new WeakHashMap<>();

    private record Rain(ServerLevel level, Vec3 center, SpellDefinition spell, long nextPulse, int pulses) {}

    private WitchPlayerSpells() {}

    static void register() {
        SpellCasting.registerEffect("madoka_magic:witch_black_wings", (player, spell, reward) -> blackWings(player, spell));
        SpellCasting.registerEffect("madoka_magic:witch_cocoon_burst", (player, spell, reward) -> cocoonBurst(player, spell));
        SpellCasting.registerEffect("madoka_magic:witch_venom", (player, spell, reward) -> venom(player, spell));
        SpellCasting.registerEffect("madoka_magic:witch_honey_tide", (player, spell, reward) -> honeyTide(player, spell));
        SpellCasting.registerEffect("madoka_magic:witch_tear_rain", (player, spell, reward) -> startRain(player, spell));
        SpellCasting.registerEffect("madoka_magic:witch_thorn_echo", (player, spell, reward) -> thornEcho(player, spell));
    }

    /**
     * 与蝶翼斩一致的目标选取：范围内所有非玩家存活目标（含测试假人盔甲架），
     * 不做额外视线射线过滤——旧写法的 clip 判定会漏掉盔甲架。
     */
    private static List<LivingEntity> targets(ServerPlayer caster, Vec3 center, double radius, double height) {
        var level = caster.serverLevel();
        var bounds = new AABB(center, center).inflate(radius, height, radius);
        return level.getEntitiesOfClass(LivingEntity.class, bounds,
                target -> !(target instanceof Player) && SpellCasting.canAffect(caster, target));
    }

    private static boolean hit(ServerPlayer caster, LivingEntity target, double damage) {
        return target.hurt(caster.damageSources().playerAttack(caster), (float) MadokaMath.finalDamage(caster, damage));
    }

    private static int blackWings(ServerPlayer caster, SpellDefinition spell) {
        ServerLevel level = caster.serverLevel();
        double range = SpellCasting.range(spell.parameter("range"));
        double halfAngle = Math.toRadians(Math.min(180.0D, spell.parameter("angle_degrees")) / 2.0D);
        double vertical = SpellCasting.range(spell.parameter("vertical_reach"));
        double yaw = Math.toRadians(caster.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        double cosine = Math.cos(halfAngle);
        float damage = (float) MadokaMath.finalDamage(caster, spell.parameter("damage"));
        boolean hit = false;
        AABB bounds = caster.getBoundingBox().inflate(range, vertical, range);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds,
                e -> !(e instanceof Player) && SpellCasting.canAffect(caster, e))) {
            Vec3 offset = target.position().subtract(caster.position()).multiply(1.0D, 0.0D, 1.0D);
            double distance = offset.length();
            if (distance <= range && forward.dot(offset) >= distance * cosine && caster.hasLineOfSight(target)) {
                hit |= target.hurt(level.damageSources().playerAttack(caster), damage);
            }
        }
        WitchVisuals.slash(caster, forward, range, halfAngle);
        caster.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.9F, 0.7F);
        return SpellCasting.RESULT_SUCCESS;
    }

    private static int cocoonBurst(ServerPlayer caster, SpellDefinition spell) {
        var level = caster.serverLevel();
        long now = level.getGameTime();
        for (LivingEntity target : targets(caster, caster.position(), SpellCasting.range(spell.parameter("radius")),
                SpellCasting.range(spell.parameter("vertical_reach")))) {
            int stacks = ButterflyMark.getLayers(target, caster.getUUID(), now);
            if (stacks > 0 && hit(caster, target, spell.parameter("damage") + stacks * spell.parameter("damage_per_mark"))) {
                ButterflyMark.clear(target);
                WitchVisuals.cocoonBurst(level, target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D));
            }
        }
        // 没有带蝶印的目标也照常释放（空放特效），不返还失败
        return SpellCasting.RESULT_SUCCESS;
    }

    private static int venom(ServerPlayer caster, SpellDefinition spell) {
        double range = SpellCasting.range(spell.parameter("range"));
        LivingEntity target = SpellCasting.aimedMob(caster, range, e -> true);
        if (target != null && hit(caster, target, spell.parameter("damage"))) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, spell.ticks("poison_seconds")), caster);
            WitchVisuals.sting(caster, target);
        } else {
            // 无目标空放：射线打向前方空气
            WitchVisuals.sting(caster, caster.getEyePosition().add(caster.getLookAngle().scale(range)));
        }
        return SpellCasting.RESULT_SUCCESS;
    }

    private static int honeyTide(ServerPlayer caster, SpellDefinition spell) {
        Vec3 center = caster.position();
        double radius = SpellCasting.range(spell.parameter("radius"));
        for (LivingEntity target : targets(caster, center, radius, SpellCasting.range(spell.parameter("vertical_reach")))) {
            if (hit(caster, target, spell.parameter("damage"))) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        spell.ticks("slow_seconds"), (int) spell.parameter("slow_amplifier")), caster);
            }
        }
        WitchVisuals.field(caster.serverLevel(), center, radius, caster.level().getGameTime(), true);
        caster.playSound(SoundEvents.HONEY_BLOCK_SLIDE, 0.9F, 0.8F);
        return SpellCasting.RESULT_SUCCESS;
    }

    private static int startRain(ServerPlayer caster, SpellDefinition spell) {
        var level = caster.serverLevel();
        RAIN.put(caster, new Rain(level, caster.position(), spell, level.getGameTime(), (int) spell.parameter("pulses")));
        int interval = Math.max(11, (int) spell.parameter("pulse_interval_ticks"));
        int pulses = (int) spell.parameter("pulses");
        var center = caster.position();
        io.madoka.magic.network.MadokaNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> caster),
                new io.madoka.magic.network.MadokaNetwork.RainCircleState(true, center.x, center.y, center.z,
                        (float) SpellCasting.range(spell.parameter("radius")), pulses * interval + 10));
        caster.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.58F);
        return SpellCasting.RESULT_SUCCESS;
    }

    public static void tick(ServerPlayer caster) {
        Rain rain = RAIN.get(caster);
        if (rain == null) {
            return;
        }
        var data = PlayerMadokaData.of(caster);
        if (caster.level() != rain.level() || !caster.isAlive() || caster.isSpectator()
                || !data.isTransformed() || !data.isWitchPath() || rain.pulses() <= 0) {
            clear(caster);
            return;
        }
        long now = rain.level().getGameTime();
        if (now < rain.nextPulse()) {
            return;
        }
        var center = rain.center();
        var spell = rain.spell();
        double radius = SpellCasting.range(spell.parameter("radius"));
        WitchVisuals.tearRain(rain.level(), center, radius);
        for (LivingEntity target : targets(caster, center, radius, SpellCasting.range(spell.parameter("vertical_reach")))) {
            if (hit(caster, target, spell.parameter("damage"))) {
                WitchVisuals.tearHit(rain.level(), target.position().add(0.0D, target.getBbHeight() * 0.8D, 0.0D));
            }
        }
        if (rain.pulses() == 1) {
            clear(caster);
        } else {
            RAIN.put(caster, new Rain(rain.level(), center, spell,
                    now + Math.max(11, (long) spell.parameter("pulse_interval_ticks")), rain.pulses() - 1));
        }
    }

    public static void clear(ServerPlayer caster) {
        if (RAIN.remove(caster) != null && caster.connection != null) {
            io.madoka.magic.network.MadokaNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> caster),
                    new io.madoka.magic.network.MadokaNetwork.RainCircleState(false, 0.0D, 0.0D, 0.0D, 0.0F, 0));
        }
    }

    static LivingEntity thornTarget(ServerPlayer caster, SpellDefinition spell) {
        double radius = SpellCasting.range(spell.parameter("radius"));
        return targets(caster, caster.position(), radius, SpellCasting.range(spell.parameter("vertical_reach"))).stream()
                .filter(target -> GuiltMark.getAmount(target, caster.getUUID()) > 0)
                .max(Comparator.comparingDouble(target -> GuiltMark.getAmount(target, caster.getUUID()))).orElse(null);
    }

    private static int thornEcho(ServerPlayer caster, SpellDefinition spell) {
        LivingEntity target = thornTarget(caster, spell);
        if (target != null) {
            float guilt = GuiltMark.getAmount(target, caster.getUUID());
            if (!hit(caster, target, spell.parameter("damage") + guilt * spell.parameter("damage_per_guilt"))) {
                return SpellCasting.RESULT_NOT_READY;
            }
            GuiltMark.consume(target, caster.getUUID());
            WitchVisuals.thornEcho(caster.serverLevel(), target.position().add(0.0D, target.getBbHeight() * 0.35D, 0.0D));
            caster.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 0.85F, 0.65F);
            return SpellCasting.RESULT_SUCCESS;
        }
        // 无目标空放：荆棘回响打向前方空气
        Vec3 end = caster.getEyePosition().add(caster.getLookAngle().scale(SpellCasting.range(spell.parameter("radius"))));
        WitchVisuals.thornEcho(caster.serverLevel(), end);
        return SpellCasting.RESULT_SUCCESS;
    }
}

package io.madoka.magic.spell;

import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.faction.Faction;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
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

    private static boolean enemy(LivingEntity entity) {
        return entity instanceof Player || entity instanceof Enemy || Faction.isFamiliar(entity);
    }

    private static List<LivingEntity> targets(ServerPlayer caster, Vec3 center, double radius, double height) {
        var level = caster.serverLevel();
        Vec3 eye = center.add(0, caster.getEyeHeight(), 0);
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, height, radius),
                target -> enemy(target) && SpellCasting.canAffect(caster, target)
                        && target.position().subtract(center).horizontalDistanceSqr() <= radius * radius
                        && level.clip(new ClipContext(eye, target.getEyePosition(), ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE, caster)).getType() == HitResult.Type.MISS);
    }

    private static boolean hit(ServerPlayer caster, LivingEntity target, double damage) {
        return target.hurt(caster.damageSources().playerAttack(caster), (float) MadokaMath.finalDamage(caster, damage));
    }

    private static int blackWings(ServerPlayer caster, SpellDefinition spell) {
        Vec3 origin = caster.position();
        double angle = Math.toRadians(spell.parameter("angle_degrees")) / 2.0D;
        double range = SpellCasting.range(spell.parameter("range"));
        double yaw = Math.toRadians(caster.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        for (LivingEntity target : targets(caster, origin, range, SpellCasting.range(spell.parameter("vertical_reach")))) {
            Vec3 direction = target.position().subtract(origin).multiply(1, 0, 1);
            if (direction.dot(forward) >= direction.length() * Math.cos(angle)) {
                hit(caster, target, spell.parameter("damage"));
            }
        }
        DevotionVisuals.slash(caster, forward, range, angle);
        caster.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.9F, 0.7F);
        return SpellCasting.RESULT_SUCCESS;
    }

    private static int cocoonBurst(ServerPlayer caster, SpellDefinition spell) {
        var level = caster.serverLevel();
        long now = level.getGameTime();
        boolean hit = false;
        for (LivingEntity target : targets(caster, caster.position(), SpellCasting.range(spell.parameter("radius")),
                SpellCasting.range(spell.parameter("vertical_reach")))) {
            int stacks = ButterflyMark.getLayers(target, caster.getUUID(), now);
            if (stacks > 0 && hit(caster, target, spell.parameter("damage") + stacks * spell.parameter("damage_per_mark"))) {
                hit = true;
                ButterflyMark.clear(target);
                level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + target.getBbHeight() / 2,
                        target.getZ(), 12, 0.4D, 0.55D, 0.4D, 0.04D);
            }
        }
        return hit ? SpellCasting.RESULT_SUCCESS : SpellCasting.RESULT_NO_TARGET;
    }

    private static int venom(ServerPlayer caster, SpellDefinition spell) {
        LivingEntity target = SpellCasting.aimedTarget(caster, spell.parameter("range"), WitchPlayerSpells::enemy);
        if (target == null || !hit(caster, target, spell.parameter("damage"))) {
            return SpellCasting.RESULT_NO_TARGET;
        }
        target.addEffect(new MobEffectInstance(MobEffects.POISON, spell.ticks("poison_seconds")), caster);
        TakeVisuals.sting(caster, target);
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
        TakeVisuals.honeyField(caster.serverLevel(), center, radius, caster.level().getGameTime(), true);
        caster.playSound(SoundEvents.HONEY_BLOCK_SLIDE, 0.9F, 0.8F);
        return SpellCasting.RESULT_SUCCESS;
    }

    private static int startRain(ServerPlayer caster, SpellDefinition spell) {
        var level = caster.serverLevel();
        RAIN.put(caster, new Rain(level, caster.position(), spell, level.getGameTime(), (int) spell.parameter("pulses")));
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
        rain.level().sendParticles(ParticleTypes.FALLING_WATER, center.x, center.y + 4, center.z,
                55, radius * 0.45D, 0.6D, radius * 0.45D, 0);
        for (LivingEntity target : targets(caster, center, radius, SpellCasting.range(spell.parameter("vertical_reach")))) {
            if (hit(caster, target, spell.parameter("damage"))) {
                rain.level().sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + 1,
                        target.getZ(), 7, 0.35D, 0.5D, 0.35D, 0.03D);
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
        RAIN.remove(caster);
    }

    static LivingEntity thornTarget(ServerPlayer caster, SpellDefinition spell) {
        double radius = SpellCasting.range(spell.parameter("radius"));
        return targets(caster, caster.position(), radius, SpellCasting.range(spell.parameter("vertical_reach"))).stream()
                .filter(target -> GuiltMark.getAmount(target, caster.getUUID()) > 0)
                .max(Comparator.comparingDouble(target -> GuiltMark.getAmount(target, caster.getUUID()))).orElse(null);
    }

    private static int thornEcho(ServerPlayer caster, SpellDefinition spell) {
        LivingEntity target = thornTarget(caster, spell);
        if (target == null) {
            return SpellCasting.RESULT_NO_TARGET;
        }
        float guilt = GuiltMark.getAmount(target, caster.getUUID());
        if (!hit(caster, target, spell.parameter("damage") + guilt * spell.parameter("damage_per_guilt"))) {
            return SpellCasting.RESULT_NOT_READY;
        }
        GuiltMark.consume(target, caster.getUUID());
        caster.serverLevel().sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 0.8D, target.getZ(),
                18, 0.32D, 0.8D, 0.32D, 0.03D);
        caster.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 0.85F, 0.65F);
        return SpellCasting.RESULT_SUCCESS;
    }
}

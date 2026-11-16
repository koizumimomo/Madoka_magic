package io.madoka.magic.entity;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.faction.FamiliarVisibility;
import io.madoka.magic.faction.WitchFamiliars;
import io.madoka.magic.registry.ModEntities;
import io.madoka.magic.spell.ThornTracker;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

public final class GluttonyWitchEntity extends Monster {
    public static final float BODY_WIDTH = 4.16F;
    public static final float FRAGMENT_SCALE = 0.375F;
    private static final int ATTACK_RECOVERY = 28;
    private static final int SPLIT_TICKS = 120;
    private static final int MAX_AIR_TICKS = 60;
    private static final float SPLIT_HEALTH_RATIO = 0.5F;
    private static final float SWEEP_HALF_ANGLE = 65.0F;
    private static final double SWEEP_DOT = Math.cos(Math.toRadians(SWEEP_HALF_ANGLE));
    private static final int ARC_SAMPLES = 16;
    private static final double LEAP_TRAVEL_FACTOR = 9.0D;
    private static final double LEAP_MAX_DISTANCE = 10.5D;
    private static final DustParticleOptions TELEGRAPH = new DustParticleOptions(new Vector3f(0.59F, 0.91F, 0.31F), 1.35F);

    private static final EntityDataAccessor<Boolean> FRAGMENT = SynchedEntityData.defineId(
            GluttonyWitchEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(
            GluttonyWitchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(
            GluttonyWitchEntity.class, EntityDataSerializers.LONG);

    public enum Phase {
        HUNT(ATTACK_RECOVERY), SWEEP(24), LEAP(20), AIRBORNE(MAX_AIR_TICKS), SPLIT(SPLIT_TICKS);

        public final int ticks;

        Phase(int ticks) {
            this.ticks = ticks;
        }
    }

    private static final Phase[] PHASES = Phase.values();

    private final WitchBossBar bossBar = new WitchBossBar();
    private UUID fragmentOf;
    private long fragmentExpires;
    private boolean splitUsed;
    private boolean leapNext;
    private float sweepYaw;
    private Vec3 leapTarget = Vec3.ZERO;

    public GluttonyWitchEntity(EntityType<? extends GluttonyWitchEntity> type, Level level) {
        super(type, level);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(MadokaConfig.WITCH_HEALTH.get());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(MadokaConfig.WITCH_DAMAGE.get());
        setHealth(getMaxHealth());
        xpReward = MadokaConfig.WITCH_XP.get();
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, ServerPlayer.class, true, this::canFight));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(FRAGMENT, false);
        entityData.define(PHASE, Phase.HUNT.ordinal());
        entityData.define(PHASE_START, 0L);
    }

    public boolean isFragment() {
        return entityData.get(FRAGMENT);
    }

    public Phase phase() {
        return PHASES[entityData.get(PHASE)];
    }

    public float phaseProgress(float partialTick) {
        return Mth.clamp((phaseAge() + partialTick) / phase().ticks, 0.0F, 1.0F);
    }

    private long phaseAge() {
        return level().getGameTime() - entityData.get(PHASE_START);
    }

    private void begin(Phase phase) {
        entityData.set(PHASE, phase.ordinal());
        entityData.set(PHASE_START, level().getGameTime());
    }

    public boolean interruptWindup() {
        if (isFragment() || phase() != Phase.SWEEP && phase() != Phase.LEAP) {
            return false;
        }
        begin(Phase.HUNT);
        return true;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        var size = super.getDimensions(pose);
        return isFragment() || phase() == Phase.SPLIT ? size.scale(FRAGMENT_SCALE) : size;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (FRAGMENT.equals(key) || PHASE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public Component getName() {
        return isFragment() ? Component.translatable("entity.madoka_magic.gluttony_witch.fragment") : super.getName();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (!isFragment()) {
            bossBar.tick(this);
        }
        if (!isAlive() || isNoAi()) {
            return;
        }
        if (isFragment()) {
            tickFragment(server);
        } else {
            ServerPlayer target = getTarget() instanceof ServerPlayer player && canFight(player) ? player : null;
            tickFight(server, target);
        }
    }

    private boolean canFight(LivingEntity candidate) {
        if (!(candidate instanceof ServerPlayer player) || !player.isAlive() || player.isCreative()
                || player.isSpectator() || !FamiliarVisibility.canWitchTarget(player)) {
            return false;
        }
        return WitchFamiliars.isEncounterVisitor(this, player);
    }

    private void tickFight(ServerLevel server, ServerPlayer target) {
        long age = phaseAge();
        switch (phase()) {
            case HUNT -> {
                if (target == null || ThornTracker.isBound(this)) {
                    return;
                }
                if (!splitUsed && getHealth() <= getMaxHealth() * SPLIT_HEALTH_RATIO && onGround()) {
                    split(server);
                } else if (age >= ATTACK_RECOVERY && onGround() && distanceToSqr(target) <= 144.0D) {
                    setDeltaMovement(0, getDeltaMovement().y, 0);
                    if (leapNext || distanceToSqr(target) > Mth.square(MadokaConfig.GLUTTONY_SWEEP_RADIUS.get())) {
                        Vec3 direction = horizontalTo(target);
                        leapTarget = position().add(direction.normalize().scale(Math.min(LEAP_MAX_DISTANCE, direction.length())));
                        begin(Phase.LEAP);
                        leapNext = false;
                    } else {
                        sweepYaw = yawTo(target);
                        setYRot(sweepYaw);
                        yBodyRot = sweepYaw;
                        begin(Phase.SWEEP);
                        leapNext = true;
                    }
                    playSound(SoundEvents.SLIME_SQUISH, 1.2F, 0.65F);
                } else {
                    hopToward(target, 0.33D);
                }
            }
            case SWEEP -> {
                if (age >= Phase.SWEEP.ticks) {
                    strike(server, MadokaConfig.GLUTTONY_SWEEP_RADIUS.get(), 1.0F, true);
                } else if (age % 6 == 0) {
                    showEdge(server, position(), TELEGRAPH, MadokaConfig.GLUTTONY_SWEEP_RADIUS.get(),
                            sweepYaw - SWEEP_HALF_ANGLE, SWEEP_HALF_ANGLE * 2.0F, ARC_SAMPLES);
                }
            }
            case LEAP -> {
                if (age >= Phase.LEAP.ticks) {
                    Vec3 direction = new Vec3(leapTarget.x - getX(), 0, leapTarget.z - getZ());
                    setDeltaMovement(direction.scale(1.0D / LEAP_TRAVEL_FACTOR).add(0, 0.76D, 0));
                    hasImpulse = true;
                    begin(Phase.AIRBORNE);
                    playSound(SoundEvents.SLIME_JUMP, 1.3F, 0.7F);
                } else if (age % 8 == 0) {
                    showEdge(server, leapTarget, TELEGRAPH, MadokaConfig.GLUTTONY_SLAM_RADIUS.get(),
                            0, 360.0F, ARC_SAMPLES);
                }
            }
            case AIRBORNE -> {
                if (age > 3 && onGround()) {
                    strike(server, MadokaConfig.GLUTTONY_SLAM_RADIUS.get(), 1.35F, false);
                } else if (age >= MAX_AIR_TICKS) {
                    begin(Phase.HUNT);
                }
            }
            case SPLIT -> {
                if (age >= SPLIT_TICKS) {
                    begin(Phase.HUNT);
                    server.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 1.0D, getZ(),
                            28, 1.4D, 0.7D, 1.4D, 0.12D);
                } else if (target != null) {
                    hopToward(target, 0.31D);
                }
            }
        }
    }

    private static void showEdge(ServerLevel server, Vec3 origin, ParticleOptions particle, double radius,
                                 float start, float arc, int samples) {
        int intervals = arc == 360.0F ? samples : samples - 1;
        for (int i = 0; i < samples; i++) {
            Vec3 point = origin.add(forward(start + i * arc / intervals).scale(radius)).add(0, 0.22D, 0);
            server.sendParticles(particle, point.x, point.y, point.z, 1, 0, 0, 0, 0.08D);
        }
    }

    private void strike(ServerLevel server, double radius, float damageMultiplier, boolean sweep) {
        Vec3 facing = forward(sweepYaw);
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * damageMultiplier;
        for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class,
                getBoundingBox().inflate(radius, 2.0D, radius), this::canFight)) {
            Vec3 direction = horizontalTo(player);
            if (direction.lengthSqr() <= radius * radius && Math.abs(player.getY() - getY()) <= 2.0D
                    && (!sweep || direction.lengthSqr() < 0.25D || direction.normalize().dot(facing) >= SWEEP_DOT)) {
                player.hurt(damageSources().mobAttack(this), damage);
            }
        }
        showEdge(server, position(), ParticleTypes.ITEM_SLIME, radius, sweep ? sweepYaw - SWEEP_HALF_ANGLE : 0,
                sweep ? SWEEP_HALF_ANGLE * 2.0F : 360.0F, ARC_SAMPLES * 2);
        playSound(SoundEvents.SLIME_SQUISH, 1.4F, sweep ? 0.85F : 0.55F);
        begin(Phase.HUNT);
    }

    private void split(ServerLevel server) {
        splitUsed = true;
        begin(Phase.SPLIT);
        for (int side : new int[]{-1, 1}) {
            var fragment = ModEntities.GLUTTONY_WITCH.get().create(server);
            if (fragment == null) {
                continue;
            }
            fragment.fragmentOf = getUUID();
            fragment.fragmentExpires = server.getGameTime() + SPLIT_TICKS;
            fragment.entityData.set(FRAGMENT, true);
            fragment.xpReward = 0;
            var state = getPersistentData();
            if (state.hasUUID(WitchFamiliars.ENCOUNTER_TAG)) {
                fragment.getPersistentData().putUUID(WitchFamiliars.ENCOUNTER_TAG, state.getUUID(WitchFamiliars.ENCOUNTER_TAG));
            }
            Vec3 point = position().add(forward(getYRot() + side * 90.0F).scale(BODY_WIDTH * 0.72D));
            fragment.moveTo(point.x, point.y, point.z, getYRot(), 0);
            server.addFreshEntity(fragment);
        }
        server.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 0.9D, getZ(), 28, 1.4D, 0.5D, 1.4D, 0.15D);
    }

    private GluttonyWitchEntity fragmentRoot(ServerLevel server) {
        return fragmentOf != null && server.getEntity(fragmentOf) instanceof GluttonyWitchEntity root
                && root.isAlive() && root.phase() == Phase.SPLIT ? root : null;
    }

    private void tickFragment(ServerLevel server) {
        var root = fragmentRoot(server);
        if (server.getGameTime() >= fragmentExpires || (root == null && tickCount > 20)) {
            discard();
        } else if (root != null && getTarget() instanceof ServerPlayer target && canFight(target)) {
            hopToward(target, 0.37D);
            if (tickCount % 14 == 0 && distanceToSqr(target) <= 2.25D) {
                target.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.45F);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!isFragment()) {
            return super.hurt(source, amount);
        }
        if (level() instanceof ServerLevel server) {
            var root = fragmentRoot(server);
            return root != null && WitchTypes.finalAttacker(source) != null && root.hurt(source, amount);
        }
        return false;
    }

    private void hopToward(ServerPlayer target, double speed) {
        if (onGround() && tickCount % 14 == 0) {
            Vec3 velocity = horizontalTo(target).normalize().scale(speed);
            setYRot(yawTo(target));
            yBodyRot = getYRot();
            setDeltaMovement(velocity.add(0, 0.41D, 0));
            hasImpulse = true;
        }
    }

    private Vec3 horizontalTo(ServerPlayer target) {
        return new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
    }

    private float yawTo(ServerPlayer target) {
        return (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private static Vec3 forward(float yaw) {
        return Vec3.directionFromRotation(0, yaw);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!isFragment()) {
            bossBar.track(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.untrack(player);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Phase", phase().name());
        tag.putLong("PhaseStart", entityData.get(PHASE_START));
        tag.putBoolean("SplitUsed", splitUsed);
        tag.putBoolean("LeapNext", leapNext);
        tag.putFloat("SweepYaw", sweepYaw);
        if (phase() == Phase.LEAP) {
            tag.putDouble("LeapX", leapTarget.x);
            tag.putDouble("LeapY", leapTarget.y);
            tag.putDouble("LeapZ", leapTarget.z);
        }
        if (fragmentOf != null) {
            tag.putUUID("FragmentOf", fragmentOf);
            tag.putLong("FragmentExpires", fragmentExpires);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        for (Phase value : PHASES) {
            if (value.name().equals(tag.getString("Phase"))) {
                entityData.set(PHASE, value.ordinal());
                break;
            }
        }
        entityData.set(PHASE_START, tag.getLong("PhaseStart"));
        splitUsed = tag.getBoolean("SplitUsed");
        leapNext = tag.getBoolean("LeapNext");
        sweepYaw = tag.getFloat("SweepYaw");
        leapTarget = new Vec3(tag.getDouble("LeapX"), tag.getDouble("LeapY"), tag.getDouble("LeapZ"));
        if (tag.hasUUID("FragmentOf")) {
            fragmentOf = tag.getUUID("FragmentOf");
            fragmentExpires = tag.getLong("FragmentExpires");
            entityData.set(FRAGMENT, true);
            xpReward = 0;
        }
    }
}

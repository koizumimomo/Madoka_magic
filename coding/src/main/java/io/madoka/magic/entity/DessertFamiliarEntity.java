package io.madoka.magic.entity;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.faction.WitchFamiliars;
import io.madoka.magic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.UUID;

public final class DessertFamiliarEntity extends Monster {
    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(DessertFamiliarEntity.class, EntityDataSerializers.INT);
    public static final int CHASE = 0;
    public static final int WINDUP = 1;
    public static final int AIR = 2;
    private static final int LIFETIME = 220;
    private static final int WINDUP_TICKS = 20;
    private static final int AIR_TIMEOUT = 36;
    private UUID witchId;
    private int changedAt;

    public DessertFamiliarEntity(EntityType<? extends DessertFamiliarEntity> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    static void spawn(ServerLevel level, MadokaWitchEntity witch, BlockPos spot) {
        var cake = ModEntities.DESSERT_FAMILIAR.get().create(level);
        if (cake == null) {
            return;
        }
        var state = witch.getPersistentData();
        if (state.hasUUID(WitchFamiliars.ENCOUNTER_TAG)) {
            cake.getPersistentData().putUUID(WitchFamiliars.ENCOUNTER_TAG, state.getUUID(WitchFamiliars.ENCOUNTER_TAG));
        }
        cake.witchId = witch.getUUID();
        cake.moveTo(Vec3.atBottomCenterOf(spot));
        cake.setPersistenceRequired();
        if (level.noCollision(cake)) {
            level.addFreshEntity(cake);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE, CHASE);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return;
        }
        if (tickCount > LIFETIME || witchId == null || !(level.getEntity(witchId) instanceof MadokaWitchEntity witch)
                || !witch.isAlive()) {
            discard();
            return;
        }
        if (phase() == CHASE) {
            if (tickCount % 5 == 0) chase(level, witch);
        } else if (phase() == WINDUP) {
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
            if (tickCount - changedAt >= WINDUP_TICKS) {
                setDeltaMovement(0, 0.45D, 0);
                hasImpulse = true;
                playSound(SoundEvents.SLIME_JUMP, 0.75F, 1.35F);
                begin(AIR);
            }
        } else if (onGround() && tickCount > changedAt + 2) {
            slam(level, witch);
            discard();
        } else if (tickCount - changedAt > AIR_TIMEOUT) {
            discard();
        }
    }

    private void chase(ServerLevel level, MadokaWitchEntity witch) {
        var target = level.players().stream().filter(player -> canHit(player, witch))
                .filter(player -> distanceToSqr(player) < 24 * 24)
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (target == null) {
            getNavigation().stop();
            return;
        }
        double radius = MadokaConfig.CANDELORO_CAKE_RADIUS.get();
        if (onGround() && distanceToSqr(target) <= radius * radius && hasLineOfSight(target)) {
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            begin(WINDUP);
            playSound(SoundEvents.CANDLE_AMBIENT, 1.0F, 1.5F);
        } else {
            getNavigation().moveTo(target, 1.2D);
        }
        Vec3 direction = target.position().subtract(position());
        float yaw = (float) (-Mth.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        yBodyRot = yaw;
    }

    private void begin(int phase) {
        changedAt = tickCount;
        entityData.set(PHASE, phase);
    }

    private void slam(ServerLevel level, MadokaWitchEntity witch) {
        double radius = MadokaConfig.CANDELORO_CAKE_RADIUS.get();
        Vec3 origin = position().add(0, 0.12, 0);
        for (var player : level.players()) {
            Vec3 offset = player.position().subtract(origin);
            if (canHit(player, witch) && offset.horizontalDistanceSqr() <= radius * radius
                    && Math.abs(offset.y) < 1.5 && level.clip(new ClipContext(origin,
                    player.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                    .getType() == HitResult.Type.MISS) {
                player.hurt(damageSources().mobAttack(this), MadokaConfig.CANDELORO_CAKE_DAMAGE.get().floatValue());
            }
        }
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.16, getZ(), 16, 0.45, 0.1, 0.45, 0.08);
        playSound(SoundEvents.WOOD_BREAK, 1.25F, 0.85F);
    }

    private static boolean canHit(ServerPlayer player, MadokaWitchEntity witch) {
        return player.isAlive() && !player.getAbilities().invulnerable && witch.combat().allowsVisitor(player);
    }
}

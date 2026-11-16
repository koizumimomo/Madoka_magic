package io.madoka.magic.entity;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.NetworkHooks;

public final class CandeloroCutleryEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> KNIFE =
            SynchedEntityData.defineId(CandeloroCutleryEntity.class, EntityDataSerializers.BOOLEAN);
    private double remainingDistance;

    public CandeloroCutleryEntity(EntityType<? extends CandeloroCutleryEntity> type, Level level) {
        super(type, level);
    }

    static void fire(ServerLevel level, MadokaWitchEntity witch, WitchCast cast, int lane, boolean knife) {
        var shot = ModEntities.CANDELORO_CUTLERY.get().create(level);
        if (shot == null) {
            return;
        }
        Vec3 direction = cast.cutleryDirection(lane, knife);
        shot.entityData.set(KNIFE, knife);
        shot.setOwner(witch);
        shot.setPos(cast.cutleryStart(direction));
        shot.setDeltaMovement(direction.scale(knife ? 1.35D : 0.82D));
        shot.remainingDistance = cast.radius() - WitchCast.CANDELORO_MUZZLE;
        level.addFreshEntity(shot);
    }

    public boolean isKnife() {
        return entityData.get(KNIFE);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KNIFE, false);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        if (level() instanceof ServerLevel level) {
            if (remainingDistance <= 0 || !(getOwner() instanceof MadokaWitchEntity witch) || !witch.isAlive()) {
                discard();
                return;
            }
            double speed = motion.length();
            if (speed > remainingDistance) {
                motion = motion.scale(remainingDistance / speed);
                setDeltaMovement(motion);
            }
            var hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS && !ForgeEventFactory.onProjectileImpact(this, hit)) {
                onHit(hit);
                Vec3 point = hit.getLocation();
                level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 5, 0.1, 0.1, 0.1, 0.06);
                discard();
                return;
            }
            remainingDistance -= motion.length();
        }
        setPos(position().add(motion));
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return target instanceof ServerPlayer player && !player.getAbilities().invulnerable && player.isAlive()
                && getOwner() instanceof MadokaWitchEntity witch && witch.combat().allowsVisitor(player)
                && super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (getOwner() instanceof MadokaWitchEntity witch) {
            float damage = (isKnife() ? MadokaConfig.CANDELORO_KNIFE_DAMAGE : MadokaConfig.CANDELORO_FORK_DAMAGE)
                    .get().floatValue();
            hit.getEntity().hurt(damageSources().mobProjectile(this, witch), damage);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

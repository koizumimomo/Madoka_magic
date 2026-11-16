package io.madoka.magic.entity;

import io.madoka.magic.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.NetworkHooks;

public final class MermaidNoteEntity extends Projectile implements IEntityAdditionalSpawnData {
    public static final int VARIANTS = 4;
    // ShulkerBullet's initial target speed, acceleration and steering response for one flight segment.
    private static final double TARGET_SPEED = 0.15D;
    private static final double ACCELERATION = 1.025D;
    private static final double RESPONSE = 0.2D;
    private static final int FLIGHT_TICKS = 64;

    private Vec3 targetMotion = Vec3.ZERO;
    private float damage;
    private int variant;

    public MermaidNoteEntity(EntityType<? extends MermaidNoteEntity> type, Level level) {
        super(type, level);
    }

    static boolean launch(ServerLevel level, MadokaWitchEntity witch, Vec3 origin, Vec3 direction, float damage) {
        if (direction.lengthSqr() < 1.0E-6D) {
            return false;
        }
        var note = ModEntities.MERMAID_NOTE.get().create(level);
        if (note == null) {
            return false;
        }
        note.setOwner(witch);
        note.setPos(origin);
        note.targetMotion = direction.normalize().scale(TARGET_SPEED);
        note.damage = damage;
        note.variant = level.random.nextInt(VARIANTS);
        return level.addFreshEntity(note);
    }

    public int variant() {
        return variant;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public void tick() {
        super.tick();
        targetMotion = new Vec3(accelerate(targetMotion.x), accelerate(targetMotion.y), accelerate(targetMotion.z));
        Vec3 motion = getDeltaMovement().lerp(targetMotion, RESPONSE);
        setDeltaMovement(motion);
        if (level() instanceof ServerLevel level) {
            if (tickCount > FLIGHT_TICKS || !(getOwner() instanceof MadokaWitchEntity witch) || !witch.isAlive()) {
                discard();
                return;
            }
            var hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS && !ForgeEventFactory.onProjectileImpact(this, hit)) {
                onHit(hit);
                Vec3 point = hit.getLocation();
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z,
                        6, 0.16D, 0.16D, 0.16D, 0.03D);
                discard();
                return;
            }
        }
        Vec3 next = position().add(motion);
        setPos(next.x, next.y, next.z);
    }

    private static double accelerate(double component) {
        return Mth.clamp(component * ACCELERATION, -1.0D, 1.0D);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return target instanceof ServerPlayer player && player.isAlive() && !player.getAbilities().invulnerable
                && getOwner() instanceof MadokaWitchEntity witch && witch.combat().allowsVisitor(player)
                && super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!(hit.getEntity() instanceof ServerPlayer player) || !(getOwner() instanceof MadokaWitchEntity witch)
                || damage <= 0) {
            return;
        }
        WitchCombat.hurtWithoutKnockback(player, damageSources().mobProjectile(this, witch), damage);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeDouble(targetMotion.x);
        buffer.writeDouble(targetMotion.y);
        buffer.writeDouble(targetMotion.z);
        buffer.writeByte(variant);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        targetMotion = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        variant = buffer.readUnsignedByte() % VARIANTS;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

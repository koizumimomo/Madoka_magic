package io.madoka.magic.entity;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.faction.WitchFamiliars;
import io.madoka.magic.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

/** A fixed warning and three rendered skulls. The server applies one hit at the end of the orbit. */
public final class MourningSkullOrbitEntity extends Projectile implements IEntityAdditionalSpawnData {
    public static final int SKULLS = 3;
    private static final double TURN = Math.PI * 2.0D;
    private static final int FLIGHT_TICKS = 12;
    private static final int ORBIT_TICKS = 60;
    public static final int TOTAL_TICKS = FLIGHT_TICKS + ORBIT_TICKS;
    private static final double START_RADIUS = 3.0D;
    public static final double BLAST_RADIUS = 1.0D;
    private static final double BLAST_HEIGHT = 2.0D;

    private Vec3 muzzle = Vec3.ZERO;
    private float bodyYaw;
    private long started;

    public MourningSkullOrbitEntity(EntityType<? extends MourningSkullOrbitEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    static boolean launch(ServerLevel level, WitherBoss witch, ServerPlayer target) {
        Vec3 feet = target.position();
        var floor = level.clip(new ClipContext(feet.add(0, 0.08D, 0), feet.add(0, -12, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
        if (floor.getType() == HitResult.Type.MISS) {
            return false;
        }
        Vec3 center = floor.getLocation();
        var spell = ModEntities.MOURNING_SKULL_ORBIT.get().create(level);
        if (spell == null) {
            return false;
        }
        spell.setPos(center);
        spell.setOwner(witch);
        spell.started = level.getGameTime();
        spell.muzzle = witch.position().subtract(center);
        spell.bodyYaw = witch.yBodyRot;
        if (!level.addFreshEntity(spell)) {
            return false;
        }
        witch.playSound(SoundEvents.WITHER_SHOOT, 0.85F, 0.66F);
        return true;
    }

    public double age(float partialTick) {
        return Math.max(0, level().getGameTime() - started + partialTick);
    }

    public static double progress(double age) {
        return Mth.clamp((age - FLIGHT_TICKS) / ORBIT_TICKS, 0.0D, 1.0D);
    }

    public static double radius(double age) {
        return Mth.lerp(progress(age), START_RADIUS, BLAST_RADIUS);
    }

    public Vec3 skullPosition(int index, double age) {
        double time = progress(age) * ORBIT_TICKS;
        double angle = Math.atan2(muzzle.z, muzzle.x) + TURN * index / SKULLS + time * (0.17D + time * 0.0011D);
        double radius = radius(age);
        Vec3 orbit = new Vec3(Math.cos(angle) * radius, 0.65D + (1.0D - progress(age)) * 0.30D,
                Math.sin(angle) * radius);
        if (age >= FLIGHT_TICKS) {
            return orbit;
        }
        double side = index - 1.0D;
        double yaw = bodyYaw * Mth.DEG_TO_RAD;
        Vec3 from = muzzle.add(Math.cos(yaw) * 1.3D * side, index == 1 ? 3.0D : 2.2D, Math.sin(yaw) * 1.3D * side);
        double flight = Mth.clamp(age / FLIGHT_TICKS, 0.0D, 1.0D);
        return from.lerp(orbit, flight * (2.0D - flight)).add(0, Math.sin(flight * Math.PI) * 0.65D, 0);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        AABB area = getBoundingBox().inflate(START_RADIUS + 0.5D, 1.5D, START_RADIUS + 0.5D);
        if (age(0) < FLIGHT_TICKS) {
            Vec3 start = position().add(muzzle).add(0, 2.8D, 0);
            return area.minmax(new AABB(start, start).inflate(2.0D));
        }
        return area;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (!(getOwner() instanceof WitherBoss witch) || !witch.isAlive()) {
            discard();
        } else if (age(0) >= TOTAL_TICKS) {
            explode(level, witch);
            discard();
        }
    }

    private void explode(ServerLevel level, WitherBoss witch) {
        Vec3 center = position();
        for (ServerPlayer player : level.getPlayers(candidate -> candidate.isAlive() && !candidate.getAbilities().invulnerable
                && WitchFamiliars.isEncounterVisitor(witch, candidate))) {
            Vec3 offset = player.position().subtract(center);
            if (Math.abs(offset.y) <= BLAST_HEIGHT && offset.horizontalDistanceSqr() <= BLAST_RADIUS * BLAST_RADIUS) {
                player.hurt(level.damageSources().indirectMagic(this, witch), MadokaConfig.MOURNING_ORBIT_DAMAGE.get().floatValue());
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.52D, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SOUL, center.x, center.y + 0.52D, center.z, 22, 0.60D, 0.32D, 0.60D, 0.08D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.05F, 0.80F);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeDouble(muzzle.x);
        buffer.writeDouble(muzzle.y);
        buffer.writeDouble(muzzle.z);
        buffer.writeFloat(bodyYaw);
        buffer.writeLong(started);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        muzzle = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        bodyYaw = buffer.readFloat();
        started = buffer.readLong();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

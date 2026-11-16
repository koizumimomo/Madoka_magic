package io.madoka.magic.entity;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public final class NightGearEntity extends Projectile {
    private static final double GRAVITY = 0.06D;
    private static final int MAX_AGE = 105;

    public NightGearEntity(EntityType<? extends NightGearEntity> type, Level level) {
        super(type, level);
    }

    static void launch(ServerLevel level, MadokaWitchEntity witch, Vec3 start, Vec3 landing) {
        var gear = ModEntities.NIGHT_GEAR.get().create(level);
        if (gear == null) {
            return;
        }
        Vec3 offset = landing.subtract(start);
        int flight = Mth.clamp(Mth.ceil(offset.horizontalDistance() / 0.95D), 24, 58);
        // Gravity is applied after each movement step.
        Vec3 velocity = offset.scale(1.0D / flight).add(0, GRAVITY * (flight - 1) / 2, 0);
        gear.setOwner(witch);
        gear.setPos(start);
        gear.setYRot((float) Mth.atan2(velocity.x, velocity.z) * Mth.RAD_TO_DEG);
        gear.setDeltaMovement(velocity);
        level.addFreshEntity(gear);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public void tick() {
        super.tick();
        Vec3 from = position();
        Vec3 velocity = getDeltaMovement();
        Vec3 to = from.add(velocity);
        if (level() instanceof ServerLevel level) {
            if (tickCount >= MAX_AGE || to.y < level.getMinBuildHeight()
                    || !(getOwner() instanceof MadokaWitchEntity witch) || !witch.isAlive()) {
                discard();
                return;
            }
            var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            if (hit.getType() == HitResult.Type.BLOCK) {
                impact(level, witch, hit.getLocation());
                discard();
                return;
            }
        }
        setPos(to);
        setDeltaMovement(velocity.add(0, -GRAVITY, 0));
    }

    private void impact(ServerLevel level, MadokaWitchEntity witch, Vec3 point) {
        double radius = MadokaConfig.NIGHT_GEAR_RADIUS.get();
        double damage = MadokaConfig.NIGHT_GEAR_DAMAGE.get();
        level.playSound(null, point.x, point.y, point.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.25F, 0.76F);
        level.sendParticles(ParticleTypes.CRIT, point.x, point.y + 0.25D, point.z, 26, 0.85D, 0.18D, 0.85D, 0.22D);
        level.sendParticles(ParticleTypes.POOF, point.x, point.y + 0.2D, point.z, 14, 0.7D, 0.12D, 0.7D, 0.07D);
        for (var player : level.players()) {
            Vec3 offset = player.position().subtract(point);
            if (damage > 0 && player.isAlive() && witch.combat().allowsVisitor(player)
                    && Math.abs(offset.y) <= 2.0D && offset.horizontalDistanceSqr() <= radius * radius
                    && level.clip(new ClipContext(point.add(0, 0.15D, 0), player.getBoundingBox().getCenter(),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS) {
                player.hurt(damageSources().mobProjectile(this, witch), (float) damage);
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

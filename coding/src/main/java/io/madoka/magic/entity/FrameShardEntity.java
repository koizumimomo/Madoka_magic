package io.madoka.magic.entity;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class FrameShardEntity extends AbstractArrow {
    private static final int MAX_AGE = 55;

    public FrameShardEntity(EntityType<? extends FrameShardEntity> type, Level level) {
        super(type, level);
        pickup = Pickup.DISALLOWED;
    }

    static boolean fire(ServerLevel level, MadokaWitchEntity witch, ServerPlayer target) {
        var shard = ModEntities.FRAME_SHARD.get().create(level);
        if (shard == null) {
            return false;
        }
        Vec3 toward = target.position().subtract(witch.position()).multiply(1, 0, 1).normalize();
        Vec3 from = witch.position().add(toward.scale(witch.getBbWidth() * 0.55D)).add(0, witch.getBbHeight() * 0.58D, 0);
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.52D, 0).subtract(from);
        shard.setOwner(witch);
        shard.setPos(from);
        shard.setBaseDamage(MadokaConfig.ATONEMENT_SHARD_DAMAGE.get());
        shard.shoot(aim.x, aim.y + aim.horizontalDistance() * 0.09D, aim.z, 1.25F,
                MadokaConfig.ATONEMENT_SHARD_SPREAD.get().floatValue());
        return level.addFreshEntity(shard);
    }

    @Override
    public void tick() {
        if (!level().isClientSide && (tickCount >= MAX_AGE || !(getOwner() instanceof MadokaWitchEntity witch)
                || !witch.isAlive())) {
            discard();
            return;
        }
        super.tick();
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return target instanceof ServerPlayer player && !player.getAbilities().invulnerable
                && getOwner() instanceof MadokaWitchEntity witch && witch.combat().allowsVisitor(player)
                && super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (!level().isClientSide) {
            hit.getEntity().hurt(damageSources().indirectMagic(this, getOwner()), (float) getBaseDamage());
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        discard();
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }
}

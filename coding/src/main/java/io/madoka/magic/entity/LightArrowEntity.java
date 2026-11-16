package io.madoka.magic.entity;

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

public final class LightArrowEntity extends AbstractArrow {
    private static final int MAX_AGE = 30;

    public LightArrowEntity(EntityType<? extends LightArrowEntity> type, Level level) {
        super(type, level);
        pickup = Pickup.DISALLOWED;
    }

    static void fall(ServerLevel level, MadokaWitchEntity witch, Vec3 from, double damage) {
        var arrow = ModEntities.LIGHT_ARROW.get().create(level);
        if (arrow == null) {
            return;
        }
        arrow.setOwner(witch);
        arrow.setPos(from);
        arrow.setBaseDamage(damage);
        arrow.shoot(0, -1, 0, 1.10F, 0.0F);
        level.addFreshEntity(arrow);
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

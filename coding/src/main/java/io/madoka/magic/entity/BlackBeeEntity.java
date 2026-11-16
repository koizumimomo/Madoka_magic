package io.madoka.magic.entity;

import io.madoka.magic.faction.FamiliarVisibility;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class BlackBeeEntity extends Bee {
    public BlackBeeEntity(EntityType<? extends Bee> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new MeleeAttackGoal(this, 1.25D, true));
        targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Player.class, true,
                FamiliarVisibility::canWitchTarget));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return target.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
    }
}

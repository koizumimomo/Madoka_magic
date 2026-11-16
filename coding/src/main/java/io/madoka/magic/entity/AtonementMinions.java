package io.madoka.magic.entity;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.faction.FamiliarVisibility;
import io.madoka.magic.faction.WitchFamiliars;
import io.madoka.magic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public final class AtonementMinions {
    private static final String SUMMONER = "madoka_atonement_summoner";
    private static final UUID STATS = UUID.fromString("ba805e43-d742-4e2c-a090-03f6022a60bf");
    private static final List<Attribute> SCALED = List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE,
            Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.MOVEMENT_SPEED, Attributes.FOLLOW_RANGE,
            Attributes.KNOCKBACK_RESISTANCE, Attributes.ATTACK_KNOCKBACK);

    private AtonementMinions() {}

    public static boolean isMinion(Mob mob) {
        return mob instanceof MourningZombie || mob instanceof MourningSkeleton;
    }

    static boolean summonedBy(Mob mob, MadokaWitchEntity witch) {
        var tag = mob.getPersistentData();
        return isMinion(mob) && tag.hasUUID(SUMMONER) && tag.getUUID(SUMMONER).equals(witch.getUUID());
    }

    static boolean spawn(ServerLevel level, MadokaWitchEntity witch, BlockPos pos, boolean skeleton) {
        Mob mob = skeleton ? ModEntities.MOURNING_SKELETON.get().create(level)
                : ModEntities.MOURNING_ZOMBIE.get().create(level);
        if (mob == null) {
            return false;
        }
        Vec3 point = Vec3.atBottomCenterOf(pos);
        mob.moveTo(point.x, point.y, point.z, witch.getYRot(), 0);
        if (!level.noCollision(mob) || level.containsAnyLiquid(mob.getBoundingBox())) {
            return false;
        }
        var parent = witch.getPersistentData();
        var data = mob.getPersistentData();
        data.putUUID(SUMMONER, witch.getUUID());
        if (parent.hasUUID(WitchFamiliars.ENCOUNTER_TAG)) {
            data.putUUID(WitchFamiliars.ENCOUNTER_TAG, parent.getUUID(WitchFamiliars.ENCOUNTER_TAG));
        }
        double bonus = MadokaConfig.ATONEMENT_SUMMON_STAT_MULTIPLIER.get() - 1.0D;
        for (Attribute attribute : SCALED) {
            var instance = mob.getAttribute(attribute);
            if (instance != null) {
                instance.addPermanentModifier(new AttributeModifier(STATS, "madoka atonement summon", bonus,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
        if (mob instanceof Skeleton) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        }
        mob.setHealth(mob.getMaxHealth());
        mob.setPersistenceRequired();
        return level.addFreshEntity(mob);
    }

    private static boolean canHunt(Mob mob, LivingEntity target) {
        return target instanceof ServerPlayer player && FamiliarVisibility.canWitchTarget(player)
                && WitchFamiliars.isEncounterVisitor(mob, player);
    }

    public static final class MourningZombie extends Zombie {
        public MourningZombie(EntityType<? extends Zombie> type, Level level) {
            super(type, level);
        }

        @Override
        protected void addBehaviourGoals() {
            super.addBehaviourGoals();
            targetSelector.removeAllGoals(goal -> true);
            targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true,
                    target -> canHunt(this, target)));
        }

        @Override
        protected void randomizeReinforcementsChance() {
            getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0.0D);
        }
    }

    public static final class MourningSkeleton extends Skeleton {
        public MourningSkeleton(EntityType<? extends Skeleton> type, Level level) {
            super(type, level);
        }

        @Override
        protected void registerGoals() {
            super.registerGoals();
            targetSelector.removeAllGoals(goal -> true);
            targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true,
                    target -> canHunt(this, target)));
        }

        @Override
        protected AbstractArrow getArrow(ItemStack stack, float distanceFactor) {
            AbstractArrow arrow = super.getArrow(stack, distanceFactor);
            var boost = getAttribute(Attributes.MAX_HEALTH).getModifier(STATS);
            if (boost != null) {
                arrow.setBaseDamage(arrow.getBaseDamage() * (1.0D + boost.getAmount()));
            }
            return arrow;
        }
    }
}

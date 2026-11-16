package io.madoka.magic.entity;

import io.madoka.magic.data.MagicalGirl;
import io.madoka.magic.data.MagicalGirlRegistry;
import io.madoka.magic.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 半魔女化身 —— 玩家污染值超过 60 时可以释放的协助者。
 *
 * <p>它拥有玩家自己的魔女化形态，用玩家主题的技能战斗；
 * 代价是释放期间玩家自身的数值降低。</p>
 */
public class HalfWitchEntity extends Monster {

    private static final String TAG_THEME = "theme";
    private static final String TAG_OWNER = "owner";

    private String themeId = "sacrifice";
    private String ownerName = "";

    public HalfWitchEntity(EntityType<? extends HalfWitchEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }


    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1D, true));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        // 协助者不该攻击召唤它的玩家本人
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true,
                p -> !p.getName().getString().equals(ownerName)));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true));
    }

    public String getThemeId() {
        return themeId;
    }

    public void setThemeId(String themeId) {
        this.themeId = themeId == null || themeId.isEmpty() ? "sacrifice" : themeId;
        this.setCustomName(getDisplayName());
        this.setCustomNameVisible(true);
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    private String girlName() {
        MagicalGirl girl = MagicalGirlRegistry.get(themeId);
        return girl == null ? "？" : girl.getDisplayName();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("entity.madoka_magic.half_witch",
                girlName());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_THEME, themeId);
        tag.putString(TAG_OWNER, ownerName);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        themeId = tag.getString(TAG_THEME);
        ownerName = tag.getString(TAG_OWNER);
    }
}

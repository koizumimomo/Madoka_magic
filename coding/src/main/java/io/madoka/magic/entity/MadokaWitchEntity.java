package io.madoka.magic.entity;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.data.MagicalGirl;
import io.madoka.magic.data.MagicalGirlRegistry;
import io.madoka.magic.faction.FamiliarVisibility;
import io.madoka.magic.item.GriefSeedItem;
import io.madoka.magic.registry.ModEntities;
import io.madoka.magic.spell.ThornTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * 魔女。
 *
 * <p>每位主题一位魔女，各住在自己的维度（见 {@link io.madoka.magic.dimension.MadokaDimensions}）。
 * 通用模板：近战/远程攻击 + 主题粒子，等具体技能设计好再替换。</p>
 */
public class MadokaWitchEntity extends Monster implements GeoEntity, RangedAttackMob {

    private static final String TAG_THEME = "theme";
    private static final String TAG_OWNER = "owner";
    private static final String DEFAULT_THEME = "sacrifice";
    public static final double SALVATION_MODEL_LIFT = 0.26D;
    public static final float SALVATION_MODEL_HEIGHT = 5.47F;
    private static final EntityDimensions SALVATION_DIMENSIONS = EntityDimensions.scalable(4.1F, 4.15F);
    private static final EntityDimensions SALVATION_CULLING = EntityDimensions.scalable(6.5F, SALVATION_MODEL_HEIGHT);
    private static final double SALVATION_MELEE_REACH = 3.2D;
    private static final EntityDimensions BUTTERFLY_DIMENSIONS = EntityDimensions.scalable(1.25F, 2.9F);
    private static final EntityDimensions BUTTERFLY_CULLING = EntityDimensions.scalable(4.7F, 3.4F);
    private static final EntityDimensions TAKING_DIMENSIONS = EntityDimensions.scalable(1.6F, 4.125F);
    private static final EntityDimensions TAKING_CULLING = EntityDimensions.scalable(5.4F, 4.75F);
    private static final EntityDimensions MERMAID_DIMENSIONS = EntityDimensions.scalable(1.6F, 3.6F);
    private static final EntityDimensions MERMAID_CULLING = EntityDimensions.scalable(6.0F, 5.8F);
    private static final EntityDimensions CANDELORO_DIMENSIONS = EntityDimensions.scalable(3.1F, 3.5F);
    private static final EntityDimensions CANDELORO_CULLING = EntityDimensions.scalable(6.2F, 4.8F);
    public static final float MERMAID_MODEL_HEIGHT = 4.35F;
    private static final EntityDimensions ATONEMENT_DIMENSIONS = EntityDimensions.scalable(1.9F, 3.3F);
    private static final EntityDimensions ATONEMENT_CULLING = EntityDimensions.scalable(6.0F, 5.5F);
    public static final float ATONEMENT_MODEL_HEIGHT = 3.95F;
    private static final EntityDimensions OPHELIA_DIMENSIONS = EntityDimensions.scalable(1.65F, 3.8F);
    private static final EntityDimensions OPHELIA_CULLING = EntityDimensions.scalable(4.3F, 4.4F);
    public static final float NIGHT_MODEL_SCALE = 3.0F;
    private static final EntityDimensions NIGHT_DIMENSIONS = EntityDimensions.scalable(3.2F, 5.45F).scale(NIGHT_MODEL_SCALE);
    private static final EntityDimensions NIGHT_CULLING = EntityDimensions.scalable(7.0F, 6.25F).scale(NIGHT_MODEL_SCALE);
    private static final double TAKING_HOVER_HEIGHT = 2.0D;
    private static final double TAKING_HOVER_SPEED = 0.22D;
    private static final EntityDataAccessor<String> THEME =
            SynchedEntityData.defineId(MadokaWitchEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<CompoundTag> SKILL_LINKS =
            SynchedEntityData.defineId(MadokaWitchEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Boolean> COCOONED =
            SynchedEntityData.defineId(MadokaWitchEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> RAIN_VISUAL =
            SynchedEntityData.defineId(MadokaWitchEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<BlockPos>> RAIN_CENTER =
            SynchedEntityData.defineId(MadokaWitchEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<CompoundTag> ARENA_VISUAL =
            SynchedEntityData.defineId(MadokaWitchEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> QUINTET_CAST =
            SynchedEntityData.defineId(MadokaWitchEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Float> CURSE_RADIUS =
            SynchedEntityData.defineId(MadokaWitchEntity.class, EntityDataSerializers.FLOAT);

    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.madoka_witch.walk");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.madoka_witch.idle");
    private static final RawAnimation BUTTERFLY_IDLE = RawAnimation.begin().thenLoop("animation.cio_cio_san.idle");
    private static final RawAnimation BUTTERFLY_FOLD = RawAnimation.begin().thenLoop("animation.cio_cio_san.fold");
    private static final RawAnimation TAKING_IDLE = RawAnimation.begin().thenLoop("animation.melissa.idle");
    private static final RawAnimation TAKING_FEEDING = RawAnimation.begin().thenLoop("animation.melissa.feeding");
    private static final RawAnimation MERMAID_IDLE = RawAnimation.begin().thenLoop("animation.oktavia.idle");
    private static final RawAnimation MERMAID_GLIDE = RawAnimation.begin().thenLoop("animation.oktavia.glide");
    private static final RawAnimation MERMAID_CONDUCT = RawAnimation.begin().thenLoop("animation.oktavia.conduct");
    private static final RawAnimation CANDELORO_IDLE = RawAnimation.begin().thenLoop("animation.candeloro.idle");
    private static final RawAnimation ATONEMENT_IDLE = RawAnimation.begin().thenLoop("animation.atonement_frame.idle");
    private static final RawAnimation ATONEMENT_CAST = RawAnimation.begin().thenLoop("animation.atonement_frame.cast");
    private static final RawAnimation OPHELIA_IDLE = RawAnimation.begin().thenLoop("animation.ophelia.idle");
    private static final RawAnimation OPHELIA_GAIT = RawAnimation.begin().thenLoop("animation.ophelia.gait");
    private static final RawAnimation NIGHT_IDLE = RawAnimation.begin().thenLoop("animation.walpurgis_night.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final WitchCombat combat = new WitchCombat(this);
    private final WitchBossBar bossBar = new WitchBossBar();
    private WitchCast quintetCast;

    private String ownerName = "";
    private UUID ownerId;
    private boolean assisted;
    private UUID cocoonBoss;
    private long cocoonExpires;
    private boolean previousNoAi;

    public MadokaWitchEntity(EntityType<? extends MadokaWitchEntity> type, Level level) {
        super(type, level);
        if (isPrincess()) {
            return;
        }
        refreshDimensions();
        this.setPersistenceRequired();
        this.xpReward = MadokaConfig.WITCH_XP.get();
        // 配置在构造时套用（属性创建事件早于配置加载）
        applyConfig();
        setHealth(getMaxHealth());
    }

    boolean isPrincess() {
        return getType() == ModEntities.BUTTERFLY_PRINCESS.get();
    }

    /** 把配置里的生命/伤害套到实例上。 */
    private void applyConfig() {
        var health = this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(MadokaConfig.WITCH_HEALTH.get());
        }
        var damage = this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(MadokaConfig.WITCH_DAMAGE.get());
        }
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new GroundPathNavigation(this, level) {
            @Override
            protected boolean canUpdatePath() {
                return isTakingWitch() || super.canUpdatePath();
            }

            @Override
            protected Vec3 getTempMobPos() {
                return isTakingWitch() ? position().add(0.0D, -TAKING_HOVER_HEIGHT, 0.0D) : super.getTempMobPos();
            }

            @Override
            protected void followThePath() {
                if (!isTakingWitch()) {
                    super.followThePath();
                    return;
                }
                // Follow the ground waypoints from above them while keeping normal block collision.
                Vec3 groundPosition = getTempMobPos();
                Vec3 offset = path.getNextEntityPos(mob).subtract(groundPosition);
                double tolerance = getBbWidth() * 0.5D;
                if (offset.horizontalDistanceSqr() < tolerance * tolerance && Math.abs(offset.y) < 1.0D) {
                    path.advance();
                }
                doStuckDetection(groundPosition);
            }
        };
    }

    @Override
    public boolean isNoGravity() {
        return super.isNoGravity() || isTakingWitch() || isWalpurgisNight();
    }

    @Override
    protected float getFlyingSpeed() {
        return isTakingWitch() ? getSpeed() * 0.2F : super.getFlyingSpeed();
    }

    private void updateTakingHover() {
        Vec3 base = position();
        var floor = level().clip(new ClipContext(base.add(0.0D, 0.01D, 0.0D), base.add(0.0D, -8.0D, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double offset = floor.getType() == HitResult.Type.BLOCK
                ? Mth.clamp((floor.getLocation().y + TAKING_HOVER_HEIGHT - base.y) * 0.35D,
                        -TAKING_HOVER_SPEED, TAKING_HOVER_SPEED)
                : -TAKING_HOVER_SPEED;
        if (Math.abs(offset) > 0.001D) {
            move(MoverType.SELF, new Vec3(0.0D, offset, 0.0D));
        }
        Vec3 movement = getDeltaMovement();
        setDeltaMovement(movement.x, 0.0D, movement.z);
        fallDistance = 0.0F;
    }

    @Override
    protected void registerGoals() {
        Goal holdForMarch = new Goal() {
            @Override
            public boolean canUse() {
                return controlsOwnMovement();
            }

            @Override
            public void start() {
                getNavigation().stop();
            }
        };
        holdForMarch.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        this.goalSelector.addGoal(0, holdForMarch);
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, true) {
            @Override
            public boolean canUse() {
                return !isAtonement() && !isMermaid() && !WitchTypes.NUTCRACKER.equals(getThemeId()) && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !isAtonement() && !isMermaid() && !WitchTypes.NUTCRACKER.equals(getThemeId()) && super.canContinueToUse();
            }

            @Override
            protected double getAttackReachSqr(LivingEntity target) {
                if (isSalvation()) {
                    double reach = SALVATION_MELEE_REACH + target.getBbWidth() * 0.5D;
                    return reach * reach;
                }
                return super.getAttackReachSqr(target);
            }
        });
        this.goalSelector.addGoal(1, new RangedAttackGoal(this, 1.0D, MadokaConfig.MERMAID_NOTE_INTERVAL.get(),
                MadokaConfig.MERMAID_NOTE_RANGE.get().floatValue()) {
            @Override
            public boolean canUse() {
                return canPlayNote() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return canPlayNote() && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        // 只锁定「曾经签约」的玩家 —— 未签约的普通人看不见魔女，
        // 魔女也不该去打他们（设计提案 §13.5）。
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true,
                FamiliarVisibility::canWitchTarget));
    }

    @Override
    public void tick() {
        if (getQuintetCast() != null && getQuintetCast().kind() == WitchCast.Kind.MERMAID_NOTES) {
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        }
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (!isPrincess()) {
            bossBar.tick(this);
        }
        if (!isAlive()) {
            return;
        }
        if (isTakingWitch()) {
            updateTakingHover();
        }
        if (isWalpurgisNight()) {
            setDeltaMovement(Vec3.ZERO);
        }
        if (isPrincess()) {
            if (isCocooned() && (level.getGameTime() >= cocoonExpires || tickCount > 10
                    && (!(level.getEntity(cocoonBoss) instanceof MadokaWitchEntity boss) || !boss.isAlive()))) {
                endCocoon();
            }
        } else if (!isNoAi()) {
            combat.tick(level);
        }
    }

    private boolean canPlayNote() {
        return isMermaid() && getQuintetCast() == null && !ThornTracker.isBound(this);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float strength) {
        if (!canPlayNote() || !(level() instanceof ServerLevel level) || !(target instanceof ServerPlayer player)
                || !combat.allowsVisitor(player) || !player.isAlive() || player.getAbilities().invulnerable
                || distanceToSqr(player) > Mth.square(MadokaConfig.MERMAID_NOTE_RANGE.get())) {
            return;
        }
        Vec3 origin = position().add(0, getBbHeight() * 0.42D, 0);
        Vec3 aim = target.getBoundingBox().getCenter().subtract(origin);
        if (MermaidNoteEntity.launch(level, this, origin, aim, MadokaConfig.MERMAID_NOTE_DAMAGE.get().floatValue())) {
            playSound(SoundEvents.NOTE_BLOCK_HARP.value(), 1.0F, 1.15F);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!isPrincess()) {
            bossBar.track(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.untrack(player);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level() instanceof ServerLevel level && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && combat.hasShield(level)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return !controlsOwnMovement() && (!(level() instanceof ServerLevel level) || !combat.isStaggered(level))
                && super.doHurtTarget(target);
    }

    private boolean controlsOwnMovement() {
        return isWalpurgisNight() || getQuintetCast() != null && (isMermaid() || isOphelia() || isAtonement() || isCandeloro())
                || isAtonement() && getRainVisual() != 0;
    }

    public boolean isSalvation() {
        return WitchTypes.SALVATION.equals(getThemeId()) && !isPrincess();
    }

    public boolean isButterflyWitch() {
        return DEFAULT_THEME.equals(getThemeId()) && !isPrincess();
    }

    public boolean isTakingWitch() {
        return "take".equals(getThemeId()) && !isPrincess();
    }

    public boolean isMermaid() {
        return WitchTypes.MERMAID.equals(getThemeId()) && !isPrincess();
    }

    public boolean isCandeloro() {
        return "candeloro".equals(getThemeId()) && !isPrincess();
    }

    public boolean isAtonement() {
        return "atonement".equals(getThemeId()) && !isPrincess();
    }

    public boolean isOphelia() {
        return WitchTypes.OPHELIA.equals(getThemeId()) && !isPrincess();
    }

    public boolean isWalpurgisNight() {
        return WitchTypes.NIGHT.equals(getThemeId()) && !isPrincess();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (isOphelia()) {
            return OPHELIA_DIMENSIONS;
        }
        if (isWalpurgisNight()) {
            return NIGHT_DIMENSIONS;
        }
        if (isTakingWitch()) {
            return TAKING_DIMENSIONS;
        }
        if (isMermaid()) {
            return MERMAID_DIMENSIONS;
        }
        if (isCandeloro()) {
            return CANDELORO_DIMENSIONS;
        }
        if (isAtonement()) {
            return ATONEMENT_DIMENSIONS;
        }
        if (isButterflyWitch()) {
            return BUTTERFLY_DIMENSIONS;
        }
        return isSalvation() ? SALVATION_DIMENSIONS : super.getDimensions(pose);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        if (isOphelia()) {
            return OPHELIA_CULLING.makeBoundingBox(position());
        }
        if (isWalpurgisNight()) {
            return NIGHT_CULLING.makeBoundingBox(position());
        }
        if (isTakingWitch()) {
            return TAKING_CULLING.makeBoundingBox(position());
        }
        if (isMermaid()) {
            return MERMAID_CULLING.makeBoundingBox(position());
        }
        if (isCandeloro()) {
            return CANDELORO_CULLING.makeBoundingBox(position());
        }
        if (isAtonement()) {
            return ATONEMENT_CULLING.makeBoundingBox(position());
        }
        if (isButterflyWitch()) {
            return BUTTERFLY_CULLING.makeBoundingBox(position());
        }
        return isSalvation() ? SALVATION_CULLING.makeBoundingBox(position()) : super.getBoundingBoxForCulling();
    }

    public WitchCombat combat() {
        return combat;
    }

    public int[] getSkillLinks() {
        return entityData.get(SKILL_LINKS).getIntArray("targets");
    }

    void setSkillLinks(int[] ids) {
        if (!Arrays.equals(ids, getSkillLinks())) {
            var data = new CompoundTag();
            data.putIntArray("targets", ids);
            entityData.set(SKILL_LINKS, data);
        }
    }

    /** Positive radius during the warning, negative radius during the rain. */
    public float getRainVisual() {
        return entityData.get(RAIN_VISUAL);
    }

    void setRainVisual(float radius) {
        entityData.set(RAIN_VISUAL, radius);
    }

    public Optional<BlockPos> getRainCenter() {
        return entityData.get(RAIN_CENTER);
    }

    void setRainCenter(BlockPos pos) {
        entityData.set(RAIN_CENTER, Optional.ofNullable(pos));
    }

    public CompoundTag getArenaVisual() {
        return entityData.get(ARENA_VISUAL);
    }

    void setArenaVisual(CompoundTag state) {
        entityData.set(ARENA_VISUAL, state);
    }

    public WitchCast getQuintetCast() {
        return quintetCast;
    }

    void setQuintetCast(WitchCast cast) {
        entityData.set(QUINTET_CAST, cast == null ? new CompoundTag() : cast.toTag());
    }

    public float getCurseRadius() {
        return entityData.get(CURSE_RADIUS);
    }

    void setCurseRadius(float radius) {
        entityData.set(CURSE_RADIUS, radius);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (QUINTET_CAST.equals(key)) {
            quintetCast = WitchCast.fromTag(entityData.get(QUINTET_CAST));
        } else if (THEME.equals(key)) {
            refreshDimensions();
        }
    }

    public boolean isCocooned() {
        return entityData.get(COCOONED);
    }

    UUID cocoonBoss() {
        return cocoonBoss;
    }

    boolean cocoonProtects(UUID boss, long now) {
        return isCocooned() && boss.equals(cocoonBoss) && now < cocoonExpires;
    }

    void beginCocoon(UUID boss, long expires) {
        cocoonBoss = boss;
        cocoonExpires = expires;
        previousNoAi = isNoAi();
        getNavigation().stop();
        setTarget(null);
        setDeltaMovement(Vec3.ZERO);
        setNoAi(true);
        entityData.set(COCOONED, true);
    }

    private void endCocoon() {
        setNoAi(previousNoAi);
        cocoonBoss = null;
        entityData.set(COCOONED, false);
    }

    // ---------------------------------------------------------------- 数据

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(THEME, DEFAULT_THEME);
        entityData.define(SKILL_LINKS, new CompoundTag());
        entityData.define(COCOONED, false);
        entityData.define(RAIN_VISUAL, 0.0F);
        entityData.define(RAIN_CENTER, Optional.empty());
        entityData.define(ARENA_VISUAL, new CompoundTag());
        entityData.define(QUINTET_CAST, new CompoundTag());
        entityData.define(CURSE_RADIUS, 0.0F);
    }

    public String getThemeId() {
        return entityData.get(THEME);
    }

    public void setThemeId(String themeId) {
        entityData.set(THEME, themeId == null || themeId.isEmpty() ? DEFAULT_THEME : themeId);
        this.setCustomName(getDisplayName());
        this.setCustomNameVisible(true);
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public boolean isOwnedBy(Player player) {
        return ownerId != null ? ownerId.equals(player.getUUID()) : ownerName.equals(player.getGameProfile().getName());
    }

    public void markAssisted() {
        assisted = true;
    }

    public void markAssistedBy(Entity source) {
        if (source instanceof LivingEntity attacker) {
            LivingEntity responsible = attacker instanceof OwnableEntity owned && owned.getOwner() != null
                    ? owned.getOwner() : attacker;
            if (!(responsible instanceof Player player) || !isOwnedBy(player)) {
                markAssisted();
            }
        }
    }

    public boolean wasAssisted() {
        return assisted;
    }

    public MagicalGirl getTheme() {
        return MagicalGirlRegistry.get(getThemeId());
    }

    @Override
    public Component getDisplayName() {
        if (isPrincess()) {
            return Component.translatable("entity.madoka_magic.butterfly_princess");
        }
        Component witch = GriefSeedItem.witchName(getThemeId());
        return ownerName.isEmpty() ? witch : Component.translatable("entity.madoka_magic.witch.named", witch, ownerName);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_THEME, getThemeId());
        tag.putString(TAG_OWNER, ownerName);
        if (ownerId != null) {
            tag.putUUID("ownerId", ownerId);
        }
        tag.putBoolean("assisted", assisted);
        if (cocoonBoss != null) {
            tag.putUUID("CocoonBoss", cocoonBoss);
            tag.putLong("CocoonExpires", cocoonExpires);
            tag.putBoolean("CocoonPreviousNoAi", previousNoAi);
        }
        if (!isPrincess()) {
            var state = new CompoundTag();
            combat.save(state);
            tag.put("WitchCombat", state);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        String read = tag.getString(TAG_THEME);
        entityData.set(THEME, read.isEmpty() ? DEFAULT_THEME : read);
        ownerName = tag.getString(TAG_OWNER);
        ownerId = tag.hasUUID("ownerId") ? tag.getUUID("ownerId") : null;
        assisted = tag.getBoolean("assisted");
        if (isPrincess() && tag.hasUUID("CocoonBoss")) {
            cocoonBoss = tag.getUUID("CocoonBoss");
            cocoonExpires = tag.getLong("CocoonExpires");
            previousNoAi = tag.getBoolean("CocoonPreviousNoAi");
            setNoAi(true);
            entityData.set(COCOONED, true);
        } else if (!isPrincess()) {
            combat.load(tag.getCompound("WitchCombat"));
        }
    }

    // ---------------------------------------------------------------- GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 5, this::animPredicate));
    }

    private PlayState animPredicate(AnimationState<MadokaWitchEntity> state) {
        if (isSalvation()) {
            return PlayState.STOP;
        }
        if (isTakingWitch()) {
            return state.setAndContinue(getSkillLinks().length == 0 ? TAKING_IDLE : TAKING_FEEDING);
        }
        if (isButterflyWitch()) {
            return state.setAndContinue(getSkillLinks().length == 0 ? BUTTERFLY_IDLE : BUTTERFLY_FOLD);
        }
        if (isMermaid()) {
            var cast = getQuintetCast();
            if (cast != null && (cast.kind() == WitchCast.Kind.WHEEL_WAVE || cast.kind() == WitchCast.Kind.MERMAID_NOTES)) {
                return state.setAndContinue(MERMAID_CONDUCT);
            }
            return state.setAndContinue(cast == null && state.isMoving() ? MERMAID_GLIDE : MERMAID_IDLE);
        }
        if (isAtonement()) {
            return state.setAndContinue(controlsOwnMovement() ? ATONEMENT_CAST : ATONEMENT_IDLE);
        }
        if (isOphelia()) {
            boolean dashing = getQuintetCast() != null && level().getGameTime() >= getQuintetCast().strike();
            return state.setAndContinue(dashing || state.isMoving() ? OPHELIA_GAIT : OPHELIA_IDLE);
        }
        if (isWalpurgisNight()) {
            return state.setAndContinue(NIGHT_IDLE);
        }
        if (isCandeloro()) {
            return state.setAndContinue(CANDELORO_IDLE);
        }
        return state.setAndContinue(state.isMoving() ? WALK : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}

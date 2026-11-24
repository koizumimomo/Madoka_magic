package io.madoka.magic.entity;

import io.madoka.magic.registry.ModEntities;
import io.madoka.magic.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 结界共用近景道具：按 variant 读取 {@code geo/<variant>.geo.json} 与
 * {@code textures/entity/<variant>.png} 的静态装饰实体（无碰撞、不可选中）。
 *
 * <p>首批道具是路口的方向指示牌（signpost）。后续共用近景件直接新增
 * 模型资源并用新的 variant 生成，不必再建实体类。</p>
 */
public final class BarrierPropEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<String> VARIANT = SynchedEntityData.defineId(
            BarrierPropEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(
            BarrierPropEntity.class, EntityDataSerializers.FLOAT);
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public BarrierPropEntity(EntityType<? extends BarrierPropEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static boolean spawn(ServerLevel level, BlockPos pos, String variant, float yaw) {
        return spawn(level, pos, variant, yaw, 0.0F);
    }

    public static boolean spawn(ServerLevel level, BlockPos pos, String variant, float yaw, float pitch) {
        var prop = ModEntities.BARRIER_PROP.get().create(level);
        if (prop == null) {
            return false;
        }
        prop.setVariant(variant);
        prop.setPitch(pitch);
        prop.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, 0.0F);
        return level.addFreshEntity(prop);
    }

    public String getVariant() {
        return entityData.get(VARIANT);
    }

    public void setVariant(String variant) {
        entityData.set(VARIANT, variant);
    }

    /** 俯仰角（度）：渲染器在偏航之后绕局部 X 轴施加，用于倾斜放置的道具（如倾斜宴会桌）。 */
    public float getPitch() {
        return entityData.get(PITCH);
    }

    public void setPitch(float pitch) {
        entityData.set(PITCH, pitch);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** 渲染剔除盒按最宽的道具（拱门 5.8×6.1 格）给足，避免模型边缘被整体剔除。 */
    @Override
    public AABB getBoundingBoxForCulling() {
        if ("giant_spoon".equals(getVariant())) {
            // 巨勺长约 12 格、可任意偏航：给一个对称的大盒
            return new AABB(getX() - 8.5D, getY() - 1.0D, getZ() - 8.5D,
                    getX() + 8.5D, getY() + 6.0D, getZ() + 8.5D);
        }
        return new AABB(getX() - 3.2D, getY() - 0.2D, getZ() - 1.4D,
                getX() + 3.2D, getY() + 6.4D, getZ() + 1.4D);
    }

    /** 拱门轮廓控制点（右腿，单位=格；左腿取 x 镜像），蝴蝶粒子沿其散发。 */
    private static final double[][] ARCH_CURVE = {
            {2.0D, 0.2D}, {2.3D, 1.3D}, {2.35D, 2.4D}, {2.2D, 3.2D},
            {1.7D, 3.9D}, {0.9D, 4.5D}, {0.0D, 4.85D},
    };

    /** 蝴蝶拱门（butterfly_arch）不再带模型蝶，改为周期性散发奉献系蝴蝶粒子。 */
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 3 == 0
                && "butterfly_arch".equals(getVariant()) && level() instanceof ServerLevel server) {
            for (int side = -1; side <= 1; side += 2) {
                int i = random.nextInt(ARCH_CURVE.length - 1);
                double[] a = ARCH_CURVE[i];
                double[] b = ARCH_CURVE[i + 1];
                double t = random.nextDouble();
                double lx = side * (a[0] + (b[0] - a[0]) * t) + (random.nextDouble() - 0.5D) * 0.3D;
                double ly = a[1] + (b[1] - a[1]) * t + (random.nextDouble() - 0.5D) * 0.3D;
                double lz = (random.nextDouble() - 0.5D) * 0.5D;
                server.sendParticles(ModParticles.Devotion.BUTTERFLY.get(),
                        getX() + lx, getY() + ly, getZ() + lz,
                        1, 0.05D, 0.05D, 0.05D, 0.018D);
            }
        }
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(VARIANT, "signpost");
        entityData.define(PITCH, 0.0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Variant")) {
            setVariant(tag.getString("Variant"));
        }
        if (tag.contains("Pitch")) {
            setPitch(tag.getFloat("Pitch"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Variant", getVariant());
        tag.putFloat("Pitch", getPitch());
    }
}

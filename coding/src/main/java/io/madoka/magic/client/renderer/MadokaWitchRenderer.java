package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.MadokaWitchEntity;
import io.madoka.magic.entity.WitchTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Witch model, head-mounted star crown and client-side orbit.
 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT)
public class MadokaWitchRenderer extends GeoEntityRenderer<MadokaWitchEntity> {
    static final ResourceLocation WITHER_EMBER_ATLAS = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/particle/wither_ember.png");
    private static final RenderType WITHER_EMBERS = RenderType.entityTranslucentEmissive(WITHER_EMBER_ATLAS);

    public MadokaWitchRenderer(EntityRendererProvider.Context context) {
        super(context, new MadokaWitchModel());
        this.shadowRadius = 0.5f;
    }

    public static void registerVisualReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) RitualVisualStyle::reload);
        event.registerReloadListener((ResourceManagerReloadListener) WitchPortalRenderer::reload);
    }

    @Override
    public RenderType getRenderType(MadokaWitchEntity witch, ResourceLocation texture, MultiBufferSource buffers,
                                    float partialTick) {
        return witch.isOphelia() || witch.isMermaid() || witch.isAtonement() || witch.isCandeloro() ? RenderType.entityCutoutNoCull(texture)
                : super.getRenderType(witch, texture, buffers, partialTick);
    }

    @Override
    protected int getBlockLightLevel(MadokaWitchEntity witch, BlockPos pos) {
        int light = super.getBlockLightLevel(witch, pos);
        if (witch.isMermaid() || witch.isAtonement() || witch.isWalpurgisNight() || witch.isCandeloro()) {
            return Math.max(8, light);
        }
        return witch.isOphelia() ? Math.max(7, light) : light;
    }

    @Override
    public void renderRecursively(PoseStack pose, MadokaWitchEntity witch, GeoBone bone, RenderType type,
                                  MultiBufferSource buffers, VertexConsumer vertices, boolean rerender,
                                  float partialTick, int light, int overlay, float r, float g, float b, float alpha) {
        int boneLight = witch.isOphelia() && "flame".equals(bone.getName()) ? LightTexture.FULL_BRIGHT : light;
        super.renderRecursively(pose, witch, bone, type, buffers, vertices, rerender,
                partialTick, boneLight, overlay, r, g, b, alpha);
    }

    @SubscribeEvent
    public static void onWitherRender(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof WitherBoss wither) || !WitherWitchRenderer.inBarrier(wither)) {
            return;
        }
        var client = Minecraft.getInstance();
        if (client.player == null || wither.isInvisibleTo(client.player)) {
            return;
        }
        var camera = client.gameRenderer.getMainCamera();
        if (camera.getPosition().distanceToSqr(wither.position()) <= 64.0D * 64.0D) {
            var sprites = new RitualSprites(event.getPoseStack(), event.getMultiBufferSource(), camera);
            double time = wither.tickCount + (double) event.getPartialTick();
            WitchAura.crown(sprites, RitualVisualStyle.forTheme(WitchTypes.WITHER), 0.0D,
                    wither.getBbHeight() + 0.16D, 0.0D, time, 1.2D, 0.94D);
            var embers = new RitualSprites(event.getPoseStack(), event.getMultiBufferSource(), camera, WITHER_EMBERS);
            for (int i = 0; i < 6; i++) {
                double cycle = Mth.frac(time / 68.0D + i / 6.0D);
                double angle = time * 0.028D + i * Math.PI / 3;
                double radius = 1.0D + cycle * 0.45D;
                embers.drawTile(0, 1, 1, 16, 16, Math.cos(angle) * radius,
                        0.25D + wither.getBbHeight() * cycle, Math.sin(angle) * radius,
                        0.19D, 0.19D, -angle * 0.45D, 0xffffff, Math.sin(cycle * Math.PI) * 0.82D);
            }
        }
    }

    @SubscribeEvent
    public static void onWitherTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof WitherBoss wither && wither.level().isClientSide
                && wither.isAlive() && !wither.isInvisible() && WitherWitchRenderer.inBarrier(wither)
                && wither.tickCount % 5 == 0) {
            double angle = wither.tickCount * 0.19D;
            wither.level().addParticle(ParticleTypes.SOUL, wither.getX() + Math.cos(angle) * 1.05D,
                    wither.getY() + 0.3D + wither.getRandom().nextDouble() * 1.5D,
                    wither.getZ() + Math.sin(angle) * 1.05D, 0.0D, 0.022D, 0.0D);
        }
    }

    @Override
    public void scaleModelForRender(float width, float height, PoseStack pose, MadokaWitchEntity witch,
                                    BakedGeoModel model, boolean rerender, float partialTick, int light, int overlay) {
        float scale = witch.isWalpurgisNight() ? MadokaWitchEntity.NIGHT_MODEL_SCALE : 1.0F;
        super.scaleModelForRender(width * scale, height * scale, pose, witch, model, rerender, partialTick, light, overlay);
    }

    @Override
    public void render(MadokaWitchEntity witch, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        var client = Minecraft.getInstance();
        var camera = client.gameRenderer.getMainCamera();
        if (witch.isCocooned() && witch.isAlive()) {
            if (client.player != null && !witch.isInvisibleTo(client.player)) {
                WitchSkillVisuals.cocoon(pose, buffers, camera, witch.getBbHeight(), witch.tickCount + (double) partialTick);
            }
            return;
        }
        var model = getGeoModel();
        var crownBone = model.getBakedModel(model.getModelResource(witch)).getBone("witch_crown");
        crownBone.ifPresent(bone -> bone.setTrackingMatrices(true));
        boolean salvation = witch.isSalvation();
        if (salvation) {
            pose.pushPose();
            pose.translate(0, MadokaWitchEntity.SALVATION_MODEL_LIFT, 0);
            super.render(witch, yaw, partialTick, pose, buffers, light);
            pose.popPose();
        } else {
            super.render(witch, yaw, partialTick, pose, buffers, light);
        }

        if (WitchTypes.bossTheme(witch).isEmpty()) {
            return;
        }
        double distance = camera.getPosition().distanceToSqr(witch.position());
        if (client.player == null || witch.isInvisibleTo(client.player) || distance > 64.0D * 64.0D) {
            return;
        }
        var style = RitualVisualStyle.forTheme(witch.getThemeId());
        double visualHeight = salvation ? MadokaWitchEntity.SALVATION_MODEL_HEIGHT : witch.isMermaid()
                ? MadokaWitchEntity.MERMAID_MODEL_HEIGHT : witch.isAtonement()
                ? MadokaWitchEntity.ATONEMENT_MODEL_HEIGHT : witch.getBbHeight();
        double crownLift = witch.isWalpurgisNight() ? 1.2D : 0.12D;
        var center = crownBone.map(GeoBone::getLocalPosition)
                .orElseGet(() -> new Vector3d(0.0D, visualHeight + crownLift, 0.0D));
        double ticks = witch.tickCount + (double) partialTick + witch.getId() % 180;
        var sprites = new RitualSprites(pose, buffers, camera);
        WitchAura.crown(sprites, style, center.x, center.y, center.z, ticks,
                witch.isWalpurgisNight() ? 2.4D : 1.0D, 0.94D);
        if (distance < 32.0D * 32.0D) {
            if (witch.isAtonement()) {
                AtonementVisuals.frame(witch, pose, buffers, camera, ticks);
            } else if (witch.isCandeloro()) {
                CandeloroVisuals.orbit(pose, buffers, camera, ticks);
            } else {
                WitchAura.orbit(sprites, style, ticks, visualHeight);
            }
        }
        WitchSkillVisuals.boss(witch, partialTick, pose, buffers, camera);
    }

    @Override
    protected void renderNameTag(MadokaWitchEntity witch, Component name, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        double lift = 0.0D;
        if (witch.isSalvation()) {
            lift = MadokaWitchEntity.SALVATION_MODEL_HEIGHT - MadokaWitchEntity.SALVATION_MODEL_LIFT - witch.getBbHeight();
        } else if (witch.isTakingWitch()) {
            lift = 0.72D;
        } else if (witch.isButterflyWitch()) {
            lift = 0.24D;
        } else if (witch.isMermaid()) {
            lift = MadokaWitchEntity.MERMAID_MODEL_HEIGHT - witch.getBbHeight();
        } else if (witch.isAtonement()) {
            lift = MadokaWitchEntity.ATONEMENT_MODEL_HEIGHT - witch.getBbHeight();
        }
        if (lift == 0.0D) {
            super.renderNameTag(witch, name, pose, buffers, light);
            return;
        }
        pose.pushPose();
        pose.translate(0, lift, 0);
        super.renderNameTag(witch, name, pose, buffers, light);
        pose.popPose();
    }
}

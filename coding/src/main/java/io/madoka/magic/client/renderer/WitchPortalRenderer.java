package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.WitchPortalEntity;
import io.madoka.magic.entity.WitchStage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumMap;
import java.util.Map;

/**
 * Plays the PixelFX void-orb swirl loop, tinted per witch theme.
 *
 * <p>绘制放在 {@code AFTER_TRANSLUCENT_BLOCKS} 阶段（半透明地形之后）：
 * 实体阶段画的半透明层会被之后渲染的水等半透明方块整体盖住，
 * 挪到水后面画就不再被水挡住。</p>
 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT)
public final class WitchPortalRenderer extends EntityRenderer<WitchPortalEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/particle/witch_portal_swirl.png");
    private static final RenderType LAYER = RenderType.entityTranslucentEmissive(TEXTURE);
    private static final int FRAMES = 16;
    private static final int COLUMNS = 4;
    private static final int ROWS = 4;
    private static final int EXIT_TINT = 0x9fe8d8;
    private static final ResourceLocation PALETTE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "visuals/portal_stages.json");
    private static volatile Map<WitchStage, Integer> colors = Map.of();

    public static void reload(ResourceManager resources) {
        try (var reader = resources.openAsReader(PALETTE)) {
            var json = GsonHelper.parse(reader);
            var loaded = new EnumMap<WitchStage, Integer>(WitchStage.class);
            for (var stage : WitchStage.values()) {
                String color = GsonHelper.getAsString(json, stage.key());
                if (!color.matches("#[0-9a-fA-F]{6}")) {
                    throw new IllegalArgumentException("Invalid portal color for " + stage.key());
                }
                loaded.put(stage, Integer.parseInt(color.substring(1), 16));
            }
            colors = Map.copyOf(loaded);
        } catch (Exception error) {
            LogUtils.getLogger().error("Could not reload portal stage colors from {}", PALETTE, error);
        }
    }

    public WitchPortalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(WitchPortalEntity portal, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        // 实体阶段不再画，统一由 onRenderLevel 在半透明地形之后绘制。
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        var client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            return;
        }
        var eye = event.getCamera().getPosition();
        var buffers = client.renderBuffers().bufferSource();
        var pose = event.getPoseStack();
        boolean drew = false;
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof WitchPortalEntity portal)) {
                continue;
            }
            pose.pushPose();
            pose.translate(portal.getX() - eye.x, portal.getY() - eye.y, portal.getZ() - eye.z);
            draw(portal, event.getPartialTick(), pose, buffers, event.getCamera());
            pose.popPose();
            drew = true;
        }
        if (drew) {
            buffers.endBatch(LAYER);
            buffers.endBatch(RitualSprites.LAYER);
        }
    }

    private static void draw(WitchPortalEntity portal, float partialTick, PoseStack pose,
                             MultiBufferSource buffers, Camera camera) {
        double time = portal.tickCount + (double) partialTick + (portal.getId() & 15);
        boolean entrance = portal.isEntrance();
        int tick = Mth.floor(time * 0.85D) % FRAMES;
        // 出口反向播，和入口区分开。
        int frame = entrance ? tick : (FRAMES - 1 - tick);
        var style = RitualVisualStyle.forTheme(portal.getThemeId());
        // 主层按魔女阶段染色（数据驱动调色板），调色板缺失时退回原默认色。
        int tint = colors.getOrDefault(WitchStage.forTheme(portal.getThemeId()),
                entrance ? style.crown() : EXIT_TINT);
        var sprites = new RitualSprites(pose, buffers, camera, LAYER);
        double width = portal.getBbWidth();
        double height = portal.getBbHeight();
        // 外层反向缓慢旋转，形成向外流动的内外双层漩涡。
        int backFrame = (frame + FRAMES / 2) % FRAMES;
        sprites.drawTile(backFrame, COLUMNS, ROWS, 96, 128, 0, height * 0.5D, 0,
                width * 0.95D, height * 0.75D, -time * 0.55D, style.primary(), 0.42D);
        sprites.drawTile(frame, COLUMNS, ROWS, 96, 128, 0, height * 0.5D, 0,
                width * 0.73D, height * 0.55D, 0, tint, 1.0D);

        // 传送门周围漂浮对应魔女的性质粒子：本体魔女用各自的 motif 粒子
        // （与半魔女化/魔女光环同一套），联动 Boss 用 orbit 图集里的专属粒子。
        if (style.orbit() != null) {
            ExternalWitchParticles.render(pose, buffers, camera, style, time, width * 1.25D, height);
        } else {
            var aura = new RitualSprites(pose, buffers, camera);
            WitchAura.orbit(aura, style, time, height);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(WitchPortalEntity portal) {
        return TEXTURE;
    }
}

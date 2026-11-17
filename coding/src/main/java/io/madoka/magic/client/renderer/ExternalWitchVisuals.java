package io.madoka.magic.client.renderer;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.entity.GluttonyWitchEntity;
import io.madoka.magic.entity.MadokaWitchEntity;
import io.madoka.magic.entity.WitchTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 外部首领（灾变 / Alex 的洞穴）魔女化后的星环与性质粒子。
 *
 * <p>这些首领用各自模组的渲染器，本类在 {@code RenderLivingEvent.Post}
 * 里统一补画：结界维度内、且是该结界对应首领的实体，
 * 头顶绘制主题色四芒星头环，周身环绕主题色性质粒子。
 * 头环随首领体型等比放大。</p>
 *
 * <p>凋零（悼亡魔女）与原生魔女实体有自己的渲染逻辑，这里跳过。</p>
 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT)
public final class ExternalWitchVisuals {
    private ExternalWitchVisuals() {}

    @SubscribeEvent
    public static void onRender(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof WitherBoss || entity instanceof MadokaWitchEntity
                || entity instanceof GluttonyWitchEntity) {
            return;
        }
        String barrier = MadokaDimensions.barrierTheme(entity.level().dimension());
        if (barrier.isEmpty() || !barrier.equals(WitchTypes.bossTheme(entity))) {
            return;
        }
        var client = Minecraft.getInstance();
        if (client.player == null || entity.isInvisibleTo(client.player)) {
            return;
        }
        var camera = client.gameRenderer.getMainCamera();
        if (camera.getPosition().distanceToSqr(entity.position()) > 64.0D * 64.0D) {
            return;
        }
        double height = entity.getBbHeight();
        double scale = Mth.clamp(height / 3.5D, 1.0D, 2.5D);
        double ticks = entity.tickCount + (double) event.getPartialTick();
        var style = RitualVisualStyle.forTheme(barrier);
        var sprites = new RitualSprites(event.getPoseStack(), event.getMultiBufferSource(), camera);
        WitchAura.crown(sprites, style, 0.0D, height + 0.16D * scale, 0.0D, ticks, 1.2D * scale, 0.94D);
        WitchAura.orbit(sprites, style, ticks, height);
    }
}

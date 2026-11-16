package io.madoka.magic.client.renderer;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.network.ButterflyMarkState;
import io.madoka.magic.spell.ButterflyMark;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT)
public final class ButterflyMarkVisuals {
    private static final RenderType LAYER = RenderType.entityTranslucentEmissive(ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/particle/butterfly_orbit.png"));
    private static final Map<UUID, Orbit> ORBITS = new HashMap<>();
    private static final double VIEW_DISTANCE_SQR = 48.0D * 48.0D;

    private record Orbit(ClientLevel level, ButterflyMarkState state, long expires) {}

    private ButterflyMarkVisuals() {}

    public static void accept(ButterflyMarkState state) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || state.layers() <= 0 || state.remaining() <= 0) {
            ORBITS.remove(state.uuid());
        } else {
            ORBITS.put(state.uuid(), new Orbit(level, state, level.getGameTime() + state.remaining()));
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientLevel level = Minecraft.getInstance().level;
            ORBITS.values().removeIf(orbit -> orbit.level() != level || level.getGameTime() >= orbit.expires());
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ORBITS.isEmpty()) {
            return;
        }
        var client = Minecraft.getInstance();
        var level = client.level;
        var camera = event.getCamera();
        var eye = camera.getPosition();
        var buffers = client.renderBuffers().bufferSource();
        var pose = event.getPoseStack();
        boolean drew = false;
        for (Orbit orbit : ORBITS.values()) {
            var state = orbit.state();
            if (orbit.level() != level || !(level.getEntity(state.entityId()) instanceof LivingEntity target)
                    || !target.getUUID().equals(state.uuid()) || !target.isAlive()
                    || eye.distanceToSqr(target.position()) > VIEW_DISTANCE_SQR) {
                continue;
            }
            var center = target.getPosition(event.getPartialTick()).subtract(eye);
            double time = level.getGameTime() + event.getPartialTick();
            double alpha = Math.min(1.0D, (orbit.expires() - time) / 10.0D);
            int count = Math.min(ButterflyMark.MAX_LAYERS, state.layers());
            double radius = Math.max(0.55D, target.getBbWidth() * 0.5D + 0.30D);
            pose.pushPose();
            pose.translate(center.x, center.y, center.z);
            var sprites = new RitualSprites(pose, buffers, camera, LAYER);
            for (int i = 0; i < count; i++) {
                double angle = time * 0.095D + target.getId() + i * WitchAura.TURN / count;
                int frame = Math.sin(time * 1.25D + i) >= 0.0D ? 0 : 1;
                double bob = 0.54D + 0.16D * Math.sin(angle * 2.0D);
                sprites.drawTile(frame, 2, 1, radius * Math.cos(angle), target.getBbHeight() * bob,
                        radius * Math.sin(angle), 0.25D, Math.sin(angle) * 0.28D, 0xFFFFFF, alpha);
            }
            pose.popPose();
            drew = true;
        }
        if (drew) {
            buffers.endBatch(LAYER);
        }
    }
}

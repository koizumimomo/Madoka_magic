package io.madoka.magic.client.renderer;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.item.RitualItemAppearance;
import io.madoka.magic.network.RitualCraftFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT)
public final class AltarRitualVisuals {
    private static final int DURATION = 38;
    private static final int MOTES = 18;
    private static final int MAX_CUES = 40;
    private static final Map<BlockPos, Cue> CUES = new LinkedHashMap<>();

    private record Cue(ClientLevel level, RitualItemAppearance appearance, long started) {}

    private AltarRitualVisuals() {}

    public static void start(RitualCraftFx cue) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(cue.dimension())) {
            return;
        }
        CUES.remove(cue.pos());
        if (CUES.size() >= MAX_CUES) {
            CUES.remove(CUES.keySet().iterator().next());
        }
        CUES.put(cue.pos(), new Cue(level, cue.appearance(), level.getGameTime()));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientLevel level = Minecraft.getInstance().level;
            CUES.values().removeIf(cue -> cue.level() != level || level.getGameTime() - cue.started() >= DURATION);
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || CUES.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        var camera = event.getCamera();
        var eye = camera.getPosition();
        var pose = event.getPoseStack();
        var buffers = client.renderBuffers().bufferSource();
        Set<RenderType> drawn = new HashSet<>();
        for (var entry : CUES.entrySet()) {
            var cue = entry.getValue();
            if (cue.level() != client.level) {
                continue;
            }
            Vec3 center = Vec3.atBottomCenterOf(entry.getKey());
            if (eye.distanceToSqr(center) > RitualCraftFx.VIEW_DISTANCE * RitualCraftFx.VIEW_DISTANCE) {
                continue;
            }
            double age = cue.level().getGameTime() - cue.started() + event.getPartialTick();
            double phase = Mth.clamp(age / DURATION, 0.0D, 1.0D);
            var particles = new RitualItemParticles(cue.appearance());
            pose.pushPose();
            pose.translate(center.x - eye.x, center.y - eye.y, center.z - eye.z);
            var sprites = new RitualSprites(pose, buffers, camera, particles.layer());
            drawOrbit(particles, sprites, age, phase);
            pose.popPose();
            drawn.add(particles.layer());
        }
        drawn.forEach(buffers::endBatch);
    }

    private static void drawOrbit(RitualItemParticles particles, RitualSprites sprites, double age, double phase) {
        double open = WitchAura.smooth(phase / 0.22D);
        double converge = WitchAura.smooth((phase - 0.57D) / 0.43D);
        double opacity = open * (1.0D - converge);
        for (int i = 0; i < MOTES; i++) {
            boolean outer = i % 2 == 0;
            double angle = i * WitchAura.TURN / MOTES + age * (outer ? 0.16D : -0.13D);
            double orbit = outer ? 1.18D : 0.86D;
            double radius = Mth.lerp(converge, Mth.lerp(open, 0.30D, orbit), 0.08D);
            double rise = outer ? 0.14D : 0.42D;
            double y = Mth.lerp(converge, 0.88D + rise + Math.sin(angle * 2.0D) * 0.14D, 1.48D);
            particles.world(sprites, i, age, Math.cos(angle) * radius, y, Math.sin(angle) * radius,
                    outer ? 0.16D : 0.112D, Math.sin(angle) * 0.32D, opacity);
        }
    }
}

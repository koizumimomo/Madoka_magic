package io.madoka.magic.client.renderer;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.client.ClientMadokaState;
import io.madoka.magic.client.renderer.RitualVisualStyle.Motion;
import io.madoka.magic.client.renderer.RitualVisualStyle.Sprite;
import io.madoka.magic.network.TransformationFx;
import io.madoka.magic.network.PlayerFormState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT)
public final class TransformationVisuals {
    private static final int MAX_CUES = 48;
    private static final int SPIRAL_MOTES = 18;
    private static final double VIEW_RADIUS = 48.0D;
    private static final Map<UUID, Cue> CUES = new LinkedHashMap<>();
    private static final Map<UUID, Form> FORMS = new HashMap<>();

    private record Cue(ClientLevel level, TransformationFx effect, long started) {
        int duration() {
            return switch (effect.kind()) {
                case TRANSFORM -> 28;
                case WITCH -> 40;
                case HALF_WITCH -> HalfWitchVisuals.BURST_TICKS;
            };
        }
    }

    private record Form(ClientLevel level, PlayerFormState state, long received) {
    }

    private TransformationVisuals() {
    }

    public static void start(TransformationFx effect) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            CUES.remove(effect.playerId());
            if (CUES.size() >= MAX_CUES) {
                CUES.remove(CUES.keySet().iterator().next());
            }
            CUES.put(effect.playerId(), new Cue(level, effect, level.getGameTime()));
        }
    }

    public static void updateForm(PlayerFormState state) {
        ClientLevel level = Minecraft.getInstance().level;
        if (state.transformed() && level != null) {
            FORMS.put(state.playerId(), new Form(level, state, level.getGameTime()));
        } else {
            FORMS.remove(state.playerId());
        }
        var cue = CUES.get(state.playerId());
        if (cue != null && switch (cue.effect().kind()) {
            case TRANSFORM -> !state.transformed() || state.halfWitch();
            case HALF_WITCH -> !state.halfWitch();
            case WITCH -> false;
        }) {
            CUES.remove(state.playerId());
        }
    }

    public static String magicalGirlTheme(AbstractClientPlayer player) {
        var form = trackedForm(player);
        return form != null && !form.state().witch() ? form.state().theme() : "";
    }

    static String accessoryTheme(AbstractClientPlayer player) {
        var form = trackedForm(player);
        if (form != null && form.state().halfWitch()) {
            return "";
        }
        if (player == Minecraft.getInstance().player) {
            return ClientMadokaState.isContracted() && ClientMadokaState.isTransformed() && !ClientMadokaState.isWitchPath()
                    ? ClientMadokaState.getThemeId() : "";
        }
        return form != null && !form.state().witch() ? form.state().theme() : "";
    }

    private static Form trackedForm(AbstractClientPlayer player) {
        var form = FORMS.get(player.getUUID());
        return form != null && form.level() == player.level() && form.state().entityId() == player.getId() ? form : null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientLevel level = Minecraft.getInstance().level;
            CUES.values().removeIf(cue -> cue.level() != level || level.getGameTime() - cue.started() >= cue.duration());
            FORMS.values().removeIf(form -> form.level() != level || level.getGameTime() - form.received() > 20
                    && level.getEntity(form.state().entityId()) == null);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || CUES.isEmpty() && FORMS.isEmpty() && !ClientMadokaState.isTransformed()) {
            return;
        }
        var client = Minecraft.getInstance();
        var level = client.level;
        if (level == null || client.player == null) {
            return;
        }
        var buffers = client.renderBuffers().bufferSource();
        var pose = event.getPoseStack();
        var camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        float partialTick = event.getPartialTick();
        boolean drewRitual = false;
        boolean drewHalf = false;

        for (Cue cue : CUES.values()) {
            if (cue.level() != level) {
                continue;
            }
            var effect = cue.effect();
            Vec3 center = effect.origin();
            double height = effect.height();
            boolean transforming = effect.kind() == TransformationFx.Kind.TRANSFORM;
            boolean halfWitch = effect.kind() == TransformationFx.Kind.HALF_WITCH;
            boolean firstPerson = false;
            if (level.getEntity(effect.entityId()) instanceof Player player && player.getUUID().equals(effect.playerId())) {
                if (halfWitch && (!player.isAlive() || player.isInvisibleTo(client.player))) {
                    continue;
                }
                if (transforming || halfWitch) {
                    center = player.getPosition(partialTick);
                    height = player.getBbHeight();
                }
                firstPerson = camera.getEntity() == player && client.options.getCameraType().isFirstPerson();
            } else if (halfWitch) {
                continue;
            }
            if (eye.distanceToSqr(center) > VIEW_RADIUS * VIEW_RADIUS) {
                continue;
            }
            double elapsed = level.getGameTime() - cue.started() + partialTick;
            double phase = Mth.clamp(elapsed / cue.duration(), 0.0D, 1.0D);
            pose.pushPose();
            pose.translate(center.x - eye.x, center.y - eye.y, center.z - eye.z);
            if (halfWitch) {
                var sprites = new RitualSprites(pose, buffers, camera, HalfWitchVisuals.LAYER);
                HalfWitchVisuals.burst(sprites, effect.theme(), height, phase, firstPerson);
                pose.popPose();
                drewHalf = true;
                continue;
            }
            var form = FORMS.get(effect.playerId());
            boolean witch = client.player.getUUID().equals(effect.playerId())
                    ? ClientMadokaState.isWitchPath() : form != null && form.state().witch();
            var style = transforming ? RitualVisualStyle.forPlayer(effect.theme(), witch)
                    : RitualVisualStyle.forTheme(effect.theme());
            RitualSprites sprites = new RitualSprites(pose, buffers, camera);
            spiral(sprites, style, phase, height, transforming, firstPerson);
            if (!transforming && !firstPerson) {
                double crown = WitchAura.smooth((phase - 0.69D) / 0.10D);
                double fade = 1.0D - WitchAura.smooth((phase - 0.92D) / 0.08D);
                WitchAura.crown(sprites, style, 0.0D, height + 0.14D, 0.0D, elapsed,
                        crown, crown * fade);
            }
            pose.popPose();
            drewRitual = true;
        }
        for (var player : level.players()) {
            var form = FORMS.get(player.getUUID());
            boolean local = player == client.player;
            boolean tracked = form != null && form.level() == level
                    && form.state().entityId() == player.getId();
            boolean active = local ? ClientMadokaState.isTransformed() : tracked;
            if (!active || !player.isAlive() || player.isInvisibleTo(client.player)
                    || eye.distanceToSqr(player.position()) > VIEW_RADIUS * VIEW_RADIUS) {
                continue;
            }
            var cue = CUES.get(player.getUUID());
            if (cue != null && cue.level() == level && cue.effect().kind() == TransformationFx.Kind.TRANSFORM) {
                continue;
            }
            boolean witch = local ? ClientMadokaState.isWitchPath() : form.state().witch();
            boolean halfWitch = tracked && form.state().halfWitch();
            String theme = local ? ClientMadokaState.getThemeId() : form.state().theme();
            boolean firstPerson = camera.getEntity() == player && client.options.getCameraType().isFirstPerson();
            Vec3 center = player.getPosition(partialTick);
            double ticks = player.tickCount + (double) partialTick + player.getId() % 180;
            pose.pushPose();
            pose.translate(center.x - eye.x, center.y - eye.y, center.z - eye.z);
            if (halfWitch || witch) {
                var sprites = new RitualSprites(pose, buffers, camera, HalfWitchVisuals.LAYER);
                HalfWitchVisuals.orbit(sprites, theme, player.getBbHeight(), ticks, firstPerson);
                drewHalf = true;
            } else {
                var sprites = new RitualSprites(pose, buffers, camera);
                var style = RitualVisualStyle.forPlayer(theme, witch);
                WitchAura.orbit(sprites, style, ticks, player.getBbHeight());
                if (witch && !firstPerson) {
                    WitchAura.crown(sprites, style, 0, player.getBbHeight() + 0.14D, 0, ticks, 1.0D, 0.94D);
                }
                drewRitual = true;
            }
            pose.popPose();
        }
        if (drewRitual) {
            buffers.endBatch(RitualSprites.LAYER);
        }
        if (drewHalf) {
            buffers.endBatch(HalfWitchVisuals.LAYER);
        }
    }

    private static void spiral(RitualSprites sprites, RitualVisualStyle style, double phase, double height,
                               boolean transforming, boolean firstPerson) {
        double alpha = WitchAura.fade(phase);
        for (int index = 0; index < SPIRAL_MOTES; index++) {
            double along = (double) index / SPIRAL_MOTES;
            double angle = along * WitchAura.TURN;
            double radius;
            double y;
            if (transforming) {
                double rise = WitchAura.smooth(phase / 0.66D);
                double scatter = WitchAura.smooth((phase - 0.61D) / 0.39D);
                angle += (rise + scatter * 0.30D) * WitchAura.TURN;
                radius = 0.57D + 0.25D * rise + scatter * (0.59D + along * 0.24D);
                y = 0.08D + height * (rise * 0.72D + along * 0.19D) + scatter * 0.24D;
            } else {
                double flare = WitchAura.smooth(phase / 0.32D);
                double collapse = WitchAura.smooth((phase - 0.32D) / 0.54D);
                angle += (flare * 0.32D - collapse * 0.90D) * WitchAura.TURN;
                radius = (0.32D + flare * 0.88D) * (1.0D - collapse * 0.93D);
                y = height * (0.44D + along * 0.24D + collapse * (0.59D - along * 0.24D));
            }
            if (firstPerson && radius < 0.45D && y > height * 0.68D) {
                continue;
            }
            boolean accent = index % 3 == 0;
            Sprite sprite = accent ? style.accent() : style.motif();
            int color = accent ? style.secondary() : style.primary();
            if (!accent && style.motion() == Motion.SHED) {
                sprite = WitchAura.butterfly(phase * 28.0D + index);
                color = 0xffffff;
            }
            sprites.draw(sprite, radius * Math.cos(angle), y, radius * Math.sin(angle),
                    accent ? 0.10D : 0.155D, Math.sin(angle) * 0.32D, color, alpha);
        }
    }
}

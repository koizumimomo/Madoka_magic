package io.madoka.magic.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.madoka.magic.entity.MourningWitchCombat;
import io.madoka.magic.registry.ModParticles;
import io.madoka.magic.registry.ModParticles.Devotion;
import io.madoka.magic.registry.ModParticles.Take;
import io.madoka.magic.registry.ModParticles.Witch;
import net.minecraft.client.Camera;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

public final class DevotionParticle extends TextureSheetParticle {
    private enum Surface { BILLBOARD, GROUND }

    private record Style(int ticks, float size, float gravity, float spin, Surface surface) {
        Style(int ticks, float size) {
            this(ticks, size, 0, 0, Surface.BILLBOARD);
        }
    }

    private final SpriteSet frames;
    private final Style style;
    private final float spin;

    private DevotionParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                             SpriteSet frames, Style style, float scale) {
        super(level, x, y, z);
        this.frames = frames;
        this.style = style;
        spin = style.spin() * (random.nextBoolean() ? 1.0F : -1.0F);
        xd = dx;
        yd = dy;
        zd = dz;
        hasPhysics = false;
        friction = 0.93F;
        gravity = style.gravity();
        quadSize = style.size() * scale;
        setSize(quadSize * 2.0F, style.surface() == Surface.GROUND ? 0.08F : quadSize);
        lifetime = style.ticks();
        setSprite(frames.get(0, lifetime - 1));
    }

    public static void register(RegisterParticleProvidersEvent event) {
        for (Devotion effect : Devotion.values()) {
            Style style = switch (effect) {
                case SLASH -> new Style(13, 2.55F);
                case BUTTERFLY -> new Style(16, 0.55F, -0.012F, 0.018F, Surface.BILLBOARD);
                case MARK -> new Style(12, 0.35F);
                case TULIP -> new Style(16, 0.62F);
                case POLLEN -> new Style(9, 0.37F);
                case BLOOM -> new Style(18, 0.92F);
                case CLOSE -> new Style(13, 0.78F);
                case FALL -> new Style(19, 0.92F);
                case PETAL -> new Style(20, 0.12F, 0.18F, 0.16F, Surface.BILLBOARD);
                case WITHER_LIFE, WITHER_TAINT -> new Style(16, 4.65F, 0, 0, Surface.GROUND);
            };
            boolean scaled = switch (effect) {
                case SLASH, TULIP, POLLEN, BLOOM, CLOSE, FALL, WITHER_LIFE, WITHER_TAINT -> true;
                default -> false;
            };
            register(event, effect.get(), style, scaled);
        }
        register(event, Take.HONEY.get(), new Style(12, 0.20F, -0.015F, 0.11F, Surface.BILLBOARD), false);
        register(event, Take.SEED.get(), new Style(6, 0.22F), false);
        register(event, Take.ILLUSION.get(), new Style(12, 0.28F, -0.012F, 0.07F, Surface.BILLBOARD), false);
        // 法阵上升光点：单像素短线条，微小 spin 只是让 tick 里的淡出生效
        for (ModParticles.CircleStreak effect : ModParticles.CircleStreak.values()) {
            register(event, effect.get(), new Style(16, 0.40F, 0, 0.0001F, Surface.BILLBOARD), false);
        }
        for (Witch effect : Witch.values()) {
            Style style = switch (effect) {
                case SLASH -> new Style(13, 2.55F);
                case MOTE -> new Style(16, 0.55F, -0.012F, 0.018F, Surface.BILLBOARD);
                case MARK -> new Style(12, 0.35F);
                case VENOM -> new Style(12, 0.20F, -0.015F, 0.11F, Surface.BILLBOARD);
                case PULSE -> new Style(9, 0.37F);
                case TEAR -> new Style(22, 0.15F, 2.6F, 0.0F, Surface.BILLBOARD);
            };
            boolean scaled = switch (effect) {
                case SLASH, MARK, PULSE -> true;
                default -> false;
            };
            register(event, effect.get(), style, scaled);
        }
        register(event, ModParticles.MOURNING_FOOTPRINT.get(),
                new Style(MourningWitchCombat.FOOTPRINT_WARNING_TICKS, 1.0F, 0, 0, Surface.GROUND), true);
        register(event, ModParticles.MOURNING_ROSE.get(), new Style(18, 0.82F), false);
    }

    private static void register(RegisterParticleProvidersEvent event, SimpleParticleType type, Style style, boolean scaled) {
        event.registerSpriteSet(type, sprites -> (options, level, x, y, z, dx, dy, dz) ->
                scaled ? new DevotionParticle(level, x, y, z, 0.0D, 0.0D, 0.0D, sprites, style,
                        dx > 0.0D ? Mth.clamp((float) dx, 0.25F, 5.0F) : 1.0F)
                        : new DevotionParticle(level, x, y, z, dx, dy, dz, sprites, style, 1.0F));
    }

    @Override
    public void tick() {
        super.tick();
        if (!isAlive()) {
            return;
        }
        oRoll = roll;
        roll += spin;
        setSprite(frames.get(Math.min(age, lifetime - 1), lifetime - 1));
        if (spin != 0.0F) {
            setAlpha(Mth.clamp((lifetime - age) / (lifetime * 0.28F), 0.0F, 1.0F));
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        if (style.surface() == Surface.BILLBOARD) {
            super.render(buffer, camera, partialTick);
            return;
        }
        Vec3 eye = camera.getPosition();
        float cx = (float) (Mth.lerp(partialTick, xo, x) - eye.x);
        float cy = (float) (Mth.lerp(partialTick, yo, y) - eye.y);
        float cz = (float) (Mth.lerp(partialTick, zo, z) - eye.z);
        float size = getQuadSize(partialTick);
        vertex(buffer, cx - size, cy, cz + size, getU0(), getV1());
        vertex(buffer, cx + size, cy, cz + size, getU1(), getV1());
        vertex(buffer, cx + size, cy, cz - size, getU1(), getV0());
        vertex(buffer, cx - size, cy, cz - size, getU0(), getV0());
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v) {
        buffer.vertex(x, y, z).uv(u, v).color(rCol, gCol, bCol, alpha).uv2(getLightColor(0)).endVertex();
    }
}

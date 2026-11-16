package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.MadokaWitchEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

final class WitchSkillVisuals {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/particle/witch_skills.png");
    static final RenderType LAYER = RenderType.entityTranslucentEmissive(TEXTURE);
    private static final int COLUMNS = 4;

    private WitchSkillVisuals() {
    }

    static void cocoon(PoseStack pose, MultiBufferSource buffers, Camera camera, double height, double time) {
        var sprites = new RitualSprites(pose, buffers, camera, LAYER);
        sprites.drawTile(0, COLUMNS, 1, 32, 32, 0, height * 0.51D, 0, 0.62D, height * 0.54D,
                Math.sin(time * 0.10D) * 0.07D, 0xffffff, 1.0D);
    }

    static void node(PoseStack pose, MultiBufferSource buffers, Camera camera, double height, double time) {
        var sprites = new RitualSprites(pose, buffers, camera, LAYER);
        double bob = Math.sin(time * 0.17D) * 0.052D;
        sprites.drawTile(1, COLUMNS, 1, 32, 32, 0, height * 0.49D + bob, 0, 0.56D, height * 0.54D,
                0.0D, 0xffffff, 1.0D);
    }

    static void boss(MadokaWitchEntity witch, float partialTick, PoseStack pose, MultiBufferSource buffers, Camera camera) {
        int[] links = witch.getSkillLinks();
        if (links.length == 0) {
            return;
        }
        var sprites = new RitualSprites(pose, buffers, camera, LAYER);
        Vec3 origin = witch.getPosition(partialTick);
        Vec3 eye = camera.getPosition().subtract(origin);
        Vec3 start = new Vec3(0, witch.getBbHeight() * 0.63D, 0);
        double ticks = witch.tickCount + (double) partialTick;
        int color = "take".equals(witch.getThemeId()) ? 0xffa45b : 0xffdb83;
        for (int id : links) {
            if (!(witch.level().getEntity(id) instanceof LivingEntity target) || !target.isAlive()) {
                continue;
            }
            Vec3 end = target.getPosition(partialTick).subtract(origin).add(0, target.getBbHeight() * 0.54D, 0);
            double pulse = (ticks / 16.0D + id * 0.19D) % 1.0D;
            sprites.ribbon(3, COLUMNS, start, end, eye, 0.135D, color, 0.83D);
            sprites.ribbon(3, COLUMNS, start.lerp(end, pulse), start.lerp(end, Math.min(1.0D, pulse + 0.14D)),
                    eye, 0.20D, 0xffedbd, WitchAura.fade(pulse));
        }
    }

    static void drawRain(RitualSprites sprites, double radius, boolean falling, double ticks) {
        for (int i = 0; i < 24; i++) {
            double angle = i * WitchAura.TURN / 24.0D;
            double y = 0.21D + 0.10D * Math.sin(ticks * 0.15D + i * 0.9D);
            sprites.drawTile(2, COLUMNS, 1, radius * Math.cos(angle), y, radius * Math.sin(angle),
                    0.15D, 0, 0xffffff, 0.65D);
            if (falling) {
                double phase = (ticks / 12.0D + i * 0.618D) % 1.0D;
                double r = radius * (0.22D + 0.68D * ((i * 7 % 23) / 23.0D));
                sprites.drawTile(2, COLUMNS, 1, r * Math.cos(angle), 4.6D - phase * 4.3D,
                        r * Math.sin(angle), 0.17D, 0, 0xffffff, WitchAura.fade(phase));
            }
        }
    }
}

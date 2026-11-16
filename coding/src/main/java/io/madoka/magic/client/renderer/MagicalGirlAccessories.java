package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.madoka.magic.MadokaMagic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Accessories on the normal magical-girl form. */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT)
public final class MagicalGirlAccessories extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final double TAU = Math.PI * 2.0D;
    private static final double WAIST = 0.70D;
    private static final float SPECIMEN_SCALE = 0.9375F;
    private static final double SPECIMEN_HALF_WIDTH = 0.80D;
    private static final double SPECIMEN_HALF_HEIGHT = 1.28D;
    private static final double SPECIMEN_BODY_GAP = 0.70D;
    // The back of the vanilla torso is two model pixels behind the player origin.
    private static final double SPECIMEN_BACK_DEPTH = 2.0D / 16.0D * SPECIMEN_SCALE + SPECIMEN_BODY_GAP;
    private static final float SPECIMEN_ROLL = 30.0F;
    private static final double SPECIMEN_ROLL_RADIANS = Math.toRadians(SPECIMEN_ROLL);
    private static final double SPECIMEN_CENTER_HEIGHT = SPECIMEN_SCALE * (SPECIMEN_HALF_WIDTH * Math.sin(SPECIMEN_ROLL_RADIANS)
            + SPECIMEN_HALF_HEIGHT * Math.cos(SPECIMEN_ROLL_RADIANS)) + 0.04D;
    private static final double HONEYCOMB_CENTER_Y = -0.47D;
    private static final double HONEYCOMB_DEPTH = 0.284D;
    private static final double HONEYCOMB_CENTER_RADIUS = 0.50D;
    private static final double HONEYCOMB_PETAL_RADIUS = 0.235D;
    private static final double HONEYCOMB_PETAL_DISTANCE = 0.76D;
    private static final float HONEYCOMB_ROTATION_TICKS = 440.0F;
    private static final double VIEW_DISTANCE = 48.0D;
    private static final int WHITE = 0xffffff;
    private static final int VINE_SEGMENTS = 32;
    private static final int VINE_SIDES = 5;
    private static final RenderType SPECIMEN = layer("butterfly_specimen");
    private static final RenderType HONEYCOMB = layer("honeycomb");
    private static final RenderType THORNS = layer("thorn_vine");
    private static final Uv WAX = new Uv(0.5F / 64F, 32.5F / 64F, 15.5F / 64F, 47.5F / 64F);
    private static final Uv THORN = new Uv(16.5F / 32F, 31.5F / 32F, 31.5F / 32F, 0.5F / 32F);
    private static final List<Face> FRAME = specimenFrame();
    private static final List<Face> CELLS = honeycomb();
    private static final List<Face> VINE = thornVine();

    private MagicalGirlAccessories(PlayerRenderer renderer) {
        super(renderer);
    }

    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new MagicalGirlAccessories(renderer));
            }
        }
    }

    @SubscribeEvent
    public static void renderSpecimen(RenderPlayerEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player) || !canShow(player)
                || !"sacrifice".equals(TransformationVisuals.accessoryTheme(player))) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        float partialTick = event.getPartialTick();
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        double renderOffsetY = event.getRenderer().getRenderOffset(player, partialTick).y;
        pose.pushPose();
        try {
            // Follow the player's facing with a vertical plane independent of the torso's pitch.
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
            pose.translate(0, SPECIMEN_CENTER_HEIGHT - renderOffsetY, SPECIMEN_BACK_DEPTH + chestArmorClearance(player));
            pose.scale(-SPECIMEN_SCALE, -SPECIMEN_SCALE, SPECIMEN_SCALE);
            pose.mulPose(Axis.ZP.rotationDegrees(-SPECIMEN_ROLL));
            draw(FRAME, pose.last(), event.getMultiBufferSource().getBuffer(SPECIMEN), accessoryLight(event.getPackedLight()));
        } finally {
            pose.popPose();
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbAmount, float partialTick, float age, float yaw, float pitch) {
        if (!canShow(player)) {
            return;
        }
        String theme = TransformationVisuals.accessoryTheme(player);
        if (!theme.equals("take") && !theme.equals("atonement")) {
            return;
        }
        int packedLight = accessoryLight(light);
        pose.pushPose();
        try {
            getParentModel().body.translateAndRotate(pose);
            switch (theme) {
                case "take" -> {
                    pose.translate(0, HONEYCOMB_CENTER_Y, HONEYCOMB_DEPTH + chestArmorClearance(player));
                    pose.mulPose(Axis.ZP.rotationDegrees(honeycombAngle(age)));
                    draw(CELLS, pose.last(), buffers.getBuffer(HONEYCOMB), packedLight);
                }
                case "atonement" -> {
                    pose.translate(0, WAIST + Math.sin(age * 0.082D) * 0.027D, 0);
                    pose.mulPose(Axis.YP.rotation((float) (-(age % 520.0F) * TAU / 520.0D)));
                    draw(VINE, pose.last(), buffers.getBuffer(THORNS), packedLight);
                }
                default -> { }
            }
        } finally {
            pose.popPose();
        }
    }

    private static boolean canShow(AbstractClientPlayer player) {
        var viewer = Minecraft.getInstance().player;
        return viewer != null && player.isAlive() && !player.isSpectator() && !player.isInvisibleTo(viewer)
                && player.distanceToSqr(viewer) <= VIEW_DISTANCE * VIEW_DISTANCE;
    }

    private static double chestArmorClearance(AbstractClientPlayer player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() ? 0 : 0.08D;
    }

    private static int accessoryLight(int light) {
        return LightTexture.pack(Math.max(8, LightTexture.block(light)), LightTexture.sky(light));
    }

    static float honeycombAngle(float age) {
        return (age % HONEYCOMB_ROTATION_TICKS) * 360.0F / HONEYCOMB_ROTATION_TICKS - 11.0F;
    }

    private static List<Face> specimenFrame() {
        double halfWidth = SPECIMEN_HALF_WIDTH;
        double halfHeight = SPECIMEN_HALF_HEIGHT;
        double depth = 0.097D;
        double corner = 0.08D;
        Uv glassSide = new Uv(0.5F / 128F, 0.5F / 256F, 95.5F / 128F, 151.5F / 256F);
        Uv backSide = new Uv(0.5F / 128F, 152.5F / 256F, 95.5F / 128F, 247.5F / 256F);
        Uv edge = new Uv(96.5F / 128F, 47.5F / 256F, 127.5F / 128F, 0.5F / 256F);
        List<Face> faces = new ArrayList<>();
        faces.add(new Face(new Vec3(-halfWidth, -halfHeight, depth), new Vec3(halfWidth, -halfHeight, depth),
                new Vec3(halfWidth, halfHeight, depth), new Vec3(-halfWidth, halfHeight, depth), glassSide, WHITE));
        faces.add(new Face(new Vec3(halfWidth, -halfHeight, 0), new Vec3(-halfWidth, -halfHeight, 0),
                new Vec3(-halfWidth, halfHeight, 0), new Vec3(halfWidth, halfHeight, 0), backSide, WHITE));
        Vec3[] outline = {new Vec3(-halfWidth + corner, -halfHeight, 0), new Vec3(halfWidth - corner, -halfHeight, 0),
                new Vec3(halfWidth, -halfHeight + corner, 0), new Vec3(halfWidth, halfHeight - corner, 0),
                new Vec3(halfWidth - corner, halfHeight, 0), new Vec3(-halfWidth + corner, halfHeight, 0),
                new Vec3(-halfWidth, halfHeight - corner, 0), new Vec3(-halfWidth, -halfHeight + corner, 0)};
        for (int i = 0; i < outline.length; i++) {
            Vec3 start = outline[i];
            Vec3 end = outline[(i + 1) % outline.length];
            faces.add(new Face(end, start, start.add(0, 0, depth), end.add(0, 0, depth), edge, WHITE));
        }
        return List.copyOf(faces);
    }

    private static List<Face> honeycomb() {
        List<Face> faces = new ArrayList<>();
        addCell(faces, Vec3.ZERO, HONEYCOMB_CENTER_RADIUS, 0.139D, 0, true);
        for (int cell = 0; cell < 6; cell++) {
            double angle = cell * TAU / 6.0D;
            Vec3 center = new Vec3(Math.cos(angle) * HONEYCOMB_PETAL_DISTANCE,
                    Math.sin(angle) * HONEYCOMB_PETAL_DISTANCE, 0.042D);
            addCell(faces, center, HONEYCOMB_PETAL_RADIUS, 0.088D, angle + Math.PI / 2.0D, cell % 2 == 0);
        }
        return List.copyOf(faces);
    }

    private static void addCell(List<Face> faces, Vec3 center, double radius, double depth, double rotation, boolean filled) {
        double halfWidth = radius * Math.sqrt(3.0D) / 2.0D;
        int tile = filled ? 32 : 0;
        var cap = new Uv((tile + 3F) / 64F, 1F / 64F, (tile + 29F) / 64F, 31F / 64F);
        float angle = (float) -rotation;
        Vec3 upperLeft = center.add(new Vec3(-halfWidth, -radius, 0).zRot(angle));
        Vec3 upperRight = center.add(new Vec3(halfWidth, -radius, 0).zRot(angle));
        Vec3 lowerRight = center.add(new Vec3(halfWidth, radius, 0).zRot(angle));
        Vec3 lowerLeft = center.add(new Vec3(-halfWidth, radius, 0).zRot(angle));
        faces.add(new Face(upperLeft.add(0, 0, depth), upperRight.add(0, 0, depth),
                lowerRight.add(0, 0, depth), lowerLeft.add(0, 0, depth), cap, WHITE));
        faces.add(new Face(upperRight, upperLeft, lowerLeft, lowerRight, cap, 0xbe8c63));
        for (int edge = 0; edge < 6; edge++) {
            double a = rotation - Math.PI / 2.0D + edge * TAU / 6.0D;
            double b = a + TAU / 6.0D;
            Vec3 start = center.add(Math.cos(a) * radius, Math.sin(a) * radius, 0);
            Vec3 end = center.add(Math.cos(b) * radius, Math.sin(b) * radius, 0);
            faces.add(new Face(end, start, start.add(0, 0, depth), end.add(0, 0, depth), WAX,
                    edge < 3 ? 0xebc69b : 0xb98764));
        }
    }

    private static List<Face> thornVine() {
        List<Face> faces = new ArrayList<>();
        Vec3[][] rings = new Vec3[VINE_SEGMENTS + 1][VINE_SIDES];
        for (int step = 0; step <= VINE_SEGMENTS; step++) {
            double progress = (double) step / VINE_SEGMENTS;
            double angle = vineAngle(progress);
            Vec3 center = vinePoint(progress);
            Vec3 tangent = vinePoint(progress + 0.001D).subtract(vinePoint(progress - 0.001D)).normalize();
            Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 across = tangent.cross(radial).normalize();
            Vec3 outward = across.cross(tangent).normalize();
            double taper = 0.014D + 0.045D * Math.sin(progress * Math.PI);
            for (int side = 0; side < VINE_SIDES; side++) {
                double around = side * TAU / VINE_SIDES;
                rings[step][side] = center.add(outward.scale(taper * Math.cos(around)))
                        .add(across.scale(taper * Math.sin(around)));
            }
            if (step >= 3 && step <= VINE_SEGMENTS - 3 && (step % 4 == 3 || step == 17)) {
                addThorn(faces, center.add(outward.scale(taper * 0.75D)), tangent, outward, across, step);
            }
        }
        for (int step = 0; step < VINE_SEGMENTS; step++) {
            for (int side = 0; side < VINE_SIDES; side++) {
                int next = (side + 1) % VINE_SIDES;
                float v0 = (step % 8 * 4.0F + 0.5F) / 32F;
                float v1 = (step % 8 * 4.0F + 3.5F) / 32F;
                var bark = new Uv((side * 3.0F + 0.5F) / 32F, v0, (side * 3.0F + 3.5F) / 32F, v1);
                faces.add(new Face(rings[step][side], rings[step][next], rings[step + 1][next],
                        rings[step + 1][side], bark, WHITE));
            }
        }
        return List.copyOf(faces);
    }

    private static double vineAngle(double progress) {
        return TAU * (1.24D * progress - 0.11D);
    }

    private static Vec3 vinePoint(double progress) {
        double angle = vineAngle(progress);
        double radius = 0.53D + 0.044D * Math.sin(0.7D + progress * TAU * 2.0D);
        double y = -0.54D + 1.02D * progress + 0.04D * Math.sin(angle * 2.0D);
        return new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * (radius - 0.056D));
    }

    private static void addThorn(List<Face> faces, Vec3 base, Vec3 tangent, Vec3 outward, Vec3 across, int index) {
        double lean = index % 8 == 3 ? 0.06D : -0.052D;
        Vec3 tip = base.add(outward.scale(0.17D + index % 3 * 0.026D)).add(tangent.scale(lean))
                .add(across.scale(0.055D));
        Vec3[] root = {base.add(tangent.scale(0.044D)), base.add(across.scale(0.036D)),
                base.add(tangent.scale(-0.044D)), base.add(across.scale(-0.036D))};
        for (int side = 0; side < root.length; side++) {
            faces.add(new Face(root[side], root[(side + 1) % root.length], tip, tip,
                    THORN, side == 0 ? WHITE : 0xbfc6e1));
        }
    }

    private static void draw(List<Face> faces, PoseStack.Pose pose, VertexConsumer vertices, int light) {
        for (Face face : faces) {
            face.render(pose, vertices, light);
        }
    }

    private static RenderType layer(String name) {
        return RenderType.entityCutoutNoCull(ResourceLocation.fromNamespaceAndPath(
                MadokaMagic.MODID, "textures/entity/form/" + name + ".png"));
    }

    private record Uv(float u0, float v0, float u1, float v1) {}

    private record Face(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, Uv uv, int color) {
        private Face(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Uv uv, int color) {
            this(a, b, c, d, b.subtract(a).cross(c.subtract(a)).normalize(), uv, color);
        }

        private void render(PoseStack.Pose pose, VertexConsumer vertices, int light) {
            vertex(pose, vertices, a, uv.u0(), uv.v0(), light);
            vertex(pose, vertices, b, uv.u1(), uv.v0(), light);
            vertex(pose, vertices, c, uv.u1(), uv.v1(), light);
            vertex(pose, vertices, d, uv.u0(), uv.v1(), light);
        }

        private void vertex(PoseStack.Pose pose, VertexConsumer vertices, Vec3 point, float u, float v, int light) {
            vertices.vertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
                    .color(color >> 16 & 255, color >> 8 & 255, color & 255, 255)
                    .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(pose.normal(), (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
        }
    }
}

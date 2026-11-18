package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.compat.AlexCavesCompat;
import io.madoka.magic.dimension.MadokaDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.EntityRenderersEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Follows the optional Citadel head hierarchy without replacing Alex's Caves' renderer or animations. */
public final class AnnegretMaskLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final RenderType MASK = RenderType.entityCutoutNoCull(ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/annegret_mask.png"));
    private static final float[] X = {-4.5F, -1.95F, 1.95F, 4.5F};
    private static final float[] Z = {-3.95F, -7.12F, -7.12F, -3.95F};
    private static final float TOP = -9.2F;
    private static final float BOTTOM = 2.35F;

    private final List<Object> headChain;
    private final Method transform;
    private boolean enabled = true;

    private AnnegretMaskLayer(LivingEntityRenderer<T, M> renderer) throws ReflectiveOperationException {
        super(renderer);
        M model = renderer.getModel();
        var head = model.getClass().getDeclaredField("head");
        head.setAccessible(true);
        Object part = head.get(model);
        Method parent = part.getClass().getMethod("getParent");
        transform = part.getClass().getMethod("translateAndRotate", PoseStack.class);
        var parts = new ArrayList<>();
        while (part != null) {
            parts.add(0, part);
            part = parent.invoke(part);
        }
        headChain = List.copyOf(parts);
    }

    public static void register(EntityRenderersEvent.AddLayers event) {
        AlexCavesCompat.licowitchType().ifPresent(type -> {
            if (event.getEntityRenderer(type) instanceof LivingEntityRenderer<?, ?> renderer) addLayer(renderer);
        });
    }

    private static <T extends LivingEntity, M extends EntityModel<T>> void addLayer(LivingEntityRenderer<T, M> renderer) {
        try {
            renderer.addLayer(new AnnegretMaskLayer<>(renderer));
        } catch (ReflectiveOperationException exception) {
            LogUtils.getLogger().warn("Could not attach the Annegret mask to the Licowitch model", exception);
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T witch, float swing, float amount,
                       float partialTick, float age, float headYaw, float headPitch) {
        var player = Minecraft.getInstance().player;
        if (!enabled || player == null || witch.isInvisibleTo(player)
                || !"annegret".equals(MadokaDimensions.barrierTheme(witch.level().dimension()))) return;
        pose.pushPose();
        try {
            for (Object part : headChain) transform.invoke(part, pose);
            VertexConsumer out = buffers.getBuffer(MASK);
            int overlay = LivingEntityRenderer.getOverlayCoords(witch, 0);
            for (int face = 0; face < X.length - 1; face++) {
                float dx = X[face + 1] - X[face];
                float dz = Z[face + 1] - Z[face];
                float length = (float) Math.sqrt(dx * dx + dz * dz);
                vertex(pose.last(), out, face, TOP, 0, overlay, light, dz / length, -dx / length);
                vertex(pose.last(), out, face + 1, TOP, 0, overlay, light, dz / length, -dx / length);
                vertex(pose.last(), out, face + 1, BOTTOM, 1, overlay, light, dz / length, -dx / length);
                vertex(pose.last(), out, face, BOTTOM, 1, overlay, light, dz / length, -dx / length);
            }
        } catch (ReflectiveOperationException exception) {
            enabled = false;
            LogUtils.getLogger().warn("Could not pose the Annegret mask", exception);
        } finally {
            pose.popPose();
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, int point, float y, float v, int overlay,
                               int light, float nx, float nz) {
        float u = (X[point] - X[0]) / (X[X.length - 1] - X[0]);
        out.vertex(pose.pose(), X[point] / 16, y / 16, Z[point] / 16).color(255, 255, 255, 255)
                .uv(u, v).overlayCoords(overlay).uv2(light).normal(pose.normal(), nx, 0, nz).endVertex();
    }
}

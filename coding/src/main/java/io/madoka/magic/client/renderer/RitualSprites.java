package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.client.renderer.RitualVisualStyle.Sprite;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Upright, depth-tested sprites in the caller's entity- or camera-relative space. */
final class RitualSprites {
    static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/particle/ritual_visuals.png");
    static final RenderType LAYER = RenderType.entityTranslucentEmissive(ATLAS);

    static final int TILE_SIZE = 32;
    static final int COLUMNS = 8;
    static final int ROWS = 2;

    private final PoseStack.Pose pose;
    private final VertexConsumer buffer;
    private final float rightX;
    private final float rightZ;

    RitualSprites(PoseStack pose, MultiBufferSource buffers, Camera camera) {
        this(pose, buffers, camera, LAYER);
    }

    RitualSprites(PoseStack pose, MultiBufferSource buffers, Camera camera, RenderType layer) {
        this.pose = pose.last();
        buffer = buffers.getBuffer(layer);
        float yaw = camera.getYRot() * Mth.DEG_TO_RAD;
        rightX = Mth.cos(yaw);
        rightZ = Mth.sin(yaw);
    }

    void draw(Sprite sprite, double x, double y, double z, double size, double roll, int color, double alpha) {
        drawTile(sprite.tile, COLUMNS, ROWS, x, y, z, size, roll, color, alpha);
    }

    void drawTile(int tile, int columns, int rows, double x, double y, double z, double size, double roll,
                  int color, double alpha) {
        drawTile(tile, columns, rows, TILE_SIZE, TILE_SIZE, x, y, z, size, size, roll, color, alpha);
    }

    void drawTile(int tile, int columns, int rows, int tileWidth, int tileHeight,
                  double x, double y, double z, double halfWidth, double halfHeight, double roll, int color, double alpha) {
        if (alpha <= 0.01D || halfWidth <= 0.0D || halfHeight <= 0.0D) {
            return;
        }
        int row = tile / columns;
        int column = tile % columns;
        float u0 = (column * tileWidth + 0.5F) / (columns * tileWidth);
        float u1 = ((column + 1) * tileWidth - 0.5F) / (columns * tileWidth);
        float v0 = (row * tileHeight + 0.5F) / (rows * tileHeight);
        float v1 = ((row + 1) * tileHeight - 0.5F) / (rows * tileHeight);
        double cosine = Math.cos(roll);
        double sine = Math.sin(roll);
        float wx = (float) (cosine * halfWidth);
        float wy = (float) (sine * halfWidth);
        float hx = (float) (sine * halfHeight);
        float hy = (float) (cosine * halfHeight);
        int opacity = (int) (255 * Mth.clamp(alpha, 0.0D, 1.0D));
        vertex(x, y, z, hx - wx, -hy - wy, u0, v1, color, opacity);
        vertex(x, y, z, wx + hx, wy - hy, u1, v1, color, opacity);
        vertex(x, y, z, wx - hx, hy + wy, u1, v0, color, opacity);
        vertex(x, y, z, -wx - hx, hy - wy, u0, v0, color, opacity);
    }

    private void vertex(double x, double y, double z, float u, float v, float tx, float ty, int rgb, int alpha) {
        buffer.vertex(pose.pose(), (float) (x + rightX * u), (float) (y + v), (float) (z + rightZ * u))
                .color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha).uv(tx, ty)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), -rightZ, 0.0F, rightX).endVertex();
    }

    void ribbon(int tile, int columns, Vec3 start, Vec3 end, Vec3 camera, double width, int color, double alpha) {
        Vec3 path = end.subtract(start);
        if (alpha <= 0.01D || path.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 side = path.cross(camera.subtract(start.lerp(end, 0.5D)));
        if (side.lengthSqr() < 1.0E-6D) {
            side = path.cross(Math.abs(path.normalize().y) < 0.90D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0));
        }
        side = side.normalize().scale(width);
        Vec3 normal = side.cross(path).normalize();
        float u0 = (tile * TILE_SIZE + 0.5F) / (columns * TILE_SIZE);
        float u1 = ((tile + 1) * TILE_SIZE - 0.5F) / (columns * TILE_SIZE);
        float v0 = 0.5F / TILE_SIZE;
        float v1 = 1.0F - v0;
        int opacity = (int) (255 * Mth.clamp(alpha, 0.0D, 1.0D));
        ribbonVertex(start.subtract(side), u0, v0, normal, color, opacity);
        ribbonVertex(end.subtract(side), u1, v0, normal, color, opacity);
        ribbonVertex(end.add(side), u1, v1, normal, color, opacity);
        ribbonVertex(start.add(side), u0, v1, normal, color, opacity);
    }

    private void ribbonVertex(Vec3 point, float u, float v, Vec3 normal, int rgb, int alpha) {
        buffer.vertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
                .color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
    }
}

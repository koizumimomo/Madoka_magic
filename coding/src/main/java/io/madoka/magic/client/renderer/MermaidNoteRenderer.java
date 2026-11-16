package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.MermaidNoteEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class MermaidNoteRenderer extends EntityRenderer<MermaidNoteEntity> {
    private static final int FRAMES = 4;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/mermaid_note.png");
    private static final RenderType LAYER = RenderType.entityTranslucentEmissive(TEXTURE);

    public MermaidNoteRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MermaidNoteEntity note, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        double time = note.tickCount + (double) partialTick;
        var sprites = new RitualSprites(pose, buffers, Minecraft.getInstance().gameRenderer.getMainCamera(), LAYER);
        int frame = Math.floorMod((int) (time / 3 + note.getId()), FRAMES);
        int tile = note.variant() * FRAMES + frame;
        sprites.drawTile(tile, FRAMES, MermaidNoteEntity.VARIANTS, 32, 32, 0, 0, 0, 0.39D, 0.39D,
                Math.sin(time * 0.16D) * 0.16D, 0xffffff, 1.0D);
        super.render(note, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MermaidNoteEntity note) {
        return TEXTURE;
    }
}

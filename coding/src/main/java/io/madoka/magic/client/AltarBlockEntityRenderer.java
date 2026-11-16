package io.madoka.magic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.madoka.magic.block.AltarBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/**
 * 契约祭坛的渲染器：把放上去的材料**悬浮在祭坛上方**并缓慢旋转。
 *
 * <p>交互方式参考灵灾（Malum）的精魂祭坛：
 * 右键放上去 → 悬浮；右键空手 → 按放入顺序取回。</p>
 */
public class AltarBlockEntityRenderer implements BlockEntityRenderer<AltarBlockEntity> {

    private static final int ORBIT_TICKS = 120;
    private static final double ORBIT_RADIUS = 0.72D;
    /** 悬浮的起始高度。 */
    private static final float BASE_HEIGHT = 1.15F;
    /** 单件材料的缩放。 */
    private static final float ITEM_SCALE = 0.55F;

    @Override
    public void render(AltarBlockEntity altar, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        ItemStackHandler items = altar.getItems();
        var level = altar.getLevel();
        int count = altar.getItemCount();
        if (level == null || count == 0) {
            return;
        }
        double turns = (level.getGameTime() % ORBIT_TICKS + partialTick) / ORBIT_TICKS;
        double spacing = Math.PI * 2.0D / count;
        BlockPos pos = altar.getBlockPos();
        int index = 0;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            pose.pushPose();
            double angle = turns * Math.PI * 2.0D + index * spacing;
            double x = 0.5D + Math.cos(angle) * ORBIT_RADIUS;
            double y = BASE_HEIGHT + Math.sin(angle * 2.0D) * 0.06D;
            double z = 0.5D + Math.sin(angle) * ORBIT_RADIUS;
            pose.translate(x, y, z);
            int orbitLight = LevelRenderer.getLightColor(level,
                    BlockPos.containing(pos.getX() + x, pos.getY() + y, pos.getZ() + z));
            pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(angle)));
            pose.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    stack,
                    net.minecraft.world.item.ItemDisplayContext.GROUND,
                    orbitLight,
                    overlay,
                    pose,
                    buffer,
                    level,
                    slot);
            pose.popPose();
            index++;
        }
    }
}

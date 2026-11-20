package io.madoka.magic.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** 客户端键位。 */
public final class MadokaKeys {

    /** R 键：打开轮盘选择技能。 */
    public static final KeyMapping OPEN_WHEEL = new KeyMapping(
            "key.madoka_magic.open_wheel",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.madoka_magic");

    /** G 键：召唤半魔女附身（污染值 ≥ 附身花费时可用，成功后扣除花费）。 */
    public static final KeyMapping OPEN_BARRIER = new KeyMapping(
            "key.madoka_magic.open_barrier",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.madoka_magic");

    public static final KeyMapping TOGGLE_TRANSFORMATION = new KeyMapping(
            "key.madoka_magic.toggle_transformation", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H, "key.categories.madoka_magic");

    /** J 键：打开 HUD 布局编辑。 */
    public static final KeyMapping HUD_EDIT = new KeyMapping(
            "key.madoka_magic.edit_hud", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J, "key.categories.madoka_magic");

    private MadokaKeys() {
    }
}

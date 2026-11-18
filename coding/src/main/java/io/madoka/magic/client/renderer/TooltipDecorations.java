package io.madoka.magic.client.renderer;

import com.mojang.math.Axis;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.item.ContractItem;
import io.madoka.magic.item.ContractWeapon;
import io.madoka.magic.item.GriefSeedItem;
import io.madoka.magic.item.WitchKissItem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Background ornaments for the three categories of ritual equipment. */
public final class TooltipDecorations {
    private static final String SEED_RUNES = "textures/item/witch_rune/";
    private static final String KISS_RUNES = "textures/item/witch_kiss/";
    private static final ResourceLocation ATLAS = id("textures/gui/tooltip_decor.png");
    private static final int ATLAS_WIDTH = 128;
    private static final int ATLAS_HEIGHT = 64;
    private static final int QUILL_SIZE = 16;
    private static final int RIBBON_WIDTH = 8;
    private static final long CIRCLE_PERIOD = 20_000;
    private static Set<ResourceLocation> seedRunes = Set.of();
    private static Set<ResourceLocation> kissRunes = Set.of();

    private TooltipDecorations() {}

    public static void reload(ResourceManager resources) {
        seedRunes = pngs(resources, SEED_RUNES);
        kissRunes = pngs(resources, KISS_RUNES);
    }

    private static Set<ResourceLocation> pngs(ResourceManager resources, String directory) {
        return Set.copyOf(resources.listResources(directory.substring(0, directory.length() - 1),
                file -> MadokaMagic.MODID.equals(file.getNamespace()) && file.getPath().endsWith(".png")).keySet());
    }

    public static void ribbons(GuiGraphics graphics, ItemStack stack, int left, int top, int right, int bottom, int z) {
        if (!(stack.getItem() instanceof ContractItem)) return;
        var style = RitualVisualStyle.forPlayer(ContractItem.getThemeId(stack), false);
        int width = right - left;
        int height = bottom - top;
        float dx = Math.min(width * 0.36F, height * 0.90F);
        float dy = height + 16;
        int length = Mth.ceil(Math.sqrt(dx * dx + dy * dy));
        float angle = (float) Math.toDegrees(Math.atan2(-dy, dx));
        var pose = graphics.pose();
        tint(graphics, style.primary(), 0.88F);
        try {
            for (int sign = -1; sign <= 1; sign += 2) {
                pose.pushPose();
                pose.translate(left + width * (0.5F + sign * 0.18F), (top + bottom) * 0.5F, z);
                pose.mulPose(Axis.ZP.rotationDegrees(angle));
                int start = -length / 2;
                int ribbonTop = -RIBBON_WIDTH / 2;
                int cap = 5;
                blit(graphics, start, ribbonTop, cap, RIBBON_WIDTH, 16, 0, cap, 8);
                blit(graphics, start + cap, ribbonTop, length - cap * 2, RIBBON_WIDTH, 21, 0, 6, 8);
                blit(graphics, start + length - cap, ribbonTop, cap, RIBBON_WIDTH, 27, 0, cap, 8);
                pose.popPose();
            }
        } finally {
            graphics.setColor(1, 1, 1, 1);
        }
    }

    public static void interior(GuiGraphics graphics, ItemStack stack, int left, int top, int right, int bottom, int z) {
        boolean seed = stack.getItem() instanceof GriefSeedItem;
        ContractWeapon weapon = ContractWeapon.of(stack);
        if (!seed && weapon == null) return;
        String theme = seed ? GriefSeedItem.getOwner(stack) : switch (weapon) {
            case BUTTERFLY -> "sacrifice";
            case BEE -> "take";
            case THORN -> "atonement";
        };
        var style = seed ? RitualVisualStyle.forTheme(theme) : RitualVisualStyle.forPlayer(theme, false);
        int diameter = Math.min(right - left, bottom - top) - 12;
        long now = Util.getMillis();
        if (diameter >= 12) {
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate((left + right) * 0.5F, (top + bottom) * 0.5F, z);
            pose.mulPose(Axis.ZP.rotationDegrees((now % CIRCLE_PERIOD) * 360F / CIRCLE_PERIOD));
            try {
                tint(graphics, style.crown(), 0.17F);
                blit(graphics, -diameter / 2, -diameter / 2, diameter, diameter, 32, 0, 64, 64);
                ResourceLocation rune = seed ? seedRune(theme) : null;
                int inner = Math.max(8, Math.round(diameter * 0.43F));
                if (rune == null) {
                    tint(graphics, style.crown(), 0.14F);
                    blit(graphics, -inner / 2, -inner / 2, inner, inner, 96, 0, 32, 32);
                } else {
                    graphics.setColor(1, 1, 1, 0.16F);
                    graphics.blit(rune, -inner / 2, -inner / 2, inner, inner, 0, 0, 1, 1, 1, 1);
                }
            } finally {
                pose.popPose();
                graphics.setColor(1, 1, 1, 1);
            }
        }
        if (weapon != null) weaponSparks(graphics, weapon, style.crown(), left, top, right, bottom, z, now);
    }

    private static ResourceLocation seedRune(String theme) {
        if (!WitchKissItem.THEMES.contains(theme)) return null;
        ResourceLocation source = id(SEED_RUNES + theme + ".png");
        if (seedRunes.contains(source)) return source;
        ResourceLocation seal = id(KISS_RUNES + theme + ".png");
        return kissRunes.contains(seal) ? seal : null;
    }

    private static void weaponSparks(GuiGraphics graphics, ContractWeapon weapon, int color, int left, int top,
                                     int right, int bottom, int z, long now) {
        var random = RandomSource.create(weapon.id.hashCode());
        int width = right - left - 5;
        int height = bottom - top - 5;
        if (width <= 0 || height <= 0) return;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(left + 2, top + 2, z);
        try {
            for (int i = 0; i < 8; i++) {
                double lifetime = 4 + random.nextDouble() * 4;
                double phase = Mth.frac(now / (lifetime * 1000) + random.nextDouble());
                double travel = Math.min(height, lifetime * (1.5 + random.nextDouble() * 1.5));
                int x = (int) (width * (i + 0.18 + random.nextDouble() * 0.64) / 8);
                int y = (int) Math.round((height - travel) * random.nextDouble() + travel * (1 - phase));
                tint(graphics, color, (float) WitchAura.fade(phase) * 0.43F);
                blit(graphics, x, y, 2, 2, 0, 16, 2, 2);
            }
        } finally {
            pose.popPose();
            graphics.setColor(1, 1, 1, 1);
        }
    }

    public static void quill(GuiGraphics graphics, ItemStack stack, int right, int bottom, int z) {
        if (stack.getItem() instanceof ContractItem) {
            graphics.blit(ATLAS, right - 3, bottom - 5, z, 0, 0, QUILL_SIZE, QUILL_SIZE, ATLAS_WIDTH, ATLAS_HEIGHT);
        }
    }

    private static void tint(GuiGraphics graphics, int color, float alpha) {
        graphics.setColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, alpha);
    }

    private static void blit(GuiGraphics graphics, int x, int y, int w, int h, int u, int v, int sourceW, int sourceH) {
        graphics.blit(ATLAS, x, y, w, h, u, v, sourceW, sourceH, ATLAS_WIDTH, ATLAS_HEIGHT);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, path);
    }
}

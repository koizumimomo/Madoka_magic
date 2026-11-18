package io.madoka.magic.client.renderer;

import io.madoka.magic.entity.WitchTypes;
import io.madoka.magic.item.RitualItemAppearance;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Selects the same sprites used by player forms and witch auras. */
public final class RitualItemParticles {
    private static final int TOOLTIP_MOTES = 8;
    private static final Atlas RITUAL = new Atlas(RitualSprites.ATLAS, RitualSprites.COLUMNS,
            RitualSprites.ROWS, RitualSprites.TILE_SIZE);
    private static final Atlas FRAME = new Atlas(AtonementVisuals.ATLAS, AtonementVisuals.COLUMNS, 1, 32);
    private static final Atlas TEA = new Atlas(CandeloroVisuals.ORBIT_ATLAS, CandeloroVisuals.MOTIFS, 1, 32);
    private static final Atlas EMBER = new Atlas(MadokaWitchRenderer.WITHER_EMBER_ATLAS, 1, 1, 16);
    private static final Atlas MORPH = new Atlas(GluttonyWitchRenderer.MORPH_ATLAS, 6, 6, 32);
    private static final Atlas EXTERNAL = new Atlas(ExternalWitchParticles.ATLAS, ExternalWitchParticles.COLUMNS,
            ExternalWitchParticles.ROWS, ExternalWitchParticles.SIDE);

    private record Atlas(ResourceLocation texture, int columns, int rows, int side, RenderType layer) {
        private Atlas(ResourceLocation texture, int columns, int rows, int side) {
            this(texture, columns, rows, side, RenderType.entityTranslucentEmissive(texture));
        }
    }

    private record Mote(int tile, int color) {}

    private final Atlas atlas;
    private final RitualVisualStyle style;

    RitualItemParticles(RitualItemAppearance appearance) {
        style = RitualVisualStyle.forPlayer(appearance.theme(), appearance.witch());
        atlas = appearance.witch() && style.orbit() != null ? EXTERNAL : appearance.witch() ? switch (appearance.theme()) {
            case "atonement" -> FRAME;
            case "candeloro" -> TEA;
            case WitchTypes.WITHER -> EMBER;
            case WitchTypes.GLUTTONY -> MORPH;
            default -> RITUAL;
        } : RITUAL;
    }

    RenderType layer() {
        return atlas.layer();
    }

    void world(RitualSprites sprites, int index, double ticks, double x, double y, double z,
               double size, double roll, double opacity) {
        var mote = mote(index, ticks);
        sprites.drawTile(mote.tile(), atlas.columns(), atlas.rows(), atlas.side(), atlas.side(),
                x, y, z, size, size, roll, mote.color(), opacity);
    }

    private Mote mote(int index, double ticks) {
        if (atlas == EXTERNAL) {
            return new Mote(ExternalWitchParticles.tile(style.orbit(), index, ticks), 0xffffff);
        }
        if (atlas == MORPH) {
            return new Mote(GluttonyWitchRenderer.morphFrame(ticks, index), 0xffffff);
        }
        if (atlas == FRAME) {
            return new Mote(index % AtonementVisuals.SHARDS, 0xffffff);
        }
        if (atlas == TEA) {
            return new Mote(index % CandeloroVisuals.MOTIFS, 0xffffff);
        }
        if (atlas == EMBER) {
            return new Mote(0, 0xffffff);
        }
        if (index % 3 == 0) {
            return new Mote(style.accent().tile, style.secondary());
        }
        if (style.motion() == RitualVisualStyle.Motion.SHED) {
            return new Mote(WitchAura.butterfly(ticks + index).tile, 0xffffff);
        }
        return new Mote(style.motif().tile, style.primary());
    }

    public static void tooltip(GuiGraphics graphics, ItemStack stack, int left, int top, int right, int bottom, int z) {
        var appearance = RitualItemAppearance.of(stack);
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        int width = right - left;
        int height = bottom - top;
        if (appearance == null || id == null || width < 12 || height < 8) {
            return;
        }
        var particles = new RitualItemParticles(appearance);
        var atlas = particles.atlas;
        var random = RandomSource.create(31L * id.hashCode() + appearance.theme().hashCode());
        double seconds = Util.getMillis() / 1000.0D;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0D, 0.0D, z);
        try {
            for (int i = 0; i < TOOLTIP_MOTES; i++) {
                int size = Math.min(height - 2, 5 + random.nextInt(2));
                double duration = (appearance.witch() ? 6.0D : 4.0D) + random.nextDouble() * 2.0D;
                double phase = Mth.frac(seconds / duration + random.nextDouble());
                double xFraction = (i + 0.14D + random.nextDouble() * 0.72D) / TOOLTIP_MOTES;
                int x = left + 1 + (int) ((width - size - 2) * xFraction);
                int travel = height - size - 2;
                int drift = height <= 16 ? Math.min(travel, 3) : travel;
                int band = i % 2 == 0 ? 0 : travel - drift;
                int y = top + 1 + band + (int) (drift * (1.0D - phase));
                float opacity = (float) (WitchAura.fade(phase) * (appearance.witch() ? 0.34D : 0.42D));
                var mote = particles.mote(i, seconds * 20.0D);
                int color = mote.color();
                graphics.setColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, opacity);
                graphics.blit(atlas.texture(), x, y, size, size, (mote.tile() % atlas.columns()) * atlas.side(),
                        (mote.tile() / atlas.columns()) * atlas.side(), atlas.side(), atlas.side(),
                        atlas.columns() * atlas.side(), atlas.rows() * atlas.side());
            }
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            pose.popPose();
        }
    }
}

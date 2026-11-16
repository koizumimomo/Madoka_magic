package io.madoka.magic.compat.jei;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.client.MagicalGirlInfoScreen;
import io.madoka.magic.item.GriefSeedItem;
import io.madoka.magic.registry.ModItems;
import io.madoka.magic.ritual.AltarRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

@JeiPlugin
public final class MadokaJeiPlugin implements IModPlugin {
    private static final RecipeType<AltarRecipe> ALTAR = new RecipeType<>(AltarRecipe.ID, AltarRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return AltarRecipe.ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new AltarCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            registration.addRecipes(ALTAR, connection.getRecipeManager().getAllRecipesFor(AltarRecipe.TYPE.get()));
        }
        registration.addIngredientInfo(new ItemStack(ModItems.ALTAR_ITEM.get()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei.madoka_magic.altar.alternative"));
        ModItems.GRIEF_SEEDS.forEach((witch, seed) ->
                registration.addIngredientInfo(new ItemStack(seed.get()), VanillaTypes.ITEM_STACK,
                        Component.translatable("jei.madoka_magic.grief_seed.source", GriefSeedItem.witchName(witch))));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.ALTAR_ITEM.get()), ALTAR);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        reserveSoulGem(registration, InventoryScreen.class);
        reserveSoulGem(registration, CreativeModeInventoryScreen.class);
    }

    private static <T extends AbstractContainerScreen<?>> void reserveSoulGem(
            IGuiHandlerRegistration registration, Class<T> screen) {
        registration.addGuiContainerHandler(screen, new IGuiContainerHandler<T>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(T inventory) {
                return List.of(MagicalGirlInfoScreen.inventoryTabBounds(inventory));
            }
        });
    }

    private static final class AltarCategory implements IRecipeCategory<AltarRecipe> {
        private static final int WIDTH = 166;
        private static final int HEIGHT = 104;
        private static final int CENTER_X = 54;
        private static final int CENTER_Y = 49;
        private static final int RADIUS = 29;
        private static final int OUTPUT_X = 135;
        private final IDrawable background;
        private final IDrawable icon;
        private final IDrawable slot;

        AltarCategory(IGuiHelper gui) {
            background = texture(gui, "altar_parchment", WIDTH, HEIGHT);
            icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.ALTAR_ITEM.get()));
            slot = texture(gui, "altar_parchment_slot", 20, 20);
        }

        private static IDrawable texture(IGuiHelper gui, String name, int width, int height) {
            var texture = ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "textures/gui/" + name + ".png");
            return gui.drawableBuilder(texture, 0, 0, width, height).setTextureSize(width, height).build();
        }

        @Override
        public RecipeType<AltarRecipe> getRecipeType() {
            return ALTAR;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("block.madoka_magic.altar");
        }

        @Override
        public IDrawable getBackground() {
            return background;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, AltarRecipe recipe, IFocusGroup focuses) {
            int count = recipe.inputs().size();
            double start = count == 2 ? 0.0D : count == 4 ? -Math.PI / 4.0D : -Math.PI / 2.0D;
            for (int i = 0; i < count; i++) {
                double angle = start + i * Math.PI * 2.0D / count;
                int x = CENTER_X + (int) Math.round(Math.cos(angle) * RADIUS);
                int y = CENTER_Y + (int) Math.round(Math.sin(angle) * RADIUS);
                builder.addSlot(RecipeIngredientRole.INPUT, x - 8, y - 8)
                        .setBackground(slot, -2, -2).addIngredients(recipe.inputs().get(i));
            }
            builder.addSlot(RecipeIngredientRole.INPUT, CENTER_X - 8, CENTER_Y - 8)
                    .setBackground(slot, -2, -2).addIngredients(recipe.catalyst())
                    .addTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.madoka_magic.altar.trigger")));
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X - 8, CENTER_Y - 8)
                    .setBackground(slot, -2, -2).addItemStack(recipe.result());
        }

        @Override
        public void draw(AltarRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
            var font = Minecraft.getInstance().font;
            Component caption = recipe.lifeCost() > 0.0F
                    ? Component.translatable("jei.madoka_magic.altar.life_cost", Math.round(recipe.lifeCost() * 100))
                    : Component.translatable("jei.madoka_magic.altar.instructions");
            graphics.drawString(font, caption, (WIDTH - font.width(caption)) / 2, 89, 0x513429, false);
        }
    }
}

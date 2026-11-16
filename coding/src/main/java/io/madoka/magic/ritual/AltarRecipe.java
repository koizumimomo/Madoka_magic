package io.madoka.magic.ritual;

import com.google.gson.JsonObject;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.block.AltarBlockEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.RecipeMatcher;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/** Server-synced altar recipe. The catalyst is held while the other ingredients rest on the altar. */
public record AltarRecipe(ResourceLocation id, List<Ingredient> inputs, Ingredient catalyst,
                          float lifeCost, ItemStack result)
        implements Recipe<SimpleContainer> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "altar_ritual");
    private static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, MadokaMagic.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MadokaMagic.MODID);
    public static final RegistryObject<RecipeType<AltarRecipe>> TYPE =
            TYPES.register(ID.getPath(), () -> RecipeType.simple(ID));
    private static final RegistryObject<RecipeSerializer<AltarRecipe>> SERIALIZER =
            SERIALIZERS.register(ID.getPath(), Serializer::new);

    public AltarRecipe {
        inputs = List.copyOf(inputs);
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        SERIALIZERS.register(bus);
    }

    @Override
    public boolean matches(SimpleContainer altar, Level level) {
        List<ItemStack> materials = new ArrayList<>();
        for (int slot = 0; slot < altar.getContainerSize(); slot++) {
            ItemStack stack = altar.getItem(slot);
            if (!stack.isEmpty()) {
                materials.add(stack);
            }
        }
        return materials.size() == inputs.size() && RecipeMatcher.findMatches(materials, inputs) != null;
    }

    @Override
    public ItemStack assemble(SimpleContainer altar, RegistryAccess access) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= inputs.size();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.addAll(inputs);
        return ingredients;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return TYPE.get();
    }

    private static final class Serializer implements RecipeSerializer<AltarRecipe> {
        @Override
        public AltarRecipe fromJson(ResourceLocation id, JsonObject json) {
            List<Ingredient> ingredients = new ArrayList<>();
            GsonHelper.getAsJsonArray(json, "ingredients").forEach(element -> ingredients.add(Ingredient.fromJson(element)));
            if (ingredients.isEmpty() || ingredients.size() > AltarBlockEntity.SLOT_COUNT) {
                throw new IllegalArgumentException("Ritual requires one ingredient per occupied altar slot");
            }
            Ingredient catalyst = json.has("catalyst") ? Ingredient.fromJson(json.get("catalyst")) : Ingredient.of(Items.PAPER);
            float lifeCost = GsonHelper.getAsFloat(json, "life_cost", 0.0F);
            if (!Float.isFinite(lifeCost) || lifeCost < 0.0F || lifeCost >= 1.0F) {
                throw new IllegalArgumentException("Ritual life_cost must be in [0, 1)");
            }
            return new AltarRecipe(id, ingredients, catalyst, lifeCost,
                    ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result")));
        }

        @Override
        public AltarRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new AltarRecipe(id, buffer.readList(Ingredient::fromNetwork), Ingredient.fromNetwork(buffer),
                    buffer.readFloat(), buffer.readItem());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, AltarRecipe recipe) {
            buffer.writeCollection(recipe.inputs(), (out, ingredient) -> ingredient.toNetwork(out));
            recipe.catalyst().toNetwork(buffer);
            buffer.writeFloat(recipe.lifeCost());
            buffer.writeItem(recipe.result());
        }
    }
}

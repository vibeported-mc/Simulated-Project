package dev.simulated_team.simulated.data.neoforge;

import com.simibubi.create.api.data.recipe.BaseRecipeProvider;
import dev.simulated_team.simulated.Simulated;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Recipe;

import java.util.function.Supplier;
import java.util.function.Function;

public class SimStandardRecipeGen extends BaseRecipeProvider {

    GeneratedRecipe PORTABLE_ENGINE_DYEING = this.createSpecial(PortableEngineDyeingRecipe::new, "crafting", "portable_engine_dyeing");

    /**
     * <h2>26.2 note</h2>
     * <p>A recipe generator is no longer a {@code DataProvider}: 26.2 builds it once the registries
     * have loaded and hands it the output to write into, so the constructor takes those two rather
     * than a {@code PackOutput} and a future. {@code BaseRecipeProvider.runner} is what supplies the
     * {@code DataProvider} half.
     */
    public SimStandardRecipeGen(final HolderLookup.Provider registries, final RecipeOutput output) {
        super(registries, output, Simulated.MOD_ID);
    }

    @Override
    public String getName() {
        return "Simulated's Surprisingly Standard Recipes";
    }

    // 26.2: SpecialRecipeBuilder takes a Supplier rather than a per-category factory, because a
    // CustomRecipe answers its own category now.
    private GeneratedRecipe createSpecial(final Supplier<Recipe<?>> builder, final String recipeType, final String path) {
        final Identifier location = Simulated.path(recipeType + "/" + path);

        return this.register(consumer -> {
            final SpecialRecipeBuilder b = SpecialRecipeBuilder.special(builder);
            b.save(consumer, location.toString());
        });
    }
}

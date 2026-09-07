package dev.simulated_team.simulated.data.neoforge;

import com.simibubi.create.api.data.recipe.FillingRecipeGen;
import com.simibubi.create.foundation.data.recipe.CommonMetal;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.index.SimItems;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.common.Tags;

public class SimFillingRecipes extends FillingRecipeGen {
    private final GeneratedRecipe HONEY_GLUE = this.create("honey_glue",
            b -> b.require(Tags.Fluids.HONEY, 500)
                  .require(CommonMetal.IRON.plates)
                  .output(SimItems.HONEY_GLUE));

    /**
     * <h2>26.2 note</h2>
     * <p>A recipe generator is no longer a {@code DataProvider}: 26.2 builds it once the registries
     * have loaded and hands it the output to write into, so the constructor takes those two rather
     * than a {@code PackOutput} and a future. {@code BaseRecipeProvider.runner} is what supplies the
     * {@code DataProvider} half.
     */
    public SimFillingRecipes(final HolderLookup.Provider registries, final RecipeOutput output) {
        super(registries, output, Simulated.MOD_ID);
    }

    @Override
    public String getName() {
        return "Simulated's Fantastic Filling Recipes";
    }

}

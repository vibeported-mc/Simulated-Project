package dev.simulated_team.simulated.data.neoforge;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.simibubi.create.api.data.recipe.BaseRecipeProvider;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;

/**
 * <h2>26.2 note</h2>
 * <p>A recipe generator is no longer a {@code DataProvider}, so this can no longer be an anonymous
 * one that fans {@code run} out across a static list. 26.2 builds the provider once the registries
 * have loaded and hands it the output to write into -- which is what
 * {@link BaseRecipeProvider#runner} wraps -- and the generators are built there rather than being
 * accumulated in a static field that never got cleared between runs.
 *
 * <p>This also stopped being a base class: nothing extended it, and the constructor it offered no
 * longer matches what a generator takes.
 */
public class SimProcessingRecipeGen {

    public static DataProvider registerAll(final PackOutput output, final CompletableFuture<HolderLookup.Provider> lookupProvider) {
        return BaseRecipeProvider.runner(output, lookupProvider, "Simulated's Peculiar Processing Recipes", AllProcessing::new);
    }

    private static class AllProcessing extends RecipeProvider {

        private final List<BaseRecipeProvider> generators;

        private AllProcessing(final HolderLookup.Provider registries, final RecipeOutput output) {
            super(registries, output);
            this.generators = List.of(
                    new SimFillingRecipes(registries, output),
                    new SimMechanicalCraftingRecipes(registries, output),
                    new SimSequencedAssemblyRecipes(registries, output),
                    new SimStandardRecipeGen(registries, output));
        }

        @Override
        protected void buildRecipes() {
            this.generators.forEach(BaseRecipeProvider::buildRecipes);
        }
    }
}

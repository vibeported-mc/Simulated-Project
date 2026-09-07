package dev.eriksonn.aeronautics.neoforge.data.recipe;

import com.simibubi.create.api.data.recipe.WashingRecipeGen;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.index.AeroBlocks;
import dev.eriksonn.aeronautics.index.AeroTags;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.NotNull;

public class AeroWashingRecipes extends WashingRecipeGen {
    /**
     * <h2>26.2 note</h2>
     * <p>A recipe generator is no longer a {@code DataProvider}: 26.2 builds it once the registries
     * have loaded and hands it the output to write into, so the constructor takes those two rather
     * than a {@code PackOutput} and a future. {@code BaseRecipeProvider.runner} is what supplies the
     * {@code DataProvider} half.
     */
    public AeroWashingRecipes(final HolderLookup.Provider registries, final RecipeOutput output) {
        super(registries, output, Aeronautics.MOD_ID);
    }

    GeneratedRecipe ENVELOPE_WASHING = this.create("envelope_washing", b -> b
            .require(AeroTags.ItemTags.SHAFTLESS_ENVELOPE)
            .output(AeroBlocks.WHITE_ENVELOPE_BLOCK.asItem())
    );

    @Override
    public @NotNull String getName() {
        return "Aero's Whimsical Washing Recipes";
    }
}

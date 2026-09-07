package dev.eriksonn.aeronautics.neoforge.data.recipe;

import com.simibubi.create.api.data.recipe.DeployingRecipeGen;
import com.simibubi.create.foundation.utility.DyeHelper;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.index.AeroBlocks;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

public class AeroDeployingRecipes extends DeployingRecipeGen {
    /**
     * <h2>26.2 note</h2>
     * <p>A recipe generator is no longer a {@code DataProvider}: 26.2 builds it once the registries
     * have loaded and hands it the output to write into, so the constructor takes those two rather
     * than a {@code PackOutput} and a future. {@code BaseRecipeProvider.runner} is what supplies the
     * {@code DataProvider} half.
     */
    public AeroDeployingRecipes(final HolderLookup.Provider registries, final RecipeOutput output) {
        super(registries, output, Aeronautics.MOD_ID);

        for (final DyeColor color : DyeColor.values()) {
            this.create("deploying_envelope_" + color.getName(), b -> b
                    .require(DyeHelper.getWoolOfDye(color))
                    .require(Items.STICK)
                    .output(AeroBlocks.DYED_ENVELOPE_BLOCKS.get(color), 3)
            );
        }
    }

    @Override
    public @NotNull String getName() {
        return "Aero's Devious Deploying Recipes";
    }
}

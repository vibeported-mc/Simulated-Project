package dev.eriksonn.aeronautics.neoforge.data.recipe;

import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.index.AeroItems;
import com.simibubi.create.api.data.recipe.CrushingRecipeGen;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

public class AeroCrushingRecipes extends CrushingRecipeGen {
	/**
	 * <h2>26.2 note</h2>
	 * <p>A recipe generator is no longer a {@code DataProvider}: 26.2 builds it once the registries
	 * have loaded and hands it the output to write into, so the constructor takes those two rather
	 * than a {@code PackOutput} and a future. {@code BaseRecipeProvider.runner} is what supplies the
	 * {@code DataProvider} half.
	 */
	public AeroCrushingRecipes(final HolderLookup.Provider registries, final RecipeOutput output) {
		super(registries, output, Aeronautics.MOD_ID);
	}

	GeneratedRecipe END_STONE_POWDER = create("end_stone_powder", b -> b
			.duration(250)
			.require(Blocks.END_STONE)
			.output(0.5f, Blocks.END_STONE)
			.output(AeroItems.ENDSTONE_POWDER)
	);

	@Override
	public @NotNull String getName() {
		return "Aero's Captivating Crushing Recipes";
	}
}

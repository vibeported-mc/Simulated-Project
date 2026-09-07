package dev.eriksonn.aeronautics.neoforge.data.recipe;

import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.index.AeroItems;
import dev.eriksonn.aeronautics.neoforge.index.AeroFluidsNeoForge;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.data.recipe.MixingRecipeGen;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.NotNull;

public class AeroMixingRecipes extends MixingRecipeGen {
	/**
	 * <h2>26.2 note</h2>
	 * <p>A recipe generator is no longer a {@code DataProvider}: 26.2 builds it once the registries
	 * have loaded and hands it the output to write into, so the constructor takes those two rather
	 * than a {@code PackOutput} and a future. {@code BaseRecipeProvider.runner} is what supplies the
	 * {@code DataProvider} half.
	 */
	public AeroMixingRecipes(final HolderLookup.Provider registries, final RecipeOutput output) {
		super(registries, output, Aeronautics.MOD_ID);
	}

	GeneratedRecipe LEVITITE_BLEND = create("levitite_blend", b -> b
			.require(AeroItems.ENDSTONE_POWDER)
			.require(AeroItems.ENDSTONE_POWDER)
			.require(AeroItems.ENDSTONE_POWDER)
			.require(AeroItems.ENDSTONE_POWDER)
			.require(AllItems.ZINC_NUGGET)
			.require(AllItems.ZINC_NUGGET)
			.require(Tags.Fluids.WATER, 500)
			.output(AeroFluidsNeoForge.LEVITITE_BLEND.get(), 500)
			.requiresHeat(HeatCondition.HEATED)
	);

	@Override
	public @NotNull String getName() {
		return "Aero's Miraculous Mixing Recipes";
	}
}

package dev.eriksonn.aeronautics.neoforge.data.recipe;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.data.recipe.MechanicalCraftingRecipeGen;
import com.simibubi.create.foundation.data.recipe.CommonMetal;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.index.AeroBlocks;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;

public class AeroMechanicalCraftingRecipes extends MechanicalCraftingRecipeGen {
	/**
	 * <h2>26.2 note</h2>
	 * <p>A recipe generator is no longer a {@code DataProvider}: 26.2 builds it once the registries
	 * have loaded and hands it the output to write into, so the constructor takes those two rather
	 * than a {@code PackOutput} and a future. {@code BaseRecipeProvider.runner} is what supplies the
	 * {@code DataProvider} half.
	 */
	public AeroMechanicalCraftingRecipes(final HolderLookup.Provider registries, final RecipeOutput output) {
		super(registries, output, Aeronautics.MOD_ID);
	}

	private final GeneratedRecipe MOUNTED_POTATO_CANNON = this.create(AeroBlocks.MOUNTED_POTATO_CANNON::get)
			.returns(1)
			.recipe(b -> b
					.patternLine("SR  ")
					.patternLine("KCPP")
					.patternLine("SR  ")
					.key('S', ingredient(CommonMetal.COPPER.plates))
					.key('R', ingredient(Tags.Items.DUSTS_REDSTONE))
					.key('K', Blocks.DRIED_KELP_BLOCK)
					.key('C', AllBlocks.COGWHEEL)
					.key('P', AllBlocks.FLUID_PIPE)
			);

	@Override
	public String getName() {
		return "Aero's Mischievous Mechanical Crafting Recipes";
	}
}

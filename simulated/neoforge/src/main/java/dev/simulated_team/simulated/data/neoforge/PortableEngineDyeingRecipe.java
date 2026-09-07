package dev.simulated_team.simulated.data.neoforge;

import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlock;
import dev.simulated_team.simulated.index.SimBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;

public class PortableEngineDyeingRecipe extends CustomRecipe {

    /**
     * <h2>26.2 note</h2>
     * <p>{@code CustomRecipe} has no constructor arguments any more -- the crafting book category is
     * a method on the recipe rather than state handed in, and {@code CustomRecipe} answers it with
     * MISC, which is what this recipe was registered with.
     *
     * <p>The serializer moved onto the recipe too. {@code SimpleCraftingRecipeSerializer} is gone; a
     * special recipe is a codec pair over a single instance. {@code StreamCodec.unit} checks the
     * decoded value against the one it holds, so the recipe read from data and the recipe sent over
     * the network have to be the same object -- hence the singleton.
     */
    private static final PortableEngineDyeingRecipe INSTANCE = new PortableEngineDyeingRecipe();

    public static final RecipeSerializer<PortableEngineDyeingRecipe> SERIALIZER =
            new RecipeSerializer<>(MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

    @Override
    public boolean matches(final CraftingInput input, final Level level) {
        int engines = 0;
        int dyes = 0;

        for (int i = 0; i < input.size(); ++i) {
            final ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                if (Block.byItem(stack.getItem()) instanceof PortableEngineBlock) {
                    ++engines;
                } else {
                    if (!stack.is(Tags.Items.DYES))
                        return false;
                    ++dyes;
                }

                if (dyes > 1 || engines > 1) {
                    return false;
                }
            }
        }

        return engines == 1 && dyes == 1;
    }

    @Override
    public ItemStack assemble(final CraftingInput input) {
        ItemStack engine = ItemStack.EMPTY;
        DyeColor color = DyeColor.RED;

        for (int i = 0; i < input.size(); ++i) {
            final ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                if (Block.byItem(stack.getItem()) instanceof PortableEngineBlock) {
                    engine = stack;
                } else {
                    final DyeColor color1 = DyeColor.getColor(stack);
                    if (color1 != null) {
                        color = color1;
                    }
                }
            }
        }

        final ItemStack dyedEngine = SimBlocks.PORTABLE_ENGINES.get(color)
                .asStack();
        if (!engine.isComponentsPatchEmpty()) {
            dyedEngine.applyComponents(engine.getComponentsPatch());
        }

        return dyedEngine;
    }

    /**
     * 26.2: {@code canCraftInDimensions} is gone -- a special recipe reports what it can be placed
     * into through {@code placementInfo}, and {@code CustomRecipe} already answers NOT_PLACEABLE.
     * The two-slot minimum this expressed is enforced by {@link #matches} anyway, which needs one
     * engine and one dye.
     */
    @Override
    public RecipeSerializer<PortableEngineDyeingRecipe> getSerializer() {
        return SERIALIZER;
    }

}

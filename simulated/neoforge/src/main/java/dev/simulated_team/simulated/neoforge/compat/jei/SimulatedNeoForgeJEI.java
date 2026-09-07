package dev.simulated_team.simulated.neoforge.compat.jei;

import dev.simulated_team.simulated.Simulated;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.Identifier;

import javax.annotation.ParametersAreNonnullByDefault;

@JeiPlugin
@SuppressWarnings("unused")
@ParametersAreNonnullByDefault
public class SimulatedNeoForgeJEI implements IModPlugin {

    private static final Identifier ID = Simulated.path("jei_plugin/neoforge");

    @Override
    public Identifier getPluginUid() {
        return ID;
    }

    @Override
    public void registerRecipes(final IRecipeRegistration registration) {
        registration.addRecipes(RecipeTypes.CRAFTING, PortableEngineDyeingRecipeMaker.createRecipes().toList());
    }
}

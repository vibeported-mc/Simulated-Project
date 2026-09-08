package dev.simulated_team.simulated.data;

import java.util.concurrent.CompletableFuture;

import com.simibubi.create.api.data.recipe.BaseRecipeProvider;

import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.Nullable;

/**
 * The registries datagen was handed, so that anything generating data can bind item components
 * before it asks an item about itself.
 *
 * <h2>26.2 note</h2>
 * <p>An item's default components are bound when a server loads its datapacks, which never happens
 * during datagen -- so anything that asks an item about itself fails on "Components not bound yet".
 * Create's {@code BaseRecipeProvider} binds them from the registries the provider was handed, and
 * that is fine for recipes, but the lang provider runs first and has no registries of its own.
 *
 * <p>Ponder is what actually needs it: compiling a scene builds item stacks, and the lang provider
 * compiles every scene to collect their text. Create's own datagen keeps the same field for the same
 * reason.
 */
public class SimDatagenRegistries {

    private static @Nullable CompletableFuture<HolderLookup.Provider> registries;

    public static void set(final CompletableFuture<HolderLookup.Provider> provider) {
        registries = provider;
    }

    /**
     * Binds item components if the registries are known. Safe to call more than once --
     * {@code BaseRecipeProvider} guards against binding twice.
     */
    public static void bindItemComponents() {
        if (registries == null) {
            return;
        }

        BaseRecipeProvider.bindItemComponents(registries.join());
    }
}

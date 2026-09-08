package dev.eriksonn.aeronautics.data;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import dev.eriksonn.aeronautics.Aeronautics;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;

/**
 * The datapack registry entries Aeronautics declares in code.
 *
 * <h2>26.2 note</h2>
 * <p>See {@link AeroJukeboxSongs} -- an item naming a datapack registry entry has that reference
 * resolved when its components are bound, and during datagen only generated entries are there to
 * find. Create keeps the same provider for the same reason.
 */
public class AeroGeneratedEntries extends DatapackBuiltinEntriesProvider {

    private static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.JUKEBOX_SONG, AeroJukeboxSongs::bootstrap);

    public AeroGeneratedEntries(final PackOutput output, final CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, BUILDER, Set.of(Aeronautics.MOD_ID));
    }

    @Override
    public String getName() {
        return "Aeronautics' Generated Registry Entries";
    }
}

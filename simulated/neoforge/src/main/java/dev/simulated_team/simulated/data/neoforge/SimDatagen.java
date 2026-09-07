package dev.simulated_team.simulated.data.neoforge;

import java.util.concurrent.CompletableFuture;

import dev.simulated_team.simulated.data.advancements.SimAdvancements;
import dev.simulated_team.simulated.index.SimTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Advancements are written to use the traditional datagen entrypoint, and only need to be run on one
 * side.
 *
 * <h2>26.2 note</h2>
 * <p>26.2 fires a separate {@code GatherDataEvent} per side rather than handing one event a pair of
 * include flags, and providers are added to the event rather than to the generator. The event is
 * also raised per mod, so there is no mod set to filter on.
 *
 * <p>Nothing calls this class -- {@code SimNeoForgeCommonEvents.ModBusEvents} carries the live
 * listeners and does the same work. It is ported rather than deleted so the two do not drift, but
 * whichever of the pair is dead should go.
 */
public class SimDatagen {

    public static void gatherDataHighPriority(final GatherDataEvent event) {
        SimTags.addGenerators();
    }

    public static void gatherData(final GatherDataEvent.Server event) {
        final PackOutput output = event.getGenerator().getPackOutput();
        final CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        event.addProvider(new SimAdvancements(output, lookupProvider));
        event.addProvider(SimProcessingRecipeGen.registerAll(output, lookupProvider));
    }
}

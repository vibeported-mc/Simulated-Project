package dev.ryanhcode.offroad.neoforge.data;

import dev.simulated_team.simulated.data.SimDatagenRegistries;
import dev.ryanhcode.offroad.Offroad;
import dev.ryanhcode.offroad.data.OffroadAdvancementTriggers;
import dev.ryanhcode.offroad.index.OffroadAdvancements;
import dev.ryanhcode.offroad.index.OffroadSoundEvents;
import dev.ryanhcode.offroad.index.OffroadTags;
import dev.ryanhcode.offroad.neoforge.index.OffroadSoundEventsNeoForge;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.concurrent.CompletableFuture;

public class OffroadDatagen {

    /**
     * <h2>26.2 note</h2>
     * <p>26.2 fires a separate event per side instead of handing one event a pair of include flags,
     * and providers are added to the event rather than to the generator. The event is also raised
     * per mod, so there is no mod set to filter on -- but it is raised twice, once per side, so the
     * tag generators guard against being added a second time.
     */
    private static boolean addedGenerators;

    public static void gatherDataHighPriority(final GatherDataEvent event) {
        SimDatagenRegistries.set(event.getLookupProvider());
        if (addedGenerators)
            return;
        addedGenerators = true;
        OffroadTags.addGenerators();
    }

    public static void gatherData(final GatherDataEvent.Server event) {
        final PackOutput output = event.getGenerator().getPackOutput();
        final CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        event.addProvider(new OffroadAdvancements(output, lookupProvider));
    }

    public static void gatherData(final GatherDataEvent.Client event) {
        event.addProvider(OffroadSoundEvents.REGISTRY.getProvider(event.getGenerator().getPackOutput()));
    }

    public static void registerEvent(final RegisterEvent event) {
        event.register(Registries.SOUND_EVENT, OffroadSoundEventsNeoForge::register);

        if (event.getRegistry() == BuiltInRegistries.TRIGGER_TYPES) {
            OffroadAdvancements.init();
            OffroadAdvancementTriggers.register();
        }
    }
}

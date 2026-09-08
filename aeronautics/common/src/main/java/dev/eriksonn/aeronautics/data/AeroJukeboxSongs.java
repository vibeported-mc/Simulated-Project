package dev.eriksonn.aeronautics.data;

import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.index.AeroSoundEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.JukeboxSong;

/**
 * Aeronautics' music discs.
 *
 * <h2>26.2 note</h2>
 * <p>{@code jukebox_song} is a datapack registry, and an item that names one has that reference
 * resolved while its default components are bound. During datagen the registry holds only what a
 * {@code DatapackBuiltinEntriesProvider} put there -- a hand-written file under
 * {@code src/main/resources} is not loaded -- so binding the music disc's components failed with
 * "Missing element". The song is declared here and generated instead, which is what 26.2 expects and
 * what keeps the item and the song from drifting apart.
 */
public class AeroJukeboxSongs {

    public static final ResourceKey<JukeboxSong> CLOUD_SKIPPER =
            ResourceKey.create(Registries.JUKEBOX_SONG, Aeronautics.path("cloud_skipper"));

    public static void bootstrap(final BootstrapContext<JukeboxSong> context) {
        context.register(CLOUD_SKIPPER, new JukeboxSong(
                // A jukebox song names its sound as a holder, which the registry object already is.
                AeroSoundEvents.MUSIC_DISC_CLOUD_SKIPPER.registryObject().asHolder(),
                Component.translatable("jukebox_song.aeronautics.cloud_skipper"),
                225.0f,
                12));
    }
}

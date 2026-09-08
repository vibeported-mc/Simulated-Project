package dev.eriksonn.aeronautics.neoforge.events;

import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.events.AeronauticsClientEvents;
import dev.eriksonn.aeronautics.neoforge.content.fluids.AeroFluidType;
import dev.eriksonn.aeronautics.neoforge.index.AeroFluidsNeoForge;
import dev.eriksonn.aeronautics.api.CustomSituationalMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = Aeronautics.MOD_ID, value = Dist.CLIENT)
public class AeroNeoForgeClientEvents {

    @SubscribeEvent
    public static void preClientTick(final ClientTickEvent.Pre event) {
        AeronauticsClientEvents.clientLevelTick(false);
    }

    @SubscribeEvent
    public static void postClientTick(final ClientTickEvent.Post event) {
        AeronauticsClientEvents.clientLevelTick(true);
    }

    /**
     * <h2>26.2 note</h2>
     * <p>This was a mixin wrapping the read of {@code Musics.GAME} inside
     * {@code Minecraft.getSituationalMusic}. That field is no longer read there -- background music
     * is an environment attribute the camera carries, selected through {@code BackgroundMusic} -- so
     * there is nothing to wrap.
     *
     * <p>NeoForge's {@code SelectMusicEvent} is the hook for this and is a better fit than the mixin
     * was: it fires once music is chosen, whatever chose it, and {@code setMusic} leaves the
     * higher-priority cases alone. The mixin could only win over the one field it wrapped.
     */
    @SubscribeEvent
    public static void selectMusic(final SelectMusicEvent event) {
        final Minecraft minecraft = Minecraft.getInstance();
        // The music manager ticks on the title screen too, where there is no level and no player.
        // The mixin this replaces sat inside a path that only ran in-world, so it never had to say so.
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        final Music custom = CustomSituationalMusic.getSituationalMusic(minecraft.level, minecraft.player);

        if (custom != null) {
            event.setMusic(custom);
        }
    }


    @EventBusSubscriber(modid = Aeronautics.MOD_ID, value = Dist.CLIENT)
    public static class ModBusEvents {

        @SubscribeEvent
        public static void registerClientExtensions(final RegisterClientExtensionsEvent event) {
            final AeroFluidType type = (AeroFluidType) AeroFluidsNeoForge.LEVITITE_BLEND.getType();
            event.registerFluidType(type, type);
        }

        /**
         * <h2>26.2 note</h2>
         * <p>Three things lived here and none of them has a target any more. All are the same
         * feature -- levitite drawn as a chunk layer with its own shader -- and all are recorded in
         * AERONAUTICS-26.2-OPEN-QUESTIONS.md alongside the three mixins parked for it.
         *
         * <ul>
         *   <li>{@code clientSetup} assigned both levitite render types to the levitite blocks
         *       through {@code ItemBlockRenderTypes}. That class is gone: 26.2 derives a block's
         *       chunk layer from its texture's alpha channel rather than letting a mod name one, and
         *       {@code ChunkSectionLayer} is a closed enum a render type cannot join.</li>
         *   <li>{@code fixChunkRenderTypeSet} reached into NeoForge's {@code ChunkRenderTypeSet} to
         *       re-open its static layer list, for mods that forced the class to initialise early.
         *       The class no longer exists, and neither does {@code RenderType.chunkBufferLayers}.</li>
         *   <li>{@code registerRegisterStageEvent} added the two types as render-level stages
         *       through {@code RenderLevelStageEvent.RegisterStageEvent}, which is gone as well.
         *       Veil's own fixed-buffer registration in {@code AeronauticsClient} covers the same
         *       ground and does survive.</li>
         * </ul>
         */
    }
}

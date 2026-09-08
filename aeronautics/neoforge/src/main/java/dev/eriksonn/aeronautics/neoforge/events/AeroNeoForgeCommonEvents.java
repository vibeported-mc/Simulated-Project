package dev.eriksonn.aeronautics.neoforge.events;

import dev.simulated_team.simulated.data.SimDatagenRegistries;
import dev.eriksonn.aeronautics.data.AeroGeneratedEntries;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.data.AeroEquipmentAssets;
import dev.eriksonn.aeronautics.data.AeroAdvancementTriggers;
import dev.eriksonn.aeronautics.events.AeronauticsCommonEvents;
import dev.eriksonn.aeronautics.index.*;
import dev.eriksonn.aeronautics.neoforge.data.recipe.AeroProcessingRecipeGen;
import dev.eriksonn.aeronautics.neoforge.index.AeroFluidsNeoForge;
import dev.eriksonn.aeronautics.neoforge.service.NeoForgeAeroConfigService;
import net.createmod.catnip.api.config.ConfigBase;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = Aeronautics.MOD_ID)
public class AeroNeoForgeCommonEvents {

	@SubscribeEvent
	public static void serverStop(ServerStoppedEvent event) {
		AeronauticsCommonEvents.onServerStopped(event.getServer());
	}

	@SubscribeEvent
	public static void postServerTick(ServerTickEvent.Post event) {
		final MinecraftServer server = event.getServer();
		for (final ServerLevel level : server.getAllLevels()) {
			AeronauticsCommonEvents.onServerTickEnd(level);
		}
	}

	@EventBusSubscriber(modid = Aeronautics.MOD_ID)
	public static class ModBusEvents {

		@SubscribeEvent
		public static void registerEvent(RegisterEvent event) {
			AeroArmInteractionPoints.init();

			if (event.getRegistry() == BuiltInRegistries.TRIGGER_TYPES) {
				AeroAdvancements.init();
				AeroAdvancementTriggers.register();
			}
		}

		/**
		 * <h2>26.2 note</h2>
		 * <p>26.2 fires a separate event per side instead of handing one event a pair of include
		 * flags, and providers are added to the event rather than to the generator. The event is also
		 * raised per mod, so there is no mod set to filter on -- but it is raised twice, once per
		 * side, so the tag generators guard against being added a second time.
		 */
		private static boolean addedGenerators;

		/**
		 * <h2>26.2 note</h2>
		 * <p>The registries datagen hands over do not contain this mod's own datapack entries -- only
		 * what a {@code DatapackBuiltinEntriesProvider} has put there. The music disc names a jukebox
		 * song, and binding its components resolves that reference, so anything that binds components
		 * needs the enriched view rather than the raw one.
		 *
		 * <p>Both sides need it: the lang provider binds components to compile ponder scenes, and it
		 * runs on the client, where the entries provider itself does not.
		 */
		private static CompletableFuture<HolderLookup.Provider> withGeneratedEntries(final GatherDataEvent event) {
			return new AeroGeneratedEntries(event.getGenerator().getPackOutput(), event.getLookupProvider())
					.getRegistryProvider();
		}

		@SubscribeEvent(priority = EventPriority.HIGH)
		public static void gatherDataHighPriority(GatherDataEvent.Server event) {
			SimDatagenRegistries.set(withGeneratedEntries(event));
			addGenerators();
		}

		@SubscribeEvent(priority = EventPriority.HIGH)
		public static void gatherDataHighPriority(GatherDataEvent.Client event) {
			SimDatagenRegistries.set(withGeneratedEntries(event));
			addGenerators();
		}

		private static void addGenerators() {
			if (addedGenerators)
				return;
			addedGenerators = true;
			AeroTags.addGenerators();
		}

		@SubscribeEvent
		public static void gatherData(GatherDataEvent.Server event) {
			final PackOutput output = event.getGenerator().getPackOutput();
			final CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

			// The datapack entries have to be generated before anything that resolves them: the
			// advancements and the recipes both bind item components, and the music disc names a
			// jukebox song that only exists once this has run.
			final AeroGeneratedEntries generatedEntries = new AeroGeneratedEntries(output, lookupProvider);
			event.addProvider(generatedEntries);

			event.addProvider(new AeroAdvancements(output, generatedEntries.getRegistryProvider()));
			event.addProvider(AeroProcessingRecipeGen.registerAll(output, generatedEntries.getRegistryProvider()));
		}

		@SubscribeEvent
		public static void gatherData(GatherDataEvent.Client event) {
			final PackOutput output = event.getGenerator().getPackOutput();

			event.addProvider(AeroSoundEvents.REGISTRY.getProvider(output));
			// 26.2: an armour material names an equipment asset, and the layers it draws are written
			// out as data rather than handed to the item constructor as a texture.
			event.addProvider(new AeroEquipmentAssets(output));
		}

		@SubscribeEvent
		public static void commonSetup(FMLCommonSetupEvent event) {
			AeroFluidsNeoForge.registerFluidInteractions();
		}

		@SubscribeEvent
		public static void loadConfig(final ModConfigEvent.Loading event) {
			for (final ConfigBase config : NeoForgeAeroConfigService.CONFIGS.values()) {
				if (config.specification == event.getConfig().getSpec()) {
					config.onLoad();
				}
			}
		}

		@SubscribeEvent
		public static void reloadConfig(final ModConfigEvent.Reloading event) {
			for (final ConfigBase config : NeoForgeAeroConfigService.CONFIGS.values()) {
				if (config.specification == event.getConfig().getSpec()) {
					config.onReload();
				}
			}
		}
	}
}

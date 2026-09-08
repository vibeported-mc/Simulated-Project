package dev.eriksonn.aeronautics.neoforge;

import com.tterrag.registrate.util.OneTimeEventReceiver;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.AeronauticsClient;
import dev.eriksonn.aeronautics.events.AeronauticsClientEvents;
import dev.eriksonn.aeronautics.index.client.AeroRenderTypes;
import dev.eriksonn.aeronautics.neoforge.events.AeroNeoForgeClientEvents;
import foundry.veil.forge.event.ForgeVeilRegisterBlockLayersEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Aeronautics.MOD_ID, dist = Dist.CLIENT)
public class AeronauticsNeoForgeClient {
	public AeronauticsNeoForgeClient(final IEventBus modBus, final ModContainer container) {
		// 26.2 port: these classes carry @EventBusSubscriber, and that annotation now covers both buses
		// -- EventBusSubscriber.Bus is gone, and an event reaches the mod bus by implementing
		// IModBusEvent. In 1.21.1 an unqualified @EventBusSubscriber meant the game bus only, so the
		// explicit registration below was what put the mod-bus handlers on. Keeping both now delivers
		// every mod-bus event twice, which showed up as a duplicate trigger-type registration.
		// 26.2 port: Catnip's config *screens* are not ported -- configure-platform.gradle.kts
		// excludes **/client/config/** from the build, because the definitions in api/config are what
		// back the TOML files and only the UI needed porting. Create comments out its own
		// BaseConfigScreen registration for the same reason. Config still works; it just has no
		// in-game screen. Restore this line when Catnip's screens land.
		// container.registerExtensionPoint(IConfigScreenFactory.class, ((c, l) -> new BaseConfigScreen(l, Aeronautics.MOD_ID)));

		modBus.<ForgeVeilRegisterBlockLayersEvent>addListener(event -> event.registerBlockLayer(AeroRenderTypes.levitite()));

		AeronauticsClient.init();
	}
}

package dev.simulated_team.simulated.neoforge;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.SimulatedClient;
import dev.simulated_team.simulated.neoforge.events.SimNeoForgeClientEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Simulated.MOD_ID, dist = Dist.CLIENT)
public class SimulatedNeoForgeClient {

	public SimulatedNeoForgeClient(final IEventBus modEventBus, final ModContainer container) {
		// 26.2 port: Catnip's config *screens* are not ported -- configure-platform.gradle.kts
		// excludes **/client/config/** from the build, because the definitions in api/config are what
		// back the TOML files and only the UI needed porting. Create comments out its own
		// BaseConfigScreen registration for the same reason. Config still works; it just has no
		// in-game screen. Restore this line when Catnip's screens land.
		// container.registerExtensionPoint(IConfigScreenFactory.class, ((c, l) -> new BaseConfigScreen(l, Simulated.MOD_ID)));

		// 26.2 port: these classes carry @EventBusSubscriber, and that annotation now covers both buses
		// -- EventBusSubscriber.Bus is gone, and an event reaches the mod bus by implementing
		// IModBusEvent. In 1.21.1 an unqualified @EventBusSubscriber meant the game bus only, so the
		// explicit registration below was what put the mod-bus handlers on. Keeping both now delivers
		// every mod-bus event twice, which showed up as a duplicate trigger-type registration.
		SimulatedClient.PLUNGER_LAUNCHER_RENDER_HANDLER.registerListeners(NeoForge.EVENT_BUS);

		SimulatedClient.init();
	}

}
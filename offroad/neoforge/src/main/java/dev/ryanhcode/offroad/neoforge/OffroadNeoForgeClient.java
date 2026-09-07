package dev.ryanhcode.offroad.neoforge;

import dev.ryanhcode.offroad.Offroad;
import dev.ryanhcode.offroad.OffroadClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = Offroad.MOD_ID, dist = Dist.CLIENT)
public class OffroadNeoForgeClient {
	public OffroadNeoForgeClient(final IEventBus modBus, final ModContainer container) {
		this.listenClientEvents(modBus);
		// 26.2 port: Catnip's config *screens* are not ported -- configure-platform.gradle.kts
		// excludes **/client/config/** from the build, because the definitions in api/config are what
		// back the TOML files and only the UI needed porting. Create comments out its own
		// BaseConfigScreen registration for the same reason. Config still works; it just has no
		// in-game screen. Restore this line when Catnip's screens land.
		// container.registerExtensionPoint(IConfigScreenFactory.class, ((c, l) -> new BaseConfigScreen(l, Offroad.MOD_ID)));

		OffroadClient.init();
	}

	private void listenClientEvents(final IEventBus modBus) {

	}
}

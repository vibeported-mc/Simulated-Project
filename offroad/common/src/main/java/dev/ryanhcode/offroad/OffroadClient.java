package dev.ryanhcode.offroad;

import com.simibubi.create.content.contraptions.render.ActorClients;
import dev.ryanhcode.offroad.content.blocks.rock_cutting_wheel.RockCuttingWheelActor;
import dev.ryanhcode.offroad.content.blocks.rock_cutting_wheel.RockCuttingWheelActorClient;
import dev.ryanhcode.offroad.content.ponder.OffroadPonderPlugin;
import dev.ryanhcode.offroad.index.OffroadPartialModels;
import net.createmod.ponder.api.client.PonderIndex;

public class OffroadClient {
	public static void init() {
		PonderIndex.addPlugin(new OffroadPonderPlugin());

		// 26.2: how an actor draws itself is registered here rather than implemented on the
		// behaviour, which a dedicated server also instantiates. See ActorClients.
		ActorClients.register(RockCuttingWheelActor.class, new RockCuttingWheelActorClient());

		OffroadPartialModels.init();
	}
}

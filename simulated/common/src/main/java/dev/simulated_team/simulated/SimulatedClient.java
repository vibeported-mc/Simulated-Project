package dev.simulated_team.simulated;

import com.simibubi.create.content.contraptions.render.ActorClients;
import dev.simulated_team.simulated.index.client.SimCustomItemRenderers;
import dev.simulated_team.simulated.client.BlockPropertiesTooltip;
import dev.simulated_team.simulated.content.blocks.altitude_sensor.AltitudeSensorActorClient;
import dev.simulated_team.simulated.content.blocks.altitude_sensor.AltitudeSensorMovementBehaviour;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelRenderer;
import dev.simulated_team.simulated.content.end_sea.EndSeaFadeTransformer;
import dev.simulated_team.simulated.content.end_sea.EndSeaShadowRenderer;
import dev.simulated_team.simulated.content.items.merging_glue.MergingGlueItemHandler;
import dev.simulated_team.simulated.content.items.plunger_launcher.PlungerLauncherItemRenderer;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler;
import dev.simulated_team.simulated.events.SimulatedCommonClientEvents;
import dev.simulated_team.simulated.index.SimPartialModels;
import dev.simulated_team.simulated.index.SimRenderTypes;
import dev.simulated_team.simulated.index.SimResourceManagers;
import dev.simulated_team.simulated.index.ponder.SimPonderPlugin;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.platform.VeilEventPlatform;
import net.createmod.catnip.api.client.render.SuperByteBufferCache;
import net.createmod.ponder.api.client.PonderIndex;

public class SimulatedClient {

    public static final PhysicsStaffClientHandler PHYSICS_STAFF_CLIENT_HANDLER = new PhysicsStaffClientHandler();
    public static PlungerLauncherItemRenderer.RenderHandler PLUNGER_LAUNCHER_RENDER_HANDLER = new PlungerLauncherItemRenderer.RenderHandler();
	public static final MergingGlueItemHandler MERGING_GLUE_ITEM_HANDLER = new MergingGlueItemHandler();

    public static void init() {
        SimPartialModels.init();

        // 26.2: how an actor draws itself is registered here rather than implemented on the
        // behaviour, which a dedicated server also instantiates. See ActorClients.
        ActorClients.register(AltitudeSensorMovementBehaviour.class, new AltitudeSensorActorClient());
        BlockPropertiesTooltip.init();
        SimResourceManagers.init();

        PonderIndex.addPlugin(new SimPonderPlugin());

        VeilEventPlatform.INSTANCE.onVeilRenderLevelStage(EndSeaShadowRenderer::renderShadowMap);

        VeilEventPlatform.INSTANCE.onVeilAddShaderProcessors((provider, registry) -> {
            registry.addPreprocessor(new EndSeaFadeTransformer(), false);
        });
        VeilEventPlatform.INSTANCE.onVeilRegisterFixedBuffers(registry -> {
            registry.registerFixedBuffer(VeilRenderLevelStageEvent.Stage.AFTER_PARTICLES, SimRenderTypes.laser());
            registry.registerFixedBuffer(VeilRenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS, SimRenderTypes.lens());
            registry.registerFixedBuffer(VeilRenderLevelStageEvent.Stage.AFTER_LEVEL, SimRenderTypes.staffOverlay());
            // The physics staff's lock markers, drawn from PhysicsStaffRenderHandler at the same
            // stage. They were never registered, which on 1.21.1 cost nothing because the buffer
            // source was flushed wholesale each frame; on 26.2 only registered types have their
            // batch ended, so the markers were written and then dropped.
            registry.registerFixedBuffer(VeilRenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS, SimRenderTypes.lock());
        });

        VeilEventPlatform.INSTANCE.onVeilRenderLevelStage(SimulatedCommonClientEvents::onRenderLevelStage);

        SuperByteBufferCache.getInstance().registerCompartment(SteeringWheelRenderer.STEERING_WHEEL);
    }
}

package dev.ryanhcode.offroad.content.blocks.rock_cutting_wheel;

import java.util.List;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviourClient;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ActorGeometry;
import com.simibubi.create.content.contraptions.render.ActorVisual;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.createmod.catnip.api.animation.LerpedFloat;
import org.jetbrains.annotations.Nullable;

/**
 * How the rock cutting wheel draws itself on a contraption.
 *
 * <h2>26.2 note</h2>
 * <p>Both rendering hooks left the behaviour. They name {@link VirtualRenderWorld} and
 * {@link VisualizationContext}, which resolve to client-only types; a behaviour is instantiated
 * during registration, including on a dedicated server, where linking a class that declares them
 * fails. Registered into {@code ActorClients} from {@code OffroadClient}.
 */
public class RockCuttingWheelActorClient implements MovementBehaviourClient {

    @Override
    public void extractInContraption(final MovementBehaviour behaviour, final MovementContext context,
                                     final VirtualRenderWorld renderWorld, final ContraptionMatrices matrices,
                                     final List<ActorGeometry> out) {
        if (renderWorld.supportsVisualization()) {
            return;
        }

        if (context.temporaryData == null) {
            context.temporaryData = LerpedFloat.angular();
        }

        RockCuttingWheelRenderer.extractInContraption(context, renderWorld, matrices, out);
    }

    @Override
    public @Nullable ActorVisual createVisual(final MovementBehaviour behaviour, final VisualizationContext visualizationContext,
                                              final VirtualRenderWorld simulationWorld, final MovementContext context) {
        if (context.temporaryData == null) {
            context.temporaryData = LerpedFloat.angular();
        }

        return new RockCuttingWheelActorVisual(visualizationContext, simulationWorld, context);
    }
}

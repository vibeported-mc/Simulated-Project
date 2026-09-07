package dev.simulated_team.simulated.content.blocks.altitude_sensor;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import net.createmod.catnip.api.data.Pair;

/**
 * <h2>26.2 note</h2>
 * <p>{@code renderInContraption} moved out to {@link AltitudeSensorActorClient}. A behaviour is
 * instantiated during registration, including on a dedicated server, and the rendering hook names
 * client-only types -- so it may not live here, nor may this class implement the interface that
 * declares it. See {@code ActorClients}.
 */
public class AltitudeSensorMovementBehaviour implements MovementBehaviour {

    @Override
    public boolean disableBlockEntityRendering() {
        return true;
    }

    @Override
    public void tick(final MovementContext context) {
        MovementBehaviour.super.tick(context);

        // temporaryData <- (previousVisualHeight, visualHeight)
        final float yPos = (float) Sable.HELPER.projectOutOfSubLevel(context.world, JOMLConversion.toJOML(context.position)).y;
        if (context.temporaryData instanceof final Pair<?, ?> heights) {
            context.temporaryData = Pair.of(heights.getSecond(), yPos);
        } else {
            context.temporaryData = Pair.of(yPos, yPos);
        }
    }
}

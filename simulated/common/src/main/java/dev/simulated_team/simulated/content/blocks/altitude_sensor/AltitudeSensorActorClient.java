package dev.simulated_team.simulated.content.blocks.altitude_sensor;

import java.util.List;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviourClient;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ActorGeometry;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.render.RenderLevels;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import net.createmod.catnip.api.client.animation.AnimationTickHolder;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.data.Pair;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

/**
 * How the altitude sensor draws itself on a contraption.
 *
 * <h2>26.2 note</h2>
 * <p>This used to be {@code renderInContraption} on the behaviour itself. Create 26.2 split it out,
 * and the reason is worth repeating because it is not obvious: these methods name
 * {@link VirtualRenderWorld}, which implements a type 26.2 moved into the client. A behaviour is
 * instantiated during registration -- on a dedicated server -- and the JVM resolves parameter types
 * when it links the class, so a behaviour that so much as {@code implements}
 * {@link MovementBehaviourClient} fails to load there. The table of which client belongs to which
 * behaviour lives in {@code ActorClients} instead, and this class is registered into it from
 * {@code SimulatedClient}.
 */
public class AltitudeSensorActorClient implements MovementBehaviourClient {

    @Override
    public void extractInContraption(final MovementBehaviour behaviour, final MovementContext context,
                                     final VirtualRenderWorld renderWorld, final ContraptionMatrices matrices,
                                     final List<ActorGeometry> out) {
        final float lowSignal = context.blockEntityData.getFloatOr("low_signal", 0.0f);
        final float highSignal = context.blockEntityData.getFloatOr("high_signal", 0.0f);

        final float visualHeight;
        if (context.temporaryData instanceof final Pair<?, ?> heights) {
            visualHeight = ((float) heights.getFirst()) * (1 - AnimationTickHolder.getPartialTicks()) + (float) heights.getSecond() * AnimationTickHolder.getPartialTicks();
        } else {
            final Vector3d pos = context.position != null ? JOMLConversion.toJOML(context.position) : new Vector3d();
            visualHeight = (float) Sable.HELPER.projectOutOfSubLevel(context.world, pos).y;
        }

        final Level level = context.contraption.entity.level();
        final float y = (float) Mth.map(context.position.y, level.getMinY(), level.getMaxY(), 0.0f, 1.0f);
        final float value = Mth.clampedMap(y, 0.0f, 1.0f, lowSignal, highSignal);

        for (final SuperByteBuffer geometry : AltitudeSensorRenderer.buildBuffers(context.state, 1000, value, visualHeight,
                matrices.getModel(), RenderLevels.lightSource(context.world, renderWorld), matrices.getWorld(),
                LightCoordsUtil.getLightCoords(renderWorld, context.localPos))) {
            out.add(ActorGeometry.of(matrices.getViewProjection(), geometry, RenderTypes.cutoutMovingBlock()));
        }
    }
}

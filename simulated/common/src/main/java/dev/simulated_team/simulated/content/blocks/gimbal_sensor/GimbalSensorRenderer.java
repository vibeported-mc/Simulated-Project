package dev.simulated_team.simulated.content.blocks.gimbal_sensor;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer.FilterRenderState;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.simulated_team.simulated.index.SimPartialModels;
import dev.simulated_team.simulated.util.SimColors;
import dev.simulated_team.simulated.util.SimDirectionUtil;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * <h2>26.2 note</h2>
 * <p>Everything here reads the block entity -- the redstone power per face, and the three nested
 * quaternions the gimbal, compass and needle are turned by -- so it all moves into extraction.
 *
 * <p>{@code FilteringRenderer.renderOnBlockEntity} is gone: the filter is extracted into a
 * {@link FilterRenderState} and submitted from it, which needs an {@link ItemModelResolver}. That is
 * what {@code SmartBlockEntityRenderer} does for its own subclasses; this renderer is a
 * {@code SafeBlockEntityRenderer}, so it takes the resolver from the context itself.
 *
 * <p>The indicators are drawn under a shared pose translation. That is not something a buffer can
 * carry, so it stays in the submit phase, where the pose exists.
 */
public class GimbalSensorRenderer
        extends SafeBlockEntityRenderer<GimbalSensorBlockEntity, GimbalSensorRenderer.GimbalSensorRenderState> {

    public static class GimbalSensorRenderState extends SafeRenderState {
        public final List<SuperByteBufferRenderState> indicators = new ArrayList<>();
        public final List<SuperByteBufferRenderState> dials = new ArrayList<>();
        public @Nullable FilterRenderState filter;
    }

    private final ItemModelResolver itemModelResolver;

    public GimbalSensorRenderer(final BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public GimbalSensorRenderState createRenderState() {
        return new GimbalSensorRenderState();
    }

    @Override
    protected void extractSafe(final GimbalSensorBlockEntity be, final GimbalSensorRenderState state, final float partialTicks, final Vec3 cameraPosition) {
        state.filter = FilteringRenderer.getFilterRenderState(be, this.itemModelResolver, cameraPosition);

        state.indicators.clear();
        state.dials.clear();

        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            return;
        }

        final Quaternionf Q = be.getBaseQuaternion();

        for (final Direction direction : SimDirectionUtil.Y_AXIS_PLANE) {
            // SuperByteBuffer.rotateToFace is gone; CachedBufferer.partialFacing bakes exactly the
            // same turn into the model and caches it per direction.
            final SuperByteBuffer indicator = CachedBufferer.partialFacing(SimPartialModels.GIMBAL_SENSOR_INDICATOR, be.getBlockState(), direction);

            indicator.translate(0, 0, -0.5);
            final float signalStrength = Math.max(be.getPower(direction), 0) / 15.0F;
            final int color = SimColors.redstone(signalStrength); // Analog indicators (mixes between colors smoothly)
            // int color = (signalStrength > 0) ? 0xCD0000 : 0x630002; // Digital indicators (on/off only)
            state.indicators.add(indicator.light(state.lightCoords)
                    .color(color)
                    .extractRenderState());
        }

        be.applyPrimaryQuaternion(Q, partialTicks);
        this.apply(SimPartialModels.GIMBAL_SENSOR_GIMBAL, be, Q, state);
        be.applySecondaryQuaternion(Q, partialTicks);
        this.apply(SimPartialModels.GIMBAL_SENSOR_COMPASS, be, Q, state);
        be.applyCompassQuaternion(Q, partialTicks);
        this.apply(SimPartialModels.GIMBAL_SENSOR_NEEDLE, be, Q, state);
    }

    @Override
    protected void submitSafe(final GimbalSensorRenderState state, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        if (state.filter != null)
            state.filter.submit(state.blockState, queue, ms, state.lightCoords);

        ms.pushPose();
        ms.translate(0.5, 0, 0.5);
        for (final SuperByteBufferRenderState indicator : state.indicators)
            indicator.submit(ms, RenderTypes.cutoutMovingBlock(), queue);
        ms.popPose();

        for (final SuperByteBufferRenderState dial : state.dials)
            dial.submit(ms, RenderTypes.cutoutMovingBlock(), queue);
    }

    private void apply(final PartialModel model, final GimbalSensorBlockEntity te, final Quaternionf Q, final GimbalSensorRenderState state) {
        final SuperByteBuffer buf = CachedBufferer.partial(model, te.getBlockState());
        buf.rotateCentered(Q);
        buf.translate(0.5, 0.5, 0.5);
        state.dials.add(buf.light(state.lightCoords).extractRenderState());
    }
}

package dev.simulated_team.simulated.content.blocks.analog_transmission;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.simulated_team.simulated.index.SimPartialModels;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * <h2>26.2 note</h2>
 * <p>The shaft is what the base class draws, so it comes through {@code getRenderedBlockState} as
 * before and needs no work here. The extra cogwheel is this renderer's own, and it is turned by the
 * block entity's kinetics -- so it is baked during extraction and queued from the render state.
 *
 * <p>{@code renderRotatingKineticBlock} is gone; the shaft it drew is exactly what the base class's
 * own extraction already produces, so the second draw was redundant once the model came from
 * {@code getRenderedBlockState}.
 */
public class AnalogTransmissionRenderer
        extends KineticBlockEntityRenderer<AnalogTransmissionBlockEntity, AnalogTransmissionRenderer.AnalogTransmissionRenderState> {

    public static class AnalogTransmissionRenderState extends KineticRenderState {
        public @Nullable SuperByteBufferRenderState cogwheel;
    }

    public AnalogTransmissionRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public AnalogTransmissionRenderState createRenderState() {
        return new AnalogTransmissionRenderState();
    }

    @Override
    protected void extractSafe(final AnalogTransmissionBlockEntity be, final AnalogTransmissionRenderState state, final float partialTicks, final Vec3 cameraPosition) {
        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            state.skip = true;
            return;
        }

        super.extractSafe(be, state, partialTicks, cameraPosition);

        final BlockState blockState = be.getBlockState();
        final Direction.Axis axis = ((IRotate) blockState.getBlock()).getRotationAxis(blockState);

        state.cogwheel = kineticRotationTransform(
                CachedBufferer.partialFacingVertical(SimPartialModels.ANALOG_TRANSMISSION_COG, blockState, Direction.fromAxisAndDirection(blockState.getValue(AnalogTransmissionBlock.AXIS), Direction.AxisDirection.POSITIVE)),
                be.getExtraKinetics(),
                axis,
                getAngleForBe(be.getExtraKinetics(), be.getBlockPos(), axis),
                state.lightCoords).extractRenderState();
    }

    @Override
    protected void submitSafe(final AnalogTransmissionRenderState state, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(state, ms, queue, camera);
        if (state.cogwheel != null)
            state.cogwheel.submit(ms, RenderTypes.solidMovingBlock(), queue);
    }

    @Override
    protected BlockState getRenderedBlockState(final AnalogTransmissionBlockEntity be) {
        return shaft(getRotationAxisOf(be));
    }
}

package dev.simulated_team.simulated.content.blocks.velocity_sensor;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.simulated_team.simulated.content.blocks.util.AbstractDirectionalAxisBlock;
import dev.simulated_team.simulated.index.SimPartialModels;
import dev.simulated_team.simulated.util.SimColors;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * <h2>26.2 note</h2>
 * <p>The two diodes and the fan all read the block entity -- signal strength for the diode colours,
 * the fan's interpolated angle -- so all three are baked during extraction.
 *
 * <p>One thing the old code got away with and the split does not: both diode draws shared a single
 * {@code SuperByteBuffer}, re-transforming it between them. A render state is a snapshot, so each
 * needs its own buffer or the second transform would apply to geometry the first already captured.
 */
public class VelocitySensorRenderer
        extends SafeBlockEntityRenderer<VelocitySensorBlockEntity, VelocitySensorRenderer.VelocitySensorRenderState> {

    public static class VelocitySensorRenderState extends SafeRenderState {
        public final List<SuperByteBufferRenderState> parts = new ArrayList<>();
    }

    public VelocitySensorRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public VelocitySensorRenderState createRenderState() {
        return new VelocitySensorRenderState();
    }

    @Override
    protected void extractSafe(final VelocitySensorBlockEntity be, final VelocitySensorRenderState renderState, final float partialTicks, final Vec3 cameraPosition) {
        renderState.parts.clear();

        final BlockState state = be.getBlockState();

        boolean front = (state.getValue(VelocitySensorBlock.POWERED) == 1);
        // north + east always opposite
        // south + down second axis opposite, west first axis opposite
        final boolean axis = state.getValue(VelocitySensorBlock.AXIS_ALONG_FIRST_COORDINATE);
        front = switch (state.getValue(VelocitySensorBlock.FACING)) {
            case NORTH, EAST -> !front;
            case SOUTH, DOWN -> axis == front;
            case WEST -> axis != front;
            case UP -> front;
        };
        final float signalStrength = be.getRedstoneStrength() / 15F;
        final int color = SimColors.redstone(signalStrength);

        final SuperByteBuffer diodeFront = CachedBufferer.partial(SimPartialModels.VELOCITY_SENSOR_DIODE, state);
        this.transform(diodeFront, state);
        renderState.parts.add(diodeFront.light(renderState.lightCoords)
                .color(front ? color : SimColors.REDSTONE_OFF).extractRenderState());

        final SuperByteBuffer diodeBack = CachedBufferer.partial(SimPartialModels.VELOCITY_SENSOR_DIODE, state);
        this.transform(diodeBack, state);
        diodeBack.rotateCentered(Mth.PI, Direction.Axis.Y);
        renderState.parts.add(diodeBack.light(renderState.lightCoords)
                .color(front ? SimColors.REDSTONE_OFF : color).extractRenderState());

        final SuperByteBuffer fan = CachedBufferer.partial(SimPartialModels.VELOCITY_SENSOR_FAN, state);
        this.transform(fan.rotateCentered(be.getFanAngle(partialTicks), AbstractDirectionalAxisBlock.getDirectionOfAxis(state)), state);
        renderState.parts.add(fan.light(renderState.lightCoords).extractRenderState());
    }

    @Override
    protected void submitSafe(final VelocitySensorRenderState renderState, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        for (final SuperByteBufferRenderState part : renderState.parts)
            part.submit(ms, RenderTypes.cutoutMovingBlock(), queue);
    }

    private void transform(final SuperByteBuffer diode, final BlockState state) {
        final Direction dir = state.getValue(VelocitySensorBlock.FACING);
        final boolean axis = state.getValue(VelocitySensorBlock.AXIS_ALONG_FIRST_COORDINATE);
        if (axis == (dir.getStepX() == 0)) {
            diode.rotateCenteredDegrees(90, state.getValue(VelocitySensorBlock.FACING));
        }

        diode.rotateCentered(dir.getRotation());
    }
}

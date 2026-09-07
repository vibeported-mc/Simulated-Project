package dev.simulated_team.simulated.content.blocks.swivel_bearing;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.simulated_team.simulated.index.SimPartialModels;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
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
 * <p>Both extra pieces -- the cogwheel, and the shaft drawn only while the bearing is not assembled
 * -- are decided from the block entity, so they are baked during extraction and queued from the
 * render state. {@code renderRotatingBuffer} went with the immediate-mode path;
 * {@code standardKineticRotationTransform} is the transform it applied.
 */
public class SwivelBearingRenderer
        extends KineticBlockEntityRenderer<SwivelBearingBlockEntity, SwivelBearingRenderer.SwivelBearingRenderState> {

    public static class SwivelBearingRenderState extends KineticRenderState {
        public final List<SuperByteBufferRenderState> extra = new ArrayList<>();
    }

    public SwivelBearingRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public SwivelBearingRenderState createRenderState() {
        return new SwivelBearingRenderState();
    }

    @Override
    protected void extractSafe(final SwivelBearingBlockEntity be, final SwivelBearingRenderState state, final float partialTicks, final Vec3 cameraPosition) {
        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            state.skip = true;
            return;
        }

        super.extractSafe(be, state, partialTicks, cameraPosition);

        // Reused between frames, so cleared rather than appended to.
        state.extra.clear();

        final BlockState blockState = be.getBlockState();
        final Direction.Axis axis = ((IRotate) blockState.getBlock()).getRotationAxis(blockState);

        state.extra.add(kineticRotationTransform(
                CachedBufferer.partialFacingVertical(SimPartialModels.SWIVEL_BEARING_COG, blockState, blockState.getValue(SwivelBearingBlock.FACING).getOpposite()),
                be.getExtraKinetics(),
                axis,
                getAngleForBe(be.getExtraKinetics(), be.getBlockPos(), axis),
                state.lightCoords).extractRenderState());

        if (!be.isAssembled()) {
            state.extra.add(standardKineticRotationTransform(
                    CachedBufferer.partialFacing(SimPartialModels.SHAFT_SIXTEENTH, blockState, blockState.getValue(SwivelBearingBlock.FACING)),
                    be, state.lightCoords).extractRenderState());
        }
    }

    @Override
    protected void submitSafe(final SwivelBearingRenderState state, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(state, ms, queue, camera);
        for (final SuperByteBufferRenderState part : state.extra)
            part.submit(ms, RenderTypes.solidMovingBlock(), queue);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(final SwivelBearingBlockEntity be, final BlockState state) {
        return CachedBufferer.partialFacing(SimPartialModels.SHAFT_SIXTEENTH, state, state.getValue(SwivelBearingBlock.FACING).getOpposite());
    }
}

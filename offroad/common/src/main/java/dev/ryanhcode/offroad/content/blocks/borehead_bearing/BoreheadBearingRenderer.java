package dev.ryanhcode.offroad.content.blocks.borehead_bearing;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.api.client.animation.AnimationTickHolder;
import net.createmod.catnip.api.data.Iterate;
import net.createmod.catnip.api.math.AngleHelper;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * <h2>26.2 note</h2>
 * <p>Both shaft halves and the bearing top are turned by the block entity's speed and interpolated
 * angle, so all of it is baked during extraction. The render time is read there too -- the submit
 * phase has no business asking what tick it is.
 *
 * <p>{@code getRenderedBlockState} still names the shaft the base class draws, so this renderer's own
 * pieces are the two half-shafts and the top.
 */
public class BoreheadBearingRenderer
        extends KineticBlockEntityRenderer<BoreheadBearingBlockEntity, BoreheadBearingRenderer.BoreheadBearingRenderState> {

    public static class BoreheadBearingRenderState extends KineticRenderState {
        public final List<SuperByteBufferRenderState> parts = new ArrayList<>();
    }

    public BoreheadBearingRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public BoreheadBearingRenderState createRenderState() {
        return new BoreheadBearingRenderState();
    }

    @Override
    protected void extractSafe(final BoreheadBearingBlockEntity be, final BoreheadBearingRenderState renderState, final float partialTicks, final Vec3 cameraPosition) {
        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            renderState.skip = true;
            return;
        }

        super.extractSafe(be, renderState, partialTicks, cameraPosition);
        renderState.parts.clear();

        final BlockState state = be.getBlockState();

        final float time = AnimationTickHolder.getRenderTime(be.getLevel());
        final Direction.Axis rotationAxis = getRotationAxisOf(be);

        for (final Direction direction : Iterate.directionsInAxis(rotationAxis)) {
            final SuperByteBuffer dirShaft = CachedBufferer.partialFacing(AllPartialModels.SHAFT_HALF, state, direction);
            final float offset = getRotationOffsetForPosition(be, be.getBlockPos(), rotationAxis);

            float angle = 0;

            if (be.getSpeed() != 0) {
                angle = direction.getAxisDirection().getStep() * (time * be.getSpeed() * 3f / 10) % 360;
            }

            angle += offset;
            angle = angle / 180f * (float) Math.PI;
            kineticRotationTransform(dirShaft, be, rotationAxis, angle, renderState.lightCoords);
            renderState.parts.add(dirShaft.extractRenderState());
        }

        final Direction facing = state.getValue(BlockStateProperties.FACING);
        final SuperByteBuffer bearingTop = CachedBufferer.partial(AllPartialModels.BEARING_TOP, state);

        final float interpolatedAngle = be.getInterpolatedAngle(partialTicks - 1);
        kineticRotationTransform(bearingTop, be, facing.getAxis(), (float) (interpolatedAngle / 180 * Math.PI), renderState.lightCoords);

        if (facing.getAxis().isHorizontal()) {
            bearingTop.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
        }

        bearingTop.rotateCentered(AngleHelper.rad(-90 - AngleHelper.verticalAngle(facing)), Direction.EAST);
        renderState.parts.add(bearingTop.extractRenderState());
    }

    @Override
    protected void submitSafe(final BoreheadBearingRenderState renderState, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(renderState, ms, queue, camera);
        for (final SuperByteBufferRenderState part : renderState.parts)
            part.submit(ms, RenderTypes.solidMovingBlock(), queue);
    }

    @Override
    protected BlockState getRenderedBlockState(final BoreheadBearingBlockEntity be) {
        return shaft(getRotationAxisOf(be));
    }
}

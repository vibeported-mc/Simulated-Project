package dev.simulated_team.simulated.content.blocks.auger_shaft;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.transform.TransformStack;
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
 * <p>The shaft itself is the base class's job -- {@code renderRotatingBuffer} did by hand what
 * {@code KineticBlockEntityRenderer.extractSafe} already does from {@code getRotatedModel} and
 * {@code getRenderedBlockState}, including the visualization check -- so that call is simply
 * {@code super.extractSafe} now.
 *
 * <p>The two redstone indicators are this renderer's own, and which model each uses depends on the
 * auger's flow direction and speed, so both are baked during extraction.
 */
public class AugerShaftRenderer
        extends KineticBlockEntityRenderer<AugerShaftBlockEntity, AugerShaftRenderer.AugerShaftRenderState> {

    public static class AugerShaftRenderState extends KineticRenderState {
        public final List<SuperByteBufferRenderState> redstone = new ArrayList<>();
    }

    public AugerShaftRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public AugerShaftRenderState createRenderState() {
        return new AugerShaftRenderState();
    }

    @Override
    protected void extractSafe(final AugerShaftBlockEntity be, final AugerShaftRenderState renderState, final float partialTicks,
                               final Vec3 cameraPosition) {
        super.extractSafe(be, renderState, partialTicks, cameraPosition);

        renderState.redstone.clear();

        if (!(be.getBlockState().getBlock() instanceof AugerCogBlock))
            return;

        final BlockState state = this.getRenderedBlockState(be);
        final Direction facing = Direction.get(Direction.AxisDirection.POSITIVE, state.getValue(AugerShaftBlock.AXIS));

        for (int i = 0; i < 2; i++) {
            final SuperByteBuffer redstone = CachedBufferer.partialFacing(be.flowDirection == (i == 1 ? facing.getOpposite() : facing) && be.getSpeed() != 0 ? SimPartialModels.AUGER_REDSTONE_ON : SimPartialModels.AUGER_REDSTONE_OFF, state, facing);

            TransformStack.of(redstone.getTransforms())
                    .center()
                    .rotateToFace(facing)
                    .rotate(Axis.XN.rotationDegrees((facing.getAxis().isHorizontal() ? 90 : 0) + i * 180))
                    .uncenter();

            renderState.redstone.add(redstone.light(renderState.lightCoords).extractRenderState());
        }
    }

    @Override
    protected void submitSafe(final AugerShaftRenderState renderState, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(renderState, ms, queue, camera);
        for (final SuperByteBufferRenderState part : renderState.redstone)
            part.submit(ms, RenderTypes.solidMovingBlock(), queue);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(final AugerShaftBlockEntity be, final BlockState state) {
        if (!(be.getBlockState().getBlock() instanceof AugerCogBlock)) {
            return super.getRotatedModel(be, state);
        }
        final Direction facing = Direction.get(Direction.AxisDirection.POSITIVE, state.getValue(AugerShaftBlock.AXIS));
        return CachedBufferer.partialDirectional(
                SimPartialModels.AUGER_COG, state,
                facing, () -> {
                    final PoseStack poseStack = new PoseStack();
                    TransformStack.of(poseStack)
                            .center()
                            .rotateToFace(facing)
                            .rotate(Axis.XN.rotationDegrees(90))
                            .uncenter();
                    return poseStack;
                });
    }

    @Override
    protected BlockState getRenderedBlockState(final AugerShaftBlockEntity be) {
        return shaft(getRotationAxisOf(be));
    }

}

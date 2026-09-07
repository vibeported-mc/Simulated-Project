package dev.simulated_team.simulated.content.blocks.rope.rope_winch;


import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.pulley.AbstractPulleyRenderer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer.FilterRenderState;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.RopeStrandRenderer;
import dev.simulated_team.simulated.index.SimPartialModels;
import dev.simulated_team.simulated.index.SimSpriteShifts;
import net.createmod.catnip.api.math.AngleHelper;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SpriteShiftEntry;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import static com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE;
import static com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING;

/**
 * <h2>26.2 note</h2>
 * <p>The shaft, the coil and the rope strand all read the block entity, so all three are baked
 * during extraction. The rope is the interesting one: it is a chain of segments whose poses are
 * rebuilt at submit time, so {@link RopeStrandRenderer} splits the same way and its state is carried
 * here.
 *
 * <p>{@code shouldRenderOffScreen} no longer takes the block entity, and
 * {@code FilteringRenderer.renderOnBlockEntity} is gone -- the filter is extracted into a
 * {@link FilterRenderState}, which needs an {@link ItemModelResolver} from the context.
 */
public class RopeWinchRenderer
        extends SafeBlockEntityRenderer<RopeWinchBlockEntity, RopeWinchRenderer.RopeWinchRenderState> {

    public static class RopeWinchRenderState extends SafeRenderState {
        public @Nullable FilterRenderState filter;
        public @Nullable SuperByteBufferRenderState shaft;
        public @Nullable SuperByteBufferRenderState ropeCoil;
        public final RopeStrandRenderer.RopeRenderState rope = new RopeStrandRenderer.RopeRenderState();
    }

    private final ItemModelResolver itemModelResolver;

    public RopeWinchRenderer(final BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public RopeWinchRenderState createRenderState() {
        return new RopeWinchRenderState();
    }

    private static SuperByteBuffer transform(final SuperByteBuffer buffer, final BlockState state, final boolean axisDirectionMatters) {
        final Direction facing = state.getValue(FACING);

        final float zRotLast =
                axisDirectionMatters && (state.getValue(AXIS_ALONG_FIRST_COORDINATE) ^ facing.getAxis() == Direction.Axis.Z) ? 90
                        : 0;
        final float yRot = AngleHelper.horizontalAngle(facing) + (state.getValue(AXIS_ALONG_FIRST_COORDINATE) || facing.getAxis()
                != Direction.Axis.Y ? 0.0f : 90.0f);
        final float zRot = facing == Direction.UP ? 270 : facing == Direction.DOWN ? 90 : 0;

        buffer.rotateCentered((float) ((zRot) / 180 * Math.PI), Direction.SOUTH);
        buffer.rotateCentered((float) ((yRot) / 180 * Math.PI), Direction.UP);
        buffer.rotateCentered((float) ((zRotLast) / 180 * Math.PI), Direction.SOUTH);
        return buffer;
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public boolean shouldRender(final RopeWinchBlockEntity pBlockEntity, final Vec3 pCameraPos) {
        return true;
    }

    @Override
    protected void extractSafe(final RopeWinchBlockEntity be, final RopeWinchRenderState renderState, final float partialTicks, final Vec3 cameraPosition) {
        renderState.filter = FilteringRenderer.getFilterRenderState(be, this.itemModelResolver, cameraPosition);

        final BlockState state = be.getBlockState();
        final SuperByteBuffer shaft = CachedBufferer.partial(SimPartialModels.ROPE_WINCH_SHAFT, state);

        final Direction.Axis axis = KineticBlockEntityRenderer.getRotationAxisOf(be);
        final float angle = KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), axis);
        KineticBlockEntityRenderer.kineticRotationTransform(shaft, be, axis, angle, renderState.lightCoords);
        renderState.shaft = transform(shaft, state, true).extractRenderState();

        if (be.getRopeHolder().isAttached() || (be.isVirtual() && be.getRopeHolder().renderAttached)) {
            final SuperByteBuffer ropeCoil = CachedBufferer.partial(SimPartialModels.ROPE_WINCH_ROPE_COIL, state);
            ropeCoil.light(renderState.lightCoords);

            final Direction facing = state.getValue(FACING);
            final float speed;

            if (facing == Direction.DOWN) {
                speed = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 1.0f : -1.0f;
            } else {
                speed = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE == (state.getValue(AXIS_ALONG_FIRST_COORDINATE)) ? 1.0f : -1.0f;
            }

            AbstractPulleyRenderer.scrollCoil(ropeCoil, this.getCoilShift(), be.clientAngle.getValue(partialTicks), speed);

            renderState.ropeCoil = transform(ropeCoil, state, true).extractRenderState();
        } else {
            // Reused between frames, so an absent coil has to be cleared rather than left standing.
            renderState.ropeCoil = null;
        }

        RopeStrandRenderer.extract(be, be.getRopeHolder(), partialTicks, renderState.rope);
    }

    @Override
    protected void submitSafe(final RopeWinchRenderState renderState, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        if (renderState.filter != null)
            renderState.filter.submit(renderState.blockState, queue, ms, renderState.lightCoords);

        ms.pushPose();
        if (renderState.shaft != null)
            renderState.shaft.submit(ms, RenderTypes.solidMovingBlock(), queue);
        if (renderState.ropeCoil != null)
            renderState.ropeCoil.submit(ms, RenderTypes.solidMovingBlock(), queue);
        ms.popPose();

        RopeStrandRenderer.submit(renderState.rope, ms, queue);
    }

    protected SpriteShiftEntry getCoilShift() {
        return SimSpriteShifts.ROPE_WINCH_COIL;
    }
}

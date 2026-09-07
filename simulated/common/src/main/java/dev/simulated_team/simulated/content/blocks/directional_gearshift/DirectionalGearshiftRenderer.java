package dev.simulated_team.simulated.content.blocks.directional_gearshift;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.SplitShaftRenderer;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.simulated_team.simulated.index.SimBlocks;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.api.client.animation.AnimationTickHolder;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * <h2>26.2 note</h2>
 * <p>Split across extract/submit. Every one of the three pieces is baked from block-entity state --
 * speed, powered sides, source facing -- so all three are built during extraction and submission only
 * plays them back.
 *
 * <p>Three transforms moved. {@code rotateToFace} came from Flywheel's {@code Rotate}, which
 * {@code SuperByteBuffer} no longer inherits; it is reached through the buffer's own transform stack
 * instead. {@code rotateZCentered} and {@code rotateYCentered} became the axis-taking
 * {@code rotateCentered}, so the axis is named rather than baked into the method.
 */
public class DirectionalGearshiftRenderer extends SplitShaftRenderer {

    public static class DirectionalGearshiftRenderState extends SplitShaftRenderState {
        public final List<SuperByteBufferRenderState> pieces = new ArrayList<>(3);
    }

    public DirectionalGearshiftRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public DirectionalGearshiftRenderState createRenderState() {
        return new DirectionalGearshiftRenderState();
    }

    @Override
    protected void extractSafe(final SplitShaftBlockEntity be, final SplitShaftRenderState renderState, final float partialTicks, final Vec3 cameraPosition) {
        super.extractSafe(be, renderState, partialTicks, cameraPosition);

        final DirectionalGearshiftRenderState state = (DirectionalGearshiftRenderState) renderState;
        state.pieces.clear();
        if (state.skip) {
            return;
        }

        final BlockState blockState = be.getBlockState();
        final Direction.Axis axis = SimBlocks.DIRECTIONAL_GEARSHIFT.get().getRotationAxis(blockState);

        final float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float angle = (time * be.getSpeed() * 3f / 10) % 360;
        float shaftAngle = 0.0f;
        float modifier = 0.0f;
        float offset = 0.0f;

        if (be.hasSource() && !blockState.getValue(DirectionalGearshiftBlock.LEFT_POWERED) && blockState.getValue(DirectionalGearshiftBlock.RIGHT_POWERED)) {
            shaftAngle = angle;
        }

        if (be.hasSource() && blockState.getValue(DirectionalGearshiftBlock.LEFT_POWERED) && !blockState.getValue(DirectionalGearshiftBlock.RIGHT_POWERED)) {
            modifier = be.getRotationSpeedModifier(be.getSourceFacing().getOpposite());
            offset = getRotationOffsetForPosition(be, be.getBlockPos(), axis);
        }

        angle *= modifier;
        angle += offset;
        angle = angle / 180f * (float) Math.PI;
        shaftAngle = shaftAngle / 180f * (float) Math.PI;

        final Direction direction = blockState.getValue(DirectionalGearshiftBlock.FACING);
        final boolean vertical = axis.isVertical() || (direction.getAxis().isVertical() && !blockState.getValue(DirectionalGearshiftBlock.AXIS_ALONG_FIRST_COORDINATE));
        final int light = state.lightCoords;

        final SuperByteBuffer barrelShaftA = CachedBufferer.partial(SimPartialModels.DIRECTIONAL_GEARSHIFT_BARREL_SHAFT, blockState);
        kineticRotationTransform(barrelShaftA, be, axis, angle, light);
        faceDirection(barrelShaftA, direction);
        if (vertical) {
            barrelShaftA.rotateCenteredDegrees(90, Direction.SOUTH);
        }
        barrelShaftA.rotateCentered((float) Math.PI, Direction.SOUTH);
        barrelShaftA.rotateCentered(shaftAngle, Direction.UP);
        state.pieces.add(barrelShaftA.light(light).extractRenderState());

        final SuperByteBuffer barrelShaftB = CachedBufferer.partial(SimPartialModels.DIRECTIONAL_GEARSHIFT_BARREL_SHAFT, blockState);
        kineticRotationTransform(barrelShaftB, be, axis, angle, light);
        faceDirection(barrelShaftB, direction);
        if (vertical) {
            barrelShaftB.rotateCenteredDegrees(90, Direction.SOUTH);
        }
        barrelShaftB.rotateCentered(shaftAngle, Direction.UP);
        state.pieces.add(barrelShaftB.light(light).extractRenderState());

        final SuperByteBuffer barrel = CachedBufferer.partial(SimPartialModels.DIRECTIONAL_GEARSHIFT_CENTER, blockState);
        kineticRotationTransform(barrel, be, axis, angle, light);
        faceDirection(barrel, direction);
        if (vertical) {
            barrel.rotateCenteredDegrees(90, Direction.SOUTH);
        }
        state.pieces.add(barrel.light(light).extractRenderState());
    }

    @Override
    protected void submitSafe(final SplitShaftRenderState renderState, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(renderState, ms, queue, camera);

        for (final SuperByteBufferRenderState piece : ((DirectionalGearshiftRenderState) renderState).pieces) {
            piece.submit(ms, RenderTypes.solidMovingBlock(), queue);
        }
    }

    private static void faceDirection(final SuperByteBuffer buffer, final @Nullable Direction direction) {
        buffer.center();
        TransformStack.of(buffer.getTransforms()).rotateToFace(direction);
        buffer.uncenter();
    }
}

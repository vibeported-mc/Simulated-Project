package dev.simulated_team.simulated.content.blocks.throttle_lever;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlock;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.simulated_team.simulated.index.SimBlocks;
import dev.simulated_team.simulated.index.SimPartialModels;
import dev.simulated_team.simulated.util.SimColors;
import net.createmod.catnip.api.math.AngleHelper;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * <h2>26.2 note</h2>
 * <p>The handle angle, the button press and the diode's signal all come off the block entity, so the
 * geometry is baked during extraction. Whether the player is looking at this lever is read there
 * too -- the submit phase has no business asking about the hit result.
 *
 * <p>{@code LevelRenderer.renderShape} is gone, and with it the {@code @Invoker} that reached it.
 * The outline is drawn by walking the shape's edges here instead, inside a
 * {@code submitCustomGeometry} callback, which is how 26.2 hands over a consumer for raw line
 * geometry.
 */
public class ThrottleLeverRenderer
        extends SafeBlockEntityRenderer<ThrottleLeverBlockEntity, ThrottleLeverRenderer.ThrottleLeverRenderState> {

    protected static final double ANGLE_LIMIT = 40.0;

    public static class ThrottleLeverRenderState extends SafeRenderState {
        public final List<SuperByteBufferRenderState> parts = new ArrayList<>();
        /** The handle's angle, needed again to place the outline. */
        public float angle;
        public boolean outlined;
        public @Nullable BlockState leverState;
    }

    public ThrottleLeverRenderer(final BlockEntityRendererProvider.Context context) {

    }

    @Override
    public ThrottleLeverRenderState createRenderState() {
        return new ThrottleLeverRenderState();
    }

    public static void transformHandleExternal(final ThrottleLeverBlockEntity blockEntity, final float partialTicks, final PoseStack ms) {
        final float state = blockEntity.clientAngle.getValue(partialTicks);
        final AttachFace face = blockEntity.getBlockState().getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
        float angle = (float) (((state / 15) * (ANGLE_LIMIT * 2) - ANGLE_LIMIT) / 180 * Math.PI);

        if (face == AttachFace.WALL) {
            angle = -angle;
        }

        final PoseTransformStack stack = TransformStack.of(ms);
        transform(stack, blockEntity.getBlockState());
        stack
                .translate(1 / 2f, 3.0 / 16.0, 1 / 2f)
                .rotateX(angle)
                .translateBack(1 / 2f, 3.0 / 16.0, 1 / 2f);
    }

    @Override
    protected void extractSafe(final ThrottleLeverBlockEntity be, final ThrottleLeverRenderState renderState, final float partialTicks,
                               final Vec3 cameraPosition) {
        final BlockState leverState = be.getBlockState();
        final float state = be.clientAngle.getValue(partialTicks);
        final AttachFace face = leverState.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
        float angle = (float) (((state / 15) * (ANGLE_LIMIT * 2) - ANGLE_LIMIT) / 180 * Math.PI);

        if (face == AttachFace.WALL) {
            angle = -angle;
        }

        renderState.parts.clear();
        renderState.angle = angle;
        renderState.leverState = leverState;

        if (!VisualizationManager.supportsVisualization(be.getLevel())) {
            final SuperByteBuffer handle = CachedBufferer.partial(SimPartialModels.THROTTLE_LEVER_HANDLE, leverState);
            final SuperByteBuffer button = CachedBufferer.partial(SimPartialModels.THROTTLE_LEVER_BUTTON, leverState);

            final float signalStrength = Math.max(0, be.state / 15F);
            final SuperByteBuffer diode = CachedBufferer.partial(SimPartialModels.THROTTLE_LEVER_DIODE, leverState);
            final int color = SimColors.redstone(signalStrength);

            final double buttonAngle = be.clientPressedLerp.getValue(partialTicks) * -7f;

            // 26.2: SuperByteBuffer no longer implements TransformStack. It still owns the same
            // pose stack, so the shared helpers below reach it through getTransforms() rather than
            // needing a second copy of themselves for buffers.
            transform(TransformStack.of(handle.getTransforms()), leverState);
            transform(TransformStack.of(button.getTransforms()), leverState);
            transform(TransformStack.of(diode.getTransforms()), leverState);

            this.transformHandleExternal(TransformStack.of(handle.getTransforms()), angle, face);
            renderState.parts.add(handle
                    .light(renderState.lightCoords)
                    .extractRenderState());

            this.transformHandleExternal(TransformStack.of(button.getTransforms()), angle, face)
                    .translate(0, 14 / 16f, 8 / 16f)
                    .rotateXDegrees((float) buttonAngle)
                    .translateBack(0, 14 / 16f, 8 / 16f);
            renderState.parts.add(button
                    .light(renderState.lightCoords)
                    .extractRenderState());

            renderState.parts.add(diode.light(renderState.lightCoords)
                    .color(color)
                    .extractRenderState());
        }

        final Minecraft minecraft = Minecraft.getInstance();

        renderState.outlined = !be.isVirtual()
                && minecraft.hitResult instanceof final BlockHitResult hitResult
                && hitResult.getBlockPos().equals(be.getBlockPos());
    }

    @Override
    protected void submitSafe(final ThrottleLeverRenderState renderState, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        for (final SuperByteBufferRenderState part : renderState.parts)
            part.submit(ms, RenderTypes.cutoutMovingBlock(), queue);

        if (renderState.outlined && renderState.leverState != null)
            submitOutline(renderState.leverState, ms, queue, renderState.angle);
    }

    private static void submitOutline(final BlockState leverState, final PoseStack ms, final SubmitNodeCollector queue, final float angle) {
        final VoxelShape leverShape = SimBlocks.THROTTLE_LEVER.get().getHandleShape(SimBlocks.THROTTLE_LEVER.getDefaultState());

        ms.pushPose();
        final PoseTransformStack stack = TransformStack.of(ms);
        transform(stack, leverState);
        stack
                .translate(1 / 2f, 3.0 / 16.0, 1 / 2f)
                .rotateX(angle)
                .translateBack(1 / 2f, 3.0 / 16.0, 1 / 2f);
        queue.submitCustomGeometry(ms, RenderTypes.lines(),
                (pose, vb) -> renderShape(leverShape, pose, vb, 0.0f, 0.0f, 0.0f, 0.4f));
        ms.popPose();
    }

    /** As Create's track outline uses, so the two look the same where they meet. */
    private static final float LINE_WIDTH = 1.0f;

    /**
     * What {@code LevelRenderer.renderShape} used to do: one line per edge of the shape, each
     * carrying the edge direction as its normal and its width.
     */
    private static void renderShape(final VoxelShape shape, final PoseStack.Pose transform, final VertexConsumer vb,
                                    final float red, final float green, final float blue, final float alpha) {
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float xDiff = (float) (x2 - x1);
            float yDiff = (float) (y2 - y1);
            float zDiff = (float) (z2 - z1);
            final float length = Mth.sqrt(xDiff * xDiff + yDiff * yDiff + zDiff * zDiff);

            xDiff /= length;
            yDiff /= length;
            zDiff /= length;

            // The width goes on every vertex. 26.2 moved line width out of the GL state machine
            // and into the vertex format -- RenderTypes.lines() draws with
            // POSITION_COLOR_NORMAL_LINE_WIDTH -- and a vertex left without one is not drawn thin,
            // it is rejected: `Missing elements in vertex`, thrown mid-frame, which took the client
            // down the moment a throttle lever came into view.
            vb.addVertex(transform.pose(), (float) x1, (float) y1, (float) z1)
                    .setColor(red, green, blue, alpha)
                    .setNormal(transform, xDiff, yDiff, zDiff)
                    .setLineWidth(LINE_WIDTH);
            vb.addVertex(transform.pose(), (float) x2, (float) y2, (float) z2)
                    .setColor(red, green, blue, alpha)
                    .setNormal(transform, xDiff, yDiff, zDiff)
                    .setLineWidth(LINE_WIDTH);
        });
    }

    private <T extends TransformStack<T>> TransformStack<T> transformHandleExternal(final TransformStack<T> buffer, final float angle, final AttachFace face) {
        return buffer
                .translate(1 / 2f, 3 / 16f, 1 / 2f)
                .rotateX(angle)
                .translateBack(1 / 2f, 3 / 16f, 1 / 2f)
                .rotateCentered(face == AttachFace.WALL ? (float) Math.PI : 0.0f, Direction.UP);
    }

    private static  <T extends TransformStack<T>> TransformStack<T> transform(final TransformStack<T> buffer, final BlockState leverState) {
        final AttachFace attached = leverState.getValue(AnalogLeverBlock.FACE);
        final Direction facing = leverState.getValue(AnalogLeverBlock.FACING);

        final float rX;
        switch (attached) {
            case FLOOR -> rX = 0;
            case WALL -> rX = 90;
            default -> rX = 180;
        }

        final float rY = AngleHelper.horizontalAngle(facing);
        buffer.rotateCentered((float) (rY / 180 * Math.PI), Direction.UP);
        buffer.rotateCentered((float) (rX / 180 * Math.PI), Direction.EAST);
        buffer.rotateCentered(attached == AttachFace.CEILING ? (float) Math.PI : 0.0f, Direction.UP);
        return buffer;
    }

}

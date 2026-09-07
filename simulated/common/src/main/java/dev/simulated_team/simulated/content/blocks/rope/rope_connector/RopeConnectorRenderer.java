package dev.simulated_team.simulated.content.blocks.rope.rope_connector;


import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.RopeStrandRenderer;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.api.math.AngleHelper;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * <h2>26.2 note</h2>
 * <p>The knot's position comes from the block entity's visual attachment point, so it is baked
 * during extraction along with the rope strand itself.
 *
 * <p>{@code shouldRenderOffScreen} no longer takes the block entity it is being asked about.
 */
public class RopeConnectorRenderer
        extends SafeBlockEntityRenderer<RopeConnectorBlockEntity, RopeConnectorRenderer.RopeConnectorRenderState> {

    public static class RopeConnectorRenderState extends SafeRenderState {
        public final RopeStrandRenderer.RopeRenderState rope = new RopeStrandRenderer.RopeRenderState();
        public @Nullable SuperByteBufferRenderState knot;
    }

    public RopeConnectorRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public RopeConnectorRenderState createRenderState() {
        return new RopeConnectorRenderState();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public boolean shouldRender(final RopeConnectorBlockEntity blockEntity, final Vec3 cameraPos) {
        return true;
    }

    @Override
    protected void extractSafe(final RopeConnectorBlockEntity be, final RopeConnectorRenderState renderState, final float partialTicks, final Vec3 cameraPosition) {
        RopeStrandRenderer.extract(be, be.getRopeHolder(), partialTicks, renderState.rope);

        final RopeStrandHolderBehavior holder = be.getRopeHolder();

        if ((!holder.isAttached()) && (!be.isVirtual() || !be.getRopeHolder().renderAttached)) {
            // Reused between frames, so a detached connector has to clear its knot.
            renderState.knot = null;
            return;
        }
        final SuperByteBuffer knot = CachedBufferer.partialFacing(SimPartialModels.ROPE_CONNECTOR_KNOT, AllBlocks.ROPE.getDefaultState(), Direction.NORTH);

        final BlockPos blockPos = be.getBlockPos();
        final BlockState state = be.getBlockState();

        final Vec3 attachmentPoint = be.getVisualAttachmentPoint(blockPos, state);
        final Direction facing = state.getValue(RopeConnectorBlock.FACING);

        final SuperByteBuffer knotBuffer = knot.light(renderState.lightCoords);

        final boolean axisAlongFirstCoordinate = state.getValue(RopeConnectorBlock.AXIS_ALONG_FIRST_COORDINATE);

        final float zRotLast = (axisAlongFirstCoordinate ^ facing.getAxis() == Direction.Axis.Z) ? 90 : 0;
        final float yRot = AngleHelper.horizontalAngle(facing) + (axisAlongFirstCoordinate || facing.getAxis() != Direction.Axis.Y ? 0.0f : 90.0f);
        final float zRot = facing == Direction.UP ? 270 : facing == Direction.DOWN ? 90 : 0;

        knotBuffer.translate(attachmentPoint.subtract(Vec3.atCenterOf(blockPos)));
        knotBuffer.rotateCentered((float) ((zRot) / 180 * Math.PI), Direction.SOUTH);
        knotBuffer.rotateCentered((float) ((yRot) / 180 * Math.PI), Direction.UP);
        knotBuffer.rotateCentered((float) ((zRotLast) / 180 * Math.PI), Direction.SOUTH);

        knotBuffer.rotateCentered((float) (Math.PI / 2.0), Direction.UP);
        renderState.knot = knotBuffer.extractRenderState();
    }

    @Override
    protected void submitSafe(final RopeConnectorRenderState renderState, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        RopeStrandRenderer.submit(renderState.rope, ms, queue);

        if (renderState.knot != null)
            renderState.knot.submit(ms, RenderTypes.solidMovingBlock(), queue);
    }
}

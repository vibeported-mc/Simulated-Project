package dev.simulated_team.simulated.content.blocks.redstone.redstone_inductor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.ColoredOverlayBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.simulated_team.simulated.index.SimPartialModels;
import dev.simulated_team.simulated.util.SimColors;
import net.createmod.catnip.api.math.AngleHelper;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * <h2>26.2 note</h2>
 * <p>The base class already extracts the coloured overlay and submits it, but this indicator also has
 * to be turned to face the block. That turn reads the block state, so it belongs in extraction --
 * which means overriding {@code extractSafe} rather than the old {@code renderSafe}, and letting the
 * base submit what is produced.
 *
 * <p>The overlay went from the {@code cutout} chunk layer to
 * {@code RenderTypes.solidMovingBlock()}, which is the base class's own choice for this geometry.
 */
public class RedstoneInductorRenderer
        extends ColoredOverlayBlockEntityRenderer<RedstoneInductorBlockEntity, ColoredOverlayBlockEntityRenderer.ColoredOverlayRenderState> {

    public RedstoneInductorRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void extractSafe(final RedstoneInductorBlockEntity be, final ColoredOverlayRenderState state, final float partialTicks, final Vec3 cameraPosition) {
        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            state.skip = true;
            return;
        }

        final SuperByteBuffer render = render(this.getOverlayBuffer(be), this.getColor(be, partialTicks), state.lightCoords);
        final Direction facing = be.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        render.translate(0.5, 0, 0.5);
        render.rotateYDegrees(AngleHelper.horizontalAngle(facing));
        // 26.2 moved the pose stack off SuperByteBuffer itself; getTransforms() is the same stack.
        // Nothing pops it, so this only duplicates the top entry -- kept so the transform this
        // renderer builds is literally the one it built before.
        render.getTransforms().pushPose();
        state.overlay = render.extractRenderState();
    }

    @Override
    protected int getColor(final RedstoneInductorBlockEntity te, final float partialTicks) {
        final float state = te.lerpedState.getValue(partialTicks);
        return SimColors.redstone(state / 15F);
    }

    @Override
    protected SuperByteBuffer getOverlayBuffer(final RedstoneInductorBlockEntity te) {
        return CachedBufferer.partial(SimPartialModels.REDSTONE_INDUCTOR_INDICATOR, te.getBlockState());
    }
}

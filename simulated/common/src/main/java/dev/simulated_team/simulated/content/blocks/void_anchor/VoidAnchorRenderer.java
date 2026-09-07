package dev.simulated_team.simulated.content.blocks.void_anchor;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.simulated_team.simulated.content.end_sea.EndSeaShadowRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * <h2>26.2 note</h2>
 * <p>This renderer draws nothing. It exists so that every loaded void anchor gets a chance to put
 * itself on the shadow renderer's list once a frame, which used to happen in {@code render}.
 *
 * <p>That registration is a read of the block entity, so it belongs in extraction now. Submission is
 * empty, and the render state is the bare base one -- there is nothing to carry across.
 *
 * <p>{@code shouldRenderOffScreen} lost its block-entity argument: 26.2 asks the renderer once, as a
 * property of the renderer rather than of the instance, and walks the level once per answer.
 */
public class VoidAnchorRenderer implements BlockEntityRenderer<VoidAnchorBlockEntity, BlockEntityRenderState> {

    public VoidAnchorRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public BlockEntityRenderState createRenderState() {
        return new BlockEntityRenderState();
    }

    @Override
    public boolean shouldRender(final VoidAnchorBlockEntity blockEntity, final Vec3 cameraPosition) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 512;
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public void extractRenderState(final VoidAnchorBlockEntity blockEntity, final BlockEntityRenderState state,
                                   final float partialTicks, final Vec3 cameraPosition,
                                   final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);

        if (EndSeaShadowRenderer.renderingShadowMap())
            return;

        EndSeaShadowRenderer.addVoidAnchor(blockEntity);
    }

    @Override
    public void submit(final BlockEntityRenderState state, final PoseStack poseStack,
                       final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
    }
}

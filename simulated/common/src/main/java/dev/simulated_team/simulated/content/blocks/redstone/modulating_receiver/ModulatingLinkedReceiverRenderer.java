package dev.simulated_team.simulated.content.blocks.redstone.modulating_receiver;


import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.simulated_team.simulated.index.SimPartialModels;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import static dev.simulated_team.simulated.content.blocks.redstone.modulating_receiver.ModulatingLinkVisual.MAX_DISTANCE;
import static dev.simulated_team.simulated.content.blocks.redstone.modulating_receiver.ModulatingLinkVisual.SMOOTHING;


/**
 * <h2>26.2 note</h2>
 * <p>Both range plates are positioned from the block entity's configured minimum and maximum, so
 * they are baked during extraction.
 *
 * <p>The old body called {@code super.renderSafe} twice, once before the visualization check and
 * once after. That drew the filter and link overlays twice over; the split makes it one call.
 */
public class ModulatingLinkedReceiverRenderer
        extends SmartBlockEntityRenderer<ModulatingLinkedReceiverBlockEntity, ModulatingLinkedReceiverRenderer.ModulatingLinkedReceiverRenderState> {

    public static class ModulatingLinkedReceiverRenderState extends SmartRenderState {
        public final List<SuperByteBufferRenderState> plates = new ArrayList<>();
    }

    public ModulatingLinkedReceiverRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ModulatingLinkedReceiverRenderState createRenderState() {
        return new ModulatingLinkedReceiverRenderState();
    }

    @Override
    protected void extractSafe(final ModulatingLinkedReceiverBlockEntity be, final ModulatingLinkedReceiverRenderState state, final float partialTicks, final Vec3 cameraPosition) {
        super.extractSafe(be, state, partialTicks, cameraPosition);

        state.plates.clear();

        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            return;
        }

        final Direction facing = be.getBlockState()
                .getValue(BlockStateProperties.FACING);

        final Vec3 pixelNormal = new Vec3(facing.step()).scale(1 / 16.0);

        final float minPos = 5.5f * ((be.minRange - 1) * (SMOOTHING + MAX_DISTANCE - 1)) / ((MAX_DISTANCE - 1) * (SMOOTHING + be.minRange - 1));
        final float maxPos = 5.5f * ((be.maxRange - 1) * (SMOOTHING + MAX_DISTANCE - 1)) / ((MAX_DISTANCE - 1) * (SMOOTHING + be.maxRange - 1));

        for (final boolean bottom : Iterate.trueAndFalse) {
            final SuperByteBuffer superBuffer = CachedBufferer.partial(SimPartialModels.MODULATING_RECEIVER_PLATE, be.getBlockState());

            if (bottom) {
                superBuffer.translate(pixelNormal.scale(minPos));
            } else {
                superBuffer.translate(pixelNormal.scale(0.5 + maxPos));
            }

            if (facing.getAxis().isHorizontal()) {
                superBuffer.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
            }

            superBuffer.rotateCentered(AngleHelper.rad(-90 - AngleHelper.verticalAngle(facing)), Direction.EAST);

            superBuffer.light(state.lightCoords);
            state.plates.add(superBuffer.extractRenderState());
        }
    }

    @Override
    protected void submitSafe(final ModulatingLinkedReceiverRenderState state, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(state, ms, queue, camera);
        for (final SuperByteBufferRenderState plate : state.plates)
            plate.submit(ms, RenderTypes.solidMovingBlock(), queue);
    }
}

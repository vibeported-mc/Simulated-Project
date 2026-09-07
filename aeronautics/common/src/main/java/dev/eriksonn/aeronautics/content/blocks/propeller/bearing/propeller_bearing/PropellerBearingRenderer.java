package dev.eriksonn.aeronautics.content.blocks.propeller.bearing.propeller_bearing;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.eriksonn.aeronautics.index.AeroPartialModels;
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
 * <p>The bearing plate turns with the propeller's interpolated angle, which is read off the block
 * entity, so it is baked during extraction. The base class still draws the shaft through
 * {@code getRotatedModel}.
 */
public class PropellerBearingRenderer
        extends KineticBlockEntityRenderer<PropellerBearingBlockEntity, PropellerBearingRenderer.PropellerBearingRenderState> {

    public static class PropellerBearingRenderState extends KineticRenderState {
        public @Nullable SuperByteBufferRenderState plate;
    }

    public PropellerBearingRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public PropellerBearingRenderState createRenderState() {
        return new PropellerBearingRenderState();
    }

    @Override
    protected void extractSafe(PropellerBearingBlockEntity be, PropellerBearingRenderState state, float partialTicks,
                               Vec3 cameraPosition) {
        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            state.skip = true;
            return;
        }

        super.extractSafe(be, state, partialTicks, cameraPosition);

        final Direction facing = be.getBlockState()
                .getValue(BlockStateProperties.FACING);
        PartialModel top = AeroPartialModels.BEARING_PLATE;
        SuperByteBuffer superBuffer = CachedBufferer.partial(top, be.getBlockState());

        float interpolatedAngle = be.getInterpolatedAngle(partialTicks - 1);
        kineticRotationTransform(superBuffer, be, facing.getAxis(), (float) (interpolatedAngle / 180 * Math.PI), state.lightCoords);

        if (facing.getAxis()
                .isHorizontal())
            superBuffer.rotateCentered(
                    AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())),Direction.UP);
        superBuffer.rotateCentered(AngleHelper.rad(-90 - AngleHelper.verticalAngle(facing)),Direction.EAST);
        state.plate = superBuffer.extractRenderState();
    }

    @Override
    protected void submitSafe(PropellerBearingRenderState state, PoseStack ms, SubmitNodeCollector queue,
                              CameraRenderState camera) {
        super.submitSafe(state, ms, queue, camera);
        if (state.plate != null)
            state.plate.submit(ms, RenderTypes.solidMovingBlock(), queue);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(PropellerBearingBlockEntity te, BlockState state) {
        return CachedBufferer.partialFacing(AllPartialModels.SHAFT_HALF, state, state
                .getValue(BearingBlock.FACING)
                .getOpposite());
    }
}

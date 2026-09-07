package dev.eriksonn.aeronautics.content.blocks.propeller.small;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
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
 * <p>Still abstract and still generic in the block entity, with the render state added as a second
 * parameter so subclasses can carry their own. The propeller's angle is interpolated from the block
 * entity, so it is baked during extraction.
 */
public abstract class SimplePropellerRenderer<T extends BasePropellerBlockEntity, S extends SimplePropellerRenderer.SimplePropellerRenderState>
        extends KineticBlockEntityRenderer<T, S> {

    public static class SimplePropellerRenderState extends KineticRenderState {
        public @Nullable SuperByteBufferRenderState propeller;
    }

    public SimplePropellerRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    @SuppressWarnings("unchecked")
    public S createRenderState() {
        return (S) new SimplePropellerRenderState();
    }

    @Override
    protected void extractSafe(final T be, final S renderState, final float partialTicks, final Vec3 cameraPosition) {
        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            renderState.skip = true;
            return;
        }

        super.extractSafe(be, renderState, partialTicks, cameraPosition);

        final BlockState state = be.getBlockState();
        final Direction dir = state.getValue(BlockStateProperties.FACING);

        final SuperByteBuffer propeller = CachedBufferer.partialFacing(this.getCurrentModel(be), state);

        final float angle = this.getAngle(partialTicks, dir, be);
        kineticRotationTransform(propeller, be, dir.getAxis(), angle, renderState.lightCoords);

        if (dir.getAxis().isHorizontal()) {
            propeller.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(dir.getOpposite())), Direction.UP);
        }
        if (dir.getAxis().isVertical()) {
            propeller.rotateCentered(AngleHelper.rad(AngleHelper.verticalAngle(dir.getOpposite())), Direction.EAST);
        }

        propeller.translate(0, 0, -3 / 16f).rotateCentered(AngleHelper.rad(-90 - AngleHelper.verticalAngle(dir)), Direction.EAST);

        renderState.propeller = propeller.extractRenderState();
    }

    @Override
    protected void submitSafe(final S renderState, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(renderState, ms, queue, camera);
        if (renderState.propeller != null)
            renderState.propeller.submit(ms, RenderTypes.solidMovingBlock(), queue);
    }

    public abstract PartialModel getCurrentModel(T be);

    public float getAngle(final float partialTicks, final Direction dir, final T be) {
        float angle = be.getPreviousAngle() * (1f - partialTicks) + be.getAngle() * partialTicks;

        angle = angle / 180f * (float) Math.PI;

        angle *= 2;

        return angle;
    }

    @Override
    protected SuperByteBuffer getRotatedModel(final T be, final BlockState state) {
        return CachedBufferer.partialFacing(AllPartialModels.SHAFT_HALF, state, state
                .getValue(BearingBlock.FACING)
                .getOpposite());
    }
}

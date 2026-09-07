package dev.eriksonn.aeronautics.content.blocks.propeller.small.smart_propeller;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.render.CachedBufferer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.eriksonn.aeronautics.content.blocks.propeller.small.SimplePropellerRenderer;
import dev.eriksonn.aeronautics.index.AeroPartialModels;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.createmod.catnip.api.math.AngleHelper;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import static dev.eriksonn.aeronautics.content.blocks.propeller.small.smart_propeller.SmartPropellerBlock.REVERSED;

/**
 * <h2>26.2 note</h2>
 * <p>This one does not use the propeller its superclass extracts -- the blade tilts on a hinge, so
 * it builds its own pair of buffers and carries them in its own render state. The shaft still comes
 * from the kinetic base, so {@code super.extractSafe} is skipped and the kinetic transform is
 * applied here instead, which is what the old {@code renderSafe} override did.
 */
public class SmartPropellerRenderer extends SimplePropellerRenderer<SmartPropellerBlockEntity, SmartPropellerRenderer.SmartPropellerRenderState> {

    public static class SmartPropellerRenderState extends SimplePropellerRenderer.SimplePropellerRenderState {
        public @Nullable SuperByteBufferRenderState hinge;
    }

    public SmartPropellerRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public SmartPropellerRenderState createRenderState() {
        return new SmartPropellerRenderState();
    }

    @Override
    protected void extractSafe(final SmartPropellerBlockEntity be, final SmartPropellerRenderState renderState, final float partialTicks,
                               final Vec3 cameraPosition) {
        final BlockState state = this.getRenderedBlockState(be);
        renderState.renderType = this.getRenderType(be, state);
        renderState.model = standardKineticRotationTransform(this.getRotatedModel(be, state), be, renderState.lightCoords)
                .extractRenderState();

        final Direction.Axis horizontal = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);

        final SuperByteBuffer propeller = CachedBufferer.partialFacing(this.getCurrentModel(be), state, Direction.UP)
                .light(renderState.lightCoords);
        final SuperByteBuffer hinge = CachedBufferer.partialFacing(AeroPartialModels.SMART_PROPELLER_HINGE, state, Direction.UP)
                .light(renderState.lightCoords);

        final float hingeAngle = be.getLerpedHingeAngle(partialTicks);
        final float angle = this.getAngle(partialTicks, Direction.UP, be);

        final Direction d = Direction.get(Direction.AxisDirection.NEGATIVE, horizontal);

        hinge.rotateCentered(AngleHelper.rad(hingeAngle), d.getClockWise());
        propeller.rotateCentered(AngleHelper.rad(hingeAngle), d.getClockWise());

        final float factChecked = AngleHelper.rad(AngleHelper.horizontalAngle(d));
        propeller.rotateCentered(factChecked, Direction.UP);
        hinge.rotateCentered(factChecked, Direction.UP);

        kineticRotationTransform(propeller, be, Direction.UP.getAxis(), angle, renderState.lightCoords);

        propeller.translate(0, 10 / 16f, 0);
        propeller.rotateCentered(AngleHelper.rad(90), Direction.EAST);

        hinge.translate(0, -1 / 16f, 0);
        hinge.rotateCentered(AngleHelper.rad(90), Direction.EAST);

        renderState.propeller = propeller.extractRenderState();
        renderState.hinge = hinge.extractRenderState();
    }

    @Override
    protected void submitSafe(final SmartPropellerRenderState renderState, final PoseStack ms, final SubmitNodeCollector queue,
                              final CameraRenderState camera) {
        if (renderState.model != null)
            renderState.model.submit(ms, renderState.renderType, queue);
        if (renderState.propeller != null)
            renderState.propeller.submit(ms, RenderTypes.solidMovingBlock(), queue);
        if (renderState.hinge != null)
            renderState.hinge.submit(ms, RenderTypes.solidMovingBlock(), queue);
    }

    @Override
    public PartialModel getCurrentModel(final SmartPropellerBlockEntity be) {
        return be.getBlockState().getValue(REVERSED) ? AeroPartialModels.SMART_PROPELLER_REVERSED : AeroPartialModels.SMART_PROPELLER;
    }

    @Override
    protected SuperByteBuffer getRotatedModel(final SmartPropellerBlockEntity be, final BlockState state) {
        return CachedBufferer.partialFacing(AllPartialModels.SHAFT_HALF, state, Direction.DOWN);
    }
}

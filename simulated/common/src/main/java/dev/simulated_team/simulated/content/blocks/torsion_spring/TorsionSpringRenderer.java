package dev.simulated_team.simulated.content.blocks.torsion_spring;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.api.math.AngleHelper;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * <h2>26.2 note</h2>
 * <p>The spring's angle and the output shaft's rotation both come off the block entity, so both are
 * baked during extraction. The base class still draws the input shaft through
 * {@code getRotatedModel}.
 */
public class TorsionSpringRenderer
        extends KineticBlockEntityRenderer<TorsionSpringBlockEntity, TorsionSpringRenderer.TorsionSpringRenderState> {

    public static class TorsionSpringRenderState extends KineticRenderState {
        public final List<SuperByteBufferRenderState> extra = new ArrayList<>();
    }

    public TorsionSpringRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public TorsionSpringRenderState createRenderState() {
        return new TorsionSpringRenderState();
    }

    @Override
    protected void extractSafe(final TorsionSpringBlockEntity be, final TorsionSpringRenderState state, final float partialTicks, final Vec3 cameraPosition) {
        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            state.skip = true;
            return;
        }

        super.extractSafe(be, state, partialTicks, cameraPosition);
        state.extra.clear();

        final Direction facing = be.getBlockState().getValue(TorsionSpringBlock.FACING);

        final SuperByteBuffer spring = CachedBufferer.partial(SimPartialModels.TORSION_SPRING, be.getBlockState());
        final float angle = be.interpolatedSpring(partialTicks);
        kineticRotationTransform(spring, be, facing.getAxis(), Mth.DEG_TO_RAD * angle, state.lightCoords);
        if (facing.getAxis().isHorizontal()) {
            spring.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
        }
        spring.rotateCentered(AngleHelper.rad(-90 - AngleHelper.verticalAngle(facing)), Direction.EAST);
        state.extra.add(spring.extractRenderState());

        final SuperByteBuffer shaftOut = CachedBufferer.partialFacing(AllPartialModels.SHAFT_HALF, be.getBlockState(), facing);
        kineticRotationTransform(shaftOut, be, facing.getAxis(), getAngleForBe(be.getExtraKinetics(), be.getBlockPos(), facing.getAxis()), state.lightCoords);
        state.extra.add(shaftOut.extractRenderState());
    }

    @Override
    protected void submitSafe(final TorsionSpringRenderState state, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(state, ms, queue, camera);
        for (final SuperByteBufferRenderState part : state.extra)
            part.submit(ms, RenderTypes.solidMovingBlock(), queue);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(final TorsionSpringBlockEntity be, final BlockState state) {
        return CachedBufferer.partialFacing(AllPartialModels.SHAFT_HALF, state, state
                .getValue(BearingBlock.FACING)
                .getOpposite());
    }
}

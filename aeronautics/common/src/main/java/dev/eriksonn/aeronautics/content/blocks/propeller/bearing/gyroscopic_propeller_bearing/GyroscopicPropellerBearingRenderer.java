package dev.eriksonn.aeronautics.content.blocks.propeller.bearing.gyroscopic_propeller_bearing;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.eriksonn.aeronautics.index.AeroPartialModels;
import net.createmod.catnip.api.math.AngleHelper;
import net.createmod.catnip.api.math.VecHelper;
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
import org.joml.Quaternionf;

/**
 * <h2>26.2 note</h2>
 * <p>Every transform here goes onto the buffer rather than the pose, so the whole body moves into
 * extraction unchanged and the submit is a flat replay. The tilt quaternion is interpolated from the
 * block entity, which is why it cannot wait.
 */
public class GyroscopicPropellerBearingRenderer
        extends KineticBlockEntityRenderer<GyroscopicPropellerBearingBlockEntity, GyroscopicPropellerBearingRenderer.GyroscopicPropellerBearingRenderState> {

    public static class GyroscopicPropellerBearingRenderState extends KineticRenderState {
        public final List<SuperByteBufferRenderState> parts = new ArrayList<>();
    }

    public GyroscopicPropellerBearingRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public GyroscopicPropellerBearingRenderState createRenderState() {
        return new GyroscopicPropellerBearingRenderState();
    }

    @Override
    protected void extractSafe(final GyroscopicPropellerBearingBlockEntity be, final GyroscopicPropellerBearingRenderState renderState, final float partialTicks, final Vec3 cameraPosition) {
        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            renderState.skip = true;
            return;
        }

        super.extractSafe(be, renderState, partialTicks, cameraPosition);
        renderState.parts.clear();

        final Direction facing = be.getBlockState().getValue(BlockStateProperties.FACING);
        final Vec3 normal = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        final Quaternionf tiltQuat = new Quaternionf(be.previousTiltQuat).slerp(be.tiltQuat, partialTicks);
        final Quaternionf Q = new Quaternionf(tiltQuat);
        Q.conjugate();
        Q.mul(new Quaternionf((float) normal.x, (float) normal.y, (float) normal.z, 0f));
        Q.mul(tiltQuat);
        final Vec3 contraptionNormal = new Vec3(Q.x(), Q.y(), Q.z());

        final PartialModel top = AeroPartialModels.BEARING_PLATE_METAL;
        final SuperByteBuffer superBuffer = CachedBufferer.partial(top, be.getBlockState());

        superBuffer.translate(normal.scale(4 / 16f));
        superBuffer.rotateCentered(tiltQuat);
        superBuffer.translate(normal.scale(-4 / 16f));

        final float interpolatedAngle = be.getInterpolatedAngle(partialTicks - 1);
        kineticRotationTransform(superBuffer, be, facing.getAxis(), (float) (interpolatedAngle / 180 * Math.PI), renderState.lightCoords);

        if (facing.getAxis()
                .isHorizontal()) {
            superBuffer.rotateCentered(
                    AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
        }

        superBuffer.rotateCentered(AngleHelper.rad(-90 - AngleHelper.verticalAngle(facing)), Direction.EAST);
        renderState.parts.add(superBuffer.extractRenderState());


        for (int i = 0; i < 4; i++) {

            final SuperByteBuffer headBuffer = CachedBufferer.partial(AeroPartialModels.GYRO_BEARING_PISTON_HEAD, be.getBlockState());
            final SuperByteBuffer poleBuffer = CachedBufferer.partial(AeroPartialModels.GYRO_BEARING_PISTON_POLE, be.getBlockState());
            final Vec3 originalPos = VecHelper.rotate(new Vec3(5.9 / 16.0, 0, 0), -90 * i, Direction.Axis.Y);
            Vec3 translatedPos = originalPos;

            if (facing.getAxis().isHorizontal()) {

                translatedPos = VecHelper.rotate(translatedPos, AngleHelper.horizontalAngle(facing), Direction.Axis.Z);
                translatedPos = VecHelper.rotate(translatedPos, -90 + AngleHelper.verticalAngle(facing), Direction.Axis.X);
            }

            final double translateDistance = translatedPos.dot(contraptionNormal) / normal.dot(contraptionNormal);
            translatedPos = translatedPos.add(normal.scale(translateDistance + 3 / 16.0));

            headBuffer.translate(translatedPos);
            headBuffer.translate(0.5f, 0.5f, 0.5f);

            poleBuffer.translate(translatedPos);
            poleBuffer.translate(0.5f, 0.5f, 0.5f);

            headBuffer.rotate(tiltQuat);
            int j = i;
            if (facing == Direction.DOWN) {
                if (i % 2 == 0) {
                    headBuffer.rotate(AngleHelper.rad(180), Direction.EAST);
                    poleBuffer.rotate(AngleHelper.rad(180), Direction.EAST);
                } else {
                    headBuffer.rotate(AngleHelper.rad(180), Direction.SOUTH);
                    poleBuffer.rotate(AngleHelper.rad(180), Direction.SOUTH);
                }
            }
            if (facing.getAxis().isHorizontal()) {

                headBuffer.rotate(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
                poleBuffer.rotate(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
                headBuffer.rotate(AngleHelper.rad(-90 + AngleHelper.verticalAngle(facing)), Direction.EAST);
                poleBuffer.rotate(AngleHelper.rad(-90 + AngleHelper.verticalAngle(facing)), Direction.EAST);
                j = 2 - j;
            }

            poleBuffer.translate(0, 0.5f / 16.0, 0);

            headBuffer.rotate(AngleHelper.rad(-90 * j), Direction.UP);
            poleBuffer.rotate(AngleHelper.rad(-90 * j), Direction.UP);

            renderState.parts.add(headBuffer.light(renderState.lightCoords).extractRenderState());
            renderState.parts.add(poleBuffer.light(renderState.lightCoords).extractRenderState());

        }
    }

    @Override
    protected void submitSafe(final GyroscopicPropellerBearingRenderState renderState, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(renderState, ms, queue, camera);
        for (final SuperByteBufferRenderState part : renderState.parts)
            part.submit(ms, RenderTypes.solidMovingBlock(), queue);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(final GyroscopicPropellerBearingBlockEntity be, final BlockState state) {
        return CachedBufferer.partialFacing(AllPartialModels.SHAFT_HALF, state, state
                .getValue(BearingBlock.FACING)
                .getOpposite());
    }
}

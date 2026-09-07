package dev.simulated_team.simulated.content.blocks.altitude_sensor;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.simulated_team.simulated.index.SimPartialModels;
import dev.simulated_team.simulated.util.SimColors;
import net.createmod.catnip.api.client.animation.AnimationTickHolder;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;


/**
 * <h2>26.2 note</h2>
 * <p>The shared {@code render} is now {@link #buildBuffers}, which stops one step earlier: it
 * returns the transformed buffers rather than drawing them. That is what lets the two callers -- this
 * renderer and {@link AltitudeSensorMovementBehaviour} -- take the same geometry to different places,
 * one into a render state and the other into a contraption's {@code ActorGeometry} list.
 *
 * <p>{@code useLevelLight} takes a {@code BlockAndTintGetter} rather than a {@code Level}, which is
 * what lets a contraption pass a light source built from its virtual world.
 */
public class AltitudeSensorRenderer
        extends SmartBlockEntityRenderer<AltitudeSensorBlockEntity, AltitudeSensorRenderer.AltitudeSensorRenderState> {

    public static class AltitudeSensorRenderState extends SmartRenderState {
        public final List<SuperByteBufferRenderState> parts = new ArrayList<>();
    }

    public AltitudeSensorRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public AltitudeSensorRenderState createRenderState() {
        return new AltitudeSensorRenderState();
    }

    public static float calculateLinearDial(final float minHeight, final float maxHeight, final float height) {
        final float fraction = (height - minHeight) / (maxHeight - minHeight);
        return Math.min(Math.max(fraction, 0), 1);
    }

    /**
     * The case, the dial and the redstone indicator, transformed and lit but not yet drawn.
     *
     * @param lightSource non-null only on the contraption path, where the light has to come from the
     *                    contraption's own world rather than from the block's position
     */
    public static List<SuperByteBuffer> buildBuffers(final BlockState blockState, final int tickCount, final float dialValue, final float visualHeight,
                                                     final @Nullable PoseStack contraptionPose, final @Nullable BlockAndTintGetter lightSource,
                                                     final @Nullable Matrix4f worldLight, final int light) {
        final SuperByteBuffer indicator = CachedBufferer.partial(SimPartialModels.ALTITUDE_SENSOR_INDICATOR, blockState);

        PartialModel box = SimPartialModels.ALTITUDE_SENSOR_LINEAR_CASE;
        PartialModel dial = SimPartialModels.ALTITUDE_SENSOR_LINEAR_HAND;
        final boolean isRadial = blockState.getValue(AltitudeSensorBlock.DIAL) == AltitudeSensorBlock.FaceType.RADIAL;

        if (isRadial) {
            box = SimPartialModels.ALTITUDE_SENSOR_RADIAL_CASE;
            dial = SimPartialModels.ALTITUDE_SENSOR_RADIAL_HAND;
        }

        final SuperByteBuffer face = CachedBufferer.partial(box, blockState);
        final SuperByteBuffer dialBuffer = CachedBufferer.partial(dial, blockState);

        final Direction direction = blockState.getValue(HORIZONTAL_FACING);

        if (contraptionPose != null) {
            face.transform(contraptionPose);
            dialBuffer.transform(contraptionPose);
            indicator.transform(contraptionPose);
        }

        if (isRadial) {
            dialBuffer.rotateCentered(-(float) (visualHeight * Math.PI / 2.0), direction);
        } else {
            dialBuffer.translate(0, (dialValue * 8f - 4f) / 16f, 0);
        }

        final AttachFace attachFace = blockState.getValue(AltitudeSensorBlock.FACE);
        final float attachFaceAngle = attachFace == AttachFace.WALL ? 90 : attachFace == AttachFace.CEILING ? 180 : 0;

        final float time = tickCount + AnimationTickHolder.getPartialTicks();
        final float wobbleAngle = (float) (-Math.sin(time * 0.8) * Math.exp(-time / 3.5)) * 0.7f;

        final float yRot = !direction.getAxis().equals(Direction.Axis.Z) ?
                (float) Math.toRadians(blockState.getValue(HORIZONTAL_FACING).getOpposite().toYRot()) :
                (float) Math.toRadians(blockState.getValue(HORIZONTAL_FACING).toYRot());

        face.rotateCentered((float) (yRot + Math.PI), Direction.UP).rotateCentered(wobbleAngle, Direction.WEST);
        dialBuffer.rotateCentered((float) (yRot + Math.PI), Direction.UP).rotateCentered(wobbleAngle, Direction.WEST);
        indicator.rotateCentered((float) (yRot + Math.PI), Direction.UP).rotateCentered((float) Math.toRadians(attachFaceAngle), Direction.WEST);

        if (worldLight != null && lightSource != null) {
            face.useLevelLight(lightSource, new Matrix4f(worldLight));
            dialBuffer.useLevelLight(lightSource, new Matrix4f(worldLight));
            indicator.useLevelLight(lightSource, new Matrix4f(worldLight));
        }
        face.light(light);
        dialBuffer.light(light);
        indicator.light(light);

        final int color = SimColors.redstone(dialValue);
        indicator.color(color);

        return List.of(face, dialBuffer, indicator);
    }

    @Override
    protected void extractSafe(final AltitudeSensorBlockEntity be, final AltitudeSensorRenderState renderState, final float partialTicks, final Vec3 cameraPosition) {
        super.extractSafe(be, renderState, partialTicks, cameraPosition);

        renderState.parts.clear();
        for (final SuperByteBuffer buffer : buildBuffers(be.getBlockState(), be.tickCount, be.getValue(), be.getVisualHeight(partialTicks),
                null, null, null, renderState.lightCoords)) {
            renderState.parts.add(buffer.extractRenderState());
        }
    }

    @Override
    protected void submitSafe(final AltitudeSensorRenderState renderState, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(renderState, ms, queue, camera);
        for (final SuperByteBufferRenderState part : renderState.parts)
            part.submit(ms, RenderTypes.cutoutMovingBlock(), queue);
    }
}

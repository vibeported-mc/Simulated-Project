package dev.ryanhcode.offroad.content.blocks.rock_cutting_wheel;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ActorGeometry;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.simibubi.create.foundation.render.RenderLevels;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.simulated_team.simulated.content.blocks.util.AbstractDirectionalAxisBlock;
import dev.ryanhcode.offroad.index.OffroadPartialModels;
import net.createmod.catnip.api.client.animation.AnimationTickHolder;
import net.createmod.catnip.api.animation.LerpedFloat;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;

/**
 * <h2>26.2 note</h2>
 * <p>Two independent moves. The block-entity path splits into extract and submit like every other
 * renderer. The contraption path -- {@code renderInContraption} -- became
 * {@code extractInContraption}, which collects into a list of {@link ActorGeometry} rather than
 * drawing into a buffer source; that is the shape Create's own actors use now.
 *
 * <p>The {@code ms.pushPose()}/{@code popPose()} around the block-entity draw wrapped no pose
 * operations at all -- every transform went onto the buffer -- so it is simply gone.
 */
public class RockCuttingWheelRenderer
        extends SafeBlockEntityRenderer<RockCuttingWheelBlockEntity, RockCuttingWheelRenderer.RockCuttingWheelRenderState> {

    public static class RockCuttingWheelRenderState extends SafeRenderState {
        public @Nullable SuperByteBufferRenderState wheel;
    }

    public RockCuttingWheelRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public RockCuttingWheelRenderState createRenderState() {
        return new RockCuttingWheelRenderState();
    }

    private static void transformBuffer(final Direction facing, final boolean alongFirstCoords, final SuperByteBuffer wheel) {
        if ((facing.getAxis() == Direction.Axis.Z || facing.getAxis() == Direction.Axis.Y) ^ alongFirstCoords) {
            wheel.rotateCentered(facing.getRotation())
                    .rotateZCenteredDegrees(90)
                    .rotateXCenteredDegrees(0)
                    .translate(0.625, 0.5, 0);
        } else {
            wheel.rotateCentered(facing.getRotation())
                    .rotateZCenteredDegrees(0)
                    .rotateXCenteredDegrees(90)
                    .translate(0, 0.5, -0.625);
        }
    }

    public static void extractInContraption(final MovementContext context, final VirtualRenderWorld renderWorld, final ContraptionMatrices matrices, final List<ActorGeometry> out) {
        final BlockState state = context.state;
        final Direction facing = state.getValue(FACING);
        final SuperByteBuffer wheel = CachedBufferer.partial(OffroadPartialModels.ROCK_CUTTING_WHEEL_WHEEL, state);

        wheel.transform(matrices.getModel());

        transformBuffer(facing, state.getValue(AbstractDirectionalAxisBlock.AXIS_ALONG_FIRST_COORDINATE), wheel);
        wheel.rotateYCenteredDegrees(((LerpedFloat) context.temporaryData).getValue(AnimationTickHolder.getPartialTicks(context.world)));

        wheel.light(LightCoordsUtil.getLightCoords(renderWorld, context.localPos))
                .useLevelLight(RenderLevels.lightSource(context.world, renderWorld), matrices.getWorld());
        out.add(ActorGeometry.of(matrices.getViewProjection(), wheel, RenderTypes.solidMovingBlock()));
    }

    @Override
    protected void extractSafe(final RockCuttingWheelBlockEntity blockEntity, final RockCuttingWheelRenderState renderState, final float partialTicks, final Vec3 cameraPosition) {
        final BlockState state = blockEntity.getBlockState();
        final SuperByteBuffer wheel = CachedBufferer.partial(OffroadPartialModels.ROCK_CUTTING_WHEEL_WHEEL, state);

        transformBuffer(state.getValue(FACING), state.getValue(AbstractDirectionalAxisBlock.AXIS_ALONG_FIRST_COORDINATE), wheel);
        if (blockEntity.isVirtual()) {
            wheel.rotateYCenteredDegrees(blockEntity.getAnimatedSpeed(partialTicks));
        }

        renderState.wheel = wheel.light(renderState.lightCoords).extractRenderState();
    }

    @Override
    protected void submitSafe(final RockCuttingWheelRenderState renderState, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        if (renderState.wheel != null)
            renderState.wheel.submit(ms, RenderTypes.solidMovingBlock(), queue);
    }
}

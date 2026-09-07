package dev.simulated_team.simulated.content.blocks.absorber;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.api.math.AngleHelper;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * <h2>26.2 note</h2>
 * <p>{@code renderSafe} split into an extract phase, which reads the block entity, and a submit
 * phase, which may run on another thread and may touch nothing but the render state. The sponge and
 * the four arm pieces are baked into {@link SuperByteBufferRenderState}s while the block entity is
 * still readable, and queued from those.
 *
 * <p>{@code RenderType.cutout()} was a chunk layer; block entities draw through
 * {@code RenderTypes.cutoutMovingBlock()}, which is what Create's own renderers use.
 */
public class AbsorberRenderer extends SmartBlockEntityRenderer<AbsorberBlockEntity, AbsorberRenderer.AbsorberRenderState> {

    public static class AbsorberRenderState extends SmartRenderState {
        public final List<SuperByteBufferRenderState> parts = new ArrayList<>();
    }

    public AbsorberRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public AbsorberRenderState createRenderState() {
        return new AbsorberRenderState();
    }

    @Override
    protected void extractSafe(final AbsorberBlockEntity be, final AbsorberRenderState state, final float partialTicks, final Vec3 cameraPosition) {
        super.extractSafe(be, state, partialTicks, cameraPosition);

        // Render states are reused between frames, so the list has to be emptied rather than
        // appended to, or last frame's geometry is drawn again alongside this frame's.
        state.parts.clear();

        final BlockState blockState = be.getBlockState();

        final float yRot = (float)Math.toRadians(AngleHelper.horizontalAngle(blockState.getValue(AbsorberBlock.HORIZONTAL_FACING))+180);

        float pos = be.animationTimer.getValue(partialTicks);
        final float target = be.animationTimer.getChaseTarget();

        if(target > 0.5) {

            final float fallTime = 0.3f;
            if (pos < fallTime)
                pos = 1f - pos * pos / (fallTime * fallTime);
            else {

                pos = (pos - fallTime) / (1f - fallTime);
                float bounce = (float) (Math.exp(-pos * 4.0) * Math.sin(pos * Math.PI * 3.0));
                final float smoothing = 0.05f;
                bounce = (float) Math.sqrt(bounce * bounce + smoothing * smoothing) - smoothing;
                pos = bounce / 2f;
            }
        }else
        {
            pos = 1-pos;
            final float startVelocity = 2f;
            pos = pos * Mth.lerp(pos,startVelocity,1);
        }

        final float movementDistance = 8;

        float totalMovement = (1+(1-pos)*movementDistance)/16f;

        final SuperByteBuffer sponge = CachedBufferer.partial(blockState.getValue(AbsorberBlock.WET) ? SimPartialModels.ABSORBER_SPONGE_WET :SimPartialModels.ABSORBER_SPONGE_DRY,blockState);

        sponge.translate(0,0.25,0);
        sponge.scale(1,1-pos*movementDistance/9,1);
        state.parts.add(sponge.light(state.lightCoords).extractRenderState());

        final Matrix4f rotationMatrix = new Matrix4f();
        this.apply(CachedBufferer.partial(SimPartialModels.ABSORBER_HAT,blockState),state,yRot,totalMovement,rotationMatrix);
        totalMovement/=2;
        this.apply(CachedBufferer.partial(SimPartialModels.ABSORBER_PIVOT,blockState),state,yRot,totalMovement,rotationMatrix);

        float height = totalMovement+0.5f/16; //height from base to pivot
        final float length = 13.8f/32f; //distance from pivot to endpoint of arm
        float width = (float)Math.sqrt(length*length-height*height);
        width /= length;
        height /= length;

        rotationMatrix.m22(width);
        rotationMatrix.m21(height);
        rotationMatrix.m11(width);
        rotationMatrix.m12(-height);

        this.apply(CachedBufferer.partial(SimPartialModels.ABSORBER_ARM,blockState),state,yRot,totalMovement,rotationMatrix);
        rotationMatrix.m21(-height);
        rotationMatrix.m12(height);
        rotationMatrix.m00(0.98f);
        this.apply(CachedBufferer.partial(SimPartialModels.ABSORBER_ARM,blockState),state,yRot,totalMovement,rotationMatrix);
    }

    @Override
    protected void submitSafe(final AbsorberRenderState state, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(state, ms, queue, camera);
        for (final SuperByteBufferRenderState part : state.parts)
            part.submit(ms, RenderTypes.cutoutMovingBlock(), queue);
    }

    void apply(final SuperByteBuffer buffer, final AbsorberRenderState state, final float yRot, final float offset, final Matrix4f rotationMatrix)
    {

        buffer.translate(0.5,0.25+offset,0.5);
        final Matrix4f r = new Matrix4f().rotate(yRot,0,1,0);
        buffer.mulPose(r.mul(rotationMatrix));
        buffer.translate(-0.5,0,-0.5);
        state.parts.add(buffer.light(state.lightCoords).extractRenderState());
    }
}

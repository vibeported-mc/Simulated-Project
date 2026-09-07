package dev.simulated_team.simulated.content.blocks.lasers.laser_pointer;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.simulated_team.simulated.content.blocks.lasers.AbstractLaserRenderer;
import dev.simulated_team.simulated.content.blocks.lasers.LaserBehaviour;
import dev.simulated_team.simulated.index.SimPartialModels;
import dev.simulated_team.simulated.index.SimRenderTypes;
import dev.simulated_team.simulated.util.SimColors;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.util.Util;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector4f;

import java.awt.*;

/**
 * <h2>26.2 note</h2>
 * <p>The lens is a model and the beam is raw geometry, so they take different routes: the lens is
 * baked into a render state during extraction, the beam is written by the base class during
 * submission. Both still hang off the same "is this laser black" test, which is measured once in
 * extraction and remembered.
 */
public class LaserPointerRenderer
        extends AbstractLaserRenderer<LaserPointerBlockEntity, LaserPointerRenderer.LaserPointerRenderState> {

    public static class LaserPointerRenderState extends LaserRenderState {
        public @Nullable SuperByteBufferRenderState lens;
    }

    public LaserPointerRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public LaserPointerRenderState createRenderState() {
        return new LaserPointerRenderState();
    }

    @Override
    protected void extractSafe(final LaserPointerBlockEntity blockEntity, final LaserPointerRenderState state, final float partialTicks, final Vec3 cameraPosition) {
        final Vector4f colors = this.getColors(blockEntity, partialTicks);
        final boolean isDarkerThanDark = colors.x == 0 && colors.y == 0 && colors.z == 0;

        // only draw non-black lasers -- the base class measures the beam, so it is asked first and
        // its result cleared when the pointer is black.
        if (!isDarkerThanDark) {
            super.extractSafe(blockEntity, state, partialTicks, cameraPosition);
        } else {
            state.color = null;
        }

        final SuperByteBuffer superBuffer;
        if (blockEntity.shouldCast() && !isDarkerThanDark) {
            superBuffer = CachedBufferer.partial(SimPartialModels.LASER_POINTER_LENS_ON, blockEntity.getBlockState());
        } else {
            superBuffer = CachedBufferer.partial(SimPartialModels.LASER_POINTER_LENS_OFF, blockEntity.getBlockState());
        }
        superBuffer.translate(0.5, 0.5, 0.5);
        // SuperByteBuffer.rotateToFace is gone; the turn it applied is the same one
        // CachedBufferer.partialFacing bakes in, but this buffer is already chosen above, so the
        // rotation is applied by hand through the direction's own quaternion.
        superBuffer.rotateCentered(blockEntity.getBlockState().getValue(LaserPointerBlock.FACING).getRotation());
        superBuffer.translate(-0.5, -0.5, -0.5);
        if (blockEntity.shouldCast()) {
            superBuffer.light(LightCoordsUtil.FULL_BRIGHT);
        } else {
            superBuffer.light(state.lightCoords);
        }
        superBuffer.disableDiffuse();
        superBuffer.color((int) (colors.x * 255), (int) (colors.z * 255), (int) (colors.y * 255), 255);
        state.lens = superBuffer.extractRenderState();
    }

    @Override
    protected void submitSafe(final LaserPointerRenderState state, final PoseStack pose, final SubmitNodeCollector queue, final CameraRenderState camera) {
        if (state.lens != null)
            state.lens.submit(pose, SimRenderTypes.lens(), queue);

        super.submitSafe(state, pose, queue, camera);
    }

    @Override
    public float getLaserScale(final LaserBehaviour laser) {
        return 0.48f;
    }

    @Override
    public Vector4f getColors(final LaserPointerBlockEntity blockEntity, final float partialTicks) {
        Color c = new Color(blockEntity.laserColor);
        if (blockEntity.isRainbow()) {
            final Vector3d baseLCh = SimColors.LabToLCh(SimColors.toOklab(c));
            final float t;
            if (blockEntity.isVirtual()) {
                t = (float) ((Util.getMillis() % 5000) * 2 * Math.PI / 5000);
            } else {
                final long timeOff = blockEntity.getLevel().getGameTime();
                t = (float) (((timeOff) % 100 + partialTicks) * 2 * Math.PI / 100f);
            }
            c = SimColors.LChOklab(0.8f, 0.3f, (float) (t + baseLCh.z()));
        }
        // 25% opacity at strongest
        return new Vector4f(c.getRed() / 255f, c.getBlue() / 255f, c.getGreen() / 255f, blockEntity.getPower() / 60f);
    }
}

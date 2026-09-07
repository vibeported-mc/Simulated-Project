package dev.simulated_team.simulated.content.blocks.lasers;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.util.SableDistUtil;
import dev.simulated_team.simulated.index.SimRenderTypes;
import net.createmod.catnip.api.data.Couple;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * <h2>26.2 note</h2>
 * <p>The laser is raw geometry, not a model, and everything that shapes it -- the behaviour's range,
 * its hit result, the block's facing -- is only readable during extraction. So extraction measures
 * the beam and submission draws it.
 *
 * <p>{@code SuperRenderTypeBuffer.getLateBuffer} is gone with {@code MultiBufferSource}. The laser
 * render type is already registered as a Veil fixed buffer at {@code AFTER_PARTICLES}
 * (see {@code SimulatedClient}), which is what put it late in the frame; the buffer is now handed
 * over by {@code submitCustomGeometry} when the queue drains.
 *
 * <p>The beam is four quads with a quarter turn between each. A custom-geometry callback receives one
 * flattened pose, so the four are submitted separately with the pose turned between them, rather than
 * turned inside a single callback.
 */
public abstract class AbstractLaserRenderer<T extends AbstractLaserBlockEntity, S extends AbstractLaserRenderer.LaserRenderState>
        extends SmartBlockEntityRenderer<T, S> {

    public static class LaserRenderState extends SmartRenderState {
        /** Null when the laser is not casting, or is fully transparent. */
        public @Nullable Vector4f color;
        public @Nullable Direction facing;
        public float scale;
        public float maxLength;
        public float length;
    }

    public AbstractLaserRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    @SuppressWarnings("unchecked")
    public S createRenderState() {
        return (S) new LaserRenderState();
    }

    @Override
    protected void extractSafe(final T blockEntity, final S state, final float partialTicks, final Vec3 cameraPosition) {
        super.extractSafe(blockEntity, state, partialTicks, cameraPosition);

        // Reused between frames, so a laser that stopped casting has to clear itself.
        state.color = null;

        final LaserBehaviour laser = blockEntity.getAllBehaviours().stream().filter(behaviour -> behaviour instanceof LaserBehaviour).map(behaviour -> (LaserBehaviour) behaviour).findFirst().orElse(null);

        if (laser == null || !laser.shouldCast()) {
            return;
        }

        final Vector4f colors = this.getColors(blockEntity, partialTicks);
        if (colors.w <= 0) { // alpha > 0
            return;
        }

        state.color = colors;
        state.facing = blockEntity.getDirection();
        state.scale = this.getLaserScale(laser);
        state.maxLength = laser.getRange();
        state.length = this.getLaserLength(laser);
    }

    @Override
    protected void submitSafe(final S state, final PoseStack pose, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(state, pose, queue, camera);

        if (state.color == null || state.facing == null) {
            return;
        }

        pose.pushPose();
        transformPose(state.facing, state.scale, pose);
        this.submitLaser(state.color, pose, queue, state.maxLength, state.length);
        pose.popPose();
    }

    public abstract Vector4f getColors(T blockEntity, float partialTicks);

    public float getLaserLength(final LaserBehaviour laser) {
        float laserRange = laser.getRange();

        final HitResult hr = this.getRenderedHitResult(laser);
        final Couple<Vec3> positions = laser.getLaserPositions().get();
        if (hr != null && !hr.getType().equals(HitResult.Type.MISS)) {
            Vec3 hitPos = hr.getLocation();
            if (laser.getVirtualHitPos() != Vec3.ZERO) {
                hitPos = laser.getVirtualHitPos();
            }

            laserRange = (float) Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(SableDistUtil.getClientLevel(), positions.getFirst(), hitPos)) - 0.1f;
        } else if (laser.getVirtualHitPos() != Vec3.ZERO) {
            final Vec3 hitPos = laser.getVirtualHitPos();

            laserRange = (float) Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(SableDistUtil.getClientLevel(), positions.getFirst(), hitPos)) - 0.1f;
        }

        return laserRange;
    }

    public abstract float getLaserScale(final LaserBehaviour laser);

    public HitResult getRenderedHitResult(final LaserBehaviour laser) {
        return laser.getClosestHitResult();
    }

    protected static void transformPose(final Direction facing, final float scale, final PoseStack pose) {
        pose.translate(0.5, 0.5, 0.5);

        TransformStack.of(pose)
                .rotate(facing.getRotation())
                .rotateXDegrees(-90)
                .translate(0, 0, 0.5 - 0.0625);

        pose.scale(scale, scale, 1);

        pose.translate(-0.5, -0.5, 0.0);
    }

    protected void submitLaser(final Vector4f color, final PoseStack pose, final SubmitNodeCollector queue, final float maxLength, final float length) {
        final float lengthFrac = length / maxLength;
        final float offset = lengthFrac / 10;
        final float endU = 1f + 1f / length; // frag shader uses v > 1 to taper off

        final float red = color.x();
        final float blue = color.y();
        final float green = color.z();
        final float alpha = color.w();
        final float endAlpha = alpha * (1 - lengthFrac);
        pose.pushPose();
        final Quaternionf rotationQuat = Axis.ZN.rotationDegrees(90);

        for (int i = 0; i < 4; i++) {
            queue.submitCustomGeometry(pose, SimRenderTypes.laser(), (transform, builder) -> {
                final Matrix4f matrix = transform.pose();

                builder.addVertex(matrix, 0, 0f, 0).setColor(red, green, blue, alpha).setUv(0, endU).setLight(LightCoordsUtil.FULL_BRIGHT).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0.0f, 1.0f, 0.0f);
                builder.addVertex(matrix, 1, 0f, 0).setColor(red, green, blue, alpha).setUv(0, endU).setLight(LightCoordsUtil.FULL_BRIGHT).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0.0f, 1.0f, 0.0f);
                // offset makes the end of the laser spread out, helping reduce z-fighting
                builder.addVertex(matrix, 1 + offset, -offset, length + 0.5f).setColor(red, green, blue, endAlpha).setUv(endU, endU).setLight(LightCoordsUtil.FULL_BRIGHT).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0.0f, 1.0f, 0.0f);
                builder.addVertex(matrix, -offset, -offset, length + 0.5f).setColor(red, green, blue, endAlpha).setUv(endU, endU).setLight(LightCoordsUtil.FULL_BRIGHT).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0.0f, 1.0f, 0.0f);
            });

            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(rotationQuat);
            pose.translate(-0.5, -0.5, -0.5);
        }
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}

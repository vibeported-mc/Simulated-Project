package dev.simulated_team.simulated.content.entities.launched_plunger;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.simulated_team.simulated.content.items.plunger_launcher.PlungerLauncherItemRenderer;
import dev.simulated_team.simulated.index.SimPartialModels;
import dev.simulated_team.simulated.index.SimRenderTypes;
import dev.simulated_team.simulated.util.CatmulRomSpline;
import dev.simulated_team.simulated.util.render.ProjectionUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import org.jspecify.annotations.Nullable;

import java.lang.Math;
import java.util.List;

/**
 * <h2>26.2 note</h2>
 * <p>Split across {@code extractRenderState} / {@code submit}. Everything this renderer used to do
 * per frame is a level read -- the other plunger's position, the sub-level poses both ends sit in,
 * the owner player's head rotation, the light at every point along the rope -- so all of it happens
 * during extraction, and submission works only from the snapshot.
 *
 * <p>The rope was drawn straight into a buffer in camera space, ignoring the pose stack. That is
 * kept: the spline is baked camera-relative during extraction, along with one packed light value per
 * point, and the quads go out through {@code submitCustomGeometry}, whose {@code Pose} argument is
 * deliberately unused for the same reason the old code ignored the {@code PoseStack}.
 *
 * <p>The scratch quaternions and vectors the rope loop used were {@code static}. One renderer
 * instance serves every plunger in the world and submission may run off the render thread, so they
 * are locals now.
 *
 * <p>{@code GameRenderer.getFov} and {@code getProjectionMatrix} are gone; see
 * {@link ProjectionUtil} for where the two projections come from now.
 */
public class LaunchedPlungerEntityRenderer
        extends EntityRenderer<LaunchedPlungerEntity, LaunchedPlungerEntityRenderer.PlungerRenderState> {

    private static final Quaternionf POSITIVE_Y = new Quaternionf().setAngleAxis(Math.PI / 2, 1, 0, 0);
    private static final Quaternionf NEGATIVE_Y = new Quaternionf().setAngleAxis(-Math.PI / 2, 1, 0, 0);

    public static class PlungerRenderState extends EntityRenderState {
        public @Nullable SuperByteBufferRenderState body;
        public @Nullable SuperByteBufferRenderState joint;
        public @Nullable SuperByteBufferRenderState spool;

        public boolean plunged;
        public Direction plungedDirection = Direction.UP;
        public float yRot;
        public float xRot;
        public float jointAngle;
        public float spoolSpin;

        /** Spline points in camera space, or null when the rope is not drawn this frame. */
        public @Nullable List<Vec3> ropeSpline;
        public int @Nullable [] ropeLights;
        public Vec3 cameraPosition = Vec3.ZERO;
    }

    public LaunchedPlungerEntityRenderer(final EntityRendererProvider.Context context) {
        super(context);
    }

    public static Vec3 getFirstPersonFocusPos(final float pt) {
        final GameRenderer gameRenderer = Minecraft.getInstance().gameRenderer;
        final Camera camera = gameRenderer.mainCamera();

        final Vector3d focusPoint = new Vector3d(PlungerLauncherItemRenderer.focusPos);
        final Quaternionf orientation = camera.rotation();
        orientation.transformInverse(focusPoint);
        final Vector4f v4 = new Vector4f((float) focusPoint.x, (float) focusPoint.y, (float) focusPoint.z, 1.0f);

        final Matrix4f actualProjMat = ProjectionUtil.levelProjection(camera);
        actualProjMat.invert(new Matrix4f()).transform(v4);
        PlungerLauncherItemRenderer.itemProjMat.transform(v4);
        final Vec3 cameraPosition = camera.position();
        focusPoint.set(v4.x, v4.y, v4.z);
        orientation.transform(focusPoint);

        final double fov = camera.getFov();
        focusPoint.mul(100 / fov);
        focusPoint.add(cameraPosition.x, cameraPosition.y, cameraPosition.z);

        return JOMLConversion.toMojang(focusPoint);
    }

    @Override
    public PlungerRenderState createRenderState() {
        return new PlungerRenderState();
    }

    @Override
    public void extractRenderState(final LaunchedPlungerEntity entity, final PlungerRenderState state, final float pt) {
        super.extractRenderState(entity, state, pt);

        final LaunchedPlungerEntity other = entity.getOther();

        Vec3 selfNormal;
        Vec3 perpendicularNormal;
        final Direction dir = entity.getData(LaunchedPlungerEntity.PLUNGED_DIRECTION);
        if (entity.isPlunged()) {
            selfNormal = Vec3.atLowerCornerOf(dir.getUnitVec3i());
            if (dir.getAxis().isHorizontal()) {
                perpendicularNormal = Vec3.atLowerCornerOf(Direction.UP.getUnitVec3i());
            } else {
                perpendicularNormal = Vec3.atLowerCornerOf(Direction.NORTH.getUnitVec3i());
            }
        } else {
            selfNormal = entity.calculateViewVector(-Mth.lerp(pt, entity.xRotO, entity.getXRot()), -Mth.lerp(pt, entity.yRotO, entity.getYRot())).reverse();
            perpendicularNormal = entity.calculateViewVector(-Mth.lerp(pt, entity.xRotO, entity.getXRot()), -Mth.lerp(pt, entity.yRotO, entity.getYRot()) - 90).reverse();
        }

        Vec3 oldPos = new Vec3(entity.xo, entity.yo, entity.zo);
        Vec3 newPos = entity.position();
        final float scalingFactor = 0.6f;
        final SubLevel subLevel = Sable.HELPER.getContainingClient(newPos);
        if (subLevel != null) {
            final Pose3dc clientPos = ((ClientSubLevel) subLevel).renderPose(pt);
            newPos = clientPos.transformPosition(newPos);
            selfNormal = clientPos.transformNormal(selfNormal);
            perpendicularNormal = clientPos.transformNormal(perpendicularNormal);
        }

        final SubLevel oldSubLevel = Sable.HELPER.getContainingClient(oldPos);
        if (oldSubLevel != null) {
            oldPos = ((ClientSubLevel) oldSubLevel).renderPose(pt).transformPosition(oldPos);
        }
        final Vec3 renderPos = oldPos.lerp(newPos, pt);
        final Vec3 pos = renderPos.add(selfNormal.scale(scalingFactor));

        final Vec3 target = extractTarget(entity, other, pt, scalingFactor);

        state.plunged = entity.isPlunged();
        state.plungedDirection = dir;
        state.yRot = Mth.lerp(pt, entity.yRotO, entity.getYRot());
        state.xRot = Mth.lerp(pt, entity.xRotO, entity.getXRot());
        state.jointAngle = jointAngle(entity, pos, target, selfNormal, perpendicularNormal);
        state.spoolSpin = (float) pos.distanceTo(target) * 90f * 2.6f;

        state.body = CachedBufferer.partial(SimPartialModels.LAUNCHED_PLUNGER_BODY, Blocks.AIR.defaultBlockState())
                .light(state.lightCoords)
                .extractRenderState();
        state.joint = CachedBufferer.partial(SimPartialModels.LAUNCHED_PLUNGER_JOINT, Blocks.AIR.defaultBlockState())
                .light(state.lightCoords)
                .extractRenderState();
        state.spool = CachedBufferer.partial(SimPartialModels.LAUNCHED_PLUNGER_SPOOL, Blocks.AIR.defaultBlockState())
                .light(state.lightCoords)
                .extractRenderState();

        state.ropeSpline = null;
        state.ropeLights = null;
        if ((entity.getData(LaunchedPlungerEntity.IS_FIRST) || other == null || other.isRemoved()) && !target.equals(Vec3.ZERO)) {
            extractRope(entity, state, pt, pos, renderPos, target);
        }
    }

    private Vec3 extractTarget(final LaunchedPlungerEntity entity, final @Nullable LaunchedPlungerEntity other, final float pt, final float scalingFactor) {
        if (other != null) {
            Vec3 otherNormal;
            if (other.isPlunged()) {
                final Direction otherDir = other.getData(LaunchedPlungerEntity.PLUNGED_DIRECTION);
                otherNormal = Vec3.atLowerCornerOf(otherDir.getUnitVec3i());
            } else {
                otherNormal = other.calculateViewVector(-Mth.lerp(pt, other.xRotO, other.getXRot()), -Mth.lerp(pt, other.yRotO, other.getYRot())).reverse();
            }

            Vec3 targetOldPos = new Vec3(other.xo, other.yo, other.zo);
            Vec3 targetNewPos = other.position();
            if (other.isRemoved()) {
                targetOldPos = entity.getEntityData().get(LaunchedPlungerEntity.TARGET_POS);
                targetNewPos = targetOldPos;
            }
            final SubLevel targetSublevel = Sable.HELPER.getContainingClient(targetNewPos);
            if (targetSublevel != null) {
                final Pose3dc pose = ((ClientSubLevel) targetSublevel).renderPose(pt);
                targetNewPos = pose.transformPosition(targetNewPos);
                otherNormal = pose.transformNormal(otherNormal);
            }
            final SubLevel targetOldSublevel = Sable.HELPER.getContainingClient(other.getPosition(pt));
            if (targetOldSublevel != null) {
                targetOldPos = ((ClientSubLevel) targetOldSublevel).renderPose(pt).transformPosition(targetOldPos);
            }
            return targetOldPos.lerp(targetNewPos, pt).add(otherNormal.scale(scalingFactor));
        }

        final Entity owner = entity.getOwner();
        final boolean unpaired = entity.getData(LaunchedPlungerEntity.OTHER_PLUNGER).isEmpty();
        if (unpaired && owner == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return getFirstPersonFocusPos(pt);
        }
        if (owner instanceof final AbstractClientPlayer player && unpaired) {
            float headYDirection = Mth.lerp(pt, player.yHeadRotO, player.yHeadRot);
            final float headXDirection = Mth.lerp(pt, player.xRotO, player.getXRot());
            final float lookDelta = Math.abs(Mth.map(headXDirection, 90, 0, 1f, 0f));
            headYDirection = Mth.lerp(lookDelta, headYDirection, player.getPreciseBodyRotation(pt));
            final Vec3 viewDirection = player.calculateViewVector(headXDirection, headYDirection);
            final Vec3 handDirection = player.calculateViewVector(0, headYDirection + 90.0f);
            return player.getPosition(pt)
                    .add(0.0, 1.28, 0.0)
                    .add(viewDirection.scale(0.875))
                    .add(handDirection.scale(Math.abs(Mth.map(headXDirection, 90, 0, 0.325f, 0f))));
        }
        return Vec3.ZERO;
    }

    private static float jointAngle(final LaunchedPlungerEntity entity, final Vec3 pos, final Vec3 target, final Vec3 selfNormal, final Vec3 perpendicularNormal) {
        if (!entity.getData(LaunchedPlungerEntity.IS_PLUNGED)) {
            return 0;
        }
        final Vector3f faceNormal = new Vector3f((float) selfNormal.x, (float) selfNormal.y, (float) selfNormal.z);
        final Vector3f self = new Vector3f((float) pos.x, (float) pos.y, (float) pos.z);
        final Vector3f toTarget = new Vector3f((float) target.x, (float) target.y, (float) target.z);
        toTarget.add(self.mul(-1)).normalize();
        final Vector3f perpendicular = new Vector3f((float) perpendicularNormal.x, (float) perpendicularNormal.y, (float) perpendicularNormal.z);

        final float angle = (float) (perpendicular.angleSigned(toTarget, faceNormal) + Math.PI / 2f);
        return Float.isNaN(angle) ? 0 : angle;
    }

    private void extractRope(final LaunchedPlungerEntity entity, final PlungerRenderState state, final float pt,
                             final Vec3 pos, final Vec3 renderPos, final Vec3 target) {
        final float renderTime = entity.tickCount + pt + entity.getAnimationOffset();
        final List<Vec3> points = new ObjectArrayList<>();
        final Vec3 start = pos;
        final Vec3 toTarget = target.subtract(start);
        final Vec3 normalizedScalar = toTarget.normalize();
        final float length = (float) renderPos.distanceTo(target);
        points.add(start);

        final Vector3f firstRotation = new Vector3f();
        final Vector3f secondRotation = new Vector3f();
        final Vector3f finalRotation = new Vector3f();

        if (length < 1000.0) {
            for (float j = 0.01f; j < length; j += 0.5f) {
                finalRotation.set(0, 0, 0);
                final float delta = j / length;
                final Vec3 point = start.add(toTarget.scale(delta));
                firstRotation.set(Math.cos((renderTime / 10) + j) * (1 - Math.abs(normalizedScalar.x)), Math.cos((renderTime / 10) + j) * (1 - Math.abs(normalizedScalar.y)), Math.cos((renderTime / 10) + j / 2) * (1 - Math.abs(normalizedScalar.z)));
                secondRotation.set(Math.sin((renderTime / 10) + j / 4) * (1 - Math.abs(normalizedScalar.x)) * 2, Math.sin((renderTime / 10) + j / 4) * (1 - Math.abs(normalizedScalar.y)), Math.sin((renderTime / 10) + j / 4) * (1 - Math.abs(normalizedScalar.z)));
                finalRotation.add(firstRotation);
                finalRotation.add(secondRotation);
                finalRotation.mul(Math.max(0, 1 - (entity.tickCount + pt) / 40f - ((entity.getPlungedTime() + (entity.getPlungedTime() > 0 ? pt : 0)) / 8f))); // Scales down the animation over time
                finalRotation.mul((float) (1.0 - Math.pow(2 * delta - 1, 2.0)));
                points.add(point.subtract(finalRotation.x, finalRotation.y, finalRotation.z));
            }
        }

        points.add(start.add(toTarget));
        points.add(start.add(toTarget));

        final Vec3 cameraPosition = this.entityRenderDispatcher.camera.position();
        final List<Vec3> cablePoints = new ObjectArrayList<>(points.size());
        for (final Vec3 position : points) {
            cablePoints.add(position.subtract(cameraPosition));
        }

        final List<Vec3> splinePoints = CatmulRomSpline.generateSpline(cablePoints, 4);
        final BlockAndTintGetter level = Minecraft.getInstance().level;
        final int[] lights = new int[splinePoints.size()];
        if (level != null) {
            final BlockPos.MutableBlockPos lightPos = new BlockPos.MutableBlockPos();
            for (int i = 0; i < splinePoints.size(); i++) {
                final Vec3 p = splinePoints.get(i);
                lights[i] = LightCoordsUtil.getLightCoords(level,
                        lightPos.set(p.x + cameraPosition.x, p.y + cameraPosition.y, p.z + cameraPosition.z));
            }
        }

        state.ropeSpline = splinePoints;
        state.ropeLights = lights;
        state.cameraPosition = cameraPosition;
    }

    @Override
    public void submit(final PlungerRenderState state, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submit(state, ms, queue, camera);

        final List<Vec3> spline = state.ropeSpline;
        final int[] lights = state.ropeLights;
        if (spline != null && lights != null) {
            queue.submitCustomGeometry(ms, SimRenderTypes.rope(), (pose, builder) -> renderRope(spline, lights, builder));
        }

        if (state.body == null || state.joint == null || state.spool == null) {
            return;
        }

        final RenderType renderType = RenderTypes.solidMovingBlock();
        final PoseTransformStack stack = TransformStack.of(ms);

        ms.pushPose();
        if (state.plunged) {
            stack.rotate(state.plungedDirection.getRotation());
        } else {
            ms.mulPose(Axis.YP.rotationDegrees(state.yRot - 90.0F));
            ms.mulPose(Axis.ZP.rotationDegrees(state.xRot));
            stack.rotateZDegrees(90f);
        }
        stack.rotateXDegrees(-90);

        stack.scale(1.75f, 1.75f, 1.75f);
        stack.translate(0, 0, 2.5f / 16f);
        stack.rotateZDegrees(90f);
        state.body.submit(ms, renderType, queue);

        ms.pushPose();
        stack.rotateZDegrees((float) Math.toDegrees(state.jointAngle));
        state.joint.submit(ms, renderType, queue);
        stack.translate(0, 0, 3f / 16f);
        stack.rotateXDegrees(state.spoolSpin);
        state.spool.submit(ms, renderType, queue);
        ms.popPose();

        ms.popPose();
    }

    /**
     * Writes the rope's quads in camera space. The pose stack is deliberately not consulted -- the
     * spline was baked camera-relative during extraction, exactly as the 1.21.1 code wrote it.
     */
    private static void renderRope(final List<Vec3> splinePoints, final int[] lights, final VertexConsumer builder) {
        final Quaternionf orientation = new Quaternionf();
        final Quaternionf nextOrientation = new Quaternionf();
        final Vector3f pos = new Vector3f();
        final Vector3f normal = new Vector3f();
        final Vector3f nextNormal = new Vector3f();

        final int color = 0xFFFFFFFF;
        final float constantRadius = (2f / 16.0f) / 2.0f;
        final float u = 2f / 16F;
        float v = 0;
        float nextV;

        for (int i = 0; i < splinePoints.size() - 1; i++) {
            final float delta = (float) i / (splinePoints.size() - 1);
            final float nextDelta = (float) (i + 1) / (splinePoints.size() - 1);
            final float cableRadius = constantRadius - (0.001f * delta);
            final float nextCableRadius = constantRadius - (0.001f * nextDelta);
            final Vec3 point = splinePoints.get(i);
            final Vec3 nextPoint = splinePoints.get(i + 1);

            final double x = point.x;
            final double y = point.y;
            final double z = point.z;
            final double nextX = nextPoint.x;
            final double nextY = nextPoint.y;
            final double nextZ = nextPoint.z;

            if (i < splinePoints.size() - 2) {
                calculateOrientation(nextOrientation, nextX, nextY, nextZ, splinePoints.get(i + 2));
            } else {
                nextOrientation.set(orientation);
            }

            final int lightStart = lights[i];
            final int lightEnd = lights[i + 1];

            final double length = Math.sqrt((nextX - x) * (nextX - x) + (nextY - y) * (nextY - y) + (nextZ - z) * (nextZ - z));
            nextV = v + (float) (length * (17 / 16f));

            // Down
            orientation.transform(normal.set(0, -1, 0));
            nextOrientation.transform(nextNormal.set(0, -1, 0));

            nextOrientation.transform(pos.set(-nextCableRadius, -nextCableRadius, 0));
            builder.addVertex((float) (nextX + pos.x), (float) (nextY + pos.y), (float) (nextZ + pos.z)).setColor(color).setUv(0, nextV).setLight(lightEnd).setNormal(nextNormal.x, nextNormal.y, nextNormal.z);

            orientation.transform(pos.set(-cableRadius, -cableRadius, 0));
            builder.addVertex((float) (x + pos.x), (float) (y + pos.y), (float) (z + pos.z)).setColor(color).setUv(0, v).setLight(lightStart).setNormal(normal.x, normal.y, normal.z);

            orientation.transform(pos.set(cableRadius, -cableRadius, 0));
            builder.addVertex((float) (x + pos.x), (float) (y + pos.y), (float) (z + pos.z)).setColor(color).setUv(u, v).setLight(lightStart).setNormal(normal.x, normal.y, normal.z);

            nextOrientation.transform(pos.set(nextCableRadius, -nextCableRadius, 0));
            builder.addVertex((float) (nextX + pos.x), (float) (nextY + pos.y), (float) (nextZ + pos.z)).setColor(color).setUv(u, nextV).setLight(lightEnd).setNormal(nextNormal.x, nextNormal.y, nextNormal.z);

            // Up
            orientation.transform(normal.set(0, 1, 0));
            nextOrientation.transform(nextNormal.set(0, 1, 0));

            orientation.transform(pos.set(-cableRadius, cableRadius, 0));
            builder.addVertex((float) (x + pos.x), (float) (y + pos.y), (float) (z + pos.z)).setColor(color).setUv(0, v).setLight(lightStart).setNormal(normal.x, normal.y, normal.z);

            nextOrientation.transform(pos.set(-nextCableRadius, nextCableRadius, 0));
            builder.addVertex((float) (nextX + pos.x), (float) (nextY + pos.y), (float) (nextZ + pos.z)).setColor(color).setUv(0, nextV).setLight(lightEnd).setNormal(nextNormal.x, nextNormal.y, nextNormal.z);

            nextOrientation.transform(pos.set(nextCableRadius, nextCableRadius, 0));
            builder.addVertex((float) (nextX + pos.x), (float) (nextY + pos.y), (float) (nextZ + pos.z)).setColor(color).setUv(u, nextV).setLight(lightEnd).setNormal(nextNormal.x, nextNormal.y, nextNormal.z);

            orientation.transform(pos.set(cableRadius, cableRadius, 0));
            builder.addVertex((float) (x + pos.x), (float) (y + pos.y), (float) (z + pos.z)).setColor(color).setUv(u, v).setLight(lightStart).setNormal(normal.x, normal.y, normal.z);

            // West
            orientation.transform(normal.set(-1, 0, 0));
            nextOrientation.transform(nextNormal.set(-1, 0, 0));

            nextOrientation.transform(pos.set(-nextCableRadius, -nextCableRadius, 0));
            builder.addVertex((float) (nextX + pos.x), (float) (nextY + pos.y), (float) (nextZ + pos.z)).setColor(color).setUv(u, nextV).setLight(lightEnd).setNormal(nextNormal.x, nextNormal.y, nextNormal.z);

            nextOrientation.transform(pos.set(-nextCableRadius, nextCableRadius, 0));
            builder.addVertex((float) (nextX + pos.x), (float) (nextY + pos.y), (float) (nextZ + pos.z)).setColor(color).setUv(0, nextV).setLight(lightEnd).setNormal(nextNormal.x, nextNormal.y, nextNormal.z);

            orientation.transform(pos.set(-cableRadius, cableRadius, 0));
            builder.addVertex((float) (x + pos.x), (float) (y + pos.y), (float) (z + pos.z)).setColor(color).setUv(0, v).setLight(lightStart).setNormal(normal.x, normal.y, normal.z);

            orientation.transform(pos.set(-cableRadius, -cableRadius, 0));
            builder.addVertex((float) (x + pos.x), (float) (y + pos.y), (float) (z + pos.z)).setColor(color).setUv(u, v).setLight(lightStart).setNormal(normal.x, normal.y, normal.z);

            // East
            orientation.transform(normal.set(1, 0, 0));
            nextOrientation.transform(nextNormal.set(1, 0, 0));

            orientation.transform(pos.set(cableRadius, -cableRadius, 0));
            builder.addVertex((float) (x + pos.x), (float) (y + pos.y), (float) (z + pos.z)).setColor(color).setUv(u, v).setLight(lightStart).setNormal(normal.x, normal.y, normal.z);

            orientation.transform(pos.set(cableRadius, cableRadius, 0));
            builder.addVertex((float) (x + pos.x), (float) (y + pos.y), (float) (z + pos.z)).setColor(color).setUv(0, v).setLight(lightStart).setNormal(normal.x, normal.y, normal.z);

            nextOrientation.transform(pos.set(nextCableRadius, nextCableRadius, 0));
            builder.addVertex((float) (nextX + pos.x), (float) (nextY + pos.y), (float) (nextZ + pos.z)).setColor(color).setUv(0, nextV).setLight(lightEnd).setNormal(nextNormal.x, nextNormal.y, nextNormal.z);

            nextOrientation.transform(pos.set(nextCableRadius, -nextCableRadius, 0));
            builder.addVertex((float) (nextX + pos.x), (float) (nextY + pos.y), (float) (nextZ + pos.z)).setColor(color).setUv(u, nextV).setLight(lightEnd).setNormal(nextNormal.x, nextNormal.y, nextNormal.z);

            orientation.set(nextOrientation);
            v = nextV;
        }
    }

    private static void calculateOrientation(final Quaternionf store, final double x, final double y, final double z, final Vec3 nextPoint) {
        final double dx = nextPoint.x - x;
        final double dy = nextPoint.y - y;
        final double dz = nextPoint.z - z;
        final float factor = 0;//(float) Mth.smoothstep(1.0-Mth.clamp(8*Math.sqrt(dx * dx + dz * dz), 0.0, 1.0));
        store.identity().rotateAxis((float) Math.atan2(dx, dz), 0, 1, 0).rotateAxis((float) (Math.acos(dy / Math.sqrt(dx * dx + dy * dy + dz * dz)) - Math.PI / 2.0), 1, 0, 0).slerp(dy < 0 ? POSITIVE_Y : NEGATIVE_Y, factor);
    }

    @Override
    public boolean shouldRender(final LaunchedPlungerEntity entity, final Frustum frustum, final double d, final double e, final double f) {
        return true;
    }
}

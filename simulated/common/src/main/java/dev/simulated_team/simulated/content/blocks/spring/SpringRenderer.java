package dev.simulated_team.simulated.content.blocks.spring;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.index.SimRenderTypes;
import dev.simulated_team.simulated.util.SimColors;
import dev.simulated_team.simulated.util.SimMathUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.joml.*;

import java.lang.Math;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <h2>26.2 note</h2>
 * <p>A spring is raw geometry along a spline between two block entities, and everything shaping it --
 * the partner spring, both sub-level poses, the stress colour -- is only readable during extraction.
 * So extraction walks the spline into a list of segments and submission writes their vertices, inside
 * a {@code submitCustomGeometry} callback.
 *
 * <p>The scratch vectors this class kept as fields had to become locals. They were safe while
 * rendering was one call on one thread; submission may run on another, and one renderer instance
 * serves every spring in the world, so shared scratch is a race rather than an optimisation.
 */
public class SpringRenderer
        extends SmartBlockEntityRenderer<SpringBlockEntity, SpringRenderer.SpringRenderState> {

    /** One straight length of spring, with the frame at each end. */
    public record Segment(Vector3dc startDirection, Vector3dc endDirection,
                          Vector3dc startUp, Vector3dc endUp,
                          Vector3dc startPos, Vector3dc endPos,
                          boolean second, float uvStart, float uvEnd,
                          float width, float textureWidth) { }

    public static class SpringRenderState extends SmartRenderState {
        public final List<Segment> segments = new ArrayList<>();
        public @Nullable Identifier texture;
        public @Nullable Vector3dc origin;
        public int color;
    }

    public SpringRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public SpringRenderState createRenderState() {
        return new SpringRenderState();
    }

    private static int getStressColor(final SpringBlockEntity be, final float partialTicks, final Vector3d otherCenter, final Vector3dc center, final Minecraft minecraft) {
        final double distance = otherCenter.distance(center);
        final double snapDistance = be.getSnappingDistance();

        // start flashing 70% to the snap distance
        final double flashingStartExtension = Mth.lerp(0.7, (be.getRenderLength(partialTicks) - 0.75), snapDistance);

        float stressAlpha = 0.0f;
        if (distance > flashingStartExtension) {
            final double renderTime = minecraft.player.tickCount + partialTicks;
            stressAlpha = Mth.clamp((float) ((distance - flashingStartExtension) / (snapDistance - flashingStartExtension)), 0.0f, 1.0f) * 0.3f;
            stressAlpha = stressAlpha * Mth.lerp(0.25f, (float) Math.sin(renderTime / 3.0f) * 0.5f + 0.5f, 1.0f);
        }
        final int color = SimColors.STRESSED_RED & 0xFFFFFF | ((int) (stressAlpha * 255) << 24);
        return color;
    }

    @Override
    protected void extractSafe(final SpringBlockEntity be, final SpringRenderState renderState, final float partialTicks, final Vec3 cameraPosition) {
        super.extractSafe(be, renderState, partialTicks, cameraPosition);

        // Reused between frames, so a spring that broke has to clear what it drew.
        renderState.segments.clear();
        renderState.texture = null;
        renderState.origin = null;

        if (!be.isController()) {
            return;
        }

        final SpringBlockEntity other = be.getPairedSpring();
        if (other == null) {
            return;
        }

        final BlockState state = be.getBlockState();
        final SpringBlock.Size size = state.getValue(SpringBlock.SIZE);
        final String name = (size == SpringBlock.Size.MEDIUM ? "" : (size.getSerializedName() + "_")) + "spring";
        renderState.texture = Simulated.path("textures/block/spring/" + name + ".png");

        final Minecraft minecraft = Minecraft.getInstance();
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        if (container == null) {
            return;
        }

        final UUID otherSubLevelID = be.getPartnerSubLevelID();
        final ClientSubLevel otherSubLevel = otherSubLevelID != null ? (ClientSubLevel) container.getSubLevel(otherSubLevelID) : null;
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(be);

        final BlockPos blockPos = be.getBlockPos();

        final Vector3dc center = be.getCenter();
        final Vector3d otherCenter = other.getCenter();

        final Direction facing = state.getValue(SpringBlock.FACING);
        final Direction otherFacing = other.getBlockState().getValue(SpringBlock.FACING);
        final Vector3dc normalA = JOMLConversion.atLowerCornerOf(facing.getUnitVec3i());
        final Vector3d normalB = JOMLConversion.atLowerCornerOf(otherFacing.getUnitVec3i());

        renderState.origin = new Vector3d(center.x() - blockPos.getX(), center.y() - blockPos.getY(), center.z() - blockPos.getZ());

        final double PI2 = Math.PI / 2.0;
        final double PI4 = PI2 / 2.0;
        final Pose3dc renderPose = subLevel != null ? subLevel.renderPose() : null;
        final Pose3dc otherRenderPose = otherSubLevel != null ? otherSubLevel.renderPose() : null;

        if (otherRenderPose != null) {
            otherRenderPose.transformNormal(normalB);
            otherRenderPose.transformPosition(otherCenter);
        }

        if (renderPose != null) {
            renderPose.transformNormalInverse(normalB);
            renderPose.transformPositionInverse(otherCenter);
        }

        renderState.color = getStressColor(be, partialTicks, otherCenter, center, minecraft);

        final List<SplinePoint> splinePoints = this.generateSpline(
                JOMLConversion.ZERO,
                otherCenter.sub(center, new Vector3d()),
                normalA,
                normalB,
                center.distance(otherCenter) / 5.0 + 0.25
        );

        final int totalPoints = splinePoints.size();

        final Vector3d pointNormal = new Vector3d();

        final Vector3d startUpDir = JOMLConversion.toJOML(this.getUpDirection(be, otherCenter.sub(center, new Vector3d())));

        pointNormal.set(splinePoints.getFirst().normal);

        final Matrix3d matrix = new Matrix3d(
                startUpDir,
                pointNormal,
                startUpDir.cross(pointNormal, new Vector3d())
        );

        // March the frame all the way through the points to get the final orientation with no twist
        double totalSpringLength = 0.0;
        for (int i = 0; i < totalPoints - 1; i++) {
            final SplinePoint point = splinePoints.get(i);
            final SplinePoint nextPoint = splinePoints.get(i + 1);

            totalSpringLength += point.point.distance(nextPoint.point);

            matrix.rotateLocal(
                    SimMathUtils.getQuaternionfFromVectorRotation(point.normal, nextPoint.normal)
            );
        }

        // we derive the twist we need to land at an increment of 90deg final twist
        final Quaterniond orientation = new Quaterniond();

        final Quaterniondc orientation1 = renderPose != null ? renderPose.orientation() : JOMLConversion.QUAT_IDENTITY;
        final Quaterniondc orientation2 = otherRenderPose != null ? otherRenderPose.orientation() : JOMLConversion.QUAT_IDENTITY;

        final Quaterniond blockOrientation1 = new Quaterniond(facing.getRotation());
        final Quaterniond blockOrientation2 = new Quaterniond(otherFacing.getRotation());
        blockOrientation2.premul(orientation2).premul(orientation1.conjugate(new Quaterniond()));

        final Quaterniond relativeBlockOrientation = new Quaterniond(blockOrientation1).div(blockOrientation2);

        orientation.mul(new Quaterniond(relativeBlockOrientation));
        orientation.mul(matrix.getNormalizedRotation(new Quaterniond()));

        // not sure how this happens. but we chillin
        if (Math.abs(OrientedBoundingBox3d.UP.dot(new Vector3d(orientation.x(), orientation.y(), orientation.z()))) < 1e-5) {
            orientation.rotateLocalX(Math.PI);
        }

        final double d = OrientedBoundingBox3d.UP.dot(new Vector3d(orientation.x(), orientation.y(), orientation.z()));
        final double deg = 2.0 * Math.atan2(-d, orientation.w());
        final double twist = Math.floor((deg + PI4) / PI2) * PI2 - deg;

        final float uvScale = (float) ((be.getRenderLength(partialTicks) - 0.75) / totalSpringLength);
        double runningSpringLength = 0.0;

        matrix.set(
                startUpDir,
                pointNormal,
                startUpDir.cross(pointNormal, new Vector3d())
        );

        for (int i = 0; i < totalPoints - 1; i++) {
            final SplinePoint point = splinePoints.get(i);
            final SplinePoint nextPoint = splinePoints.get(i + 1);

            final Vector3dc upDir = matrix.getColumn(0, new Vector3d());

            matrix.rotateLocal(
                    SimMathUtils.getQuaternionfFromVectorRotation(point.normal, nextPoint.normal)
            );
            matrix.rotateY(-twist / (totalPoints - 1));

            final Vector3dc nextUpDir = matrix.getColumn(0, new Vector3d());

            final double length = point.point.distance(nextPoint.point);

            final float width = switch (size) {
                case SMALL -> 6.0f;
                case MEDIUM -> 8.0f;
                case LARGE -> 10.0f;
            };

            final float textureWidth = switch (size) {
                case SMALL -> 16.0f;
                case MEDIUM -> 16.0f;
                case LARGE -> 32.0f;
            };

            renderState.segments.add(new Segment(
                    point.normal,
                    nextPoint.normal,
                    upDir,
                    nextUpDir,
                    point.point,
                    nextPoint.point,
                    false,
                    (float) runningSpringLength * uvScale,
                    (float) (runningSpringLength + length) * uvScale,
                    width,
                    textureWidth));

            // render inside
            renderState.segments.add(new Segment(
                    point.normal.negate(new Vector3d()),
                    nextPoint.normal.negate(new Vector3d()),
                    upDir.negate(new Vector3d()),
                    nextUpDir.negate(new Vector3d()),
                    point.point,
                    nextPoint.point,
                    true,
                    0.0f - (float) runningSpringLength * uvScale,
                    0.0f - (float) (runningSpringLength + length) * uvScale,
                    width,
                    textureWidth));

            runningSpringLength += length;
        }
    }

    @Override
    protected void submitSafe(final SpringRenderState renderState, final PoseStack ps, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(renderState, ps, queue, camera);

        if (renderState.segments.isEmpty() || renderState.texture == null || renderState.origin == null)
            return;

        final int light = renderState.lightCoords;
        final int color = renderState.color;
        final List<Segment> segments = renderState.segments;

        ps.pushPose();
        ps.translate(renderState.origin.x(), renderState.origin.y(), renderState.origin.z());
        queue.submitCustomGeometry(ps, SimRenderTypes.spring(renderState.texture), (pose, buffer) -> {
            for (final Segment segment : segments)
                renderSegment(pose, buffer, segment, color, light);
        });
        ps.popPose();
    }

    private Vec3 getUpDirection(final SpringBlockEntity be, final Vector3dc directionToSpring) {
        final Direction facing = be.getBlockState().getValue(SpringBlock.FACING);

        final Vec3 normal = Vec3.atLowerCornerOf(facing.getUnitVec3i());
        final double dot = directionToSpring.dot(normal.x, normal.y, normal.z);
        final Vector3d dir = directionToSpring.sub(normal.x * dot, normal.y * dot, normal.z * dot, new Vector3d());

        if (dir.lengthSquared() < 1e-6) {
            // default to north or up if we're in a configuration where we can't pick anything
            return facing.getAxis().isHorizontal() ? new Vec3(0, 1, 0) : new Vec3(0, 0, -1);
        }

        return Vec3.atLowerCornerOf(Direction.getApproximateNearest(dir.x, dir.y, dir.z).getOpposite().getUnitVec3i());
    }

    /**
     * Generate a spline with equally spaced points (0.5 units)
     */
    private List<SplinePoint> generateSpline(final Vector3dc pointA, final Vector3dc pointB, final Vector3dc normalA, final Vector3dc normalB, final double controlPointLength) {
        final List<SplinePoint> list = new ObjectArrayList<>();

        // Locals rather than fields: extraction runs per block entity and one renderer instance
        // serves them all.
        final Vector3d controlPointA = new Vector3d();
        final Vector3d controlPointB = new Vector3d();
        final Vector3d segmentALerp = new Vector3d();
        final Vector3d segmentBLerp = new Vector3d();
        final Vector3d segmentCLerp = new Vector3d();

        final double influence = controlPointLength;
        pointA.fma(influence, normalA, controlPointA);
        pointB.fma(influence, normalB, controlPointB);

        final double len = pointA.distance(pointB);
        final int initialPointCount = Mth.clamp(Mth.ceil(len), 5, 8);
        for (int i = 0; i <= initialPointCount; i++) {
            final double t = (double) i / initialPointCount;
            pointA.lerp(controlPointA, t, segmentALerp);
            controlPointA.lerp(controlPointB, t, segmentBLerp);
            controlPointB.lerp(pointB, t, segmentCLerp);

            final Vector3d point = new Vector3d(segmentALerp
                    .lerp(segmentBLerp, t)
                    .lerp(segmentBLerp.lerp(segmentCLerp, t), t));

            final Vector3d normal = new Vector3d();

            if (list.isEmpty()) {
                normal.set(normalA);
            } else if (list.size() == initialPointCount) {
                normal.set(normalB).negate();
            } else {
                point.sub(list.get(list.size() - 1).point, normal).normalize();
            }

            list.add(new SplinePoint(point, normal));
        }

        return list;
    }

    private static void renderSegment(final PoseStack.Pose transform,
                                      final VertexConsumer a,
                                      final Segment segment,
                                      final int color,
                                      final int light) {
        final Vector3d startLeft = new Vector3d();
        final Vector3d endLeft = new Vector3d();
        final Vector3d startUp = new Vector3d();
        final Vector3d endUp = new Vector3d();
        final Vector3d vertex = new Vector3d();

        final Vector3dc startDirection = segment.startDirection();
        final Vector3dc endDirection = segment.endDirection();
        final Vector3dc startPos = segment.startPos();
        final Vector3dc endPos = segment.endPos();
        final float width = segment.width();
        final float textureWidth = segment.textureWidth();

        segment.startUp().cross(startDirection, startLeft).normalize();
        segment.endUp().cross(endDirection, endLeft).normalize();

        final float texW = width / textureWidth;
        final double scale = width / 16.0 / 2.0;

        startLeft.mul(scale);
        segment.startUp().mul(scale, startUp);
        endLeft.mul(scale);
        segment.endUp().mul(scale, endUp);

        final Vector3d startDown = startUp.negate(new Vector3d());
        final Vector3d endDown = endUp.negate(new Vector3d());
        final Vector3d startRight = startLeft.negate(new Vector3d());
        final Vector3d endRight = endLeft.negate(new Vector3d());

        final float uvScale = 16.0f / textureWidth;
        final float uvXOffset = segment.second() ? width / textureWidth : 0.0f;
        final float uvStart = segment.uvStart();
        final float uvEnd = segment.uvEnd();

        vert(transform, a, startPos.add(startLeft, vertex).sub(startUp), color, 0.0f + uvXOffset, uvStart * uvScale, startDown, light);
        vert(transform, a, endPos.add(endLeft, vertex).sub(endUp), color, 0.0f + uvXOffset, uvEnd * uvScale, endDown, light);
        vert(transform, a, endPos.sub(endLeft, vertex).sub(endUp), color, texW + uvXOffset, uvEnd * uvScale, endDown, light);
        vert(transform, a, startPos.sub(startLeft, vertex).sub(startUp), color, texW + uvXOffset, uvStart * uvScale, startDown, light);

        vert(transform, a, startPos.sub(startLeft, vertex).add(startUp), color, 0.0f + uvXOffset, uvStart * uvScale, startUp, light);
        vert(transform, a, endPos.sub(endLeft, vertex).add(endUp), color, 0.0f + uvXOffset, uvEnd * uvScale, endUp, light);
        vert(transform, a, endPos.add(endLeft, vertex).add(endUp), color, texW + uvXOffset, uvEnd * uvScale, endUp, light);
        vert(transform, a, startPos.add(startLeft, vertex).add(startUp), color, texW + uvXOffset, uvStart * uvScale, startUp, light);

        vert(transform, a, startPos.sub(startLeft, vertex).sub(startUp), color, 0.0f + uvXOffset, uvStart * uvScale, startRight, light);
        vert(transform, a, endPos.sub(endLeft, vertex).sub(endUp), color, 0.0f + uvXOffset, uvEnd * uvScale, endRight, light);
        vert(transform, a, endPos.sub(endLeft, vertex).add(endUp), color, texW + uvXOffset, uvEnd * uvScale, endRight, light);
        vert(transform, a, startPos.sub(startLeft, vertex).add(startUp), color, texW + uvXOffset, uvStart * uvScale, startRight, light);

        vert(transform, a, startPos.add(startLeft, vertex).add(startUp), color, 0.0f + uvXOffset, uvStart * uvScale, startLeft, light);
        vert(transform, a, endPos.add(endLeft, vertex).add(endUp), color, 0.0f + uvXOffset, uvEnd * uvScale, endLeft, light);
        vert(transform, a, endPos.add(endLeft, vertex).sub(endUp), color, texW + uvXOffset, uvEnd * uvScale, endLeft, light);
        vert(transform, a, startPos.add(startLeft, vertex).sub(startUp), color, texW + uvXOffset, uvStart * uvScale, startLeft, light);
    }

    private static void vert(final PoseStack.Pose transform, final VertexConsumer a, final Vector3dc pos, final int color, final float u1, final float v1, final Vector3dc normal, final int light) {
        final Vector3d normalized = new Vector3d();
        normal.normalize(normalized);
        a.addVertex(transform.pose(), (float) pos.x(), (float) pos.y(), (float) pos.z())
                .setColor(color)
                .setUv(u1, v1)
                .setLight(light)
                .setNormal(transform, (float) normalized.x(), (float) normalized.y(), (float) normalized.z());
    }

    @Override
    public boolean shouldRender(final SpringBlockEntity blockEntity, final Vec3 vec3) {
        return true;
    }

    record SplinePoint(Vector3dc point, Vector3dc normal) {
    }
}

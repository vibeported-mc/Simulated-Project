package dev.simulated_team.simulated.content.blocks.rope.strand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.index.SimPartialModels;
import dev.simulated_team.simulated.util.SimMathUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Draws a rope strand: a chain of segments, each a stretched length of rope with a knot at its
 * start, plus a selection outline when the player is hovering it.
 *
 * <h2>26.2 note</h2>
 * <p>This used to draw straight into a buffer source, moving the pose between segments. Neither
 * half survives: the pose only exists during submission, and the rope's shape only exists during
 * extraction, because it is read from the client strand.
 *
 * <p>So it splits the same way a block entity renderer does. {@link #extract} walks the strand and
 * produces one {@link RopeSegment} per span -- its offset, orientation, length and its own copies of
 * the knot and rope geometry, each carrying the light sampled at that point. {@link #submit} rebuilds
 * the pose per segment and queues them. Both halves are static because two block entities use this
 * and neither owns it.
 *
 * <p>The hover outline is raw line geometry, which 26.2 writes through
 * {@code submitCustomGeometry} rather than by taking a {@code VertexConsumer} up front.
 */
public class RopeStrandRenderer {
    public record RopeRenderPoint(Quaternionf orientation, Vector3d position) { }

    /**
     * One span of rope. The offset and orientation place it; the length stretches it. Knot is null
     * for the first span, which has nothing before it to tie off.
     */
    public record RopeSegment(Vector3d offset, Quaternionf orientation, double length,
                              @Nullable SuperByteBufferRenderState knot,
                              SuperByteBufferRenderState middle) { }

    /** What extraction hands to submission. Reused between frames, so {@link #clear()} matters. */
    public static class RopeRenderState {
        public final List<RopeSegment> segments = new ArrayList<>();
        /** Non-null only while the player is hovering this rope. */
        public @Nullable List<RopeRenderPoint> outline;
        public @Nullable BlockPos ownerPos;

        public void clear() {
            this.segments.clear();
            this.outline = null;
            this.ownerPos = null;
        }
    }

    public static void extract(final SmartBlockEntity be, final RopeStrandHolderBehavior ropeHolder, final float partialTick, final RopeRenderState out) {
        out.clear();

        final Level level = be.getLevel();
        if (level == null) {
            return;
        }

        final BlockPos ownerPos = be.getBlockPos();
        out.ownerPos = ownerPos;

        final SuperByteBuffer middle = CachedBufferer.partialFacing(SimPartialModels.ROPE, AllBlocks.ROPE.getDefaultState(), Direction.NORTH);
        final SuperByteBuffer knot = CachedBufferer.partialFacing(SimPartialModels.ROPE_KNOT, AllBlocks.ROPE.getDefaultState(), Direction.NORTH);

        final SubLevel subLevel = Sable.HELPER.getContaining(be);
        Pose3dc containingPose = null;

        if (subLevel instanceof final ClientSubLevel clientSubLevel) {
            containingPose = clientSubLevel.renderPose();
        }

        final ClientRopeStrand rope = ropeHolder.getClientStrand();

        if (!ropeHolder.ownsRope() || rope == null) {
            return;
        }

        final ClientRopeStrand clientStrand = rope;
        final List<ClientRopePoint> points = clientStrand.getPoints();

        if (points.size() <= 1) {
            return;
        }

        final ObjectArrayList<RopeRenderPoint> ropeRenderPoints = buildRenderPoints(partialTick, points);

        if (ropeRenderPoints.isEmpty()) return;

        for (int i = 1; i < ropeRenderPoints.size(); i++) {
            final RopeRenderPoint renderPoint0 = ropeRenderPoints.get(i - 1);
            final RopeRenderPoint renderPoint1 = ropeRenderPoints.get(i);
            final Vector3d globalRenderPos = new Vector3d(renderPoint0.position());
            final Vector3d renderPos = renderPoint0.position();
            final Quaternionf orientation = renderPoint0.orientation();

            final double length = renderPoint1.position().distance(renderPoint0.position());

            if (containingPose != null) {
                containingPose.transformPositionInverse(renderPos);
                orientation.premul(new Quaternionf(containingPose.orientation()).conjugate());
            }

            final BlockPos pos = BlockPos.containing(globalRenderPos.x, globalRenderPos.y, globalRenderPos.z);
            final int worldLight = LightCoordsUtil.getLightCoords(level, pos);

            // The buffers are reused: extractRenderState resets each one, exactly as renderInto did.
            final SuperByteBufferRenderState knotState = i > 1
                    ? knot.light(worldLight).extractRenderState()
                    : null;
            final SuperByteBufferRenderState middleState = middle.light(worldLight).extractRenderState();

            out.segments.add(new RopeSegment(
                    new Vector3d(renderPos.x - ownerPos.getX(), renderPos.y - ownerPos.getY(), renderPos.z - ownerPos.getZ()),
                    new Quaternionf(orientation), length, knotState, middleState));
        }

        final RopeRenderPoint last = ropeRenderPoints.getLast();
        if (containingPose != null) {
            final Vector3d renderPos = last.position();
            final Quaternionf orientation = last.orientation();

            containingPose.transformPositionInverse(renderPos);
            orientation.premul(new Quaternionf(containingPose.orientation()).conjugate());
        }

        if (Objects.equals(ZiplineClientManager.hoveringRope, clientStrand.getUuid())) {
            out.outline = ropeRenderPoints;
        }
    }

    public static void submit(final RopeRenderState state, final PoseStack ps, final SubmitNodeCollector queue) {
        if (state.ownerPos == null) {
            return;
        }

        ps.pushPose();
        for (final RopeSegment segment : state.segments) {
            ps.pushPose();
            ps.translate(segment.offset().x, segment.offset().y, segment.offset().z);
            ps.mulPose(segment.orientation());
            ps.translate(-0.5, -0.5, -0.5);

            if (segment.knot() != null) {
                segment.knot().submit(ps, RenderTypes.solidMovingBlock(), queue);
            }

            ps.translate(0.0, 0.5, 0.0);
            ps.scale(1.0f, (float) segment.length(), 1.0f);

            segment.middle().submit(ps, RenderTypes.solidMovingBlock(), queue);
            ps.popPose();
        }
        ps.popPose();

        if (state.outline != null) {
            submitOutline(ps, queue, 3.0f / 16.0f, state.outline, state.ownerPos);
        }
    }

    private static void submitOutline(final PoseStack ps, final SubmitNodeCollector queue, final float rad, final List<RopeRenderPoint> ropeRenderPoints, final BlockPos ownerPos) {
        // The consumer arrives when the queue is drained, so the geometry is written in the lambda
        // rather than into a buffer taken up front.
        queue.submitCustomGeometry(ps, RenderTypes.lines(), (pose, linesVB) -> {
            final Vector3d previousCorner = new Vector3d();
            final Vector3d currentCorner = new Vector3d();
            final Vector3d cornerDiff = new Vector3d();

            final Vector3d[] ropeCorners = {
                    new Vector3d(-rad, 0, -rad),
                    new Vector3d(-rad, 0, rad),
                    new Vector3d(rad, 0, rad),
                    new Vector3d(rad, 0, -rad),
            };

            for (int i = 0; i < ropeRenderPoints.size() + 1; i++) {
                final RopeRenderPoint renderPoint0 = ropeRenderPoints.get(Math.max(0, i - 1));
                final RopeRenderPoint renderPoint1 = ropeRenderPoints.get(Math.min(ropeRenderPoints.size() - 1, i));
                final boolean start = i == 0;
                final boolean end = i == ropeRenderPoints.size();

                for (final Vector3d ropeCorner : ropeCorners) {
                    renderPoint0.orientation().transform(start ? ropeCorner.rotateY(Math.PI / 2.0, previousCorner) : ropeCorner, previousCorner)
                            .add(renderPoint0.position())
                            .sub(ownerPos.getX(), ownerPos.getY(), ownerPos.getZ());

                    renderPoint1.orientation().transform(end ? ropeCorner.rotateY(Math.PI / 2.0, ropeCorner) : ropeCorner, currentCorner)
                            .add(renderPoint1.position())
                            .sub(ownerPos.getX(), ownerPos.getY(), ownerPos.getZ());

                    currentCorner.sub(previousCorner, cornerDiff).normalize();

                    linesVB.addVertex(pose.pose(), (float) previousCorner.x, (float) previousCorner.y, (float) previousCorner.z)
                            .setColor(0f, 0f, 0f, .4f)
                            .setNormal(pose, (float) cornerDiff.x, (float) cornerDiff.y, (float) cornerDiff.z);

                    linesVB.addVertex(pose.pose(), (float) currentCorner.x, (float) currentCorner.y, (float) currentCorner.z)
                            .setColor(0f, 0f, 0f, .4f)
                            .setNormal(pose, (float) cornerDiff.x, (float) cornerDiff.y, (float) cornerDiff.z);
                }
            }
        });
    }

    private static @NotNull ObjectArrayList<RopeRenderPoint> buildRenderPoints(final float partialTick, final List<ClientRopePoint> inputPoints) {
        final ObjectArrayList<RopeRenderPoint> ropeRenderPoints = new ObjectArrayList<>();
        final ObjectArrayList<ClientRopePoint> points = new ObjectArrayList<>(inputPoints);

        while (points.size() >= 2 && points.getFirst().position().distanceSquared(points.get(1).position()) < 1e-3) {
            points.removeFirst();
        }

        if (points.size() <= 1) {
            return new ObjectArrayList<>();
        }

        final Vector3dc pointZeroPosition = points.get(0).renderPos(partialTick, new Vector3d());
        final Vector3dc pointOnePosition = points.get(1).renderPos(partialTick, new Vector3d());

        final Vector3d normal = pointOnePosition.sub(pointZeroPosition, new Vector3d()).normalize();

        final Quaternionf runningRotation;
        if (normal.dot(OrientedBoundingBox3d.UP) < 0) {
            runningRotation = SimMathUtils.getQuaternionfFromVectorRotation(new Vector3d(0, -1, 0), normal);
            runningRotation.rotateZ((float) Math.PI);
        } else {
            runningRotation = SimMathUtils.getQuaternionfFromVectorRotation(new Vector3d(0, 1, 0), normal);
        }

        ropeRenderPoints.add(new RopeRenderPoint(new Quaternionf(runningRotation), new Vector3d(pointZeroPosition)));

        final Vector3d runningNormal = new Vector3d();

        final Vector3d bPos = new Vector3d();
        final Vector3d aPos = new Vector3d();

        for (int i = 2; i < points.size(); i++) {
            final ClientRopePoint pointA = points.get(i - 1);
            final ClientRopePoint pointB = points.get(i);

            runningNormal.set(pointB.renderPos(partialTick, bPos))
                    .sub(pointA.renderPos(partialTick, aPos))
                    .normalize();

            if (runningNormal.dot(OrientedBoundingBox3d.UP) < -0.15) {
                runningRotation.set(SimMathUtils.getQuaternionfFromVectorRotation(new Vector3d(0, -1, 0), runningNormal));
                runningRotation.rotateZ((float) Math.PI);
            } else {
                runningRotation.set(SimMathUtils.getQuaternionfFromVectorRotation(new Vector3d(0, 1, 0), runningNormal));
            }

            ropeRenderPoints.add(new RopeRenderPoint(new Quaternionf(runningRotation), pointA.renderPos(partialTick, new Vector3d())));
            normal.set(runningNormal);
        }

        ropeRenderPoints.add(new RopeRenderPoint(new Quaternionf(runningRotation), points.getLast().renderPos(partialTick, new Vector3d())));
        return ropeRenderPoints;
    }
}

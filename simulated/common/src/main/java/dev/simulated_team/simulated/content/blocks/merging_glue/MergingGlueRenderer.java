package dev.simulated_team.simulated.content.blocks.merging_glue;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlock;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * <h2>26.2 note</h2>
 * <p>The glue strands are raw quads, not a model, and everything that positions them -- the partner
 * block entity, both sub-level poses -- is only readable during extraction. So extraction works out
 * each strand's two ends and their local axes and keeps them; submission writes the vertices.
 *
 * <p>26.2 hands the {@code VertexConsumer} over when the queue is drained rather than letting a
 * renderer take one up front, so the writing happens inside a {@code submitCustomGeometry} callback.
 */
public class MergingGlueRenderer
        extends SmartBlockEntityRenderer<MergingGlueBlockEntity, MergingGlueRenderer.MergingGlueRenderState> {

    /** One strand: both ends, and the two axes each end is spread along. */
    public record Strand(Vector3dc posA, Vector3dc upA, Vector3dc rightA,
                         Vector3dc posB, Vector3dc upB, Vector3dc rightB) { }

    public static class MergingGlueRenderState extends SmartRenderState {
        public final List<Strand> strands = new ArrayList<>();
    }

    public MergingGlueRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public MergingGlueRenderState createRenderState() {
        return new MergingGlueRenderState();
    }

    @Override
    protected void extractSafe(final MergingGlueBlockEntity be, final MergingGlueRenderState renderState, final float partialTicks, final Vec3 cameraPosition) {
        super.extractSafe(be, renderState, partialTicks, cameraPosition);

        // Reused between frames, so a broken or non-controller glue has to clear what it drew.
        renderState.strands.clear();

        if (!be.isController()) {
            return;
        }

        final MergingGlueBlockEntity other = be.getPartnerGlue();
        if (other == null) {
            return;
        }

        final SubLevel otherSubLevel = Sable.HELPER.getContainingClient(other);
        final SubLevel subLevel = Sable.HELPER.getContainingClient(be);

        final BlockPos blockPos = be.getBlockPos();

        final Vector3dc center = be.getCenter(new Vector3d());
        final Vector3d otherCenter = other.getCenter(new Vector3d());

        final BlockState state = be.getBlockState();
        final Direction facing = state.getValue(SpringBlock.FACING);
        final Direction otherFacing = other.getBlockState().getValue(SpringBlock.FACING);
        final Vector3dc normalA = JOMLConversion.atLowerCornerOf(facing.getUnitVec3i());
        final Vector3d normalB = JOMLConversion.atLowerCornerOf(otherFacing.getUnitVec3i());

        final Pose3dc renderPose = subLevel != null ? ((ClientSubLevel) subLevel).renderPose() : null;
        final Pose3dc otherRenderPose = otherSubLevel != null ? ((ClientSubLevel) otherSubLevel).renderPose() : null;

        // if one is horizontal, both glue must be horizontal
        final boolean horizontal = facing.getAxis().isHorizontal();

        final Vector3dc rightA = horizontal ?
                JOMLConversion.atLowerCornerOf(facing.getClockWise().getUnitVec3i(), new Vector3d()) :
                OrientedBoundingBox3d.FORWARD;

        final Vector3d rightB = horizontal ?
                JOMLConversion.atLowerCornerOf(otherFacing.getCounterClockWise().getUnitVec3i(), new Vector3d()) :
                new Vector3d(OrientedBoundingBox3d.FORWARD);

        final Vector3dc upA = horizontal ?
                new Vector3d(0.0, 1.0, 0.0)
                : OrientedBoundingBox3d.RIGHT;

        final Vector3d upB = horizontal ?
                new Vector3d(0.0, 1.0, 0.0)
                : new Vector3d(OrientedBoundingBox3d.RIGHT);

        if (otherRenderPose != null) {
            otherRenderPose.transformNormal(normalB);
            otherRenderPose.transformNormal(rightB);
            otherRenderPose.transformNormal(upB);
            otherRenderPose.transformPosition(otherCenter);
        }

        if (renderPose != null) {
            renderPose.transformNormalInverse(normalB);
            renderPose.transformNormalInverse(rightB);
            renderPose.transformNormalInverse(upB);
            renderPose.transformPositionInverse(otherCenter);
        }


        final Vector3d strandCenterA = center.sub(JOMLConversion.atLowerCornerOf(blockPos), new Vector3d());
        final Vector3d strandCenterB = otherCenter.sub(JOMLConversion.atLowerCornerOf(blockPos), new Vector3d());

        final Vector2d[] strandPositions = {
          new Vector2d(0.25, 0.25),
          new Vector2d(0.45, 0.3),
          new Vector2d(0.6, 0.6),
          new Vector2d(0.65, 0.7)
        };

        for (int i = 0; i < 2; i++) {
            final Vector2d strandA = strandPositions[i * 2].sub(0.5, 0.5, new Vector2d()).mul(0.75);
            final Vector2d strandB = strandPositions[i * 2 + 1].sub(0.5, 0.5, new Vector2d()).mul(0.75);

            // Each strand keeps its own copies: the state outlives this method, and the shared axis
            // vectors would otherwise be aliased by both strands.
            renderState.strands.add(new Strand(
                    new Vector3d(strandCenterA).fma(strandA.x, rightA).fma(strandA.y, upA),
                    new Vector3d(upA),
                    new Vector3d(rightA),

                    new Vector3d(strandCenterB).fma(strandB.x, rightB).fma(strandB.y, upB),
                    new Vector3d(upB),
                    new Vector3d(rightB)));
        }
    }

    @Override
    protected void submitSafe(final MergingGlueRenderState renderState, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(renderState, ms, queue, camera);

        if (renderState.strands.isEmpty())
            return;

        final int light = renderState.lightCoords;
        final List<Strand> strands = renderState.strands;
        queue.submitCustomGeometry(ms, RenderTypes.entityCutout(Simulated.path("textures/block/merging_glue/strand.png")),
                (pose, buffer) -> {
                    for (final Strand strand : strands)
                        renderGlueCross(strand, buffer, pose, light);
                });
    }

    private static VertexConsumer addVertex(final VertexConsumer buffer, final Matrix4f pose, final Vector3dc pos) {
        return buffer.addVertex(pose, (float) pos.x(), (float) pos.y(), (float) pos.z());
    }

    private static void renderGlueCross(final Strand strand,
                                        final VertexConsumer buffer,
                                        final PoseStack.Pose transform,
                                        final int light) {
        final Vector3dc posA = strand.posA();
        final Vector3dc upA = strand.upA();
        final Vector3dc rightA = strand.rightA();
        final Vector3dc posB = strand.posB();
        final Vector3dc upB = strand.upB();
        final Vector3dc rightB = strand.rightB();

        final Matrix4f pose = transform.pose();
        final Vector3d vertex = new Vector3d();

        // vertical plane & backface
        addVertex(buffer, pose, posA.fma(-0.5, upA, vertex)).setColor(0xffffffff).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform, 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posA.fma(0.5, upA, vertex)).setColor(0xffffffff).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform, 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posB.fma(0.5, upB, vertex)).setColor(0xffffffff).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform, 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posB.fma(-0.5, upB, vertex)).setColor(0xffffffff).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform, 0.0f, 1.0f, 0.0f);

        addVertex(buffer, pose, posB.fma(-0.5, upB, vertex)).setColor(0xffffffff).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform, 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posB.fma(0.5, upB, vertex)).setColor(0xffffffff).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform, 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posA.fma(0.5, upA, vertex)).setColor(0xffffffff).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform, 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posA.fma(-0.5, upA, vertex)).setColor(0xffffffff).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform, 0.0f, 1.0f, 0.0f);

        // horizontal plane & backface
        addVertex(buffer, pose, posA.fma(-0.5, rightA, vertex)).setColor(0xffffffff).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform, 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posA.fma(0.5, rightA, vertex)).setColor(0xffffffff).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform, 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posB.fma(0.5, rightB, vertex)).setColor(0xffffffff).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform, 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posB.fma(-0.5, rightB, vertex)).setColor(0xffffffff).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform, 0.0f, 1.0f, 0.0f);

        addVertex(buffer, pose, posB.fma(-0.5, rightB, vertex)).setColor(0xffffffff).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform, 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posB.fma(0.5, rightB, vertex)).setColor(0xffffffff).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform, 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posA.fma(0.5, rightA, vertex)).setColor(0xffffffff).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform, 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posA.fma(-0.5, rightA, vertex)).setColor(0xffffffff).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform, 0.0f, 1.0f, 0.0f);
    }
}

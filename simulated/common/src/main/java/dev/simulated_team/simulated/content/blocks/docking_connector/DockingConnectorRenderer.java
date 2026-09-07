package dev.simulated_team.simulated.content.blocks.docking_connector;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.api.math.AngleHelper;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;

/**
 * <h2>26.2 note</h2>
 * <p>The extension and foot rotation are read off the block entity, so all the geometry is baked
 * during extraction. Reusing one buffer per model across the four legs stays correct:
 * {@code extractRenderState} resets the buffer, exactly as {@code renderInto} did.
 *
 * <p>What cannot be baked is the pose. Each leg is drawn under its own quarter turn, and the whole
 * assembly under the connector's facing, so those stay in the submit phase -- which means the legs
 * are kept as four separate groups rather than one flat list.
 */
public class DockingConnectorRenderer
        extends SafeBlockEntityRenderer<DockingConnectorBlockEntity, DockingConnectorRenderer.DockingConnectorRenderState> {

    public static class DockingConnectorRenderState extends SafeRenderState {
        public @Nullable Direction facing;
        public final List<SuperByteBufferRenderState> mainPistons = new ArrayList<>();
        /** One group per leg; each is drawn under its own quarter turn. */
        public final List<List<SuperByteBufferRenderState>> legs = new ArrayList<>();
    }

    public DockingConnectorRenderer(final BlockEntityRendererProvider.Context context) {

    }

    @Override
    public DockingConnectorRenderState createRenderState() {
        return new DockingConnectorRenderState();
    }

    @Override
    protected void extractSafe(final DockingConnectorBlockEntity be, final DockingConnectorRenderState renderState, final float partialTicks, final Vec3 cameraPosition) {
        renderState.mainPistons.clear();
        renderState.legs.clear();

        final Direction direction = be.getBlockState()
                .getValue(BlockStateProperties.FACING);
        renderState.facing = direction;
        final BlockState blockState = be.getBlockState();
        final float extension = be.getExtensionDistance(partialTicks);
        final float rotation = be.getFeetRotation(partialTicks) * 90;

        final SuperByteBuffer piston1 = CachedBufferer.partial(SimPartialModels.DOCKING_CONNECTOR_MAIN_PISTON_BOTTOM, blockState);
        final SuperByteBuffer piston2 = CachedBufferer.partial(SimPartialModels.DOCKING_CONNECTOR_MAIN_PISTON_TOP, blockState);
        final SuperByteBuffer sidePiston1 = CachedBufferer.partial(SimPartialModels.DOCKING_CONNECTOR_SIDE_PISTON_BOTTOM, blockState);
        final SuperByteBuffer sidePiston2 = CachedBufferer.partial(SimPartialModels.DOCKING_CONNECTOR_SIDE_PISTON_TOP, blockState);
        final SuperByteBuffer foot = CachedBufferer.partial(SimPartialModels.DOCKING_CONNECTOR_FOOT, blockState);

        piston1.translate(0, extension * 0.5, 0);
        piston2.translate(0, extension, 0);
        renderState.mainPistons.add(piston1.light(renderState.lightCoords).extractRenderState());
        renderState.mainPistons.add(piston2.light(renderState.lightCoords).extractRenderState());

        final Vector2f footAnchor = new Vector2f();
        final Vector2f sidePistonTopAnchor = new Vector2f();
        final Vector2f sidePistonBottomAnchor = new Vector2f();
        final Vector2f relativeAnchor = new Vector2f();

        footAnchor.set(-7.5f, 15.5f).div(16).add(0, extension);
        this.rotateVector2f(sidePistonTopAnchor.set(1.5f, -2.5f).div(16), rotation).add(footAnchor);
        sidePistonBottomAnchor.set(-6, 2).div(16).add(0, extension / 2);

        relativeAnchor.set(sidePistonTopAnchor).sub(sidePistonBottomAnchor);
        relativeAnchor.normalize();

        final Matrix4f rotationMatrix = new Matrix4f(
                1, 0, 0, 0,
                0, relativeAnchor.y, relativeAnchor.x, 0,
                0, -relativeAnchor.x, relativeAnchor.y, 0,
                0, 0, 0, 1
        );


        for (int i = 0; i < 4; i++) {
            final List<SuperByteBufferRenderState> leg = new ArrayList<>();

            sidePiston1.translate(0, sidePistonBottomAnchor.y, sidePistonBottomAnchor.x);
            sidePiston2.translate(0, sidePistonTopAnchor.y, sidePistonTopAnchor.x);
            foot.translate(0, footAnchor.y, footAnchor.x);
            foot.rotateXDegrees(rotation);

            sidePiston1.mulPose(rotationMatrix);
            sidePiston2.mulPose(rotationMatrix);

            leg.add(sidePiston1.light(renderState.lightCoords).extractRenderState());
            leg.add(sidePiston2.light(renderState.lightCoords).extractRenderState());
            leg.add(foot.light(renderState.lightCoords).extractRenderState());
            renderState.legs.add(leg);
        }
    }

    @Override
    protected void submitSafe(final DockingConnectorRenderState renderState, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        if (renderState.facing == null)
            return;

        ms.pushPose();
        rotateToFaceCentered(ms, renderState.facing);

        for (final SuperByteBufferRenderState piston : renderState.mainPistons)
            piston.submit(ms, RenderTypes.cutoutMovingBlock(), queue);

        for (int i = 0; i < renderState.legs.size(); i++) {
            ms.pushPose();
            ms.translate(0.5, 0, 0.5);
            TransformStack.of(ms).rotateYDegrees(i * 90);
            for (final SuperByteBufferRenderState part : renderState.legs.get(i))
                part.submit(ms, RenderTypes.cutoutMovingBlock(), queue);
            ms.popPose();
        }

        ms.popPose();
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    public static void rotateToFaceCentered(final PoseStack ms, final Direction facing) {
        TransformStack.of(ms)
                .center()
                .rotateYDegrees(AngleHelper.horizontalAngle(facing))
                .rotateXDegrees(AngleHelper.verticalAngle(facing) + 90)
                .uncenter();
    }

    private Vector2f rotateVector2f(final Vector2f v, float angle) {
        angle = (float) Math.toRadians(angle);
        final float s = Mth.sin(angle);
        final float c = Mth.cos(angle);
        v.set(v.x * c + v.y * s, v.y * c - v.x * s);
        return v;
    }

}

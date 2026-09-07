package dev.ryanhcode.offroad.content.blocks.wheel_mount;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer.FilterRenderState;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.ryanhcode.offroad.content.components.TireLike;
import dev.ryanhcode.offroad.index.OffroadDataComponents;
import dev.ryanhcode.offroad.index.OffroadPartialModels;
import dev.simulated_team.simulated.util.SimColors;
import net.createmod.catnip.api.math.AngleHelper;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2d;

/**
 * <h2>26.2 note</h2>
 * <p>Almost every transform here goes onto the pose rather than onto a buffer, so the walk stays in
 * the submit phase and the values driving it -- the telescope and spring angles, the wheel's yaw and
 * roll, the tire's own offsets -- are measured during extraction and carried across.
 *
 * <p>The tire is drawn one of two ways and both had to change. A partial model becomes a render
 * state like any other; a plain item stack is resolved into an {@link ItemStackRenderState} during
 * extraction and submitted from it, because {@code ItemRenderer.renderStatic} is gone with
 * {@code MultiBufferSource}.
 */
public class WheelMountRenderer
        extends KineticBlockEntityRenderer<WheelMountBlockEntity, WheelMountRenderer.WheelMountRenderState> {

    public static class WheelMountRenderState extends KineticRenderState {
        public @Nullable FilterRenderState filter;
        public @Nullable Direction direction;

        public @Nullable SuperByteBufferRenderState teleOuter;
        public @Nullable SuperByteBufferRenderState teleInner;
        public @Nullable SuperByteBufferRenderState teleMount;
        public @Nullable SuperByteBufferRenderState springTop;
        public @Nullable SuperByteBufferRenderState springMiddle;
        public @Nullable SuperByteBufferRenderState springBottom;
        public @Nullable SuperByteBufferRenderState diodeLeft;
        public @Nullable SuperByteBufferRenderState diodeRight;

        /** The tire, drawn either as a partial model or as a plain item. At most one is set. */
        public @Nullable SuperByteBufferRenderState wheel;
        public @Nullable ItemStackRenderState wheelItem;
        public @Nullable Vec3 tireRotation;
        public @Nullable Vec3 tireOffset;

        public double teleAngle;
        public double teleDistance;
        public double springAngle;
        public double verticalWheelPosition;
        public double springMountHor;
        public double springMountVer;
        public double horizontalWheelPosition;
        public float yaw;
        public double signMultiplier;
    }

    private final ItemModelResolver itemModelResolver;

    public WheelMountRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public WheelMountRenderState createRenderState() {
        return new WheelMountRenderState();
    }

    @Override
    protected void extractSafe(final WheelMountBlockEntity be, final WheelMountRenderState renderState, final float partialTicks, final Vec3 cameraPosition) {
        super.extractSafe(be, renderState, partialTicks, cameraPosition);

        renderState.filter = FilteringRenderer.getFilterRenderState(be, this.itemModelResolver, cameraPosition);

        final Direction direction = be.getBlockState()
                .getValue(BlockStateProperties.HORIZONTAL_FACING)
                .getOpposite();
        renderState.direction = direction;
        final BlockState blockState = be.getBlockState();

        final int light = renderState.lightCoords;

        final double wheelPivotOffsetHor = 10.0 / 16.0;
        final double springWheelPivotOffsetHor = 12.0 / 16.0;
        final double springWheelPivotOffsetVer = -2.0 / 16.0;

        final double horizontalWheelPosition = 22.0 / 16.0;
        final double verticalWheelPosition = -be.getLerpedExtension(partialTicks);

        final double teleMountHor = 0.0 / 16.0;
        final double teleMountVer = -6.0 / 16.0;

        final double springMountHor = 7.0 / 16.0;
        final double springMountVer = 7.0 / 16.0;

        final double teleAngle = Math.atan2(verticalWheelPosition - teleMountVer, horizontalWheelPosition - wheelPivotOffsetHor - teleMountHor);
        final double teleDistance = new Vector2d(verticalWheelPosition - teleMountVer, horizontalWheelPosition - wheelPivotOffsetHor - teleMountHor).length();

        final double springAngle = Math.atan2(verticalWheelPosition - springWheelPivotOffsetVer - springMountVer, horizontalWheelPosition - springWheelPivotOffsetHor - springMountHor);
        final double springDistance = new Vector2d(verticalWheelPosition - springWheelPivotOffsetVer - springMountVer, horizontalWheelPosition - springWheelPivotOffsetHor - springMountHor).length();

        renderState.teleAngle = teleAngle;
        renderState.teleDistance = teleDistance;
        renderState.springAngle = springAngle;
        renderState.verticalWheelPosition = verticalWheelPosition;
        renderState.horizontalWheelPosition = horizontalWheelPosition;
        renderState.springMountHor = springMountHor;
        renderState.springMountVer = springMountVer;
        renderState.yaw = (float) be.getLerpedYaw(partialTicks);
        renderState.signMultiplier = -be.getLerpedAngle(partialTicks)
                * (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : -1.0)
                * (direction.getAxis() == Direction.Axis.X ? 1.0 : -1.0);

        renderState.teleOuter = CachedBufferer.partial(OffroadPartialModels.TELE_OUTER, blockState).light(light).extractRenderState();
        renderState.teleInner = CachedBufferer.partial(OffroadPartialModels.TELE_INNER, blockState).light(light).extractRenderState();
        renderState.teleMount = CachedBufferer.partial(OffroadPartialModels.TELE_MOUNT, blockState).light(light).extractRenderState();

        final float springExtension = (float) springDistance;
        final float springSpan = springExtension - 4.0f / 16.0f;

        renderState.springTop = CachedBufferer.partial(OffroadPartialModels.SPRING_UPPER, blockState).light(light).extractRenderState();
        renderState.springMiddle = CachedBufferer.partial(OffroadPartialModels.SPRING_MIDDLE, blockState).light(light)
                .translate(0.0f, 13.0f / 16.0f, 0.0f)
                .scale(1.0f, springSpan / (14.0f / 16.0f), 1.0f)
                .translateBack(0.0f, 13.0f / 16.0f, 0.0f)
                .extractRenderState();
        renderState.springBottom = CachedBufferer.partial(OffroadPartialModels.SPRING_LOWER, blockState).light(light)
                .translate(0.0, -(springSpan + -14.0 / 16.0), 0.0)
                .extractRenderState();

        renderState.diodeLeft = CachedBufferer.partial(OffroadPartialModels.DIODE_LEFT, blockState).light(light)
                .color(SimColors.redstone(be.clientSteeringSignalLeft / 15.0f))
                .extractRenderState();
        renderState.diodeRight = CachedBufferer.partial(OffroadPartialModels.DIODE_RIGHT, blockState).light(light)
                .color(SimColors.redstone(be.clientSteeringSignalRight / 15.0f))
                .extractRenderState();

        // Reused between frames, so a mount that lost its tire has to clear both forms.
        renderState.wheel = null;
        renderState.wheelItem = null;
        renderState.tireRotation = null;
        renderState.tireOffset = null;

        final ItemStack itemStack = be.getHeldItem();
        final TireLike tireLike = itemStack.get(OffroadDataComponents.TIRE);
        if (tireLike == null) {
            return;
        }

        renderState.tireRotation = tireLike.rotation();
        renderState.tireOffset = tireLike.offset();

        if (tireLike.model().isPresent()) {
            final Identifier model = tireLike.model().get();
            final SuperByteBuffer wheel = CachedBufferer.partial(PartialModel.of(model), this.getRenderedBlockState(be));
            renderState.wheel = wheel.light(light)
                    .translate(-0.5f, 0.0f, -0.5f)
                    .extractRenderState();
        } else {
            final ItemStackRenderState item = new ItemStackRenderState();
            this.itemModelResolver.updateForTopItem(item, itemStack, ItemDisplayContext.NONE, be.getLevel(), null, 0);
            renderState.wheelItem = item;
        }
    }

    @Override
    protected void submitSafe(final WheelMountRenderState renderState, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(renderState, ms, queue, camera);

        if (renderState.direction == null)
            return;

        if (renderState.filter != null)
            renderState.filter.submit(renderState.blockState, queue, ms, renderState.lightCoords);

        ms.pushPose();
        TransformStack.of(ms)
                .center()
                .rotateYDegrees(AngleHelper.horizontalAngle(renderState.direction))
                .rotateXDegrees(AngleHelper.verticalAngle(renderState.direction))
                .uncenter();

        // wheel & telescope
        ms.pushPose();
        ms.pushPose();
        ms.translate(0.0, -6.0 / 16.0, 0.0);
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.XP.rotation((float) renderState.teleAngle));
        ms.translate(-0.5, -0.5, -0.5);
        submit(renderState.teleOuter, ms, queue);
        ms.translate(0.0, 0.0, -(renderState.teleDistance - 1.0));
        submit(renderState.teleInner, ms, queue);
        ms.popPose();

        ms.pushPose();
        ms.translate(0.0, renderState.verticalWheelPosition, 26.0 / 16.0 - renderState.horizontalWheelPosition);

        ms.translate(0.5, 0.5, 0.5);
        ms.rotateAround(Axis.YP.rotation(renderState.yaw), 0.0F, 0.0F, (float) (-renderState.horizontalWheelPosition + 6.0 / 16.0));
        ms.translate(-0.5, -0.5, -0.5);

        submit(renderState.teleMount, ms, queue);

        ms.translate(0.5, 0.5, 0.5);
        ms.translate(0.0, 0.0, -26.0 / 16.0f);

        ms.mulPose(Axis.ZP.rotation((float) renderState.signMultiplier));

        if (renderState.tireRotation != null && renderState.tireOffset != null) {
            ms.mulPose(Axis.XP.rotation((float) Math.toRadians(renderState.tireRotation.x)));
            ms.mulPose(Axis.YP.rotation((float) Math.toRadians(renderState.tireRotation.y)));
            ms.mulPose(Axis.ZP.rotation((float) Math.toRadians(renderState.tireRotation.z)));
            ms.translate(renderState.tireOffset.x, renderState.tireOffset.y, renderState.tireOffset.z);

            if (renderState.wheel != null) {
                renderState.wheel.submit(ms, RenderTypes.cutoutMovingBlock(), queue);
            } else if (renderState.wheelItem != null) {
                renderState.wheelItem.submit(ms, queue, renderState.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            }
        }

        ms.popPose();

        ms.popPose();

        // spring
        ms.pushPose();
        ms.translate(0.5, 0.5 + renderState.springMountVer, 0.5 - renderState.springMountHor);
        ms.mulPose(Axis.XP.rotation((float) renderState.springAngle + Mth.PI / 2.0f));
        ms.translate(-0.5, -0.5 - renderState.springMountVer, -0.5 + renderState.springMountHor);

        submit(renderState.springTop, ms, queue);
        submit(renderState.springMiddle, ms, queue);
        submit(renderState.springBottom, ms, queue);
        ms.popPose();

        submit(renderState.diodeLeft, ms, queue);
        submit(renderState.diodeRight, ms, queue);
        ms.popPose();
    }

    private static void submit(final @Nullable SuperByteBufferRenderState geometry, final PoseStack ms, final SubmitNodeCollector queue) {
        if (geometry != null)
            geometry.submit(ms, RenderTypes.cutoutMovingBlock(), queue);
    }

    @Override
    public int getViewDistance() {
        return 512;
    }

    @Override
    protected SuperByteBuffer getRotatedModel(final WheelMountBlockEntity te, final BlockState state) {
        return CachedBufferer.partialFacing(AllPartialModels.SHAFT_HALF, te.getBlockState(), te.getBlockState()
                .getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
    }
}

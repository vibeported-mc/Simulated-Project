package dev.simulated_team.simulated.content.blocks.nav_table;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.RenderableNavigationTarget;
import dev.simulated_team.simulated.index.SimPartialModels;
import dev.simulated_team.simulated.index.SimTags;
import dev.simulated_team.simulated.util.SimColors;
import dev.simulated_team.simulated.util.SimDirectionUtil;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * <h2>26.2 note</h2>
 * <p>The indicators' redstone levels, the pointer's angle and the held item all come off the block
 * entity, so they are resolved during extraction; the pose walk that places them stays in
 * submission.
 *
 * <p>A held item is resolved into an {@link ItemStackRenderState} -- {@code ItemRenderer.renderStatic}
 * is gone -- and whether it is a block item, which decides how far it is lifted and how much it is
 * scaled, comes off that state's {@code usesBlockLight} rather than from a baked model's
 * {@code isGui3d}. That is what Create's own item extraction does.
 *
 * <p>{@code Direction.getNearest(double, double, double)} became {@code getApproximateNearest}. The
 * name that kept the old spelling takes ints and means something else.
 */
public class NavTableRenderer
        extends SmartBlockEntityRenderer<NavTableBlockEntity, NavTableRenderer.NavTableRenderState> {

    public static class NavTableRenderState extends SmartRenderState {
        public final List<SuperByteBufferRenderState> indicators = new ArrayList<>();
        public @Nullable SuperByteBufferRenderState pointer;
        public @Nullable ItemStackRenderState heldItem;
        public @Nullable RenderableNavigationTarget customRenderer;
        public @Nullable Direction facing;
        public float arrowAngle;
        public boolean blockItem;
        public boolean rotateWithArrow;
    }

    private final ItemModelResolver itemModelResolver;

    public NavTableRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public NavTableRenderState createRenderState() {
        return new NavTableRenderState();
    }

    @Override
    protected void extractSafe(final NavTableBlockEntity navBE, final NavTableRenderState state, final float partialTicks, final Vec3 cameraPosition) {
        super.extractSafe(navBE, state, partialTicks, cameraPosition);

        state.indicators.clear();
        state.pointer = null;
        state.heldItem = null;
        state.customRenderer = null;

        final ItemStack heldItem = navBE.getHeldItem();
        final BlockState navState = navBE.getBlockState();
        final Direction facing = navState.getValue(NavTableBlock.FACING);
        state.facing = facing;

        final float arrowAngle = (float) (navBE.getClientTargetAngle(partialTicks) - Math.PI / 2);
        state.arrowAngle = arrowAngle;

        if (!VisualizationManager.supportsVisualization(navBE.getLevel())) {
            final Vector3f logicalDirectionF = new Vector3f();
            for (final Direction direction : SimDirectionUtil.Y_AXIS_PLANE) {
                facing.getRotation().transform(direction.getStepX(), direction.getStepY(), direction.getStepZ(), logicalDirectionF);
                final Direction logicalDirection = Direction.getApproximateNearest(logicalDirectionF.x, logicalDirectionF.y, logicalDirectionF.z);

                // SuperByteBuffer.rotateToFace is gone; partialFacing bakes the same turn in.
                final SuperByteBuffer indicator = CachedBufferer.partialFacing(SimPartialModels.NAV_TABLE_INDICATOR, navState, direction);

                indicator.translate(0, 0, 0.5);
                final float signalStrength = navBE.isPowering ? Math.max(navBE.getRedstoneStrength(logicalDirection), 0) / 15.0F : 0;
                final int color = SimColors.redstone(signalStrength); // Analog indicators (mixes between colors smoothly)
                state.indicators.add(indicator.light(state.lightCoords)
                        .color(color)
                        .extractRenderState());
            }

            final SuperByteBuffer pointer = CachedBufferer.partial(SimPartialModels.NAV_TABLE_POINTER, navState);
            pointer.rotateY(arrowAngle);
            state.pointer = pointer.light(state.lightCoords).extractRenderState();
        }

        //keep item rendering outside of visual instances to allow for more flexability of custom renderers
        if (heldItem.getItem() instanceof final RenderableNavigationTarget rnti) {
            state.customRenderer = rnti;
            rnti.extractInNavTable(heldItem, navBE, navState, partialTicks, this.itemModelResolver, state);
        } else {
            final ItemStackRenderState item = new ItemStackRenderState();
            this.itemModelResolver.updateForTopItem(item, heldItem, net.minecraft.world.item.ItemDisplayContext.FIXED, navBE.getLevel(), null, 0);
            state.heldItem = item;
            state.rotateWithArrow = heldItem.is(SimTags.Items.ROTATE_WITH_NAV_ARROW);
        }

        state.blockItem = state.heldItem != null && state.heldItem.usesBlockLight();
    }

    @Override
    protected void submitSafe(final NavTableRenderState state, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(state, ms, queue, camera);

        if (state.facing == null)
            return;

        // Begin pose stack manipulation
        TransformStack.of(ms)
                .pushPose().center()
                .rotate(state.facing.getRotation());

        // Render Redstone Indicators
        if (!state.indicators.isEmpty()) {
            ms.pushPose();
            ms.translate(0, -0.5, 0);
            for (final SuperByteBufferRenderState indicator : state.indicators)
                indicator.submit(ms, RenderTypes.cutoutMovingBlock(), queue);
            ms.popPose();
        }

        // Render Pointer
        if (state.pointer != null) {
            ms.pushPose();
            ms.translate(0, 0.3, 0);
            state.pointer.submit(ms, RenderTypes.cutoutMovingBlock(), queue);
            ms.popPose();
        }

        // Render Nav table's item
        ms.pushPose();
        ms.translate(0, 0.3, 0);
        TransformStack.of(ms)
                .translate(0, state.blockItem ? 0.25f : 0.15f, 0)
                .rotate((float) Math.toRadians(90f), Direction.WEST)
                .scale(state.blockItem ? 0.5f : 0.375f);

        if (state.customRenderer != null) {
            state.customRenderer.submitInNavTable(state, ms, queue);
        } else if (state.heldItem != null) {
            if (state.rotateWithArrow)
                ms.mulPose(Axis.ZP.rotation(state.arrowAngle));
            state.heldItem.submit(ms, queue, state.lightCoords, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
        }
        ms.popPose();

        ms.popPose();
    }
}

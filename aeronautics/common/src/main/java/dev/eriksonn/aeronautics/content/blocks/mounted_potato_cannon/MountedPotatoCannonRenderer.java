package dev.eriksonn.aeronautics.content.blocks.mounted_potato_cannon;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer.FilterRenderState;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.eriksonn.aeronautics.index.AeroPartialModels;
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
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * <h2>26.2 note</h2>
 * <p>The barrel and bellows are placed entirely by buffer transforms, so they bake during extraction
 * and the submit is a flat replay. The loaded item is not: it is scaled and turned on the pose, and
 * where along the barrel it sits comes off the block entity -- so that offset is measured during
 * extraction and the pose walk stays in submission.
 *
 * <p>{@code ItemRenderer.renderStatic} went with {@code MultiBufferSource}. An item is resolved into
 * an {@link ItemStackRenderState} while the level is readable and submitted from it.
 *
 * <p>{@code renderRotatingKineticBlock} is gone; the shaft it drew is what
 * {@code extractRotatingKineticBlock} produces.
 */
public class MountedPotatoCannonRenderer
		extends SafeBlockEntityRenderer<MountedPotatoCannonBlockEntity, MountedPotatoCannonRenderer.MountedPotatoCannonRenderState> {

	public static class MountedPotatoCannonRenderState extends SafeRenderState {
		public @Nullable FilterRenderState filter;
		public final List<SuperByteBufferRenderState> parts = new ArrayList<>();

		public @Nullable ItemStackRenderState item;
		public @Nullable Direction facing;
		public float itemPosition;
		public @Nullable Quaternionf itemRotation;
	}

	private final ItemModelResolver itemModelResolver;

	public MountedPotatoCannonRenderer(final BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
	}

	@Override
	public MountedPotatoCannonRenderState createRenderState() {
		return new MountedPotatoCannonRenderState();
	}

	@Override
	protected void extractSafe(final MountedPotatoCannonBlockEntity be, final MountedPotatoCannonRenderState state, final float partialTicks, final Vec3 cameraPosition) {
		state.filter = FilteringRenderer.getFilterRenderState(be, this.itemModelResolver, cameraPosition);
		this.extractComponents(be, partialTicks, state);
		this.extractItem(be, partialTicks, state);
	}

	@Override
	protected void submitSafe(final MountedPotatoCannonRenderState state, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
		if (state.filter != null)
			state.filter.submit(state.blockState, queue, ms, state.lightCoords);

		for (final SuperByteBufferRenderState part : state.parts)
			part.submit(ms, RenderTypes.cutoutMovingBlock(), queue);

		if (state.item == null || state.facing == null || state.itemRotation == null)
			return;

		final TransformStack<PoseTransformStack> msr = TransformStack.of(ms);
		ms.pushPose();
		msr.center();

		final Vec3i facingVec = state.facing.getUnitVec3i();
		final float itemScale = 0.35f;

		ms.translate(facingVec.getX() * state.itemPosition, state.facing.getStepY() * state.itemPosition, facingVec.getZ() * state.itemPosition);
		ms.scale(itemScale, itemScale, itemScale);
		msr.rotate(state.itemRotation);
		state.item.submit(ms, queue, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		ms.popPose();
	}

	private void extractComponents(final MountedPotatoCannonBlockEntity be, final float partialTicks, final MountedPotatoCannonRenderState state) {
		state.parts.clear();

		final int light = state.lightCoords;
		final boolean drawParts = !VisualizationManager.supportsVisualization(be.getLevel());
		if (drawParts) {
			state.parts.add(KineticBlockEntityRenderer.extractRotatingKineticBlock(be, this.getRenderedBlockState(be), light));
		}

		final BlockState blockState = be.getBlockState();

		//TODO Don't make this jank
		final float barrelOffset = !be.isBlocked() ? be.getBarrelDistance(partialTicks) : (float) -(be.getBlockedLength() / 2);
		final float bellowOffset = -be.getBellowDistance(partialTicks);

		final SuperByteBuffer barrel = CachedBufferer.partial(AeroPartialModels.CANNON_BARREL, blockState);
		transform(barrel, blockState, true)
				.translate(0, 0, barrelOffset)
				.light(light);
		state.parts.add(barrel.extractRenderState());

		// Two bellows from one buffer: extractRenderState resets it between them, exactly as
		// renderInto did.
		final SuperByteBuffer bellow = CachedBufferer.partial(AeroPartialModels.CANNON_BELLOW, blockState);
		transform(bellow, blockState, true)
				.translate(0, bellowOffset, 0)
				.light(light);
		state.parts.add(bellow.extractRenderState());

		transform(bellow, blockState, true)
				.rotateCentered((float) (Math.PI), Direction.SOUTH)
				.light(light)
				.translate(0, bellowOffset, 0);
		state.parts.add(bellow.extractRenderState());

		if (drawParts) {
			final SuperByteBuffer cogwheel = CachedBufferer.partial(AeroPartialModels.CANNON_COG, blockState);
			final float angle = be.getCogwheelAngle(partialTicks);
			transform(cogwheel, blockState, true)
					.rotateCentered(Mth.DEG_TO_RAD * (angle % 360), Direction.SOUTH)
					.light(light);
			state.parts.add(cogwheel.extractRenderState());
		}
	}

	private static SuperByteBuffer transform(final SuperByteBuffer buffer, final BlockState state, final boolean axisDirectionMatters) {
		final Direction facing = state.getValue(BlockStateProperties.FACING);

		final float zRotLast = axisDirectionMatters && (state.getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE) ^ facing.getAxis() == Direction.Axis.Z) ? 90 : 0;
		final float yRot = AngleHelper.horizontalAngle(facing);
		final float zRot = facing == Direction.UP ? (float) 90 : facing == Direction.DOWN ? 90 : 0;
		final float zRotSecondLast = facing == Direction.UP ? (float) 180 : 0;

		buffer.rotateCentered((float) ((zRot) / 180 * Math.PI), Direction.SOUTH);
		buffer.rotateCentered((float) ((zRot) / 180 * Math.PI), Direction.DOWN);
		buffer.rotateCentered((float) ((yRot) / 180 * Math.PI), Direction.UP);
		buffer.rotateCentered((float) ((zRotLast) / 180 * Math.PI), Direction.SOUTH);
		buffer.rotateCentered((float) ((zRotSecondLast) / 180 * Math.PI), Direction.UP);

		return buffer;
	}

	private BlockState getRenderedBlockState(final MountedPotatoCannonBlockEntity te) {
		return KineticBlockEntityRenderer.shaft(KineticBlockEntityRenderer.getRotationAxisOf(te));
	}

	public void extractItem(final MountedPotatoCannonBlockEntity be, final float partialTicks, final MountedPotatoCannonRenderState state) {
		// Reused between frames, so an emptied cannon has to clear its item.
		state.item = null;
		if (be.getInventory().isEmpty()) {
			return;
		}

		state.facing = be.getBlockState().getValue(BlockStateProperties.FACING);

		final float normalizedTimer = be.getItemTime(partialTicks);
		float itemPosition = !be.isBlocked() ? 1 - (float) Math.exp(-0.25f * normalizedTimer) : 0;
		state.itemPosition = itemPosition * 0.8f;

		final int itemRotationId = be.getItemRotationId();
		final Quaternionf Q = new Quaternionf((float) Math.sin(itemRotationId * 0.4f), (float) Math.cos(itemRotationId * 1.4f), (float) Math.sin(itemRotationId * 3.0f), (float) Math.cos(itemRotationId * 5.0f));
		Q.normalize();
		state.itemRotation = Q;

		final ItemStackRenderState item = new ItemStackRenderState();
		this.itemModelResolver.updateForTopItem(item, be.getInventory().slot.getStack(), ItemDisplayContext.FIXED, be.getLevel(), null, 0);
		state.item = item;
	}
}

package dev.simulated_team.simulated.content.entities.diagram;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.simulated_team.simulated.index.SimPartialModels;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * <h2>26.2 note</h2>
 * <p>Split across extract/submit. Everything shaping the diagram -- its size, its pitch, the yaw it
 * is turned to -- is read off the entity, so the buffer is baked during extraction and submission
 * only plays it back.
 *
 * <p>The yaw the old {@code render} was handed as a parameter is the entity's own body rotation, so
 * it is read from the entity rather than passed in.
 */
public class DiagramEntityRenderer
	extends EntityRenderer<DiagramEntity, DiagramEntityRenderer.DiagramRenderState> {

	public static class DiagramRenderState extends EntityRenderState {
		public @Nullable SuperByteBufferRenderState diagram;
	}

	public DiagramEntityRenderer(final EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public DiagramRenderState createRenderState() {
		return new DiagramRenderState();
	}

	@Override
	public void extractRenderState(final DiagramEntity entity, final DiagramRenderState state, final float pt) {
		super.extractRenderState(entity, state, pt);

		final PartialModel partialModel = entity.size == 3 ? SimPartialModels.CONTRAPTION_DIAGRAM_3x3
			: entity.size == 2 ? SimPartialModels.CONTRAPTION_DIAGRAM_2x2 : SimPartialModels.CONTRAPTION_DIAGRAM_1x1;
		final SuperByteBuffer sbb = CachedBufferer.partial(partialModel, Blocks.AIR.defaultBlockState());
		sbb.rotateYDegrees(-entity.getYRot())
			.rotateXDegrees(90.0F + entity.getXRot())
			.translate(-.5, -1 / 32f, -.5);
		if (entity.size == 2) {
			sbb.translate(.5, 0, -.5);
		}

		state.diagram = sbb.disableDiffuse()
			.light(state.lightCoords)
			.extractRenderState();
	}

	@Override
	public void submit(final DiagramRenderState state, final PoseStack ms, final SubmitNodeCollector queue,
		final CameraRenderState camera) {
		super.submit(state, ms, queue, camera);

		if (state.diagram != null)
			state.diagram.submit(ms, RenderTypes.solidMovingBlock(), queue);
	}

}

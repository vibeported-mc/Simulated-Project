package dev.simulated_team.simulated.content.entities.honey_glue;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * <h2>26.2 note</h2>
 * <p>This renderer draws nothing -- honey glue is invisible, and {@code shouldRender} says so. It
 * exists only because an entity type must name one.
 *
 * <p>{@code EntityRenderer} is parameterised on its render state as well as its entity now, and
 * {@code getTextureLocation} is gone with the buffer source that consumed it: a renderer names its
 * textures through the render types it submits to.
 */
public class HoneyGlueRenderer extends EntityRenderer<HoneyGlueEntity, EntityRenderState> {

	public HoneyGlueRenderer(final EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}

	@Override
	public void submit(final EntityRenderState state, final PoseStack ms, final SubmitNodeCollector queue,
		final CameraRenderState camera) {
	}

	@Override
	public boolean shouldRender(final HoneyGlueEntity entity, final Frustum frustum, final double x, final double y, final double z) {
		return false;
	}

}

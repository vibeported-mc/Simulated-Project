package dev.eriksonn.aeronautics.content.particle;

import dev.ryanhcode.sable.api.particle.ParticleSubLevelKickable;
import dev.ryanhcode.sable.mixinterface.particle.ParticleExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.RandomSource;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;

public class GustParticle extends SingleQuadParticle implements ParticleSubLevelKickable {

	private static final float FPS = 16.0f;
	private static final float FRAMES = 8.0f;

	private final Quaternionf orientation;
	private final Quaternionf renderOrientation = new Quaternionf();
	private final Quaternionf subLevelOrientation = new Quaternionf();
	private final Quaternionf backFace = new Quaternionf();

	protected GustParticle(final ClientLevel level, final double x, final double y, final double z, final Quaternionf orientation) {
		super(level, x, y, z, null);
		this.orientation = orientation.normalize();
		this.quadSize = 2;
		this.lifetime = (int) (FRAMES / FPS * 20) - 1;
		this.alpha = 0.25f;
	}

	/**
	 * <h2>26.2 note</h2>
	 * <p>Particles extract into a render state rather than drawing into a buffer. The orientation
	 * work is unchanged -- this particle faces the way the gust blows rather than the camera, so it
	 * still composes its own quaternion instead of taking the camera's.
	 *
	 * <p>The quad is added twice. The old code emitted its eight vertices by hand in two winding
	 * orders to get a double-sided quad; a render state adds whole quads, and the particle pipeline
	 * culls back faces, so the second face is the same quad turned to face the other way. A
	 * genuinely double-sided quad looks mirrored from behind, which is what the half-turn produces.
	 */
	@Override
	public void extract(final QuadParticleRenderState renderState, final Camera camera, final float partialTicks) {
		this.renderOrientation.set(this.orientation);

		final ParticleExtension extension = (ParticleExtension) this;
		if (extension.sable$getTrackingSubLevel() instanceof final ClientSubLevel subLevel) {
			final Quaterniondc orientation1 = subLevel.renderPose().orientation();
			this.renderOrientation.premul(this.subLevelOrientation.set(orientation1));
		}

		this.extractRotatedQuad(renderState, camera, this.renderOrientation, partialTicks);
		this.extractRotatedQuad(renderState, camera, this.backFace.set(this.renderOrientation).rotateY((float) Math.PI), partialTicks);
	}

	@Override
	protected float getU0() {
		float offset = getFrameOffset(this.getFrame());
		return super.getU0() + offset;
	}

	@Override
	protected float getU1() {
		float offset = getFrameOffset((this.getFrame() + 1));
		return super.getU0() + offset;
	}

	private float getFrameOffset(int frame) {
		float width = this.sprite.getU1() - this.sprite.getU0();
		float frameWidth = width / FRAMES;
		return frameWidth * frame;
	}

	private int getFrame() {
		final float age = this.age / 20f;
		return (int) (age * FPS);
	}

	@Override
	public void tick() {
		super.tick();
	}

	/**
	 * <h2>26.2 note</h2>
	 * <p>A particle names the layer it belongs in; the group it batches with comes from the base
	 * class, and ParticleRenderType's sheet constants are gone.
	 */
	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	@Override
	public boolean sable$shouldKickFromTracking() {
		return false;
	}

	@Override
	public boolean sable$shouldCollideWithTrackingSubLevel() {
		return false;
	}

	public static class Factory implements ParticleProvider<GustParticleData> {
		private final SpriteSet spriteSet;

		public Factory(final SpriteSet animatedSprite) {
			this.spriteSet = animatedSprite;
		}

		@Override
		public Particle createParticle(final GustParticleData data, final ClientLevel worldIn, final double x, final double y, final double z,
									   final double xSpeed, final double ySpeed, final double zSpeed, final RandomSource random) {
			final GustParticle particle = new GustParticle(worldIn, x, y, z, data.orientation());
			particle.setSprite(this.spriteSet.get(random));
			return particle;
		}

	}
}

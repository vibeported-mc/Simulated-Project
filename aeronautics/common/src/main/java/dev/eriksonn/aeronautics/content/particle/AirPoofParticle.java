package dev.eriksonn.aeronautics.content.particle;

import dev.ryanhcode.sable.api.particle.ParticleSubLevelKickable;
import net.minecraft.util.RandomSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;

public class AirPoofParticle extends SingleQuadParticle implements ParticleSubLevelKickable {
    protected AirPoofParticle(final ClientLevel level, final double x, final double y, final double z, final double xSpeed, final double ySpeed, final double zSpeed) {
        // 26.2: SingleQuadParticle always takes a sprite; the factory sets the real one.
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, null);
        this.alpha = level.getRandom().nextFloat() * 0.2f + 0.3f;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
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

    public record Factory(SpriteSet spriteSet) implements ParticleProvider<AirPoofParticleData> {
        @Override
        public Particle createParticle(final AirPoofParticleData data, final ClientLevel worldIn, final double x, final double y, final double z,
                                       final double xSpeed, final double ySpeed, final double zSpeed, final RandomSource random) {
            final AirPoofParticle particle = new AirPoofParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed);
            // 26.2: pickSprite is gone -- a sprite is chosen from the set and set directly.
            particle.setSprite(this.spriteSet.get(random));
            return particle;
        }
    }
}

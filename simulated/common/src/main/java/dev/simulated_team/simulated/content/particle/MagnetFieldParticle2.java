package dev.simulated_team.simulated.content.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class MagnetFieldParticle2 extends SimpleAnimatedParticle {

    protected int timeUntilEnd;
    protected MagnetFieldParticle2(final ClientLevel world, final double x, final double y, final double z,
                                   final double prevX, final double prevY, final double prevZ,
                                   final double nextX, final double nextY, final double nextZ,
                                   final SpriteSet sprite, final boolean negative,final int timeUntilEnd) {
        super(world, x, y, z, sprite, world.getRandom().nextFloat() * .5f);
        this.hasPhysics = false;
        this.lifetime = 5;

        //first control point, relative to center
        this.xo = prevX;
        this.yo = prevY;
        this.zo = prevZ;

        //center control point
        this.x = x;
        this.y = y;
        this.z = z;

        //last control point, relative to center
        this.xd = nextX;
        this.yd = nextY;
        this.zd = nextZ;

        this.timeUntilEnd = timeUntilEnd;

        this.selectSprite(0);
        this.setAlpha(0.4f);
        if(negative)
            this.setColor(0.7f,0.7f,1);
        else
            this.setColor(1,0.7f,0.7f);
    }

    /**
     * <h2>26.2 note</h2>
     * <p>A particle names the layer it belongs in rather than a render type; the group it batches
     * with comes from the base class.
     */
    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    private void dissipate() {
        this.remove();
    }


    /**
     * <h2>26.2 note</h2>
     * <p>Particles are extracted into a render state rather than drawn into a buffer, so this builds
     * the same rotation and hands the quad to {@code extractRotatedQuad}.
     *
     * <p>The camera position now comes from {@code camera.position()}, and the quad is placed
     * relative to it exactly as before -- this particle deliberately does not interpolate its own
     * position the way the base class does, because it is drawn stretched along its velocity.
     */
    @Override
    public void extract(final QuadParticleRenderState renderState, final Camera camera, final float partialTicks) {
        final Quaternionf quaternionf = new Quaternionf();

        final Vec3 vec3 = camera.position();
        final float x = (float) (this.x - vec3.x());
        final float y = (float) (this.y - vec3.y());
        final float z = (float) (this.z - vec3.z());

        final float dirX = (float) Mth.lerp(partialTicks, this.xo, this.xd);
        final float dirY = (float) Mth.lerp(partialTicks, this.yo, this.yd);
        final float dirZ = (float) Mth.lerp(partialTicks, this.zo, this.zd);

        final float offsetX = (float) Mth.lerp(partialTicks, -this.xo, this.xd) * 0.5f;
        final float offsetY = (float) Mth.lerp(partialTicks, -this.yo, this.yd) * 0.5f;
        final float offsetZ = (float) Mth.lerp(partialTicks, -this.zo, this.zd) * 0.5f;

        quaternionf.identity();
        quaternionf.lookAlong(new Vector3f(dirX, dirY, dirZ), new Vector3f(x, y, z)).conjugate();
        quaternionf.rotateX((float) (Math.PI / 2.0));

        this.extractRotatedQuad(renderState, quaternionf, x + offsetX, y + offsetY, z + offsetZ, partialTicks);
    }

    @Override
    public float getQuadSize(final float scaleFactor) {
        final float x = (float)Mth.lerp(scaleFactor,this.xo,this.xd);
        final float y = (float)Mth.lerp(scaleFactor,this.yo,this.yd);
        final float z = (float)Mth.lerp(scaleFactor,this.zo,this.zd);
        return (float)Mth.length(x,y,z)*0.5f;
    }

    @Override
    public void tick() {

        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        this.selectSprite(this.age +1);
    }

    @Override
    public int getLightCoords(final float partialTick) {
        final BlockPos blockpos = BlockPos.containing(this.x, this.y, this.z);
        return this.level.hasChunkAt(blockpos) ? LightCoordsUtil.getLightCoords(this.level, blockpos) : 0;
    }

    private void selectSprite(final int index) {
        final int n = 6;

        final int clampedIndex = 2*index < n ? Math.min(index, this.timeUntilEnd):Math.max(index,n- this.timeUntilEnd +1);

        this.setSprite(this.sprites.get(clampedIndex, n));
    }

    public static class Factory implements ParticleProvider<MagnetFieldParticleData2> {
        private final SpriteSet spriteSet;

        public Factory(final SpriteSet animatedSprite) {
            this.spriteSet = animatedSprite;
        }

        @Override
        public Particle createParticle(final MagnetFieldParticleData2 data, final ClientLevel level, final double x, final double y, final double z,
                                       final double xSpeed, final double ySpeed, final double zSpeed, final RandomSource random) {
            return new MagnetFieldParticle2(level, x, y, z, data.previousOffset.x,data.previousOffset.y,data.previousOffset.z,data.nextOffset.x,data.nextOffset.y,data.nextOffset.z, this.spriteSet, data.isNegative(),data.getTimeUntilEnd());
        }
    }
}

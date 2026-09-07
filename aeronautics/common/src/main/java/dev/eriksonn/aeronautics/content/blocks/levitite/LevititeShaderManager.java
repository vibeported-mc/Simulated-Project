package dev.eriksonn.aeronautics.content.blocks.levitite;

import dev.eriksonn.aeronautics.index.AeroBlocks;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockMaterial;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.util.SableMathUtils;
import foundry.veil.api.client.render.VeilRenderSystem;
import net.minecraft.client.Minecraft;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.uniform.ShaderUniform;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class LevititeShaderManager {
    private static final double SMOOTHING_SPEED = 0.5;

    private static final Vector3d linearVelocity = new Vector3d();
    private static final Vector3d angularVelocity = new Vector3d();
    private static final Vector3d temp = new Vector3d();
    private static final Vector3d currentPos = new Vector3d();
    private static final Quaterniond currentOrientation = new Quaterniond();
    private static final Vector3d offset = new Vector3d();
    private static final Matrix3f matrix = new Matrix3f();
    private static final Vector3d gravityVector1 = new Vector3d();
    private static final Vector3f gravityVector2 = new Vector3f();
    private static boolean enabled = false;

    public static HashMap<ClientSubLevel, LevititeShaderManager> managers = new HashMap<>();

    private final Vector3d smoothedLinearVelocity = new Vector3d();
    private final Vector3d lastSmoothedLinearVelocity = new Vector3d();
    private final Vector3d smoothedAngularVelocity = new Vector3d();
    private final Vector3d lastSmoothedAngularVelocity = new Vector3d();
    private final Vector3d accumulatedPosition = new Vector3d();

    public static void tick() {
        if (managers.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<ClientSubLevel, LevititeShaderManager>> iterator = managers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ClientSubLevel, LevititeShaderManager> entry = iterator.next();

            ClientSubLevel subLevel = entry.getKey();
            if (subLevel.isRemoved()) {
                iterator.remove();
                continue;
            }

            entry.getValue().internalTick(subLevel);
        }
    }

    public static LevititeShaderManager getInstance(ClientSubLevel subLevel) {
        managers.putIfAbsent(subLevel, new LevititeShaderManager());
        return managers.get(subLevel);
    }

    public static void disableShader() {
        enabled = false;
    }

    public static void prepareShaderForWorld(ShaderProgram shader, double camX, double camY, double camZ) {
        camX = camX % 10000;
        camY = camY % 10000;
        camZ = camZ % 10000;
        setMaterialProperties(shader);
        setVector(shader, "offset", -(float) camX, -(float) camY, -(float) camZ);
        setMatrix(shader, "currentOrientation", matrix.identity());
        setVector(shader, "sublevelPosition", 0f, 0f, 0f);
        setVector(shader, "linearVelocity", 0f, 0f, 0f);
        setVector(shader, "angularVelocity", 0f, 0f, 0f);
        setInt(shader, "onSublevel", 0);
        setFloat(shader, "gravityStrength", 0);
        enabled = true;
    }

    public static void setMaterialProperties(ShaderProgram shader) {
        FloatingBlockMaterial material = PhysicsBlockPropertyHelper.getFloatingMaterial(AeroBlocks.LEVITITE.getDefaultState());
        if (material == null)
            return;
        setFloat(shader, "materialTransitionSpeed", (float) material.transitionSpeed());
        setMatrix(shader, "materialMatrixSlow", getGravityMatrix(gravityVector2, (float) material.slowVerticalFriction(), (float) material.slowHorizontalFriction(), matrix));
        setMatrix(shader, "materialMatrixFast", getGravityMatrix(gravityVector2, (float) material.fastVerticalFriction(), (float) material.fastHorizontalFriction(), matrix));
    }

    private static Matrix3f getGravityMatrix(final Vector3f g, final float verticalDrag, final float horizontalDrag, Matrix3f target) {
        if (g.lengthSquared() > 0.00001) {
            float scale = (horizontalDrag - verticalDrag) / g.dot(g);
            target.m00 = g.x() * g.x() * scale;
            target.m01 = g.y() * g.x() * scale;
            target.m02 = g.z() * g.x() * scale;
            target.m10 = g.x() * g.y() * scale;
            target.m11 = g.y() * g.y() * scale;
            target.m12 = g.z() * g.y() * scale;
            target.m20 = g.x() * g.z() * scale;
            target.m21 = g.y() * g.z() * scale;
            target.m22 = g.z() * g.z() * scale;
        } else
            target.identity();
        target.m00 -= horizontalDrag;
        target.m11 -= horizontalDrag;
        target.m22 -= horizontalDrag;
        return target;
    }

    void internalTick(ClientSubLevel subLevel) {
        lastSmoothedLinearVelocity.set(smoothedLinearVelocity);
        lastSmoothedAngularVelocity.set(smoothedAngularVelocity);

        subLevel.logicalPose().position().sub(subLevel.lastPose().position(), linearVelocity);
        subLevel.logicalPose().rotationPoint().sub(subLevel.lastPose().rotationPoint(), temp);
        DimensionPhysicsData.getGravity(subLevel.getLevel(), subLevel.logicalPose().position(), gravityVector1);
        subLevel.logicalPose().orientation().transform(temp);
        linearVelocity.sub(temp);
        SableMathUtils.getAngularVelocity(subLevel.lastPose().orientation(), subLevel.logicalPose().orientation(), angularVelocity);

        smoothedLinearVelocity.lerp(linearVelocity, SMOOTHING_SPEED);
        smoothedAngularVelocity.lerp(angularVelocity, SMOOTHING_SPEED);
        accumulatedPosition.add(smoothedLinearVelocity);
        accumulatedPosition.set(
                (accumulatedPosition.x % 10000),
                (accumulatedPosition.y % 10000),
                (accumulatedPosition.z % 10000));

    }

    public boolean needsLayers() {
        return (smoothedAngularVelocity.lengthSquared() > 1E-6 || smoothedLinearVelocity.lengthSquared() > 1E-6) && gravityVector1.lengthSquared() > 0.001;
    }

    public void prepareShaderForSublevel(ClientSubLevel subLevel, ShaderProgram shader, double camX, double camY, double camZ) {
        final float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);

        Pose3dc currentPose = subLevel.renderPose(pt);
        currentPos.set(currentPose.position());
        currentOrientation.set(currentPose.orientation());
        currentPos.sub(camX, camY, camZ, offset);

        lastSmoothedLinearVelocity.lerp(smoothedLinearVelocity, pt, linearVelocity);
        lastSmoothedAngularVelocity.lerp(smoothedAngularVelocity, pt, angularVelocity);

        currentOrientation.transformInverse(offset);
        currentOrientation.transformInverse(linearVelocity);
        currentOrientation.transformInverse(angularVelocity);
        currentOrientation.transformInverse(gravityVector1);
        gravityVector2.set(gravityVector1);

        setVector(shader, "offset", (float) offset.x, (float) offset.y, (float) offset.z);
        setVector(shader, "linearVelocity", (float) linearVelocity.x * 20, (float) linearVelocity.y * 20, (float) linearVelocity.z * 20);
        setVector(shader, "angularVelocity", (float) angularVelocity.x * 20, (float) angularVelocity.y * 20, (float) angularVelocity.z * 20);
        setVector(shader, "sublevelPosition", (float) currentPos.x % 10000, (float) currentPos.y % 10000, (float) currentPos.z % 10000);
        setMatrix(shader, "currentOrientation", matrix.set(currentOrientation));
        setInt(shader, "onSublevel", 1);
        setFloat(shader, "gravityStrength", (float) gravityVector1.length());
    }

    /**
     * <h2>26.2 note</h2>
     * <p>{@code ShaderInstance} is gone, and with it {@code safeGetUniform}, which handed back a
     * dummy uniform when a shader did not declare one. Veil's {@code ShaderProgram} returns null
     * instead, so the null check that made the old call safe lives here rather than inside the
     * engine.
     */
    private static void setVector(final ShaderProgram shader, final String name, final float x, final float y, final float z) {
        final ShaderUniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.setVector(x, y, z);
        }
    }

    private static void setFloat(final ShaderProgram shader, final String name, final float value) {
        final ShaderUniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.setFloat(value);
        }
    }

    private static void setInt(final ShaderProgram shader, final String name, final int value) {
        final ShaderUniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.setInt(value);
        }
    }

    private static void setMatrix(final ShaderProgram shader, final String name, final Matrix3fc value) {
        final ShaderUniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.setMatrix(value);
        }
    }

    public static boolean isEnabled() {
        return VeilRenderSystem.tessellationSupported() && enabled;
    }
}

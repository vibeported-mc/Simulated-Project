package dev.simulated_team.simulated.content.end_sea;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.void_anchor.VoidAnchorBlockEntity;
import dev.simulated_team.simulated.util.SimpleSubLevelGroupRenderer;
import foundry.veil.api.client.render.CachedBufferSource;
import foundry.veil.api.client.render.MatrixStack;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.lang.Math;

/**
 * <h2>26.2 note — the shadow map itself is parked</h2>
 *
 * <p>This drew every sub-level in the level into an off-screen depth buffer, from below, so the sea
 * could cast their shadows onto itself. The draw went through
 * {@link SimpleSubLevelGroupRenderer#renderGroup}, which cannot be ported — see
 * {@code SIMULATED-26.2-OPEN-QUESTIONS.md} §1.
 *
 * <p>Everything <em>around</em> that draw is kept, deliberately. The shadow camera position is still
 * computed and still published through {@link #getLastRenderOrigin}, because {@code EndSeaRenderer}
 * builds the sea's UVs from it and would drift without it; the framebuffer is still bound and
 * cleared, so the sea samples an empty shadow texture rather than a stale one. The sea therefore
 * draws correctly, with no shadows in it.
 */
public class EndSeaShadowRenderer {
    public static final float SHADOW_VOLUME_RADIUS = 256f / 2f;
    private static final Matrix4f PROJECTION_MAT = new Matrix4f();
    private static final Vector3d SHADOW_CAMERA_POSITION = new Vector3d();
    private static boolean isRenderingShadowMap = false;
    private static final ObjectArrayList<Vector3dc> voidAnchors = new ObjectArrayList<>();

    public static boolean isEnabled() {
        return true;
    }

    public static void renderShadowMap(final VeilRenderLevelStageEvent.Stage stage, final LevelRenderer levelRenderer, final CachedBufferSource bufferSource, final MatrixStack matrixStack, final Matrix4fc frustumMatrix, final Matrix4fc projectionMatrix, final int renderTick, final DeltaTracker deltaTracker, final Camera camera, final Frustum frustum) {
        if (!EndSeaShadowRenderer.isEnabled() ||
                stage != VeilRenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        final EndSeaPhysics physics = EndSeaPhysicsData.of(level);

        if (physics == null) {
            return;
        }

        final AdvancedFbo fbo = getShadowsFramebuffer();
        if (fbo == null) {
            return;
        }

        final float zNear = 0.5f;

        final Matrix4f modelView = new Matrix4f();
        PROJECTION_MAT.identity().ortho(-SHADOW_VOLUME_RADIUS, SHADOW_VOLUME_RADIUS, -SHADOW_VOLUME_RADIUS, SHADOW_VOLUME_RADIUS, zNear, SHADOW_VOLUME_RADIUS);

        // account for the smaller screen size
        final Vec3 cameraPosition = camera.position();
        final Vec3 shadowCameraPosition = new Vec3(cameraPosition.x, physics.startY() - SHADOW_VOLUME_RADIUS, cameraPosition.z);

        SHADOW_CAMERA_POSITION.set(JOMLConversion.toJOML(shadowCameraPosition));
        SHADOW_CAMERA_POSITION.set(Math.floor(SHADOW_CAMERA_POSITION.x), SHADOW_CAMERA_POSITION.y, Math.floor(SHADOW_CAMERA_POSITION.z));
        isRenderingShadowMap = true;

        final Quaternionf orientation = new Quaternionf().rotateX(Mth.DEG_TO_RAD * -90);

        fbo.bind(true);
        fbo.clear();
        // Parked: the sub-level group render that filled this buffer. The buffer is still bound and
        // cleared so the sea samples an empty shadow map rather than last frame's.
        isRenderingShadowMap = false;

        final PostProcessingManager post = VeilRenderSystem.renderer().getPostProcessingManager();
        final PostPipeline pipeline = post.getPipeline(Simulated.path("spread_end_sea"));
        if (pipeline != null) {
            for (int i = 0; i < 5; i++) {
                post.runPipeline(pipeline, false);
            }
        }
    }

    /**
     * <h2>26.2 note</h2>
     * <p>Parked, and it was already unreachable: nothing has called this since before the port, so
     * the crack quads over void anchors were not being drawn on 1.21.1 either. It drew through
     * {@code Tesselator} plus {@code BufferUploader.drawWithShader} and a {@code ShaderInstance},
     * none of which exist. Restoring it means writing the quads into the buffer source this stage
     * hands over, the way the physics staff's beam does.
     *
     * <p>The list is still drained, so anchors registered each frame by {@code VoidAnchorRenderer}
     * do not accumulate.
     */
    public static void renderVoidAnchors(final Camera camera) {
        voidAnchors.clear();
    }

    public static @Nullable AdvancedFbo getShadowsFramebuffer() {
        return VeilRenderSystem.renderer().getFramebufferManager().getFramebuffer(Simulated.path("end_sea_shadows"));
    }

    public static boolean renderingShadowMap() {
        return isRenderingShadowMap;
    }

    public static Vector3dc getLastRenderOrigin() {
        return SHADOW_CAMERA_POSITION;
    }

    public static void addVoidAnchor(final VoidAnchorBlockEntity voidAnchor) {
        voidAnchors.add(Sable.HELPER.projectOutOfSubLevel(voidAnchor.getLevel(), JOMLConversion.atCenterOf(voidAnchor.getBlockPos())));
    }
}

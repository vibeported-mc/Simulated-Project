package dev.simulated_team.simulated.util.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.state.GameRenderState;
import org.joml.Matrix4f;

/**
 * <h2>26.2 note</h2>
 * <p>{@code RenderSystem.getProjectionMatrix()} and {@code GameRenderer.getProjectionMatrix(fov)} are
 * both gone. Drawing is deferred now, so there is no single "current" projection to ask for -- the
 * matrix in force depends on which pass is replaying, and by the time anything is drawn the code that
 * wanted to know has long returned.
 *
 * <p>Both projections this mod needs are still reachable, just not as a getter:
 *
 * <ul>
 *   <li>The level projection is recovered from the camera. It hands out {@code P * V} and {@code V},
 *       so {@code (P * V) * V^-1} is {@code P} exactly -- no guess at aspect ratio or clip planes.
 *   <li>The hand projection is rebuilt the way {@code GameRenderer} builds it before drawing the item
 *       in hand: a perspective at the HUD field of view over the window, near 0.05 and far 100. Every
 *       input is public on the frame's render state.
 * </ul>
 */
public final class ProjectionUtil {

    private static final Projection HAND_PROJECTION = new Projection();

    private ProjectionUtil() {
    }

    /** The projection the level is being drawn with this frame. */
    public static Matrix4f levelProjection(final Camera camera) {
        final Matrix4f inverseView = camera.getViewRotationMatrix(new Matrix4f()).invert();
        return camera.getViewRotationProjectionMatrix(new Matrix4f()).mul(inverseView);
    }

    public static Matrix4f levelProjection() {
        return levelProjection(Minecraft.getInstance().gameRenderer.mainCamera());
    }

    /** The projection the held item is drawn with -- a narrower frustum than the level's. */
    public static Matrix4f handProjection() {
        final GameRenderState state = Minecraft.getInstance().gameRenderer.gameRenderState();
        HAND_PROJECTION.setupPerspective(0.05F, 100.0F,
                state.levelRenderState.cameraRenderState.hudFov,
                state.windowRenderState.width,
                state.windowRenderState.height);
        return HAND_PROJECTION.getMatrix(new Matrix4f());
    }
}

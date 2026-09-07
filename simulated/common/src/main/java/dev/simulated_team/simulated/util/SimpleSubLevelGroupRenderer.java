package dev.simulated_team.simulated.util;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.Collection;

/**
 * <h2>26.2 note — the rendering half of this class is parked</h2>
 *
 * <p>{@link #renderGroup} drew a chosen chain of sub-levels into an {@link AdvancedFbo} under a
 * chosen camera, by driving the chunk pass by hand: walking {@code RenderType.chunkBufferLayers()},
 * calling {@code setupRenderState}, fetching {@code RenderSystem.getShader()}, uploading its default
 * uniforms and applying it. Every one of those is gone in 26.2 — the layers are a closed
 * {@code ChunkSectionLayer} enum, {@code ShaderInstance} was deleted, and render state moved into
 * pipeline objects.
 *
 * <p>Underneath, Sable's default dispatcher no longer draws sub-level terrain itself: it contributes
 * the sections to <em>vanilla's own</em> chunk draw list, with the sub-level's pose folded into each
 * section's model-view uniform. That path belongs to the main frame and cannot be pointed at another
 * framebuffer, camera, or subset of sub-levels.
 *
 * <p>Two features rested on this — the contraption diagram and the End Sea's sky-light shadow map —
 * and both are parked with it. {@code SIMULATED-26.2-OPEN-QUESTIONS.md} records the three ways out.
 *
 * <p>{@link #getRenderedChain} is untouched. It is pure graph-walking with no rendering in it, and
 * callers that only want to know which sub-levels travel together still work.
 */
public class SimpleSubLevelGroupRenderer {

    /**
     * Read by the diagram's lighting override, which is itself parked — see
     * {@code SIMULATED-26.2-OPEN-QUESTIONS.md} §2. Kept so the flag's meaning survives for whoever
     * restores the feature.
     */
    public static boolean RENDERING_SIMPLE = false;

    /**
     * @return the chain of sub-levels that should render with a given sub-level into a diagram
     */
    public static Collection<ClientSubLevel> getRenderedChain(final ClientSubLevel subLevel) {
        final ObjectOpenHashSet<ClientSubLevel> visited = new ObjectOpenHashSet<>();
        final ObjectOpenHashSet<ClientSubLevel> frontier = new ObjectOpenHashSet<>();

        frontier.add(subLevel);

        while (!frontier.isEmpty()) {
            final ClientSubLevel current = frontier.iterator().next();

            frontier.remove(current);
            visited.add(current);

            final Iterable<SubLevel> intersecting = Sable.HELPER.getAllIntersecting(current.getLevel(), new BoundingBox3d(current.boundingBox()));

            // Intersecting dependencies
            for (final SubLevel neighbor : intersecting) {
                final ClientSubLevel serverNeighbor = (ClientSubLevel) neighbor;

                if (!visited.contains(serverNeighbor)) {
                    frontier.add(serverNeighbor);
                }
            }
        }

        return visited;
    }

    /** Parked — see the class javadoc. */
    public static void renderChain(final SubLevel subLevel, final AdvancedFbo fbo, final Matrix4f modelView, final Matrix4f projectionMat, final Vector3d cameraPosition, final Quaternionf orientation, final float partialTicks) {
    }

    /** Parked — see the class javadoc. */
    public static void renderGroup(final ClientLevel level, final Collection<ClientSubLevel> subLevels, final AdvancedFbo fbo, final Matrix4f modelView, final Matrix4f projectionMat, final Vector3d cameraPosition, final Quaternionf orientation, final float partialTicks, final boolean renderPlayers) {
    }
}

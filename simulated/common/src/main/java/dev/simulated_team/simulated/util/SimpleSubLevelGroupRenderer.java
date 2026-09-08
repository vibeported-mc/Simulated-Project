package dev.simulated_team.simulated.util;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.render.vanilla.SubLevelChunkDraws;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector4f;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws a chosen chain of sub-levels into an {@link AdvancedFbo} under a chosen camera. The
 * contraption diagram is built on it.
 *
 * <h2>26.2 note</h2>
 * <p>This was parked during the port and is restored here. What it used to do -- flip the global
 * framebuffer, walk {@code RenderType.chunkBufferLayers()}, fetch {@code RenderSystem.getShader()},
 * push uniforms, draw -- has no counterpart at all. 26.2 draws inside a {@link RenderPass} whose
 * destination is an argument rather than a global switch, so {@code glBindFramebuffer} redirects
 * nothing, and there is no shader object left to poke.
 *
 * <p>What replaces it is smaller than what it replaced. Sable's {@link SubLevelChunkDraws#collect}
 * hands back the chain's compiled geometry as vanilla draw commands for any camera and any subset --
 * that was always parameterised, only its caller was fixed -- and {@link #renderLayerGroup} is
 * vanilla's own {@code ChunkSectionsToRender.renderGroup} with one thing changed.
 *
 * <p><b>Depth runs the other way in 26.2.</b> The device is set to {@code GL_ZERO_TO_ONE} and the
 * default depth test is {@code GREATER_THAN_OR_EQUAL}, so the far plane is 0 and the near plane is
 * 1. The projection handed in must be built with near and far swapped, the depth buffer must be
 * cleared to {@code 0.0}, and anything reading the depth back -- the outline shader does -- has to
 * test the same way round. Get one of those three wrong and the diagram is simply blank.
 */
public class SimpleSubLevelGroupRenderer {

    /**
     * True while a diagram is being drawn, for anything that wants to know it is rendering the
     * schematic rather than the world.
     */
    public static boolean RENDERING_SIMPLE = false;

    /**
     * The projection is uploaded through one of these rather than set as a matrix: 26.2 keeps it in
     * a uniform buffer, and {@code RenderSystem.setProjectionMatrix} wants the slice.
     */
    @Nullable
    private static ProjectionMatrixBuffer projectionBuffer;

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

    public static void renderChain(final SubLevel subLevel, final AdvancedFbo fbo, final Matrix4f modelView, final Matrix4f projectionMat, final Vector3d cameraPosition, final Quaternionf orientation, final float partialTicks) {
        final ClientSubLevel clientSubLevel = (ClientSubLevel) subLevel;
        final ClientLevel level = clientSubLevel.getLevel();

        renderGroup(level, getRenderedChain(clientSubLevel), fbo, modelView, projectionMat, cameraPosition, orientation, partialTicks, true);
    }

    /**
     * @param projectionMat the <em>render</em> projection, built with near and far swapped for
     *                      reversed-Z. The screen-space projection a caller uses for its own overlay
     *                      maths is a different matrix and must not be passed here.
     */
    public static void renderGroup(final ClientLevel level, final Collection<ClientSubLevel> subLevels, final AdvancedFbo fbo, final Matrix4f modelView, final Matrix4f projectionMat, final Vector3d cameraPosition, final Quaternionf orientation, final float partialTicks, final boolean renderPlayers) {
        if (subLevels.isEmpty()) {
            // The caller binds the framebuffer and leaves the unbinding to this method, on every
            // path out of it.
            AdvancedFbo.unbind();
            return;
        }

        // The matrix each section's model-view is built from: the caller's transform, then the
        // viewing orientation, which is where the camera rotation used to be applied.
        //
        // The translation after it is not cosmetic. The terrain vertex shader computes
        //     pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset
        // where CameraBlockPos and CameraOffset are global, written once a frame from the player's
        // camera. Vanilla puts each section's render origin in ChunkPosition; Sable instead puts the
        // camera block position there and folds the origin into the per-section matrix, which only
        // works while that subtraction cancels -- that is, while the camera it collects with is the
        // one bound globally. In the world it always is. Handing it a different camera displaces
        // every section by the difference between the two, which is why the first version of this
        // drew the contraption off to one side.
        //
        // So the collection is done with the global camera, keeping the subtraction at zero and the
        // configuration identical to the in-world path, and the diagram's own viewpoint is applied
        // here instead.
        final Vec3 global = Minecraft.getInstance().gameRenderer.mainCamera().position();
        final Vector3d globalCamera = new Vector3d(global.x, global.y, global.z);

        final Matrix4f viewRotation = new Matrix4f(modelView)
                .rotate(orientation)
                .translate((float) (globalCamera.x - cameraPosition.x),
                        (float) (globalCamera.y - cameraPosition.y),
                        (float) (globalCamera.z - cameraPosition.z));

        if (projectionBuffer == null) {
            projectionBuffer = new ProjectionMatrixBuffer("simulated:diagram");
        }

        RenderSystem.backupProjectionMatrix();
        RENDERING_SIMPLE = true;

        try {
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projectionMat), ProjectionType.ORTHOGRAPHIC);

            final ChunkSectionsToRender draws = SubLevelChunkDraws.collect(subLevels, viewRotation,
                    globalCamera, Util.getMillis(), null);

            // Opaque clears the buffer; translucent draws over what it left.
            renderLayerGroup(fbo, draws, ChunkSectionLayerGroup.OPAQUE, true);
            renderLayerGroup(fbo, draws, ChunkSectionLayerGroup.TRANSLUCENT, false);
        } finally {
            RENDERING_SIMPLE = false;
            RenderSystem.restoreProjectionMatrix();
            AdvancedFbo.unbind();
        }
    }

    /**
     * Vanilla's {@code ChunkSectionsToRender.renderGroup}, drawing into a given framebuffer.
     *
     * <p>The only reason this is written out rather than called is that vanilla's version takes its
     * render target from {@link ChunkSectionLayerGroup#outputTarget()}, which returns the main or
     * translucent screen target and nothing else. Everything else is a transcription, including the
     * shared index buffer and the reversal of translucent draw order.
     */
    private static void renderLayerGroup(final AdvancedFbo fbo, final ChunkSectionsToRender draws, final ChunkSectionLayerGroup group, final boolean clear) {
        final Minecraft minecraft = Minecraft.getInstance();
        final RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        final int maxIndices = draws.maxIndicesRequired();
        final GpuBuffer defaultIndexBuffer = maxIndices == 0 ? null : autoIndices.getBuffer(maxIndices);
        final IndexType defaultIndexType = maxIndices == 0 ? null : autoIndices.type();

        try (final RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "simulated:diagram " + group.label(),
                        fbo.getColorTextureAttachment(0).getGpuTextureView(),
                        clear ? Optional.of(new Vector4f(0.0f, 0.0f, 0.0f, 0.0f)) : Optional.empty(),
                        fbo.getDepthTextureAttachment().getGpuTextureView(),
                        // Reversed-Z: the far plane is 0, so that is what an empty buffer holds.
                        clear ? OptionalDouble.of(0.0) : OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("Sampler0", draws.textureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true));
            pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));

            for (final ChunkSectionLayer layer : group.layers()) {
                pass.setPipeline(layer.pipeline());

                for (List<RenderPass.Draw<GpuBufferSlice[]>> list : draws.drawGroupsPerLayer().get(layer).values()) {
                    if (list.isEmpty()) {
                        continue;
                    }

                    if (layer == ChunkSectionLayer.TRANSLUCENT) {
                        list = list.reversed();
                    }

                    pass.drawMultipleIndexed(list, defaultIndexBuffer, defaultIndexType, List.of("ChunkSection"), draws.chunkSectionInfos());
                }
            }
        }
    }
}

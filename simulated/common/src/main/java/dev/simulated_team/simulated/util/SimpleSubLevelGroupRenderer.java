package dev.simulated_team.simulated.util;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.platform.Lighting;
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
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.ryanhcode.sable.mixinhelpers.sublevel_render.vanilla.VanillaSubLevelBlockEntityRenderer;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import dev.ryanhcode.sable.sublevel.render.vanilla.SubLevelChunkDraws;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import dev.simulated_team.simulated.mixin.accessor.GameRendererLightmapAccessor;
import dev.simulated_team.simulated.mixin_interface.diagram.VisualizationManagerExtension;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.util.Util;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
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
     * The nodes the diagram's block entities and entities submit into.
     *
     * <p>Reused rather than allocated per diagram: a phase clears itself as it is sorted out of the
     * storage, so what {@code renderAllFeatures} leaves behind is empty. Vanilla's
     * picture-in-picture renderers keep one the same way.
     */
    private static final SubmitNodeStorage SUBMIT_NODES = new SubmitNodeStorage();

    @Nullable
    private static VanillaSubLevelBlockEntityRenderer blockEntityRenderer;

    /**
     * Guards against a nested feature pass. {@code FeatureRenderDispatcher} holds a single
     * {@code PreparedFrame} and throws {@code "PreparedFrame already in use"} on re-entry; the main
     * diagram and the sticky note each run a cycle in the same extract, which is fine sequentially
     * and fatal nested.
     */
    private static boolean renderingFeatures = false;

    /**
     * The diagram's "blueprint" lighting.
     *
     * <h2>What 1.21.1 actually did</h2>
     * <p>Not a flat grey, which is what a first reading of it suggests. Its loop computed
     * {@code getBrightness(y) * 0.6 + 0.15} and wrote the result to pixel {@code (y, x)} -- the
     * arguments swapped -- so the curve ran along the <em>block-light</em> axis and was constant
     * across sky light. Its own comment says so. Lighting a diagram uniformly instead flattens every
     * surface to one value, and the dither and palette behind it then collapse whole faces into a
     * single tone, which is not what the original looked like.
     *
     * <p>{@code lightmap.fsh} computes
     * {@code AmbientColor + BlockLightColor * get_brightness(block_level) * BlockFactor} and then
     * {@code mix(color, notGamma(color), BrightnessFactor)} -- the same shape, so the curve carries
     * over as an ambient floor, a block factor and a gamma mix of 0.55. The constants are fitted
     * rather than copied, for the reason {@link #TERRAIN_AMBIENT} gives.
     *
     * <p>The original's warm green/blue tint is dropped, since the diagram is reduced to a palette
     * downstream and it could not be seen. Its lerp toward {@code (0.99, 1.12, 1.0)} is <em>not</em>
     * dropped, though it looks like part of that tint: at 0.25 it lifts the floor from 0.15 to 0.36,
     * which is most of the diagram's brightness. It survives inside the fitted ambient.
     */
    private static final LightmapRenderState DIAGRAM_LIGHTMAP = new LightmapRenderState();

    /**
     * The terrain's lighting, as {@code (ambient, blockFactor)}.
     *
     * <p>1.21.1 rebuilt the texture twice -- scaled to 0.65 before the chunk layers and to 1.0
     * before the block entities -- so the contraption's fittings read against its structure.
     *
     * <p>The scale cannot be carried across as a scale. It was applied to the finished colour,
     * <em>after</em> the mix toward {@code notGamma}; 26.2 has no such step, and folding it into the
     * inputs instead darkens the value before the lift rather than after it, which the lift then
     * cannot recover. Measured at block-light zero -- what a sub-level's terrain actually carries --
     * that is 0.229 against 1.21.1's 0.406, and the diagram comes out roughly half as bright.
     *
     * <p>So these are fitted to the 1.21.1 curve rather than derived from it: the pair minimising
     * squared error against all sixteen of its levels, which lands within 0.01 everywhere
     * (rms 0.004 for terrain, 0.0006 for features).
     */
    private static final float TERRAIN_AMBIENT = 0.200f;

    private static final float TERRAIN_BLOCK_FACTOR = 0.135f;

    /** @see #TERRAIN_AMBIENT */
    /**
     * The lighting for block entities and entities, which is <em>not</em> 1.21.1's curve.
     *
     * <p>It is that curve times about 0.59, and the 0.59 stands in for a diffuse factor 26.2 no
     * longer applies. 1.21.1 drew these through entity render types -- {@code Sheets.solidBlockSheet()}
     * and friends -- and {@code entity.vsh} multiplies the vertex colour by
     * {@code minecraft_mix_light(Light0, Light1, Normal, ...)}. Create has since moved its
     * {@code SuperByteBuffer} rendering to {@code solidMovingBlock()}, and {@code block.vsh} applies
     * no directional light at all, so that factor simply vanished.
     *
     * <p>Nobody notices in the world -- things are a little brighter, that is all. The diagram is
     * another matter, because the paper pass behind it saturates: {@code paletted_dither} computes
     * {@code clamp((L * 1.7 - 0.32) * 1.8 + 0.5)}, which reaches pure white at a luminance of 0.352
     * and stays there. Measured on the diagram board, the fittings that carry its drawing went in at
     * 0.509 and came out a blank white rectangle. At the values here the same board measures 0.298,
     * the drawing survives, and terrain -- which reads no light direction and so never lost anything
     * -- is untouched at 0.198.
     *
     * <p>Restoring the diffuse itself would be better than compensating for it, but there is nowhere
     * to put it: it is the shader that dropped it, the render type is the one Create's own blueprint
     * board uses, and {@code disableDiffuse()} governs catnip's separate CPU-side shading, which was
     * measured to make no difference here at all.
     *
     * @see #TERRAIN_AMBIENT
     */
    private static final float FEATURE_AMBIENT = 0.170f;

    private static final float FEATURE_BLOCK_FACTOR = 0.200f;

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
        pushDiagramLightmap(TERRAIN_AMBIENT, TERRAIN_BLOCK_FACTOR);

        try {
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projectionMat), ProjectionType.ORTHOGRAPHIC);

            final ChunkSectionsToRender draws = SubLevelChunkDraws.collect(subLevels, viewRotation,
                    globalCamera, Util.getMillis(), null);

            // Opaque clears the buffer; translucent draws over what it left.
            renderLayerGroup(fbo, draws, ChunkSectionLayerGroup.OPAQUE, true);
            renderLayerGroup(fbo, draws, ChunkSectionLayerGroup.TRANSLUCENT, false);

            // As in 1.21.1: the fittings are lit a step brighter than the structure they sit on.
            pushDiagramLightmap(FEATURE_AMBIENT, FEATURE_BLOCK_FACTOR);
            renderFeatures(level, subLevels, fbo, viewRotation, globalCamera, partialTicks, renderPlayers);
        } finally {
            restoreLightmap();
            RENDERING_SIMPLE = false;
            RenderSystem.restoreProjectionMatrix();
            AdvancedFbo.unbind();
        }
    }

    /**
     * Rewrites the lightmap texture with the diagram's lighting.
     *
     * <h2>26.2 note</h2>
     * <p>1.21.1 rebuilt the light texture pixel by pixel through a mixin on {@code LightTexture},
     * which no longer exists. The replacement is far smaller: {@code Lightmap.render} takes the
     * state to render as an argument and {@link LightmapRenderState} is a plain mutable class, so
     * the lighting is simply handed to it. Only reaching the {@code Lightmap} needs a mixin --
     * {@code GameRenderer.lightmap()} returns a texture view, not the object -- hence
     * {@link GameRendererLightmapAccessor}.
     *
     * <p>This has to sit outside a render pass, because it opens one of its own. Everything
     * downstream picks it up: the terrain pass binds {@code gameRenderer.lightmap()} as Sampler2,
     * and the feature pass receives the same lightmap through its frame context.
     *
     * <p>Directional shading is separate from this and handled in {@link #renderFeatures}. Terrain
     * needs none of it: {@code terrain.vsh} multiplies the baked vertex colour by the lightmap and
     * never reads a light direction, so its face shading comes out of the section mesh either way.
     *
     * @param ambient     the floor every surface is lit to, whatever its light level
     * @param blockFactor how much the block-light level adds on top of it
     * @see #TERRAIN_AMBIENT
     */
    private static void pushDiagramLightmap(final float ambient, final float blockFactor) {
        // Set every time: render only rebuilds the texture when this is true, and nothing clears it
        // back on our behalf.
        DIAGRAM_LIGHTMAP.needsUpdate = true;
        DIAGRAM_LIGHTMAP.skyFactor = 0.0f;
        DIAGRAM_LIGHTMAP.blockFactor = blockFactor;
        DIAGRAM_LIGHTMAP.ambientColor = new Vector3f(ambient, ambient, ambient);
        DIAGRAM_LIGHTMAP.brightness = 0.55f;
        DIAGRAM_LIGHTMAP.nightVisionEffectIntensity = 0.0f;
        DIAGRAM_LIGHTMAP.darknessEffectScale = 0.0f;
        DIAGRAM_LIGHTMAP.bossOverlayWorldDarkening = 0.0f;

        ((GameRendererLightmapAccessor) Minecraft.getInstance().gameRenderer).simulated$getLightmap().render(DIAGRAM_LIGHTMAP);
    }

    /**
     * Hands the world's own lighting back.
     *
     * <p>Nothing is restored directly -- the diagram's texture is simply marked stale, and vanilla
     * rebuilds it from the real state at the top of {@code GameRenderer.render}, which runs after
     * the extract phase a diagram is drawn in and before the level is drawn.
     *
     * <p>The flag has to be forced rather than left to the extractor, which only raises it when the
     * state actually changes. On a frame where the lighting did not change, the world would
     * otherwise keep the diagram's texture for the rest of the frame.
     */
    private static void restoreLightmap() {
        Minecraft.getInstance().gameRenderer.gameRenderState().lightmapRenderState.needsUpdate = true;
    }

    /**
     * Draws the block entities and entities that live in the chain, on top of its terrain.
     *
     * <h2>26.2 note</h2>
     * <p>1.21.1 drew these immediately, into whatever framebuffer was bound. 26.2 has neither half
     * of that: block entities and entities are <em>submitted</em> as nodes and drawn later by
     * {@code FeatureRenderDispatcher}, and a draw's destination is an argument rather than a binding.
     *
     * <p>Both are solved by machinery vanilla already has, so this needs no mixin of its own. The
     * destination is {@code RenderSystem.outputColorTextureOverride} -- the same pair of fields the
     * picture-in-picture renderers use to aim a scene at their own texture, honoured by
     * {@code PreparedRenderType.drawFromBuffer}, which is what every feature renderer eventually
     * draws through. Sable already supplies the rest: its block entity renderer submits a sub-level's
     * contents under the sub-level's transform, and its {@code extractEntity} mixin rewrites an
     * entity's render state into world-transformed coordinates, so entities need no special handling
     * here beyond the ones vanilla does in {@code LevelRenderer.submitEntities}.
     *
     * <p>The model-view stack carries the diagram's viewing rotation for the duration. Both
     * {@code applyCameraRelativePose} and {@code submit} produce camera-relative positions and
     * nothing else, exactly as in the world; the rotation lives in {@code ModelViewMat}, which is
     * read when a node is prepared. The camera is the global one, for the reason
     * {@link #renderGroup} gives -- terrain was collected against it, and these must agree.
     */
    private static void renderFeatures(final ClientLevel level, final Collection<ClientSubLevel> subLevels, final AdvancedFbo fbo, final Matrix4f viewRotation, final Vector3d cameraPosition, final float partialTicks, final boolean renderPlayers) {
        if (renderingFeatures) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final CameraRenderState cameraRenderState = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;

        if (blockEntityRenderer == null) {
            blockEntityRenderer = new VanillaSubLevelBlockEntityRenderer(minecraft.getBlockEntityRenderDispatcher());
        }

        renderingFeatures = true;

        // Light the diagram from above and below, as 1.21.1 did with Lighting.setupNetherLevel().
        // The overworld pair lights only from above, leaving every downward-facing surface dark --
        // most visibly on entities, which come out as silhouettes rather than the flat, evenly lit
        // shapes a blueprint wants. This is separate from the lightmap: it is Light0/Light1 in the
        // Lighting uniform, applied per normal by minecraft_mix_light, and terrain never reads it.
        //
        // updateLevel writes a shared slot that vanilla only rewrites when the player changes
        // dimension, so it is put back below rather than left for vanilla to fix. setupFor merely
        // points at that slot, and LevelRenderer re-points it at LEVEL every frame, so that half
        // needs no undoing.
        final Lighting lighting = minecraft.gameRenderer.lighting();
        lighting.updateLevel(CardinalLighting.Type.NETHER);
        lighting.setupFor(Lighting.Entry.LEVEL);

        // Create's block entity renderers draw their moving parts only when Flywheel is not going to
        // visualise them instead -- every one of them opens with a supportsVisualization check. In
        // the world Flywheel does visualise them, so inside a diagram those renderers would draw
        // nothing and a mechanical press or a bearing would come out as its bare static model. This
        // makes them believe they are on their own for the duration.
        final VisualizationManager visualizationManager = VisualizationManager.get(level);
        final VisualizationManagerExtension visualization =
                visualizationManager instanceof final VisualizationManagerExtension extension ? extension : null;

        if (visualization != null) {
            visualization.sable$setDrawingDiagram(true);
        }

        final Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.set(viewRotation);

        RenderSystem.outputColorTextureOverride = fbo.getColorTextureAttachment(0).getGpuTextureView();
        RenderSystem.outputDepthTextureOverride = fbo.getDepthTextureAttachment().getGpuTextureView();

        try {
            blockEntityRenderer.setSubmitTarget(SUBMIT_NODES, cameraRenderState);

            try {
                SubLevelRenderDispatcher.get().renderBlockEntities(subLevels, blockEntityRenderer,
                        cameraPosition.x, cameraPosition.y, cameraPosition.z, partialTicks);
            } finally {
                blockEntityRenderer.setSubmitTarget(null, null);
            }

            submitEntities(level, subLevels, cameraRenderState, cameraPosition, renderPlayers);

            minecraft.gameRenderer.featureRenderDispatcher().renderAllFeatures(SUBMIT_NODES);
        } finally {
            lighting.updateLevel(level.dimensionType().cardinalLightType());

            if (visualization != null) {
                visualization.sable$setDrawingDiagram(false);
            }

            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
            modelViewStack.popMatrix();
            renderingFeatures = false;
        }
    }

    /**
     * Submits the entities standing on, riding or otherwise belonging to the chain.
     *
     * <p>The search box is the sub-level's own, inflated, because an entity's position is stored in
     * world space and a sub-level's contents may hang over its bounds; membership is then decided by
     * Sable rather than by geometry. The filter is the one 1.21.1 used.
     */
    private static void submitEntities(final ClientLevel level, final Collection<ClientSubLevel> subLevels, final CameraRenderState cameraRenderState, final Vector3d cameraPosition, final boolean renderPlayers) {
        final Minecraft minecraft = Minecraft.getInstance();
        final EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        final PoseStack poseStack = new PoseStack();

        for (final ClientSubLevel subLevel : subLevels) {
            final List<Entity> entities = level.getEntitiesOfClass(Entity.class,
                    subLevel.getPlot().getBoundingBox().toAABB().inflate(16.0));

            for (final Entity entity : entities) {
                if (Sable.HELPER.getContaining(entity) != subLevel && Sable.HELPER.getTrackingOrVehicleSubLevel(entity) != subLevel) {
                    continue;
                }

                if (!renderPlayers && entity instanceof Player) {
                    continue;
                }

                final float partialTick = minecraft.getDeltaTracker()
                        .getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(entity));

                // Sable's extractEntity mixin puts the sub-level's transform into the state, so what
                // comes back is already in world space and only needs making camera-relative.
                final EntityRenderState state = dispatcher.extractEntity(entity, partialTick);

                if (state == null) {
                    continue;
                }

                dispatcher.submit(state, cameraRenderState,
                        state.x - cameraPosition.x, state.y - cameraPosition.y, state.z - cameraPosition.z,
                        poseStack, SUBMIT_NODES);
            }
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
            // Linear and mipmapped, as vanilla builds it in LevelRenderer -- not nearest.
            // terrain.fsh does its own filtering, sampleNearest and sampleRGSS, through textureGrad
            // and textureLod, and both are written against a linear mipmapped sampler: the first
            // walks a sub-texel offset expecting interpolation between texels, the second averages
            // four offset samples across two mip levels. Hand them a nearest sampler and each of
            // those degenerates to the same unfiltered texel, so block textures lose their fine
            // gradients and small shapes flatten out. It only shows in the diagram because the
            // in-world path goes through vanilla's own renderGroup with the real sampler.
            pass.bindTexture("Sampler0", draws.textureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, true));
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

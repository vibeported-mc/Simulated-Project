package dev.eriksonn.aeronautics.index.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.eriksonn.aeronautics.Aeronautics;
import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.client.render.rendertype.VeilRenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

/**
 * Aeronautics' levitite render types.
 *
 * <h2>26.2 note</h2>
 * <p>This could not stay a subclass of {@code RenderType}, and it could not stay built from
 * {@code CompositeState}. 26.2 splits a render type's state in two -- GPU state is an immutable
 * {@code RenderPipeline}, everything else is a {@code RenderSetup} -- and assembles the type with
 * {@code RenderType.create(name, setup)}. Veil's builder carries both halves, so the layer list
 * below reads the same way it always did.
 *
 * <h3>Two things did not survive, and both are recorded in AERONAUTICS-26.2-OPEN-QUESTIONS.md</h3>
 *
 * <p><b>The patches layer.</b> Levitite draws with {@code GL_PATCHES} and carries tessellation
 * control and evaluation stages. A 26.2 {@code RenderPipeline} names its topology from
 * {@code PrimitiveTopology}, a closed enum with no patches constant, so there is nowhere to put
 * either. Veil's {@code PatchesLayer} is still parked for the same reason. The type below draws
 * quads; the vertex and fragment stages of the levitite program still run, the tessellation stages
 * do not.
 *
 * <p><b>The enabled/disabled shader swap.</b> {@code LevititeShaderState} chose, per frame, between
 * Veil's levitite program and a depth-only vanilla state, so that a machine without tessellation
 * drew the geometry invisibly here and got its visible pass elsewhere. A pipeline is immutable and
 * a shader is two identifiers baked into it, so nothing can be swapped inside a render type any
 * more. {@link dev.eriksonn.aeronautics.content.blocks.levitite.LevititeShaderManager#isEnabled()}
 * is still the gate -- it has to be asked at the call sites instead.
 */
public final class AeroRenderTypes {

    /**
     * <h2>26.2 note -- this is not the levitite shader</h2>
     * <p>It is vanilla's ordinary block shader, and levitite draws as an ordinary block.
     *
     * <p>The real one cannot be a render type's shader on 26.2. It declares tessellation control and
     * evaluation stages, which a {@code RenderPipeline} has no way to express; it samples the main
     * framebuffer's depth, which a render type's static texture bindings cannot supply; and it reads
     * a dozen per-draw uniforms -- the sub-level's velocity, orientation and material matrices --
     * through a Veil shader block, which is the one thing a pipeline's engine-filled uniform buffers
     * leave no room for.
     *
     * <p>Pointing the pipeline at the Veil program instead is what the port did, and it does not
     * work: vanilla resolves a pipeline's shader under {@code assets/<namespace>/shaders/} and never
     * finds it, so the first levitite drawn threw "Pipeline contains invalid shader program" and took
     * the game down. Drawing plain is the honest failure. See AERONAUTICS-26.2-OPEN-QUESTIONS.md.
     */
    private static final Identifier LEVITITE_SHADER = Identifier.withDefaultNamespace("core/block");

    private static final RenderType LEVITITE = RenderType.create(
            Aeronautics.MOD_ID + ":levitite",
            VeilRenderBridge.createRenderType("aeronautics/levitite", DefaultVertexFormat.BLOCK)
                    .vertexShader(LEVITITE_SHADER)
                    .fragmentShader(LEVITITE_SHADER)
                    .snippet(VeilRenderPipelines.translucentBlend())
                    // The depth state 1.21.1 got by default and 26.2 does not give at all.
                    // See the note on SimRenderTypes.
                    .snippet(VeilRenderPipelines.defaultDepthTest())
                    .snippet(VeilRenderPipelines.cull())
                    .texture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                    .useLightmap()
                    .sortOnUpload()
                    .create(false));

    private static final RenderType LEVITITE_GHOSTS = RenderType.create(
            Aeronautics.MOD_ID + ":levitite_ghosts",
            VeilRenderBridge.createRenderType("aeronautics/levitite_ghosts", DefaultVertexFormat.BLOCK)
                    .vertexShader(LEVITITE_SHADER)
                    .fragmentShader(LEVITITE_SHADER)
                    .snippet(VeilRenderPipelines.translucentBlend())
                    // The depth state 1.21.1 got by default and 26.2 does not give at all.
                    // See the note on SimRenderTypes.
                    .snippet(VeilRenderPipelines.defaultDepthTest())
                    .snippet(VeilRenderPipelines.noCull())
                    .texture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                    .useLightmap()
                    .sortOnUpload()
                    .create(false));

    private AeroRenderTypes() {
    }

    public static RenderType levitite() {
        return LEVITITE;
    }

    public static RenderType levititeGhosts() {
        return LEVITITE_GHOSTS;
    }
}

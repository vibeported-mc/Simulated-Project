package dev.simulated_team.simulated.index;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.GpuFormat;
import net.minecraft.client.renderer.texture.TextureAtlas;
import com.simibubi.create.foundation.render.RenderTypes;
import dev.simulated_team.simulated.Simulated;
import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.client.render.rendertype.VeilRenderPipelines;
import net.minecraft.util.Util;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.resources.Identifier;
import java.util.function.Function;

/**
 * Simulated's own render types.
 *
 * <h2>26.2 note</h2>
 * <p>None of this could be a subclass of {@code RenderType} any more, and none of it could be built
 * from {@code CompositeState}. 26.2 splits a render type's state in two -- GPU state (blending, depth,
 * culling, write masks, shaders) is an immutable {@code RenderPipeline}, and everything else
 * (textures, lightmap, overlay) is a {@code RenderSetup} -- and {@code RenderType} itself became
 * final-in-practice, assembled by {@code RenderType.create(name, setup)}.
 *
 * <p>Veil's {@code VeilRenderTypeBuilder} carries both halves, so each type below reads as the same
 * list of layers it always did:
 *
 * <ul>
 *   <li>{@code setShaderState(VeilRenderBridge.shaderState(id))} became
 *       {@code vertexShader(id).fragmentShader(id)} -- a shader is two identifiers on the pipeline
 *       now rather than a swappable shard, and naming the Veil program on both stages is what Veil's
 *       own data-driven layer does.</li>
 *   <li>{@code setTransparencyState}, {@code setDepthTestState}, {@code setCullState} and
 *       {@code setWriteMaskState} became {@link VeilRenderPipelines} snippets.</li>
 *   <li>{@code setTextureState} became {@code texture("Sampler0", id)}; blur and mipmap belong to
 *       the sampler now rather than to the render state.</li>
 *   <li>{@code affectsCrumbling} and {@code sort} moved onto the builder, and the outline flag is
 *       the argument to {@code create}.</li>
 * </ul>
 *
 * <h2>Depth is not optional any more, and LEQUAL is not the answer</h2>
 * <p>1.21.1's {@code CompositeState.builder()} started with {@code LEQUAL_DEPTH_TEST} and
 * {@code COLOR_DEPTH_WRITE} already set, so a type that said nothing about depth still tested and
 * wrote it. 26.2 has no such default: {@code RenderPipeline.Builder.build} ends with
 * {@code depthStencilState.orElse(null)}, and a pipeline holding a null one is given no depth
 * attachment at all -- {@code wantsDepthTexture()} is false and the draw neither tests nor writes.
 *
 * <p>Nothing reports this. The draw is issued, it validates, and it simply paints over whatever is
 * already in the colour buffer: a laser, a rope, a lens or a burner's flame shows through the wall
 * in front of it, through the block it belongs to, and from any distance.
 *
 * <p>The obvious repair is wrong. Naming {@code lequalDepthTest()} -- the compare op these types had
 * on 1.21.1, by that name -- fixes nothing at all, because 26.2 reversed the depth buffer:
 * {@code DepthStencilState.DEFAULT} is {@code GREATER_THAN_OR_EQUAL}, near is 1 and far is 0, and
 * asking for LEQUAL keeps the furthest fragment rather than the nearest. It draws through walls just
 * as thoroughly as having no depth state at all, which is exactly how it looked when tried.
 *
 * <p>So every type that relied on the old default names {@link VeilRenderPipelines#defaultDepthTest()}
 * here, which is vanilla's own state and stays right whichever way round the buffer is. The two that
 * genuinely want to be seen through walls -- the staff overlay and the lock marker, both of which set
 * {@code NO_DEPTH_TEST} on 1.21.1 -- keep saying so.
 */
public final class SimRenderTypes {

    private static final RenderType STAFF_OVERLAY = RenderType.create(
            Simulated.MOD_ID + ":staff_overlay/staff_overlay",
            VeilRenderBridge.createRenderType("simulated/staff_overlay/staff_overlay", DefaultVertexFormat.POSITION_COLOR)
                    // The one type here that is not quads; 26.2 keeps the topology on the pipeline.
                    .primitiveTopology(PrimitiveTopology.TRIANGLE_STRIP)
                    // 26.2: the shader this named has never existed -- not here and not on 1.21.1,
                    // where a missing Veil program merely failed to draw. A pipeline with a missing
                    // shader is fatal, and this type is registered as a fixed buffer, so it would
                    // have taken the game down the first time the staff drew. Vanilla's plain
                    // position-colour shader is what the overlay wants anyway.
                    .vertexShader(Identifier.withDefaultNamespace("core/position_color"))
                    .fragmentShader(Identifier.withDefaultNamespace("core/position_color"))
                    .snippet(VeilRenderPipelines.translucentBlend())
                    // COLOR_WRITE and NO_DEPTH_TEST together: drawn over everything, and occluding
                    // nothing afterwards. Asked for as one snippet because the two of them are one
                    // depth state -- set separately, whichever came last decided the write as well.
                    .snippet(VeilRenderPipelines.noDepthTestOrWrite())
                    .snippet(VeilRenderPipelines.cull())
                    .sortOnUpload()
                    .create(false));

    private static final RenderType LASER = RenderType.create(
            Simulated.MOD_ID + ":laser",
            VeilRenderBridge.createRenderType("simulated/laser", DefaultVertexFormat.POSITION_TEX_COLOR)
                    .vertexShader(Simulated.path("core/laser/laser"))
                    .fragmentShader(Simulated.path("core/laser/laser"))
                    // The depth state 1.21.1 gave by default. See the class note.
                    .snippet(VeilRenderPipelines.defaultDepthTest())
                    .snippet(VeilRenderPipelines.translucentBlend())
                    .snippet(VeilRenderPipelines.noCull())
                    .sortOnUpload()
                    .create(false));

    private static final RenderType LENS = RenderType.create(
            Simulated.MOD_ID + ":laser_pointer_lens",
            VeilRenderBridge.createRenderType("simulated/laser_pointer_lens", DefaultVertexFormat.BLOCK)
                    // The old builder set a shader twice -- the vanilla cutout shader and then Veil's
                    // -- and the second won. Only the winner is named here.
                    .vertexShader(Simulated.path("core/laser_pointer/lens"))
                    .fragmentShader(Simulated.path("core/laser_pointer/lens"))
                    // The depth state 1.21.1 gave by default. See the class note.
                    .snippet(VeilRenderPipelines.defaultDepthTest())
                    .texture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                    .useLightmap()
                    .affectsCrumbling()
                    .sortOnUpload()
                    .create(true));

    /**
     * 26.2 rewrote {@code VertexFormatElement} into a plain record and deleted its constants; an
     * attribute is now a semantic name plus a {@link GpuFormat}, and the builder takes a step rate
     * rather than trailing padding. The formats below are the ones {@code DefaultVertexFormat} uses
     * for the same semantics, which it keeps private.
     */
    private static final VertexFormat SPRING_FORMAT = VertexFormat.builder(0)
            .addAttribute(DefaultVertexFormat.POSITION_SEMANTIC_NAME, GpuFormat.RGB32_FLOAT)
            // "Stress" rather than "Color": the spring shader reads the colour channel as strain.
            .addAttribute("Stress", GpuFormat.RGBA8_UNORM)
            .addAttribute(DefaultVertexFormat.UV0_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
            .addAttribute(DefaultVertexFormat.UV2_SEMANTIC_NAME, GpuFormat.RG16_SINT)
            .addAttribute(DefaultVertexFormat.NORMAL_SEMANTIC_NAME, GpuFormat.RGBA8_SNORM)
            .build();

    /**
     * What 1.21.1's {@code BLOCK} was: position, colour, texture, lightmap and a normal.
     *
     * <p>26.2's {@code DefaultVertexFormat.BLOCK} dropped the normal. The rope's renderer still
     * writes one -- every {@code addVertex} in {@code LaunchedPlungerEntityRenderer} ends in
     * {@code setNormal} -- and its shader still reads one, to mix the two directional lights. With
     * the stock format those writes go nowhere, and the mismatch is invisible on OpenGL and fatal
     * on Vulkan: {@code Shader expects input variables which are not being provided: [Normal]},
     * and the whole pipeline fails to compile.
     */
    private static final VertexFormat ROPE_FORMAT = VertexFormat.builder(0)
            .addAttribute(DefaultVertexFormat.POSITION_SEMANTIC_NAME, GpuFormat.RGB32_FLOAT)
            .addAttribute(DefaultVertexFormat.COLOR_SEMANTIC_NAME, GpuFormat.RGBA8_UNORM)
            .addAttribute(DefaultVertexFormat.UV0_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
            .addAttribute(DefaultVertexFormat.UV2_SEMANTIC_NAME, GpuFormat.RG16_SINT)
            .addAttribute(DefaultVertexFormat.NORMAL_SEMANTIC_NAME, GpuFormat.RGBA8_SNORM)
            .build();

    private static final RenderType LOCK = RenderType.create(
            Simulated.MOD_ID + ":lock",
            VeilRenderBridge.createRenderType("simulated/lock", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP)
                    // 1.21.1 drew this with POSITION_COLOR_TEX_LIGHTMAP_SHADER. 26.2's core shader
                    // set is much smaller and has no equivalent, so that one is ported into this
                    // mod rather than approximated with core/block, whose fog and filtered lightmap
                    // sampling this never had.
                    .vertexShader(Simulated.path("core/lock"))
                    .fragmentShader(Simulated.path("core/lock"))
                    .snippet(VeilRenderPipelines.noDepthTest())
                    .snippet(VeilRenderPipelines.noCull())
                    .texture("Sampler0", Simulated.path("textures/gui/lock.png"))
                    // The shader reads Sampler2, so the lightmap has to be bound. 1.21.1 got it
                    // from the render type's format alone; on 26.2 a render setup declares it.
                    .useLightmap()
                    .affectsCrumbling()
                    .create(true));

    private static final RenderType ROPE = RenderType.create(
            Simulated.MOD_ID + ":rope",
            VeilRenderBridge.createRenderType("simulated/rope", ROPE_FORMAT)
                    .vertexShader(Simulated.path("core/rope/rope"))
                    .fragmentShader(Simulated.path("core/rope/rope"))
                    // The depth state 1.21.1 gave by default. See the class note.
                    .snippet(VeilRenderPipelines.defaultDepthTest())
                    .texture("Sampler0", Simulated.path("textures/block/rope_particle.png"))
                    .useLightmap()
                    .snippet(VeilRenderPipelines.cull())
                    .affectsCrumbling()
                    .create(false));

    private static final Function<Identifier, RenderType> SPRING = Util.memoize((Identifier texture) ->
            RenderType.create("spring",
                    VeilRenderBridge.createRenderType("spring", SPRING_FORMAT)
                            .vertexShader(Simulated.path("core/spring/spring"))
                            .fragmentShader(Simulated.path("core/spring/spring"))
                            // The depth state 1.21.1 gave by default. See the class note.
                            .snippet(VeilRenderPipelines.defaultDepthTest())
                            .texture("Sampler0", texture)
                            .snippet(VeilRenderPipelines.noBlend())
                            .useLightmap()
                            .useOverlay()
                            .affectsCrumbling()
                            .create(true)));

    /**
     * The End Sea's stacked layers.
     *
     * <h2>26.2 note</h2>
     * <p>This was not a render type at all -- {@code EndSeaRenderer} bound Veil's program itself and
     * set the GL state around an immediate-mode draw: {@code disableCull}, {@code depthMask(false)},
     * and a {@code blendFuncSeparate} making it additive. All of that is pipeline state now, so it
     * becomes a render type like the rest, and the draw goes through it.
     *
     * <p>The format is Position + Color + UV0 + UV2, which vanilla already has a name for.
     */
    private static final RenderType END_SEA = RenderType.create(
            Simulated.MOD_ID + ":end_sea",
            VeilRenderBridge.createRenderType("simulated/end_sea", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP)
                    .vertexShader(Simulated.path("core/end_sea"))
                    .fragmentShader(Simulated.path("core/end_sea"))
                    .texture("SkySampler", Identifier.withDefaultNamespace("textures/entity/end_portal.png"))
                    .snippet(VeilRenderPipelines.additiveBlend())
                    // `depthMask(false)` around the old immediate-mode draw, and nothing about the
                    // test -- so the default test with the write off, which is one snippet.
                    .snippet(VeilRenderPipelines.noDepthWrite())
                    .snippet(VeilRenderPipelines.noCull())
                    .create(false));

    private SimRenderTypes() {
    }

    public static RenderType staffOverlay() {
        return STAFF_OVERLAY;
    }

    public static RenderType laser() {
        return LASER;
    }

    public static RenderType lens() {
        return LENS;
    }

    public static RenderType lock() {
        return LOCK;
    }

    public static RenderType rope() {
        return ROPE;
    }

    /**
     * 26.2 dropped {@code Sheets.solidBlockSheet} and {@code translucentCullBlockSheet}; the sheets
     * it keeps for drawing an item off the block atlas are the cutout and translucent item ones.
     * Cutout rather than solid is the substitution to be aware of -- it adds an alpha test that
     * always passes for an opaque item.
     */
    public static RenderType itemGlowingSolid(boolean shadersActive) {
        return shadersActive ? Sheets.cutoutBlockItemSheet() : RenderTypes.itemGlowingSolid();
    }

    public static RenderType itemGlowingTranslucent(boolean shadersActive) {
        return shadersActive ? Sheets.translucentBlockItemSheet() : RenderTypes.itemGlowingTranslucent();
    }

    public static RenderType endSea() {
        return END_SEA;
    }

    public static RenderType spring(final Identifier texture) {
        return SPRING.apply(texture);
    }
}

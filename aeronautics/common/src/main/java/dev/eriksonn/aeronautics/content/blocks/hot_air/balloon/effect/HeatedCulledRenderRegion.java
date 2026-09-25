package dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.effect;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.vertex.*;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.Balloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.graph.BalloonLayerData;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.graph.BalloonLayerGraph;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.render.region.SimpleCulledRenderRegionBuilder;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.client.Minecraft;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.VeilDraw;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.vertex.VertexArray;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3i;
import org.lwjgl.system.NativeResource;

import java.util.Iterator;
import java.util.List;

public class HeatedCulledRenderRegion implements NativeResource {
    private Balloon balloon;
    private boolean built = false;
    private VertexArray buffer;
    private Vec3 origin;
    private final LevelAccelerator accelerator;

    public HeatedCulledRenderRegion(final LevelAccelerator accelerator, final Balloon balloon) {
        this.accelerator = accelerator;
        this.balloon = balloon;
    }

    /**
     * <h2>26.2 note</h2>
     * <p>The shader arrives as an argument rather than being fetched from {@code RenderSystem}:
     * {@code ShaderInstance} is gone, this draw runs under a Veil program, and the caller is the one
     * that bound it. {@code setDefaultUniforms} went with it, so the two matrices the shader
     * declares are set by name here -- there is no engine-side convention left to lean on.
     */
    /**
     * <h2>26.2 note</h2>
     *
     * <p>Takes the target it draws into. It used to draw into whichever framebuffer was bound,
     * which off OpenGL is nothing -- a target is named when the pass is opened, and a pass is
     * what a draw is.
     */
    public void render(final AdvancedFbo target, final ShaderProgram shader, final DepthStencilState depthState, final net.minecraft.client.Camera camera, final Matrix4f modelView, final Matrix4f projectionMatrix) {
        if (!this.built) {
            this.build();
        }

        if (this.buffer == null) {
            return;
        }

        final Minecraft client = Minecraft.getInstance();
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(this.origin);

        Vec3 globalOrigin = this.origin;
        final Quaternionf globalOrientation = new Quaternionf();

        if (subLevel != null) {
            final Pose3dc renderPose = subLevel.renderPose();
            globalOrigin = renderPose.transformPosition(globalOrigin);
            globalOrientation.set(renderPose.orientation());
        }

        // The camera this frame's geometry was actually rasterised from.
        //
        // Not the one the level-stage event hands over, and not Minecraft's current one -- they
        // are the same object and it is the wrong one. 26.2 extracts a frame's render state and
        // draws from that, so the live camera has already moved on; Veil's NeoForge platform has
        // the frame's cameraRenderState in hand when it fires the event and passes mainCamera()
        // anyway.
        //
        // It matters here because this volume's depth is compared against the world's in
        // soft_light. Position it from a camera the world was not drawn with and the two depths
        // disagree by exactly how far the camera travelled between them -- nothing standing
        // still, more the faster you move -- so the comparison flips across a band and the effect
        // is cut away along a hard edge that drifts while walking and settles when you stop.
        final Vec3 framePos = Minecraft.getInstance()
                .gameRenderer
                .gameRenderState().levelRenderState.cameraRenderState.pos;

        final Vec3 relativePos = globalOrigin.subtract(framePos);

        final Matrix4f modelViewMatrix = new Matrix4f(modelView)
                .setTranslation(0.0f, 0.0f, 0.0f)
                .translate((float) relativePos.x, (float) relativePos.y, (float) relativePos.z)
                .rotate(globalOrientation);

        shader.getUniformSafe("ModelViewMat").setMatrix(modelViewMatrix);
        shader.getUniformSafe("ProjMat").setMatrix(projectionMatrix);

        VeilDraw.geometry(target, shader, this.buffer, depthState);
    }

    public void build() {
        final BoundingBox3ic bounds = this.balloon.getBounds();
        final Vector3i minBlock = new Vector3i(bounds.minX(), bounds.minY(), bounds.minZ());
        final Vector3i maxBlock = new Vector3i(bounds.maxX(), bounds.maxY(), bounds.maxZ());

        int gridSize = maxBlock.x() - minBlock.x() + 1;
        gridSize = Math.max(gridSize, maxBlock.y() - minBlock.y() + 1);
        gridSize = Math.max(gridSize, maxBlock.z() - minBlock.z() + 1);

        final BlockPos originBlock = new BlockPos(minBlock.x(), minBlock.y(), minBlock.z());
        this.origin = Vec3.atLowerCornerOf(originBlock);

        final SimpleCulledRenderRegionBuilder builder = this.createMeshBuilder(gridSize);

        final BalloonLayerGraph graph = this.balloon.getGraph();


        for (int y = graph.getMinY(); y <= graph.getMaxY(); y++) {
            final List<BalloonLayerData> layers = graph.getLayersAtY(y);

            for (final BalloonLayerData layer : layers) {
                final Iterator<BlockPos> layerBlocks = layer.blockIterator();

                while (layerBlocks.hasNext()) {
                    final BlockPos blockPos = layerBlocks.next();

                    builder.add(blockPos.getX() - originBlock.getX(),
                            blockPos.getY() - originBlock.getY(),
                            blockPos.getZ() - originBlock.getZ());
                }
            }
        }

        builder.buildNoGreedy();

        // 26.2: Tesselator and VertexBuffer are both gone. A BufferBuilder writes into a
        // ByteBufferBuilder the caller owns, and the mesh is uploaded through Veil's VertexArray --
        // whose draw() also knows how to issue GL_PATCHES when the bound program is tessellated.
        this.buffer = null;
        try (ByteBufferBuilder byteBuffer = new ByteBufferBuilder(this.getVertexFormat().getVertexSize() * 1024)) {
            final BufferBuilder bufferBuilder = new BufferBuilder(byteBuffer, PrimitiveTopology.QUADS, this.getVertexFormat());
            builder.render(new Matrix4f(), bufferBuilder);

            this.balloon = null;
            final MeshData builtData = bufferBuilder.build();

            if (builtData != null) {
                this.buffer = VertexArray.create();
                this.buffer.bind();
                this.buffer.upload(builtData, VertexArray.DrawUsage.STATIC);
                VertexArray.unbind();
            }
        }

        this.built = true;
    }

    public Vec3 getOrigin() {
        return this.origin;
    }

    public SimpleCulledRenderRegionBuilder createMeshBuilder(final int gridSize) {
        return new HeatedCulledRenderRegionBuilder(BlockPos.containing(this.getOrigin()), this.accelerator, gridSize);
    }

    public VertexFormat getVertexFormat() {
        return DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL;
    }

    @Override
    public void free() {
        if (this.built && this.buffer != null) {
            this.buffer.free();
        }
    }
}

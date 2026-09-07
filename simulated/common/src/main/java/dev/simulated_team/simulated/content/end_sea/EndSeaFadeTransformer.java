package dev.simulated_team.simulated.content.end_sea;

import foundry.veil.api.client.render.shader.processor.ShaderPreProcessor;
import io.github.ocelot.glslprocessor.api.GlslInjectionPoint;
import io.github.ocelot.glslprocessor.api.GlslParser;
import io.github.ocelot.glslprocessor.api.GlslSyntaxException;
import io.github.ocelot.glslprocessor.api.node.GlslNode;
import io.github.ocelot.glslprocessor.api.node.GlslTree;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.List;

public class EndSeaFadeTransformer implements ShaderPreProcessor {

    @Override
    public void modify(final Context ctx, final GlslTree tree) throws GlslSyntaxException {
        if (ctx instanceof final MinecraftContext minecraftContext) {
            // 26.2: the chunk layers are a closed ChunkSectionLayer enum rather than a list of render types.
            final ChunkSectionLayer[] renderTypes = ChunkSectionLayer.values();

            boolean anyMatches = false;

            for (final ChunkSectionLayer renderType : renderTypes) {
                if (ctx.isVertex() && minecraftContext.shaderInstance().equals("rendertype_%s".formatted(renderType.label()))) {
                    anyMatches = true;
                }
            }

            if (!anyMatches) {
                return;
            }
        } else {
            return;
        }

        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("uniform float EndSeaCameraY;"));

        // Add NormalMat if we're lacking it
        if (tree.field("NormalMat").isEmpty()) {
            tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("uniform mat3 NormalMat;"));
        }

        final List<GlslNode> body = tree.mainFunction().orElseThrow().getBody();
        body.add(GlslParser.parseExpression("""
                    if (EndSeaCameraY != 0.0) {
                        vertexColor.rgb = mix(vertexColor.rgb, vec3(0.086, 0.078, 0.109) * 2.0, clamp((-(inverse(NormalMat) * (ModelViewMat * vec4(pos, 0.0)).rgb).y - EndSeaCameraY) / 30.0, 0.0, 1.0));
                    }
                """));
    }
}

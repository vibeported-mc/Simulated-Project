#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// A faithful port of 1.21.1's position_color_tex_lightmap, which 26.2 removed. The lock marker was
// built against that shader: no fog, and the lightmap read with texelFetch rather than the filtered
// sample_lightmap, so a FULL_BRIGHT vertex lands exactly on the brightest texel.
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}

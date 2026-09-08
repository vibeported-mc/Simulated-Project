#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in float litFrac;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 vertexLight;

out vec4 fragColor;

void main() {
    vec4 colA = texture(Sampler0, texCoord0) * vertexLight;
    vec4 colB = texture(Sampler0, texCoord0 + vec2(16.0, 0.0) / textureSize(Sampler0, 0));
    vec4 col = mix(colB, colA, litFrac) * vertexColor * ColorModulator;
    fragColor = apply_fog(col, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}

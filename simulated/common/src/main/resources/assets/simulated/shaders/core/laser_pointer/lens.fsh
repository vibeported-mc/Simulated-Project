#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 vertexLight;

out vec4 fragColor;

const vec3 low_color = vec3(0.24, 0.184, 0.29);
const vec3 high_color = vec3(0.96, 0.91, 1.0);

void main() {
    vec4 textureCol = texture(Sampler0, texCoord0);
    if (textureCol.a == 0) {
        discard;
    }

    vec3 col_in = vertexColor.rgb * 0.9 + 0.05;

    // 0.5 gives vertex color. darker is mixed with black. lighter is mixed with white
    float lightness = textureCol.r;

    vec3 col;
    if (lightness < 0.5) {
        col = mix(col_in, low_color, (0.5 - lightness) * 2.0);
    } else {
        col = mix(col_in, high_color, (lightness - 0.5) * 2.0);
    }

    vec4 finalColor = vec4(col, 1.0) * vertexLight * ColorModulator;

    fragColor = apply_fog(finalColor, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}

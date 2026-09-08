#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in float vertexDistance;
// x always     (length + 0.5) / length
// y ranges [0, (length + 0.5) / length]
in vec2 lengthData;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float endTaper = (lengthData.x - 1.0) / (lengthData.y - 1.0);
    vec4 color = vertexColor;
    color.a *= (1.0 - max(endTaper, 0.0));

    // 26.2: Veil's linear_fog_fade faded the alpha out toward the fog distance. Vanilla has no
    // fade-only helper, so the same curve is written out from linear_fog_value, which it does have.
    float fade = 1.0 - linear_fog_value(vertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd);
    fragColor = vec4((color * ColorModulator).rgb, color.a * ColorModulator.a * fade);
}

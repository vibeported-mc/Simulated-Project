#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
// 26.2 port: a render type's uniforms are fixed per type, and a block entity no longer gets to set
// one before its own draw -- there is no immediate-mode draw left to set it before. The two values
// that vary per burner ride in the vertex data instead. Intensity is 0..1, so eight bits of the
// colour is ample. FlameRenderTime accumulates at a rate that depends on the burner's own intensity,
// so it cannot come from a global clock; it goes in the lightmap channel, which is free because the
// flame is drawn full-bright, as fixed point with 1/256 s resolution across two 16-bit ints.
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec2 texCoord0;
out float flameRenderTime;
out float flameIntensity;

void main() {
    vec3 pos = Position + ModelOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);

    texCoord0 = UV0;

    // Reassemble the time: high 16 bits are whole 256-second blocks, low 16 are 1/256 s steps.
    flameRenderTime = float(UV2.y) * 256.0 + float(UV2.x) / 256.0;
    flameIntensity = Color.r;
}

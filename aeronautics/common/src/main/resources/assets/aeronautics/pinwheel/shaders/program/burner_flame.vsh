#include veil:fog

layout(location = 0) in vec3 Position;
// 26.2 port: a render type's uniforms are fixed per type, and a block entity no longer gets to set
// one before its own draw -- there is no immediate-mode draw left to set it before. The two values
// that vary per burner ride in the vertex data instead. Intensity is 0..1, so eight bits of the
// colour is ample. FlameRenderTime accumulates at a rate that depends on the burner's own intensity,
// so it cannot come from a global clock; it goes in the lightmap channel, which is free because the
// flame is drawn full-bright, as fixed point with 1/256 s resolution across two 16-bit ints.
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out vec2 texCoord0;
out float vertexDistance;
out float flameRenderTime;
out float flameIntensity;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexDistance = fog_distance(ModelViewMat, Position, FogShape);

    texCoord0 = UV0;

    // Reassemble the time: high 16 bits are whole 256-second blocks, low 16 are 1/256 s steps.
    flameRenderTime = float(UV2.y) * 256.0 + float(UV2.x) / 256.0;
    flameIntensity = Color.r;
}

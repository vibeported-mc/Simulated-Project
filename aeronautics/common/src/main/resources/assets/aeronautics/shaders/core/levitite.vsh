#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 worldPos;

void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;

    // Absolute world position, which is what the shimmer is sampled against.
    //
    // On 1.21.1 this came out of `getLocalPosition`, subtracting an `offset` uniform that the level
    // renderer set to minus the camera position -- so the noise was addressed in world space and
    // stayed put as the camera moved. ChunkPosition is that same world space without the round
    // trip: it is the section's own origin, so Position plus it is where the block actually is.
    worldPos = Position + ChunkPosition;
}

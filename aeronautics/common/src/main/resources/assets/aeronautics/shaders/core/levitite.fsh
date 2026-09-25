#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Noise;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 worldPos;

out vec4 fragColor;

/**
 * The shimmer, carried over from the tessellation evaluation stage.
 *
 * On 1.21.1 this ran per generated sub-vertex and was interpolated across the face. There is no
 * tessellation stage on 26.2, so it runs per fragment instead -- which is the same field sampled
 * more finely, not an approximation of it.
 *
 * Three noise lookups, each a difference of two samples of the same texture at rotated and sheared
 * coordinates, assembled into a matrix and projected onto a scale vector. The rotations are what
 * stop the three channels agreeing with one another; the shear by time is what makes it move.
 */
vec3 getNoiseFromPositionAndTime(vec4 p, vec3 noiseScale) {
    mat2 R1 = mat2(25, 3, 2, 15) / 10;
    mat2 R2 = mat2(15, 1, 2, -25) / 10;
    mat3 M1 = mat3(
        10, 2, -1,
        4, 10, -5,
        -3, -2, 10
    ) / 10;
    mat3 M2 = transpose(M1);

    vec4 p1 = vec4(M1 * p.xyz, p.w);
    vec4 p2 = vec4(M2 * p.xyz, p.w);

    vec3 noiseOutX = texture(Noise, R1 * p1.zw).xyz - texture(Noise, R2 * p2.yw).xyz;
    vec3 noiseOutY = texture(Noise, R1 * p1.xw).xyz - texture(Noise, R2 * p2.zw).xyz;
    vec3 noiseOutZ = texture(Noise, R1 * p1.yw).xyz - texture(Noise, R2 * p2.xw).xyz;

    mat3 m = mat3(noiseOutX, noiseOutY, noiseOutZ);
    return transpose(m) * noiseScale;
}

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize, vec2 du, vec2 dv, vec2 texelScreenSize) {
    vec2 uvTexelCoords = uv / pixelSize;
    vec2 texelCenter = round(uvTexelCoords) - 0.5f;
    vec2 texelOffset = uvTexelCoords - texelCenter;
    texelOffset = (texelOffset - 0.5f) * pixelSize / texelScreenSize + 0.5f;
    texelOffset = clamp(texelOffset, 0.0f, 1.0f);
    uv = (texelCenter + texelOffset) * pixelSize;
    return textureGrad(source, uv, du, dv);
}

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    return sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
}

void main() {
    vec4 color = sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize) * vertexColor;

    // The stationary case of the 1.21.1 evaluation shader, which is the only one reachable here.
    //
    // That shader branched on whether the block was on a moving sub-level. Every term of the moving
    // branch -- velocity, drag, gravity, the sub-level's orientation -- came from uniforms a render
    // type on 26.2 has no way to be given, and with the block at rest they all collapse: the drag
    // state is (1, 0, 0), gravityScale is 0 so the global-space noise is mixed out entirely, and
    // ghostLayerFullness is 0 so the magnitude falls back on its floor of 0.3.
    //
    // What is left is exactly this: local noise, scaled by (0.7, 0, 0), over world position and
    // time. GameTime is a fraction of a day here where 1.21.1's uniform was a tick count, so it is
    // scaled back into ticks to keep the shimmer at the speed it was tuned to.
    float t = (GameTime * 24000.0) / 500.0;
    vec3 n = getNoiseFromPositionAndTime(vec4(worldPos / 10.0, t), vec3(0.7, 0.0, 0.0));
    float ghostNoiseMagnitude = abs(n.x + n.y + n.z) * 0.3;

    // Brightness and saturation raised to a power below one, so the block breathes rather than
    // flashes: the noise never adds light, it only pulls the two towards full.
    float s = exp(-ghostNoiseMagnitude);
    vec3 hsv = rgb2hsv(color.rgb);
    hsv.z = pow(hsv.z, s);
    hsv.y = pow(hsv.y, s);
    color.rgb = hsv2rgb(hsv);

    color = mix(FogColor * vec4(1, 1, 1, color.a), color, ChunkVisibility);
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}

uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D MainSampler;
uniform sampler2D MainDepthSampler;

in vec2 texCoord;

out vec4 fragColor;

/**
 * How much nearer the world has to be, in blocks, before the overlay counts as hidden.
 *
 * A distance rather than a ratio. Reversed depth is 1/z, so a fixed percentage is a
 * different distance at two blocks than at twenty -- tight enough to survive at range and
 * it lets the effect through solid blocks up close, loose enough up close and it cuts the
 * effect away along a moving edge. Converting back to z compares the thing that actually
 * matters.
 *
 * A quarter block is more than one frame of camera travel at sprinting speed and far less
 * than the span of an envelope, which is the gap real occlusion has to clear.
 */
const float HIDDEN_MARGIN = 0.25;

/**
 * How much of the surface's own steepness to add to that margin.
 *
 * A flat margin is only enough while a surface is roughly face-on. Seen at a grazing angle the
 * same surface spans several blocks of depth inside a single pixel, so the two depths being
 * compared can differ by far more than a quarter block while still describing the same place --
 * and the effect gets cut away in slivers wherever a wall is sighted nearly edge-on.
 *
 * dFdx/dFdy give exactly that steepness: how much z moves between neighbouring pixels. Adding a
 * multiple of it widens the margin only where the geometry is steep, which is the shader-side
 * equivalent of a slope-scaled depth bias.
 */
const float SLOPE_MARGIN = 2.0;

/** Minecraft's near plane; reversed depth is NEAR / z, so z is NEAR / depth. */
const float NEAR_PLANE = 0.05;

void main() {
    vec4 baseColor = texture(MainSampler, texCoord);

    // Behind by a margin, not merely behind.
    //
    // Less-than because 26.2 reverses the depth buffer: near is 1 and far is 0, so a smaller
    // depth is farther away. This asks whether the heated volume is behind what the world drew
    // and leaves the pixel alone if it is.
    //
    // The margin is the important half. The volume's surfaces are exactly the inner faces of the
    // envelope blocks, so across most of the picture these two depths are the same number to
    // within a rounding error and the comparison goes whichever way the last bit fell. That is a
    // per-pixel coin toss over a whole surface, and it moves as the camera does -- the hard-edged
    // band that drifts across the envelope while walking, and a crawling stipple when the depth
    // bias is not there to hide it.
    //
    // Relative rather than absolute, because reversed depth is not linear: the gap between
    // neighbouring representable depths near the camera is nothing like the gap far away, so a
    // fixed epsilon is either useless close up or enormous at range.
    float overlayDepth = texture(DiffuseDepthSampler, texCoord).r;
    float worldDepth = texture(MainDepthSampler, texCoord).r;

    // Both distances back in blocks, so the margin below means what it says.
    float overlayZ = NEAR_PLANE / max(overlayDepth, 1.0e-6);
    float worldZ = NEAR_PLANE / max(worldDepth, 1.0e-6);

    float slope = length(vec2(dFdx(worldZ), dFdy(worldZ)));

    if (overlayZ > worldZ + HIDDEN_MARGIN + slope * SLOPE_MARGIN) {
        fragColor = baseColor;
        return;
    }

    vec3 base = baseColor.rgb;
    vec4 blendColor = texture(DiffuseSampler0, texCoord);
    vec3 blend = blendColor.rgb;

    // from https://github.com/mattdesl/glsl-blend-soft-light/blob/master/index.glsl
    vec4 softLit = mix(vec4(mix(
        sqrt(base) * (2.0 * blend - 1.0) + 2.0 * base * (1.0 - blend),
        2.0 * base * blend + base * base * (1.0 - 2.0 * blend),
        step(base, vec3(0.5))
    ), baseColor.a), baseColor, 1.0 - blendColor.a);
    fragColor = softLit;
}
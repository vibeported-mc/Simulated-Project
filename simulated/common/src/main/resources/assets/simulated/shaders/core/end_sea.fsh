#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D SkySampler;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

/*
 * 26.2 note -- the shadowing is gone from this, and the sea draws unshadowed.
 *
 * It used to sample ShadowDepthSampler and ShadowStrengthSampler, two framebuffer attachments filled
 * by EndSeaShadowRenderer's sky-light shadow map, and fade the sea against a plane height passed in
 * as a CameraY uniform. The shadow render is parked (SIMULATED-26.2-OPEN-QUESTIONS section 1) so
 * there is nothing behind those samplers; a render type's textures are static bindings and cannot
 * name a framebuffer; and a pipeline has nowhere to put a per-draw uniform like CameraY. All three
 * come back together, and probably mean the sea going back to being drawn by Veil's own
 * ShaderProgram rather than as a render type.
 */
void main() {
    vec4 color = texture(SkySampler, texCoord);
    fragColor = (vertexColor * color + vertexColor * 0.025) * ColorModulator;
}

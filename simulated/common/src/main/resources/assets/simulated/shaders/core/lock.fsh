#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;

    // lock.png is an indexed PNG whose transparent palette entry is black, and this type carries no
    // blend state -- 1.21.1 set no transparency shard either, so those texels wrote their RGB and
    // the marker sat in a black square. The alpha is binary, 0 or 255, so discarding is identical to
    // blending here, leaves the pipeline state as 1.21.1 had it, and keeps the cut-away texels from
    // writing depth.
    if (color.a < 0.1) {
        discard;
    }

    fragColor = color * ColorModulator;
}

#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
// UV0, not UV: 26.2 matches a vertex attribute to its format element by name, and
// POSITION_TEX_COLOR calls this one UV0. OpenGL bound it anyway; Vulkan refuses the
// whole pipeline with "Shader expects input variables which are not being provided".
in vec2 UV0;
in vec4 Color;

out float vertexDistance;
out vec2 lengthData;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = length((ModelViewMat * vec4(Position, 1.0)).xyz);
    lengthData = UV0;
    vertexColor = Color;
}

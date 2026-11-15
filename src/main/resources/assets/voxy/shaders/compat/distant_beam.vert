#version 450 core
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUv;
layout(location = 2) in vec2 aLightUv;
layout(location = 3) in vec4 aColor;
layout(location = 4) in uint aFace;
layout(location = 0) uniform mat4 uTransform;
layout(location = 0) out vec2 fUv;
layout(location = 1) out vec2 fLightUv;
layout(location = 2) out vec4 fColor;
void main() {
    gl_Position = uTransform * vec4(aPos, 1.0);
    fUv = aUv;
    fLightUv = aLightUv;
    fColor = aColor;
}

#version 450 core
layout(binding = 0) uniform sampler2D uTexture;
layout(binding = 1) uniform sampler2D uLightMap;
layout(location = 0) in vec2 fUv;
layout(location = 1) in vec2 fLightUv;
layout(location = 2) in vec4 fColor;
layout(location = 0) out vec4 outColor;
void main() {
    vec4 texel = texture(uTexture, fUv);
    if (texel.a < 0.01) discard;
    outColor = vec4(texel.rgb * fColor.rgb, texel.a * 0.55);
}

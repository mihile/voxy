#version 450 core
layout(binding = 0) uniform sampler2D uTexture;
layout(binding = 1) uniform sampler2D uLightMap;
layout(location = 0) in vec2 fUv;
layout(location = 1) in vec2 fLightUv;
layout(location = 2) in vec4 fColor;
#ifdef PATCHED_SHADER
layout(location = 3) flat in uint fFace;
struct VoxyFragmentParameters {
    vec4 sampledColour;
    vec2 tile;
    vec2 uv;
    uint face;
    uint modelId;
    vec2 lightMap;
    vec4 tinting;
    uint customId;
};
void voxy_emitFragment(VoxyFragmentParameters parameters);
#else
layout(location = 0) out vec4 outColor;
#endif
void main() {
    vec4 texel = texture(uTexture, fUv);
    if (texel.a < 0.1) discard;
#ifdef PATCHED_SHADER
    voxy_emitFragment(VoxyFragmentParameters(vec4(texel.rgb, 1.0), vec2(0.0), fUv,
            fFace, 0u, fLightUv, vec4(fColor.rgb, 1.0), 0u));
#else
    outColor = vec4(texel.rgb * fColor.rgb * texture(uLightMap, fLightUv).rgb, 1.0);
#endif
}

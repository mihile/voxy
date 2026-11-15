#version 450 core

layout(binding = 0) uniform sampler2D depthTex;
layout(location = 1) uniform vec2 scaleFactor;

#import <voxy:util/depthutils.glsl>

in vec2 UV;

void main() {
    float vanillaDepth = texture(depthTex, UV * scaleFactor).r;
    if (abs(vanillaDepth - FAR) < 1e-5) discard;
    gl_FragDepth = NEAR;
}

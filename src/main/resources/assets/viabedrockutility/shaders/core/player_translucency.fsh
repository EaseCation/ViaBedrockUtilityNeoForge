#version 150

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 player = texture(InSampler, texCoord);
    if (player.a == 0.0) {
        discard;
    }
    // Offscreen blending produces premultiplied color. Fade the assembled player only once.
    fragColor = player * 0.3;
    gl_FragDepth = texture(DepthSampler, texCoord).r;
}

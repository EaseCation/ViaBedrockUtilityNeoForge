#version 150

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexTint;
in vec4 litVertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    // Bedrock emissive-alpha uses texture alpha for lighting, not opacity.
    // Only transparent black is a hole; alpha-zero colored pixels stay visible.
    if (texel.r + texel.g + texel.b + texel.a == 0.0) discard;
    vec3 illumination = mix(vertexTint.rgb, litVertexColor.rgb * lightMapColor.rgb, texel.a);
    vec4 color = vec4(texel.rgb * illumination, vertexTint.a) * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}

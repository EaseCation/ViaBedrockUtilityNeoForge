#version 150
#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
in vec3 Position;
in vec2 UV0;
in vec3 Normal;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
layout(std140) uniform RigidTransforms {
    mat4 RigidPose;
    vec4 RigidColor;
    vec3 RigidLight;
    mat4 RigidNormalUv;
    float RigidUnused;
};
out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexTint;
out vec4 litVertexColor;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
void main() {
    vec3 transformed = (RigidPose * vec4(Position,1.0)).xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(transformed,1.0);
    sphericalVertexDistance = fog_spherical_distance(transformed);
    cylindricalVertexDistance = fog_cylindrical_distance(transformed);
    vec3 normal = mat3(RigidNormalUv) * Normal;
    if (RigidLight.z < 0.5 && dot(normal,normal) > 0.0) normal=normalize(normal);
    vertexTint = RigidColor;
    litVertexColor = minecraft_mix_light(Light0_Direction,Light1_Direction,normal,RigidColor);
#ifdef NO_CARDINAL_LIGHTING
    vertexColor = RigidColor;
#else
    vertexColor = litVertexColor;
#endif
    lightMapColor = texelFetch(Sampler2,ivec2(RigidLight.xy)/16,0);
    overlayColor = texelFetch(Sampler1,ivec2(0,10),0);
    texCoord0 = UV0 * vec2(RigidNormalUv[0].w,RigidNormalUv[1].w) + vec2(RigidNormalUv[2].w,RigidNormalUv[3].x);
}

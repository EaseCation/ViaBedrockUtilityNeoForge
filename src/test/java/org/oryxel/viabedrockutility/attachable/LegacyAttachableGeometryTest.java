package org.oryxel.viabedrockutility.attachable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.easecation.bedrockmotion.animator.AnimationClock;
import net.easecation.bedrockmotion.attachable.AttachableAnimationRuntime;
import net.easecation.bedrockmotion.mocha.MoLangEvaluationContext;
import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.content.Content;
import net.minecraft.client.model.Model;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.adapter.McBoneModel;
import org.oryxel.viabedrockutility.util.GeometryUtil;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyAttachableGeometryTest {
    // Source fixture: rl_defense's unbound rightitem root, pole geometry and default_trident pose.
    private static PackManager packs() throws Exception {
        final Content content = new Content();
        for (String path : List.of("attachables/trident.player.json", "models/entity/xz_trident.geo.json",
                "animations/player.toTrident.json")) {
            try (var input = LegacyAttachableGeometryTest.class.getResourceAsStream(
                    "/attachable-legacy-trident/" + path)) {
                assertNotNull(input, path);
                content.putString(path, new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return new PackManager(List.of(content));
    }

    @Test
    void animatedTridentVerticesStayOnTheRightInBothViews() throws Exception {
        final PackManager packs = packs();
        final var definition = packs.getAttachableDefinitions().candidatesFor("minecraft:trident").getFirst();
        final var geometry = packs.getModelDefinitions().getEntityModels().get("geometry.xz_trident");
        final Model model = GeometryUtil.buildAttachableModel(geometry, "geometry.xz_trident", ignored -> null);
        final Model absoluteModel = GeometryUtil.buildModel(geometry, false, false);
        final var animation = new AttachableAnimationRuntime(definition.data(), packs,
                new AnimationClock.Client(), null);
        final Scope scope = scope();
        animation.tick(1, scope, MoLangEvaluationContext.EMPTY);
        animation.sample(new McBoneModel(model), 0.5F, scope);
        animation.sample(new McBoneModel(absoluteModel), 0.5F, scope);

        // Actual first-person physicalAnchor from the reported generation 45 diagnostic. This
        // intentionally tests submitted vertices, since the anchor itself was already on the right.
        final Matrix4f firstPerson = new Matrix4f().set(new float[]{
                0.07498141F, 0.43650693F, 0.896571F, 0,
                -0.99601465F, -0.010763809F, 0.08853853F, 0,
                0.04829829F, -0.8996365F, 0.43396017F, 0,
                0.78847736F, -0.18176085F, -1.5557206F, 1});
        final List<Vector3f> firstVertices = vertices(model, firstPerson);
        assertFalse(firstVertices.isEmpty());
        assertTrue(firstVertices.stream().allMatch(vertex -> vertex.x > 0.7F), firstVertices.toString());
        assertTrue(vertices(absoluteModel, firstPerson).stream().allMatch(vertex -> vertex.x < 0),
                "The old absolute presentation origin reproduces the reported left-hand placement");

        // Right is negative X in the third-person Java player model. The resource's third-person
        // item rotation turns an erroneous +24-pixel Y origin into a leftward X displacement.
        final Matrix4f thirdPerson = new Matrix4f().translation(-0.375F, 0.5F, 0)
                .rotateZYX((float) Math.toRadians(-90), (float) Math.toRadians(45), 0);
        final List<Vector3f> thirdVertices = vertices(model, thirdPerson);
        assertTrue(thirdVertices.stream().allMatch(vertex -> vertex.x < -0.3F), thirdVertices.toString());
        assertTrue(vertices(absoluteModel, thirdPerson).stream().allMatch(vertex -> vertex.x > 0));
    }

    @Test
    void javaModeForcesFallbackForThreeDimensionalTrident() throws Exception {
        final PackManager packs = packs();
        final var definition = packs.getAttachableDefinitions().candidatesFor("minecraft:trident").getFirst();
        assertNull(AttachableRuntimeManager.javaItemFallbackDetail(
                AttachableRuntimeManager.DebugRenderMode.AUTO, packs, definition));
        assertNull(AttachableRuntimeManager.javaItemFallbackDetail(
                AttachableRuntimeManager.DebugRenderMode.VBU, packs, definition));
        final String forced = AttachableRuntimeManager.javaItemFallbackDetail(
                AttachableRuntimeManager.DebugRenderMode.JAVA_ITEM, packs, definition);
        assertNotNull(forced);
        assertTrue(forced.contains("renderPath=JAVA_ITEM_FALLBACK"));
        assertTrue(forced.contains("Debug JAVA_ITEM mode"));
        assertTrue(forced.contains("geometry.xz_trident"));
    }

    @Test
    void explicitBindingPreservesExistingAnimatedGeometry() throws Exception {
        final PackManager packs = packs();
        final var definition = packs.getAttachableDefinitions().candidatesFor("minecraft:trident").getFirst();
        final var geometry = packs.getModelDefinitions().getEntityModels().get("geometry.xz_trident");
        geometry.getParents().getFirst().setBinding("q.item_slot_to_bone_name(c.item_slot)");
        final Model baseline = GeometryUtil.buildModel(geometry, false, false);
        final Model attachable = GeometryUtil.buildAttachableModel(geometry, "geometry.xz_trident");
        final var animation = new AttachableAnimationRuntime(definition.data(), packs,
                new AnimationClock.Client(), null);
        final Scope scope = scope();
        animation.tick(1, scope, MoLangEvaluationContext.EMPTY);
        animation.sample(new McBoneModel(baseline), 0.5F, scope);
        animation.sample(new McBoneModel(attachable), 0.5F, scope);
        assertEquals(vertices(baseline, new Matrix4f()), vertices(attachable, new Matrix4f()));
    }

    @Test
    void detachedTridentKeepsTheCoordinateFrameUsedByItsBounds() throws Exception {
        final var geometry = packs().getModelDefinitions().getEntityModels().get("geometry.xz_trident");
        final Model baseline = GeometryUtil.buildModel(geometry, false, false);
        final Model detached = GeometryUtil.buildDetachedAttachableModel(geometry, "geometry.xz_trident", null);
        assertEquals(vertices(baseline, new Matrix4f()), vertices(detached, new Matrix4f()));
    }

    @Test
    void mixedRootsRebaseOnlyTheLegacySubtreeIncludingChildAndCubePivots() throws Exception {
        final Content content = new Content();
        content.putString("models/entity/mixed.geo.json", """
                {"format_version":"1.16.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.mixed","texture_width":16,"texture_height":16},
                  "bones":[
                    {"name":"rightitem","pivot":[10,20,30]},
                    {"name":"child","parent":"rightitem","pivot":[11,22,33],
                     "cubes":[{"origin":[10,20,30],"size":[2,2,2],"pivot":[11,21,31],
                               "rotation":[0,0,90],"uv":[0,0]}]},
                    {"name":"explicit","binding":"leftitem","pivot":[10,20,30],
                     "cubes":[{"origin":[10,20,30],"size":[2,2,2],"uv":[0,0]}]}
                  ]}]}
                """);
        final var packs = new PackManager(List.of(content));
        final var geometry = packs.getModelDefinitions().getEntityModels().get("geometry.mixed");
        final Model model = GeometryUtil.buildAttachableModel(geometry, "geometry.mixed");
        final var legacy = model.root().getChild("rightitem");
        final var child = legacy.getChild("child");
        assertEquals(0.0F, extension(legacy).viaBedrockUtility$getPivot().length(), 1.0E-6F);
        assertEquals(new Vector3f(1, -2, 3), extension(child).viaBedrockUtility$getPivot());
        final var cubePart = extension(child).viaBedrockUtility$getChildren().values().iterator().next();
        assertEquals(new Vector3f(1, -1, 1), extension(cubePart).viaBedrockUtility$getPivot());
        assertEquals(BedrockTransformConvention.toJavaModel(new Vector3f(10,20,30)),
                extension(model.root().getChild("explicit")).viaBedrockUtility$getPivot());
        // Conversion must never rewrite shared pack geometry used by another owner/pass.
        assertEquals(20.0F, geometry.getParents().getFirst().getPivot().getY());
    }

    private static org.oryxel.viabedrockutility.mixin.interfaces.IModelPart extension(
            net.minecraft.client.model.geom.ModelPart part) {
        return (org.oryxel.viabedrockutility.mixin.interfaces.IModelPart) (Object) part;
    }

    private static Scope scope() {
        final Scope scope = Scope.create();
        final var query = new MutableObjectBinding();
        final var variable = new MutableObjectBinding();
        scope.set("query", query);
        scope.set("q", query);
        scope.set("variable", variable);
        scope.set("v", variable);
        return scope;
    }

    private static List<Vector3f> vertices(Model model, Matrix4f host) {
        final List<Vector3f> vertices = new ArrayList<>();
        final VertexConsumer consumer = (VertexConsumer) Proxy.newProxyInstance(
                VertexConsumer.class.getClassLoader(), new Class<?>[]{VertexConsumer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("addVertex") && args.length >= 3 && args[0] instanceof Float) {
                        vertices.add(new Vector3f((float) args[0], (float) args[1], (float) args[2]));
                    }
                    return method.getReturnType() == void.class ? null : proxy;
                });
        final PoseStack poses = new PoseStack();
        poses.mulPose(host);
        model.renderToBuffer(poses, consumer, 0xF000F0, 0, -1);
        return vertices;
    }
}

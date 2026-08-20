package org.oryxel.viabedrockutility.attachable;

import net.easecation.bedrockmotion.mocha.MoLangEvaluationContext;
import net.easecation.bedrockmotion.render.RenderControllerEvaluator;
import org.cube.converter.model.element.Parent;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.cube.converter.util.element.Position2V;
import org.cube.converter.util.element.Position3V;
import org.junit.jupiter.api.Test;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachableRenderPlannerTest {
    private static final Set<String> HOST_BONES = Set.of("leftitem", "rightitem");

    private static Parent bone(String name, String parent, String binding) {
        final Parent bone = new Parent(name, Position3V.zero(), Position3V.zero());
        bone.setParent(parent);
        bone.setBinding(binding);
        return bone;
    }

    private static BedrockGeometryModel geometry(String identifier, Parent... bones) {
        final BedrockGeometryModel geometry = new BedrockGeometryModel(identifier, new Position2V(64, 64));
        geometry.getParents().addAll(List.of(bones));
        return geometry;
    }

    private static RenderControllerEvaluator.EvaluatedRenderPass pass(String key, String geometry) {
        return new RenderControllerEvaluator.EvaluatedRenderPass(key, null, geometry,
                "test:textures/attachable", Map.of(), Map.of(), false, true,
                RenderControllerEvaluator.BlendMode.OPAQUE, false, false, 1.0F, 0xFFFFFFFF);
    }

    @Test
    void eachRootResolvesItsOwnBinding() throws Exception {
        final BedrockGeometryModel geometry = geometry("geometry.dual",
                bone("root_a", "", "leftitem"),
                bone("root_b", "", "rightitem"),
                bone("child", "root_a", "query.ignored"));
        final List<String> warnings = new ArrayList<>();

        final Map<String, String> bindings = AttachableRenderPlanner.resolveRootBindings(geometry,
                HOST_BONES::contains, "rightitem", Scope.create(), MoLangEvaluationContext.EMPTY,
                (key, message) -> warnings.add(key));

        assertEquals(Map.of("root_a", "leftitem", "root_b", "rightitem"), bindings);
        assertEquals(List.of("non-root-binding:child"), warnings);
    }

    @Test
    void contextTernaryBindingEvaluatesAsExpression() throws Exception {
        final BedrockGeometryModel geometry = geometry("geometry.ternary",
                bone("root_a", "", "c.is_first_person ? 'leftitem' : 'rightitem'"));
        final Scope scope = Scope.create();
        final MutableObjectBinding context = new MutableObjectBinding();
        context.set("is_first_person", Value.of(true));
        scope.set("c", context);

        final Map<String, String> bindings = AttachableRenderPlanner.resolveRootBindings(geometry,
                HOST_BONES::contains, "rightitem", scope, MoLangEvaluationContext.EMPTY,
                (key, message) -> {
                });

        assertEquals(Map.of("root_a", "leftitem"), bindings);
    }

    @Test
    void blankBindingFallsBackToDefaultItemBone() throws Exception {
        final BedrockGeometryModel geometry = geometry("geometry.default", bone("root_a", "", ""));

        final Map<String, String> bindings = AttachableRenderPlanner.resolveRootBindings(geometry,
                HOST_BONES::contains, "leftitem", Scope.create(), MoLangEvaluationContext.EMPTY,
                (key, message) -> {
                });

        assertEquals(Map.of("root_a", "leftitem"), bindings);
    }

    @Test
    void preflightAbortsWhenAnyPassMissesItsHostBone() throws Exception {
        final BedrockGeometryModel geometryA = geometry("geometry.a", bone("root_a", "", "rightitem"));
        final BedrockGeometryModel geometryB = geometry("geometry.b", bone("root_b", "", "missing_bone"));
        final Map<String, BedrockGeometryModel> geometries = Map.of(
                "geometry.a", geometryA, "geometry.b", geometryB);
        final List<String> warnings = new ArrayList<>();
        final AtomicReference<String> fatal = new AtomicReference<>();

        // The second pass' host bone can only be missing if validation runs before commit:
        // plan() must veto the whole frame (null) even though the first pass is perfectly valid.
        final List<AttachableRenderPlanner.PlannedPass<String>> planned = AttachableRenderPlanner.plan(
                List.of(pass("pass.a", "geometry.a"), pass("pass.b", "geometry.b")),
                geometries::get, HOST_BONES::contains, "rightitem",
                name -> HOST_BONES.contains(name) ? name : null,
                Scope.create(), MoLangEvaluationContext.EMPTY,
                (key, message) -> warnings.add(key), fatal::set);

        assertNull(planned);
        assertEquals("Host binding bone is missing: missing_bone", fatal.get());
        assertTrue(warnings.contains("binding:missing_bone"));
    }

    @Test
    void preflightGroupsRootsByHostBone() throws Exception {
        final BedrockGeometryModel geometry = geometry("geometry.dual",
                bone("root_a", "", "leftitem"), bone("root_b", "", "rightitem"));

        final List<AttachableRenderPlanner.PlannedPass<String>> planned = AttachableRenderPlanner.plan(
                List.of(pass("pass.a", "geometry.dual")),
                Map.of("geometry.dual", geometry)::get, HOST_BONES::contains, "rightitem",
                name -> HOST_BONES.contains(name) ? name : null,
                Scope.create(), MoLangEvaluationContext.EMPTY,
                (key, message) -> {
                }, detail -> {
                });

        assertNotNull(planned);
        assertEquals(1, planned.size());
        final var groups = planned.getFirst().hostGroups();
        assertEquals(List.of("root_a"), groups.get("leftitem").roots());
        assertEquals(List.of("root_b"), groups.get("rightitem").roots());
    }

    @Test
    void missingGeometrySkipsThePassWithoutVetoing() throws Exception {
        final List<String> warnings = new ArrayList<>();

        final List<AttachableRenderPlanner.PlannedPass<String>> planned = AttachableRenderPlanner.plan(
                List.of(pass("pass.a", "geometry.absent")),
                identifier -> null, HOST_BONES::contains, "rightitem",
                name -> HOST_BONES.contains(name) ? name : null,
                Scope.create(), MoLangEvaluationContext.EMPTY,
                (key, message) -> warnings.add(key), detail -> {
                });

        assertNotNull(planned);
        assertTrue(planned.isEmpty());
        assertEquals(List.of("geometry:geometry.absent"), warnings);
    }
}

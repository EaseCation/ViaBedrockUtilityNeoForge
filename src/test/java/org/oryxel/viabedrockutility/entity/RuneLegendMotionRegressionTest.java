package org.oryxel.viabedrockutility.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.easecation.bedrockmotion.animator.AnimationClock;
import net.easecation.bedrockmotion.controller.AnimationControllerInstance;
import net.easecation.bedrockmotion.model.AnimationEventListener;
import net.easecation.bedrockmotion.mocha.MoLangEngine;
import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.content.Content;
import org.junit.jupiter.api.Test;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.binding.JavaObjectBinding;
import team.unnamed.mocha.runtime.standard.MochaMath;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.Value;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuneLegendMotionRegressionTest {
    @Test
    void hunterControllerKeepsOriginalWeightsWithNativeMotionQueries() throws IOException {
        final Content content = new Content();
        content.putString("animations/hunter.json", resource("hunter-animations.json"));
        content.putString("animation_controllers/hunter.json", resource("hunter-controller.json"));
        final PackManager packs = new PackManager(List.of(content));
        final Scope scope = scope();
        final MutableObjectBinding queries = (MutableObjectBinding) scope.get("query");
        final BedrockEntityMotion motion = new BedrockEntityMotion();
        for (int tick = 0; tick <= 100; tick++) {
            motion.tick(tick, tick * 0.1, 0.0, 0.0, 0.0F, false);
        }
        queries.set("ground_speed", Value.of(motion.groundSpeed()));
        queries.set("modified_move_speed", Value.of(motion.modifiedMoveSpeed(0.5F, false)));

        final AnimationClock.Client clock = new AnimationClock.Client();
        final AnimationControllerInstance controller = new AnimationControllerInstance(
                packs.getAnimationControllerDefinitions().getControllers()
                        .get("controller.animation.ghosarea_hunter_elite.move"),
                Map.of("idle", "animation.ghosarea_hunter_elite.idle",
                        "move_arm", "animation.ghosarea_hunter_elite.move_arm",
                        "move_leg", "animation.ghosarea_hunter_elite.move_leg"),
                packs.getAnimationDefinitions(), packs.getAnimationControllerDefinitions(),
                new AnimationEventListener() {
                    @Override
                    public void onTimelineEvent(List<String> expressions) {
                    }

                    @Override
                    public Scope getEntityScope() {
                        return scope;
                    }
                }, clock);
        controller.tick(scope);

        assertEquals("move", controller.currentStateName());
        final List<AnimationControllerInstance.PlaybackDebugSnapshot> entries =
                controller.debugSnapshot().entries();
        assertEquals(2, entries.size());
        for (AnimationControllerInstance.PlaybackDebugSnapshot entry : entries) {
            // 0.1 格/tick 的实际圣符移动权重应为 1，旧实现会得到 5。
            assertEquals(1.0F, entry.baseWeight(), 1.0e-5F);
        }

        for (int tick = 101; tick <= 130; tick++) {
            motion.tick(tick, 10.0, 0.0, 0.0, 0.0F, false);
        }
        queries.set("ground_speed", Value.of(motion.groundSpeed()));
        queries.set("modified_move_speed", Value.of(motion.modifiedMoveSpeed(1.0F, false)));
        clock.advanceTick(30L);
        controller.tick(scope);
        assertEquals("default", controller.currentStateName());
    }

    @Test
    void runeLegendNpcPreAnimationUsesAnimationDistanceAndUnitGlidingValue() throws IOException {
        final BedrockEntityMotion motion = new BedrockEntityMotion();
        for (int tick = 0; tick <= 20; tick++) {
            motion.tick(tick, tick * 0.1, 0.0, 0.0, 0.0F, false);
        }
        final JsonArray entities = JsonParser.parseString(resource("npc-scripts.json")).getAsJsonArray();
        for (JsonElement entity : entities) {
            final Scope scope = scope();
            final MutableObjectBinding queries = (MutableObjectBinding) scope.get("query");
            final MutableObjectBinding variables = (MutableObjectBinding) scope.get("variable");
            variables.set("gliding_speed_value", Value.of(1.0));
            queries.set("modified_move_speed", Value.of(motion.modifiedMoveSpeed(0.5F, false)));
            queries.set("modified_distance_moved", Value.of(motion.modifiedDistanceMoved(0.5F)));
            queries.set("is_on_ground", Value.of(true));
            queries.set("is_alive", Value.of(true));
            queries.set("life_time", Value.of(1.0));
            queries.setFunction("position_delta", axis -> axis == 0.0 ? 0.1 : 0.0);
            for (JsonElement script : entity.getAsJsonObject().getAsJsonArray("pre_animation")) {
                MoLangEngine.eval(scope, script.getAsString());
            }
            assertTrue(Math.abs(variables.get("tcos0").getAsNumber()) <= 22.92,
                    entity.getAsJsonObject().get("source").getAsString());
            assertEquals(0.002, variables.get("hand_bob").getAsNumber(), 1.0e-8);
        }
    }

    private static Scope scope() {
        final Scope scope = Scope.create();
        scope.set("math", JavaObjectBinding.of(MochaMath.class, null, new MochaMath()));
        final MutableObjectBinding queries = new MutableObjectBinding();
        final MutableObjectBinding variables = new MutableObjectBinding();
        scope.set("query", queries);
        scope.set("q", queries);
        scope.set("variable", variables);
        scope.set("v", variables);
        return scope;
    }

    private static String resource(String name) throws IOException {
        try (InputStream input = RuneLegendMotionRegressionTest.class
                .getResourceAsStream("/runelegend-motion/" + name)) {
            assertNotNull(input, name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

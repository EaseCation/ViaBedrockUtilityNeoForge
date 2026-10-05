package org.oryxel.viabedrockutility.animation;

import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.content.Content;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerMotionQueryBindingTest {
    @Test
    void playerScriptsReceivePhysicalSpeedAnimationPhaseAndVelocitySeparately() {
        final Content content = new Content();
        content.putString("entity/player.entity.json", """
                {"format_version":"1.10.0","minecraft:client_entity":{"description":{
                  "identifier":"minecraft:player","animations":{},
                  "scripts":{"animate":[],"pre_animation":[
                    "v.ground = q.ground_speed; v.vertical = q.vertical_speed;",
                    "v.phase = q.modified_distance_moved; v.amount = q.modified_move_speed;",
                    "v.velocity_y = q.position_delta(1); v.walk = q.walk_distance; v.frame = q.frame_alpha;"
                  ]}
                }}}
                """);
        final PlayerAnimationRuntime runtime = new PlayerAnimationRuntime(new PackManager(List.of(content)), Map.of());
        final PlayerModel model = new PlayerModel(LayerDefinition.create(
                PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(), false);
        final PlayerAnimationOwner owner = new PlayerAnimationOwner(model, content);
        runtime.sampleThirdPerson(model, state(owner, 1L, 0.0));
        runtime.sampleThirdPerson(model, state(owner, 2L, 0.5));
        final Map<String, String> variables = runtime.debugSnapshot().thirdPerson().variables();
        assertEquals(5.0, Double.parseDouble(variables.get("ground")), 1.0e-6);
        assertEquals(-3.0, Double.parseDouble(variables.get("vertical")), 1.0e-6);
        assertEquals(0.25, Double.parseDouble(variables.get("phase")), 1.0e-6);
        assertEquals(0.4, Double.parseDouble(variables.get("amount")), 1.0e-6);
        assertEquals(-0.15, Double.parseDouble(variables.get("velocity_y")), 1.0e-6);
        assertEquals(0.3, Double.parseDouble(variables.get("walk")), 1.0e-6);
        assertEquals(0.75, Double.parseDouble(variables.get("frame")), 1.0e-6);
    }

    private static PlayerAnimationState state(PlayerAnimationOwner owner, long tick, double x) {
        return new PlayerAnimationState(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), owner,
                PlayerAnimationState.View.THIRD_PERSON, tick, 0.75F, HumanoidArm.RIGHT,
                InteractionHand.MAIN_HAND, "", "", Set.of(),
                tick, 0.25F, 0.4F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 1.0F, false, true, true, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                0, 0, 0, 0.0F, 0.0F,
                x, 0.0D, 0.2D, -0.15D, 0.0D, 5.0F, -3.0F);
    }
}

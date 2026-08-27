package org.oryxel.viabedrockutility.animation;

import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.content.Content;
import net.easecation.bedrockmotion.model.IBoneModel;
import net.easecation.bedrockmotion.model.IBoneTarget;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerAnimationResourceClosureTest {
    @Test
    void deliveredVanillaAndGunPacksFormACompletePlayerRuntime() throws Exception {
        final Path packsRoot = Path.of(System.getProperty("vbu.workspaceRoot"),
                "ec-deploy-assets", "bedrock-loader-packs");
        final Path vanilla = packsRoot.resolve("vanilla.zip");
        final Path gun = packsRoot.resolve("ec_gun_r.zip");
        final String configuredStack = System.getProperty("vbu.playerPackStack", "");
        final List<Path> stack = configuredStack.isBlank()
                ? List.of(vanilla, gun)
                : Arrays.stream(configuredStack.split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .map(Path::of)
                .toList();
        for (Path pack : stack) {
            assertTrue(Files.isRegularFile(pack), pack.toString());
        }

        final PackManager packs = new PackManager(stack.stream()
                .map(PlayerAnimationResourceClosureTest::content)
                .toList());
        final var player = packs.getEntityDefinitions().getEntities().get("minecraft:player");
        assertNotNull(player);
        assertEquals("controller.animation.player.root",
                player.entityData().getAnimations().get("root"));
        assertNotNull(packs.getAnimationControllerDefinitions().getControllers()
                .get("controller.animation.player.root"));
        assertNotNull(packs.getAnimationDefinitions().getAnimations()
                .get("animation.gun_player.holding"));

        final PlayerAnimationRuntime runtime = new PlayerAnimationRuntime(packs, Map.of(
                "holding", "animation.gun_player.holding",
                "move.arms", "animation.gun_player.move.arms",
                "attack.rotations", "animation.gun_player.attack.rotations",
                "first_person_attack_rotation", "animation.gun_player.first_person.attack_rotation",
                "first_person_attack_rotation_item", "animation.gun_player.first_person.attack_rotation_item"));
        assertTrue(runtime.hasAnimations());

        final TestBoneModel model = new TestBoneModel();
        assertTrue(runtime.sampleThirdPerson(model,
                state(1L, "easecation:gun_rifle_dynamic_default")));
        assertEquals(-80.0F, model.rotationX("rightarm"), 1.0e-3F);
        assertEquals(-77.5F, model.rotationX("leftarm"), 1.0e-3F);

        assertTrue(runtime.sampleThirdPerson(model, state(2L, "")));
        assertEquals(0.0F, model.rotationX("rightarm"), 1.0e-3F);
        assertEquals(0.0F, model.rotationX("leftarm"), 1.0e-3F);
    }

    private static PlayerAnimationState state(long tick, String mainHandIdentifier) {
        return new PlayerAnimationState(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                PlayerAnimationState.View.THIRD_PERSON, tick, 0.0F,
                HumanoidArm.RIGHT, InteractionHand.MAIN_HAND,
                mainHandIdentifier, "", Set.of(),
                tick, 0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F, 1.0F,
                true, true, false, false, false, false, false,
                false, false, false, false, false, false, false, false,
                0, 0, 0, 0.0F, 0.0F, 0.0D, 0.0D, 0.0D);
    }

    private static Content content(Path path) {
        try {
            return new Content(Files.readAllBytes(path));
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static final class TestBoneModel implements IBoneModel {
        private final Map<String, IBoneTarget> bones = new LinkedHashMap<>();

        private TestBoneModel() {
            for (String name : List.of("root", "body", "head", "rightarm", "leftarm",
                    "rightleg", "leftleg", "rightitem", "leftitem")) {
                bones.put(name, new TestBone(name));
            }
        }

        private float rotationX(String name) {
            return bones.get(name).getRotation().x;
        }

        @Override
        public Map<String, IBoneTarget> getBoneIndex() {
            return bones;
        }

        @Override
        public Iterable<IBoneTarget> getAllBones() {
            return bones.values();
        }

        @Override
        public void resetAllBones() {
            bones.values().forEach(IBoneTarget::resetToDefaultPose);
        }
    }

    private static final class TestBone implements IBoneTarget {
        private final String name;
        private final Vector3f rotation = new Vector3f();
        private final Vector3f offset = new Vector3f();
        private float scaleX = 1.0F;
        private float scaleY = 1.0F;
        private float scaleZ = 1.0F;

        private TestBone(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Vector3f getRotation() {
            return rotation;
        }

        @Override
        public Vector3f getOffset() {
            return offset;
        }

        @Override
        public float getScaleX() {
            return scaleX;
        }

        @Override
        public float getScaleY() {
            return scaleY;
        }

        @Override
        public float getScaleZ() {
            return scaleZ;
        }

        @Override
        public void setScale(float x, float y, float z) {
            scaleX = x;
            scaleY = y;
            scaleZ = z;
        }

        @Override
        public void addOffset(Vector3f value) {
            offset.add(value);
        }

        @Override
        public void addRotation(Vector3f value) {
            rotation.add(value);
        }

        @Override
        public void addScale(float x, float y, float z) {
            scaleX += x;
            scaleY += y;
            scaleZ += z;
        }

        @Override
        public void resetToDefaultPose() {
            rotation.zero();
            offset.zero();
            scaleX = 1.0F;
            scaleY = 1.0F;
            scaleZ = 1.0F;
        }

        @Override
        public Map<String, IBoneTarget> getChildren() {
            return Map.of();
        }
    }
}

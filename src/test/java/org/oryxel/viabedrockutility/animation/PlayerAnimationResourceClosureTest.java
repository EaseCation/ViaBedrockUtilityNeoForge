package org.oryxel.viabedrockutility.animation;

import com.google.gson.JsonParser;
import net.easecation.bedrockmotion.animation.Animation;
import net.easecation.bedrockmotion.animation.vanilla.AnimateBuilder;
import net.easecation.bedrockmotion.model.IBoneModel;
import net.easecation.bedrockmotion.model.IBoneTarget;
import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.ServerAnimationLayer;
import net.easecation.bedrockmotion.pack.content.Content;
import net.easecation.bedrockmotion.pack.definitions.AnimationDefinitions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerAnimationResourceClosureTest {
    private static final Object PLAYER_INSTANCE = new Object();
    private static final Object LEVEL_INSTANCE = new Object();

    @Test
    void downloadedZePacksAndBundledVanillaFormACompletePlayerRuntime() throws Exception {
        final Path packsRoot = Path.of(System.getProperty("vbu.workspaceRoot"),
                "ec-deploy-assets", "bedrock-loader-packs");
        final Path codeFunPacks = Path.of(System.getProperty("vbu.workspaceRoot"),
                "ec-deploy-assets", "resource-packs", "CodeFunCore");
        final Path gun = packsRoot.resolve("ec_gun_r.zip");
        final String configuredStack = System.getProperty("vbu.playerPackStack", "");
        final List<Path> stack = configuredStack.isBlank()
                ? List.of(codeFunPacks.resolve("ec_hub.zip"),
                        codeFunPacks.resolve("ec_ze.zip"),
                        codeFunPacks.resolve("rl_defense.zip"),
                        codeFunPacks.resolve("rl_defense_ec_entity.zip"),
                        gun)
                : Arrays.stream(configuredStack.split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .map(Path::of)
                .toList();
        for (Path pack : stack) {
            assertTrue(Files.isRegularFile(pack), pack.toString());
        }

        final List<Content> downloaded = stack.stream()
                .map(PlayerAnimationResourceClosureTest::content)
                .toList();
        final ServerAnimationLayer downloadedLayer = ServerAnimationLayer.foldBottomToTop(
                downloaded.stream().map(ServerAnimationLayer::fromContent).toList());
        assertTrue(downloadedLayer.animationControllers()
                .containsKey("controller.animation.player.root"));

        final PackManager packs = new PackManager(downloaded);
        final var player = packs.getEntityDefinitions().getEntities().get("minecraft:player");
        assertNotNull(player);
        assertEquals("controller.animation.player.root",
                player.entityData().getAnimations().get("root"));
        assertNotNull(packs.getAnimationControllerDefinitions().getControllers()
                .get("controller.animation.player.root"));
        assertNotNull(packs.getAnimationDefinitions().getAnimations()
                .get("animation.gun_player.holding"));

        final Content playerHost = content(codeFunPacks.resolve("ec_hub.zip"));
        final var playerWithoutOptionalAlias = playerHost.getJson("entity/player.entity.json");
        assertNotNull(playerWithoutOptionalAlias.getAsJsonObject("minecraft:client_entity")
                .getAsJsonObject("description")
                .getAsJsonObject("animations")
                .remove("first_person_attack_rotation_item"));
        final Content optionalAliasOverlay = new Content();
        optionalAliasOverlay.putJson("entity/player.entity.json", playerWithoutOptionalAlias);
        final List<Content> missingOptionalAliasStack = new ArrayList<>(downloaded);
        missingOptionalAliasStack.add(optionalAliasOverlay);
        final PackManager missingOptionalAliasPacks = new PackManager(missingOptionalAliasStack);
        assertFalse(missingOptionalAliasPacks.getEntityDefinitions().getEntities()
                .get("minecraft:player").entityData().getAnimations()
                .containsKey("first_person_attack_rotation_item"));
        final PlayerAnimationRuntime missingOptionalAliasRuntime =
                new PlayerAnimationRuntime(missingOptionalAliasPacks, Map.of());
        missingOptionalAliasRuntime.sampleThirdPerson(new TestBoneModel(), state(1L, ""));

        final IllegalArgumentException missingRoot = assertThrows(IllegalArgumentException.class,
                () -> new PlayerAnimationRuntime(packs, Map.of(
                        "root", "controller.animation.player.root.missing")));
        assertTrue(missingRoot.getMessage().contains("controller.animation.player.root.missing"));

        final PlayerAnimationRuntime runtime = new PlayerAnimationRuntime(packs, Map.of(
                "holding", "animation.gun_player.holding",
                "move.arms", "animation.gun_player.move.arms",
                "attack.rotations", "animation.gun_player.attack.rotations",
                "first_person_attack_rotation", "animation.gun_player.first_person.attack_rotation",
                "first_person_attack_rotation_item", "animation.gun_player.first_person.attack_rotation_item"));
        final TestBoneModel model = new TestBoneModel();
        runtime.sampleThirdPerson(model, state(1L, "easecation:gun_rifle_dynamic_default"));
        assertEquals(-80.0F, model.rotationX("rightarm"), 1.0e-3F);
        assertEquals(-77.5F, model.rotationX("leftarm"), 1.0e-3F);

        runtime.sampleThirdPerson(model, state(2L, ""));
        assertEquals(0.0F, model.rotationX("rightarm"), 1.0e-3F);
        assertEquals(0.0F, model.rotationX("leftarm"), 1.0e-3F);

        runtime.sampleThirdPerson(model,
                state(PlayerAnimationState.View.THIRD_PERSON, 3L, "", 0.0F, 1.0F, 0.0F));
        assertEquals(-57.3F, model.rotationX("rightarm"), 1.0e-3F);
        assertEquals(57.3F, model.rotationX("leftarm"), 1.0e-3F);

        final PlayerAnimationRuntime zombie = new PlayerAnimationRuntime(packs, Map.of(
                "riding.arms", "animation.player.riding.arms.zombie",
                "move.arms", "animation.player.move.arms.zombie",
                "holding", "animation.player.holding.zombie"));
        zombie.sampleThirdPerson(model,
                state(PlayerAnimationState.View.THIRD_PERSON, 4L, "", 0.0F, 1.0F, 0.0F));
        assertEquals(-90.0F, model.rotationX("rightarm"), 1.0e-3F);
        assertEquals(-90.0F, model.rotationX("leftarm"), 1.0e-3F);
        assertTrue(Math.abs(model.rotationZ("rightarm")) <= 5.73F + 1.0e-3F);
        assertEquals(-model.rotationZ("rightarm"), model.rotationZ("leftarm"), 1.0e-3F);
        zombie.sampleThirdPerson(model,
                state(PlayerAnimationState.View.THIRD_PERSON, 24L, "", 1.0F, 1.0F, 0.0F));
        assertEquals(-90.0F, model.rotationX("rightarm"), 1.0e-3F);
        assertEquals(-90.0F, model.rotationX("leftarm"), 1.0e-3F);
        assertTrue(Math.abs(model.rotationZ("rightarm")) <= 5.73F + 1.0e-3F);
        assertEquals(-model.rotationZ("rightarm"), model.rotationZ("leftarm"), 1.0e-3F);
        zombie.sampleThirdPerson(model,
                state(PlayerAnimationState.View.THIRD_PERSON, 25L, "", 1.0F, 1.0F, 0.5F));
        assertTrue(model.rotationX("rightarm") < -100.0F,
                () -> "zombie attack rightarm=" + model.rotation("rightarm"));
        assertTrue(model.rotationX("leftarm") < -90.0F,
                () -> "zombie attack leftarm=" + model.rotation("leftarm"));
        zombie.sampleThirdPerson(model,
                state(PlayerAnimationState.View.THIRD_PERSON, 26L, "", 1.0F, 1.0F, 0.0F));
        assertEquals(-90.0F, model.rotationX("rightarm"), 1.0e-3F);
        assertEquals(-90.0F, model.rotationX("leftarm"), 1.0e-3F);

        runtime.sampleFirstPerson(model,
                state(PlayerAnimationState.View.FIRST_PERSON, 5L, "", 0.0F, 0.0F, 0.0F));
        final float restingFirstPersonArm = model.rotationX("rightarm");
        runtime.sampleFirstPerson(model,
                state(PlayerAnimationState.View.FIRST_PERSON, 6L, "", 0.0F, 0.0F, 0.5F));
        assertTrue(Math.abs(model.rotationX("rightarm") - restingFirstPersonArm) > 1.0F);

        runtime.sampleFirstPerson(model,
                state(PlayerAnimationState.View.FIRST_PERSON, 7L, "", 0.0F, 0.0F, 0.0F,
                        1.0F, false, false, 0.0D));
        final float equippedArmY = model.offsetY("rightarm");
        runtime.sampleFirstPerson(model,
                state(PlayerAnimationState.View.FIRST_PERSON, 8L, "", 0.0F, 0.0F, 0.0F,
                        0.0F, false, false, 0.0D));
        assertEquals(10.0F, Math.abs(model.offsetY("rightarm") - equippedArmY), 1.0e-3F);

        runtime.sampleFirstPerson(model,
                state(PlayerAnimationState.View.FIRST_PERSON, 9L, "", 0.0F, 0.0F, 0.0F,
                        1.0F, false, true, 0.0D));
        final float wideArmY = model.offsetY("rightarm");
        runtime.sampleFirstPerson(model,
                state(PlayerAnimationState.View.FIRST_PERSON, 10L, "", 0.0F, 0.0F, 0.0F,
                        1.0F, true, true, 0.0D));
        assertEquals(0.5F, Math.abs(model.offsetY("rightarm") - wideArmY), 1.0e-3F);

        final PlayerAnimationRuntime walkingRuntime = new PlayerAnimationRuntime(packs, Map.of());
        final TestBoneModel walkingModel = new TestBoneModel();
        walkingRuntime.sampleFirstPerson(walkingModel,
                state(PlayerAnimationState.View.FIRST_PERSON, 1L, "", 0.0F, 0.0F, 0.0F,
                        1.0F, false, true, 0.1D));
        final float zeroPhaseArmX = walkingModel.offsetX("rightarm");
        walkingRuntime.sampleFirstPerson(walkingModel,
                stateAtPartial(PlayerAnimationState.View.FIRST_PERSON, 1L, "",
                        0.25F, 0.0F, 0.0F, 0.5F));
        assertTrue(Math.abs(walkingModel.offsetX("rightarm") - zeroPhaseArmX) > 1.0e-3F);

        final PlayerAnimationRuntime leftHandedRuntime = new PlayerAnimationRuntime(packs, Map.of());
        final TestBoneModel leftHandedModel = new TestBoneModel();
        leftHandedRuntime.sampleThirdPerson(leftHandedModel,
                state(PlayerAnimationState.View.THIRD_PERSON, 1L, "minecraft:stone",
                        0.0F, 0.0F, 0.0F, 1.0F, false, false, 0.0D,
                        HumanoidArm.LEFT));
        assertEquals(0.0F, leftHandedModel.rotationX("rightarm"), 1.0e-3F);
        assertEquals(-18.0F, leftHandedModel.rotationX("leftarm"), 1.0e-3F);

        final PlayerAnimationRuntime canonicalFirstPerson = new PlayerAnimationRuntime(packs, Map.of());
        final TestBoneModel canonicalFirstPersonModel = new TestBoneModel();
        canonicalFirstPerson.sampleFirstPerson(canonicalFirstPersonModel,
                state(PlayerAnimationState.View.FIRST_PERSON, 1L, "",
                        0.0F, 0.0F, 0.0F, 1.0F, false, false, 0.0D,
                        HumanoidArm.RIGHT));
        final float canonicalRestingArm = canonicalFirstPersonModel.rotationX("rightarm");
        canonicalFirstPerson.sampleFirstPerson(canonicalFirstPersonModel,
                state(PlayerAnimationState.View.FIRST_PERSON, 2L, "",
                        0.0F, 0.0F, 0.5F, 1.0F, false, false, 0.0D,
                        HumanoidArm.RIGHT));
        assertTrue(Math.abs(canonicalFirstPersonModel.rotationX("rightarm")
                - canonicalRestingArm) > 1.0F);
        assertEquals(0.0F, canonicalFirstPersonModel.rotationX("leftarm"), 1.0e-3F);

        final PlayerAnimationRuntime yawA = new PlayerAnimationRuntime(packs, Map.of());
        final PlayerAnimationRuntime yawB = new PlayerAnimationRuntime(packs, Map.of());
        final TestBoneModel yawModelA = new TestBoneModel();
        final TestBoneModel yawModelB = new TestBoneModel();
        yawA.sampleThirdPerson(yawModelA,
                state(PlayerAnimationState.View.THIRD_PERSON, 1L, "",
                        0.0F, 0.0F, 0.0F, 1.0F, false, false, 0.0D,
                        HumanoidArm.RIGHT, 35.0F, 15.0F));
        yawB.sampleThirdPerson(yawModelB,
                state(PlayerAnimationState.View.THIRD_PERSON, 1L, "",
                        0.0F, 0.0F, 0.0F, 1.0F, false, false, 0.0D,
                        HumanoidArm.RIGHT, 35.0F, 165.0F));
        assertPoseEquals(yawModelA, yawModelB);

        final PlayerAnimationRuntime firstPersonYawA = new PlayerAnimationRuntime(packs, Map.of());
        final PlayerAnimationRuntime firstPersonYawB = new PlayerAnimationRuntime(packs, Map.of());
        final TestBoneModel firstPersonYawModelA = new TestBoneModel();
        final TestBoneModel firstPersonYawModelB = new TestBoneModel();
        firstPersonYawA.sampleFirstPerson(firstPersonYawModelA,
                state(PlayerAnimationState.View.FIRST_PERSON, 1L, "",
                        0.0F, 0.0F, 0.0F, 1.0F, false, false, 0.0D,
                        HumanoidArm.RIGHT, -70.0F, 15.0F));
        firstPersonYawB.sampleFirstPerson(firstPersonYawModelB,
                state(PlayerAnimationState.View.FIRST_PERSON, 1L, "",
                        0.0F, 0.0F, 0.0F, 1.0F, false, false, 0.0D,
                        HumanoidArm.RIGHT, 70.0F, 15.0F));
        assertPoseEquals(firstPersonYawModelA, firstPersonYawModelB);

        final PlayerAnimationRuntime pitchFlat = new PlayerAnimationRuntime(packs, Map.of());
        final PlayerAnimationRuntime pitchDown = new PlayerAnimationRuntime(packs, Map.of());
        final TestBoneModel pitchFlatModel = new TestBoneModel();
        final TestBoneModel pitchDownModel = new TestBoneModel();
        pitchFlat.sampleFirstPerson(pitchFlatModel,
                stateWithPitch(PlayerAnimationState.View.FIRST_PERSON, 1L, 0.0F));
        pitchDown.sampleFirstPerson(pitchDownModel,
                stateWithPitch(PlayerAnimationState.View.FIRST_PERSON, 1L, 60.0F));
        assertPoseEquals(pitchFlatModel, pitchDownModel);
        assertEquals("0.0", pitchDown.debugSnapshot().firstPerson()
                .queries().get("body_x_rotation"));
        assertEquals("0.0", pitchDown.debugSnapshot().firstPerson()
                .queries().get("target_x_rotation"));
        assertEquals("60.0", pitchDown.debugSnapshot().firstPerson()
                .variables().get("player_x_rotation"));

        final PlayerAnimationRuntime thirdPersonPitchDown = new PlayerAnimationRuntime(packs, Map.of());
        final TestBoneModel thirdPersonPitchDownModel = new TestBoneModel();
        thirdPersonPitchDown.sampleThirdPerson(thirdPersonPitchDownModel,
                stateWithPitch(PlayerAnimationState.View.THIRD_PERSON, 1L, 60.0F));
        assertEquals("60.0", thirdPersonPitchDown.debugSnapshot().thirdPerson()
                .queries().get("body_x_rotation"));
        assertEquals("60.0", thirdPersonPitchDown.debugSnapshot().thirdPerson()
                .queries().get("target_x_rotation"));
        assertEquals("60.0", thirdPersonPitchDown.debugSnapshot().thirdPerson()
                .queries().get("head_x_rotation"));

        final PlayerAnimationRuntime standingRuntime = new PlayerAnimationRuntime(packs, Map.of());
        final PlayerAnimationRuntime swimmingRuntime = new PlayerAnimationRuntime(packs, Map.of());
        final TestBoneModel standingModel = new TestBoneModel();
        final TestBoneModel swimmingModel = new TestBoneModel();
        standingRuntime.sampleThirdPerson(standingModel,
                stateSwimming(PlayerAnimationState.View.THIRD_PERSON, 1L, 0.0F));
        swimmingRuntime.sampleThirdPerson(swimmingModel,
                stateSwimming(PlayerAnimationState.View.THIRD_PERSON, 1L, 1.0F));
        assertTrue(Math.abs(swimmingModel.rotationX("root")
                - standingModel.rotationX("root")) > 80.0F);

        final AtomicLong now = new AtomicLong();
        final PlayerAnimationRuntime oneShotRuntime = new PlayerAnimationRuntime(
                packs, Map.of(), now::get);
        final PlayerAnimationRuntime baselineRuntime = new PlayerAnimationRuntime(packs, Map.of());
        final TestBoneModel baseline = new TestBoneModel();
        final Animation oneShot = Animation.parse(JsonParser.parseString("""
                {"animations":{"animation.test.once":{"loop":false,"animation_length":0.1,
                  "bones":{"rightarm":{"rotation":[20,0,0]}}}}}
                """).getAsJsonObject()).getFirst();
        oneShotRuntime.playOnce("animation.test.once",
                new AnimationDefinitions.AnimationData(oneShot, AnimateBuilder.build(oneShot)));
        oneShotRuntime.sampleThirdPerson(model,
                state(PlayerAnimationState.View.THIRD_PERSON, 7L, "", 0.0F, 0.0F, 0.0F));
        baselineRuntime.sampleThirdPerson(baseline,
                state(PlayerAnimationState.View.THIRD_PERSON, 7L, "", 0.0F, 0.0F, 0.0F));
        assertEquals(20.0F, model.rotationX("rightarm"), 1.0e-3F);
        now.set(101L);
        oneShotRuntime.sampleThirdPerson(model,
                state(PlayerAnimationState.View.THIRD_PERSON, 8L, "", 0.0F, 0.0F, 0.0F));
        baselineRuntime.sampleThirdPerson(baseline,
                state(PlayerAnimationState.View.THIRD_PERSON, 8L, "", 0.0F, 0.0F, 0.0F));
        assertEquals(baseline.rotationX("rightarm"), model.rotationX("rightarm"), 1.0e-3F);
    }

    @Test
    void bedrockMovementQueriesKeepWalkPhaseSeparateFromGroundDistance() throws Exception {
        final Path workspace = Path.of(System.getProperty("vbu.workspaceRoot"));
        final Path loaderPacks = workspace.resolve("ec-deploy-assets/bedrock-loader-packs");
        final Path codeFunPacks = workspace.resolve("ec-deploy-assets/resource-packs/CodeFunCore");
        final PackManager packs = new PackManager(List.of(
                content(codeFunPacks.resolve("ec_hub.zip")),
                content(codeFunPacks.resolve("ec_ze.zip")),
                content(loaderPacks.resolve("ec_gun_r.zip"))));

        final TestBoneModel phaseZeroModel = new TestBoneModel();
        final TestBoneModel phaseOneModel = new TestBoneModel();
        new PlayerAnimationRuntime(packs, Map.of()).sampleThirdPerson(phaseZeroModel,
                movementState(PlayerAnimationState.View.THIRD_PERSON,
                        1L, 0.0D, 0.0F, 1.0F, true, false));
        new PlayerAnimationRuntime(packs, Map.of()).sampleThirdPerson(phaseOneModel,
                movementState(PlayerAnimationState.View.THIRD_PERSON,
                        1L, 0.0D, 1.0F, 1.0F, true, false));
        assertTrue(Math.abs(phaseZeroModel.rotationX("rightarm")
                - phaseOneModel.rotationX("rightarm")) > 5.0F);

        final PlayerAnimationRuntime firstA = new PlayerAnimationRuntime(packs, Map.of());
        final PlayerAnimationRuntime firstB = new PlayerAnimationRuntime(packs, Map.of());
        final TestBoneModel firstModelA = new TestBoneModel();
        final TestBoneModel firstModelB = new TestBoneModel();
        firstA.sampleFirstPerson(firstModelA, movementState(PlayerAnimationState.View.FIRST_PERSON,
                1L, 0.0D, 0.0F, 0.4F, true, false));
        firstB.sampleFirstPerson(firstModelB, movementState(PlayerAnimationState.View.FIRST_PERSON,
                1L, 0.0D, 0.0F, 0.4F, true, false));
        firstA.sampleFirstPerson(firstModelA, movementState(PlayerAnimationState.View.FIRST_PERSON,
                2L, 0.25D, 0.0F, 0.4F, true, false));
        firstB.sampleFirstPerson(firstModelB, movementState(PlayerAnimationState.View.FIRST_PERSON,
                2L, 0.25D, 12.0F, 0.4F, true, false));
        assertPoseEquals(firstModelA, firstModelB);

        final PlayerAnimationRuntime.MovementDistance distance =
                new PlayerAnimationRuntime.MovementDistance();
        assertEquals(0.0D, distance.update(movementState(PlayerAnimationState.View.FIRST_PERSON,
                0L, 0.0D, 0.0F, 0.0F, true, false)), 1.0e-6D);
        assertEquals(0.0D, distance.update(movementState(PlayerAnimationState.View.FIRST_PERSON,
                1L, 1.0D, 0.0F, 0.0F, false, false)), 1.0e-6D);
        assertEquals(0.0D, distance.update(movementState(PlayerAnimationState.View.FIRST_PERSON,
                2L, 2.0D, 0.0F, 0.0F, false, false)), 1.0e-6D);
        assertEquals(0.0D, distance.update(movementState(PlayerAnimationState.View.FIRST_PERSON,
                3L, 3.0D, 0.0F, 0.0F, true, true)), 1.0e-6D);
        assertEquals(0.0D, distance.update(movementState(PlayerAnimationState.View.FIRST_PERSON,
                4L, 4.0D, 0.0F, 0.0F, true, true)), 1.0e-6D);
        assertEquals(1.0D, distance.update(movementState(PlayerAnimationState.View.FIRST_PERSON,
                5L, 5.0D, 0.0F, 0.0F, true, false)), 1.0e-6D);
    }

    @Test
    void thirdPersonBowUseAppliesBedrockBowPose() throws Exception {
        final Path packsRoot = Path.of(System.getProperty("vbu.workspaceRoot"),
                "ec-deploy-assets", "bedrock-loader-packs");
        final Path codeFunPacks = Path.of(System.getProperty("vbu.workspaceRoot"),
                "ec-deploy-assets", "resource-packs", "CodeFunCore");
        final PackManager packs = new PackManager(List.of(
                content(packsRoot.resolve("vanilla.zip")),
                content(codeFunPacks.resolve("ec_hub.zip")),
                content(codeFunPacks.resolve("ec_ze.zip")),
                content(codeFunPacks.resolve("rl_defense.zip")),
                content(codeFunPacks.resolve("rl_defense_ec_entity.zip"))));
        final PlayerAnimationRuntime runtime = new PlayerAnimationRuntime(packs, Map.of());
        final TestBoneModel model = new TestBoneModel();
        runtime.sampleThirdPerson(model, stateUsingBow(1L));

        assertEquals("1.0", runtime.debugSnapshot().thirdPerson().queries().get("has_target"));
        assertEquals(-90.0F, model.rotationX("rightarm"), 1.0e-3F);
        assertEquals(-90.0F, model.rotationX("leftarm"), 1.0e-3F);
        assertEquals(35.0F, model.rotationY("rightitem"), 1.0e-3F);

        runtime.sampleThirdPerson(model, state(2L, "minecraft:stick"));
        assertEquals("0.0", runtime.debugSnapshot().thirdPerson().queries().get("has_target"));
    }

    @Test
    void woodenSwordAttachableResolvesToNonEmptyTextureMeshGeometry() throws Exception {
        final Path packPath = Path.of(System.getProperty("vbu.workspaceRoot"),
                "ec-deploy-assets", "resource-packs", "CodeFunCore", "rl_defense.zip");
        final PackManager packs = new PackManager(List.of(content(packPath)));

        final var candidates = packs.getAttachableDefinitions()
                .candidatesFor("minecraft:wooden_sword");
        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.identifier().equals("minecraft:wooden_sword.player")));

        final var geometry = packs.getModelDefinitions().getEntityModels().get("geometry.sword");
        assertNotNull(geometry);
        final var itemBone = geometry.getParents().stream()
                .filter(parent -> parent.getName().equals("rightitem"))
                .findFirst()
                .orElseThrow();
        assertNotNull(itemBone.getPolyMesh());
        assertTrue(itemBone.getPolyMesh().getPositions().length > 0);
        assertTrue(itemBone.getPolyMesh().getPolys().length > 0);
    }

    private static PlayerAnimationState state(long tick, String mainHandIdentifier) {
        return state(PlayerAnimationState.View.THIRD_PERSON, tick,
                mainHandIdentifier, 0.0F, 0.0F, 0.0F);
    }

    private static PlayerAnimationState stateUsingBow(long tick) {
        return new PlayerAnimationState(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new PlayerAnimationOwner(PLAYER_INSTANCE, LEVEL_INSTANCE),
                PlayerAnimationState.View.THIRD_PERSON, tick, 0.0F, HumanoidArm.RIGHT,
                InteractionHand.MAIN_HAND, "minecraft:bow", "", Set.of(),
                tick, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                1.0F, 1.0F, false, true, true, false, false, false, false,
                false, false, false, true, false, false, true, false, false, false,
                1, 19, 20, 0.0F, 0.0F, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static PlayerAnimationState state(PlayerAnimationState.View view, long tick,
                                              String mainHandIdentifier, float positionX,
                                              float walkSpeed, float attackTime) {
        return state(view, tick, mainHandIdentifier, positionX, walkSpeed, attackTime,
                1.0F, false, false, 0.0D);
    }

    private static PlayerAnimationState state(PlayerAnimationState.View view, long tick,
                                              String mainHandIdentifier, float positionX,
                                              float walkSpeed, float attackTime, float armHeight,
                                              boolean slim, boolean bobAnimation, double deltaX) {
        return state(view, tick, mainHandIdentifier, positionX, walkSpeed, attackTime,
                armHeight, slim, bobAnimation, deltaX, HumanoidArm.RIGHT);
    }

    private static PlayerAnimationState state(PlayerAnimationState.View view, long tick,
                                              String mainHandIdentifier, float positionX,
                                              float walkSpeed, float attackTime, float armHeight,
                                              boolean slim, boolean bobAnimation, double deltaX,
                                              HumanoidArm mainArm) {
        return state(view, tick, mainHandIdentifier, positionX, walkSpeed, attackTime,
                armHeight, slim, bobAnimation, deltaX, mainArm, 0.0F, 0.0F);
    }

    private static PlayerAnimationState state(PlayerAnimationState.View view, long tick,
                                              String mainHandIdentifier, float positionX,
                                              float walkSpeed, float attackTime, float armHeight,
                                              boolean slim, boolean bobAnimation, double deltaX,
                                              HumanoidArm mainArm, float relativeHeadYaw,
                                              float bodyYaw) {
        return state(view, tick, mainHandIdentifier, positionX, walkSpeed, attackTime,
                armHeight, slim, bobAnimation, deltaX, mainArm, relativeHeadYaw, bodyYaw,
                0.0F, 0.0F);
    }

    private static PlayerAnimationState stateAtPartial(PlayerAnimationState.View view, long tick,
                                                       String mainHandIdentifier, float positionX,
                                                       float walkSpeed, float attackTime,
                                                       float partialTick) {
        return state(view, tick, mainHandIdentifier, positionX, walkSpeed, attackTime,
                1.0F, false, true, 0.1D, HumanoidArm.RIGHT, 0.0F, 0.0F,
                partialTick, 0.0F);
    }

    private static PlayerAnimationState stateWithPitch(PlayerAnimationState.View view, long tick,
                                                       float pitch) {
        return state(view, tick, "", 0.0F, 0.0F, 0.0F,
                1.0F, false, false, 0.0D, HumanoidArm.RIGHT, 0.0F, 0.0F,
                0.0F, pitch);
    }

    private static PlayerAnimationState stateSwimming(PlayerAnimationState.View view, long tick,
                                                       float swimAmount) {
        return state(view, tick, "", 0.0F, 0.0F, 0.0F,
                1.0F, false, false, 0.0D, HumanoidArm.RIGHT, 0.0F, 0.0F,
                0.0F, 0.0F, swimAmount, swimAmount > 0.0F);
    }

    private static PlayerAnimationState state(PlayerAnimationState.View view, long tick,
                                              String mainHandIdentifier, float positionX,
                                              float walkSpeed, float attackTime, float armHeight,
                                              boolean slim, boolean bobAnimation, double deltaX,
                                              HumanoidArm mainArm, float relativeHeadYaw,
                                              float bodyYaw, float partialTick, float pitch) {
        return state(view, tick, mainHandIdentifier, positionX, walkSpeed, attackTime,
                armHeight, slim, bobAnimation, deltaX, mainArm, relativeHeadYaw, bodyYaw,
                partialTick, pitch, 0.0F, false);
    }

    private static PlayerAnimationState state(PlayerAnimationState.View view, long tick,
                                              String mainHandIdentifier, float positionX,
                                              float walkSpeed, float attackTime, float armHeight,
                                              boolean slim, boolean bobAnimation, double deltaX,
                                              HumanoidArm mainArm, float relativeHeadYaw,
                                              float bodyYaw, float partialTick, float pitch,
                                              float swimAmount, boolean swimming) {
        return new PlayerAnimationState(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new PlayerAnimationOwner(PLAYER_INSTANCE, LEVEL_INSTANCE),
                view, tick, partialTick,
                mainArm, InteractionHand.MAIN_HAND,
                mainHandIdentifier, "", Set.of(),
                tick, positionX, walkSpeed, attackTime,
                pitch, relativeHeadYaw, bodyYaw, swimAmount, 1.0F, armHeight, slim,
                true, true, false, false, false, swimming, false,
                false, false, false, false, false, false, false, false, bobAnimation,
                0, 0, 0, 0.0F, 0.0F,
                positionX, 0.0D, deltaX, 0.0D, 0.0D);
    }

    private static PlayerAnimationState movementState(PlayerAnimationState.View view, long tick,
                                                      double positionX, float walkPosition,
                                                      float walkSpeed, boolean onGround,
                                                      boolean crouching) {
        return new PlayerAnimationState(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new PlayerAnimationOwner(PLAYER_INSTANCE, LEVEL_INSTANCE),
                view, tick, 0.0F, HumanoidArm.RIGHT, InteractionHand.MAIN_HAND,
                "", "", Set.of(), tick, walkPosition, walkSpeed, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, false,
                true, onGround, false, crouching, false, false, false,
                false, false, false, false, false, false, false, false, true,
                0, 0, 0, 0.0F, 0.0F,
                positionX, 0.0D, 0.1D, 0.0D, 0.0D);
    }

    private static void assertPoseEquals(TestBoneModel expected, TestBoneModel actual) {
        for (String name : expected.bones.keySet()) {
            assertEquals(expected.bones.get(name).getRotation(),
                    actual.bones.get(name).getRotation(), name + " rotation");
            assertEquals(expected.bones.get(name).getOffset(),
                    actual.bones.get(name).getOffset(), name + " offset");
        }
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

        private float rotationY(String name) {
            return bones.get(name).getRotation().y;
        }

        private float rotationZ(String name) {
            return bones.get(name).getRotation().z;
        }

        private Vector3f rotation(String name) {
            return new Vector3f(bones.get(name).getRotation());
        }

        private float offsetX(String name) {
            return bones.get(name).getOffset().x;
        }

        private float offsetY(String name) {
            return bones.get(name).getOffset().y;
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

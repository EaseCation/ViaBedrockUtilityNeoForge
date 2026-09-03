package org.oryxel.viabedrockutility.diagnostics;

import net.easecation.bedrockmotion.pack.content.Content;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.oryxel.viabedrockutility.attachable.AttachableDebugLog;
import org.oryxel.viabedrockutility.attachable.AttachableQueryContext;
import org.oryxel.viabedrockutility.attachable.AttachableRuntimeRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPackDiagnosticsTest {
    @Test
    void appliesCompleteBottomToTopManifestAndRejectsIncompleteManifest() {
        final Content outer = new Content();
        outer.put("bedrock/top.mcpack", new byte[]{1});
        outer.put("bedrock/bottom.mcpack", new byte[]{2});
        outer.putString(BedrockPackDiagnostics.STACK_MANIFEST_PATH, """
                {"format_version":1,"order":"bottom_to_top","packs":[
                  {"path":"bedrock/bottom.mcpack"},{"path":"bedrock/top.mcpack"}
                ]}
                """);

        final var ordered = BedrockPackDiagnostics.resolveEmbeddedPackOrder(outer);

        assertTrue(ordered.manifestPresent());
        assertTrue(ordered.manifestApplied());
        assertEquals(List.of("bedrock/bottom.mcpack", "bedrock/top.mcpack"), ordered.paths());

        outer.putString(BedrockPackDiagnostics.STACK_MANIFEST_PATH, """
                {"format_version":1,"order":"bottom_to_top","packs":[
                  {"path":"bedrock/top.mcpack"}
                ]}
                """);
        final var rejected = BedrockPackDiagnostics.resolveEmbeddedPackOrder(outer);
        assertTrue(rejected.manifestPresent());
        assertFalse(rejected.manifestApplied());
        assertTrue(rejected.warning().contains("does not cover every embedded mcpack"));
    }

    @Test
    void formatsAttachableIdentityWithStableFieldNames() {
        final UUID owner = UUID.randomUUID();
        final var attempt = new AttachableDebugLog.DebugAttempt(
                new AttachableRuntimeRegistry.RuntimeKey(owner,
                        AttachableQueryContext.LogicalHand.MAIN_HAND),
                7L, 42L, "minecraft:stick", AttachableQueryContext.ViewContext.FIRST_PERSON,
                AttachableDebugLog.AttemptStage.RENDERED, 1, "example:stick",
                List.of("default"), "root->rightitem", "");

        final String formatted = VbuDebugReports.formatAttempt(attempt);

        assertEquals("{owner=" + owner + ",hand=MAIN_HAND,packGeneration=7,clientTick=42,"
                + "item=minecraft:stick,view=FIRST_PERSON,stage=RENDERED,candidates=1,"
                + "attachable=example:stick,passes=[default],binding=root->rightitem,detail=}",
                formatted);
    }

    @Test
    void recordsPackOrderConflictsAndWinningDefinitionSource() {
        final Content lower = pack("lower", "00000000-0000-0000-0000-000000000001");
        lower.putString("animation_controllers/player.json", controller("lower_pose"));
        lower.putString("entity/player.json", playerEntity());
        lower.put("textures/items/example.png", new byte[]{1});
        final Content upper = pack("upper", "00000000-0000-0000-0000-000000000002");
        upper.putString("animation_controllers/player.json", controller("upper_pose"));
        upper.putString("attachables/example.json", attachable());

        final var snapshot = BedrockPackDiagnostics.inspect(List.of(
                input(0, "bedrock/lower.mcpack", lower, "lower"),
                input(1, "bedrock/upper.mcpack", upper, "upper")),
                false, "unordered fixture");

        assertEquals(2, snapshot.packs().size());
        assertEquals(1, snapshot.conflictCount());
        assertEquals("bedrock/upper.mcpack", snapshot.winner(
                BedrockPackDiagnostics.DefinitionType.ANIMATION_CONTROLLER,
                "controller.animation.player.root").embeddedPath());
        assertEquals(2, snapshot.sources(
                BedrockPackDiagnostics.DefinitionType.ANIMATION_CONTROLLER,
                "controller.animation.player.root").size());
        assertNotNull(snapshot.winner(BedrockPackDiagnostics.DefinitionType.ENTITY,
                "minecraft:player"));
        assertNotNull(snapshot.winner(BedrockPackDiagnostics.DefinitionType.ATTACHABLE,
                "example:item"));
        assertNotNull(snapshot.winner(BedrockPackDiagnostics.DefinitionType.TEXTURE,
                "textures/items/example"));
        assertFalse(snapshot.orderedManifest());
        assertEquals("unordered fixture", snapshot.orderingWarning());
    }

    private static BedrockPackDiagnostics.PackInput input(
            int index, String path, Content content, String bytes) {
        return new BedrockPackDiagnostics.PackInput(index, "outer-" + index,
                "server-pack-" + index + ".zip", path, content,
                BedrockPackDiagnostics.hashBytes(bytes.getBytes(StandardCharsets.UTF_8)), false);
    }

    private static Content pack(String name, String uuid) {
        final Content content = new Content();
        content.putString("manifest.json", """
                {"header":{"name":"%s","uuid":"%s","version":[1,2,3]}}
                """.formatted(name, uuid));
        return content;
    }

    private static String controller(String animation) {
        return """
                {"animation_controllers":{"controller.animation.player.root":{
                  "initial_state":"default","states":{"default":{"animations":["%s"]}}
                }}}
                """.formatted(animation);
    }

    private static String playerEntity() {
        return """
                {"minecraft:client_entity":{"description":{"identifier":"minecraft:player"}}}
                """;
    }

    private static String attachable() {
        return """
                {"minecraft:attachable":{"description":{"identifier":"example:item"}}}
                """;
    }
}

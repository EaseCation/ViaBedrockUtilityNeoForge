package org.oryxel.viabedrockutility.diagnostics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.attachable.AttachableHostContext;
import org.oryxel.viabedrockutility.attachable.AttachableDebugLog;
import org.oryxel.viabedrockutility.attachable.AttachableRuntimeRegistry;
import org.oryxel.viabedrockutility.attachable.AttachableRuntimeManager;
import org.oryxel.viabedrockutility.payload.PayloadHandler;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;
import org.oryxel.viabedrockutility.renderer.CustomPlayerRenderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Stable, obfuscation-independent text reports for VBU runtime diagnostics. */
public final class VbuDebugReports {
    private VbuDebugReports() {
    }

    public static List<String> pack(ViaBedrockUtility utility,
                                    BedrockPackDiagnostics.DefinitionType lookupType,
                                    String lookupIdentifier, boolean full) {
        final BedrockPackDiagnostics.Snapshot snapshot = utility.getPackDiagnostics();
        final List<String> lines = new ArrayList<>();
        lines.add("VBU pack diagnostics: generation=" + utility.getPackGeneration().generation()
                + ",packs=" + snapshot.packs().size()
                + ",definitions=" + snapshot.definitions().size()
                + ",conflicts=" + snapshot.conflictCount()
                + ",orderedManifest=" + snapshot.orderedManifest());
        if (!snapshot.orderingWarning().isBlank()) {
            lines.add("warning=" + snapshot.orderingWarning());
        }
        appendWinner(lines, snapshot, BedrockPackDiagnostics.DefinitionType.ENTITY, "minecraft:player");
        appendWinner(lines, snapshot, BedrockPackDiagnostics.DefinitionType.ANIMATION_CONTROLLER,
                "controller.animation.player.root");
        if (lookupType != null && lookupIdentifier != null && !lookupIdentifier.isBlank()) {
            final List<BedrockPackDiagnostics.DefinitionSource> sources =
                    snapshot.sources(lookupType, lookupIdentifier);
            lines.add("lookup=" + lookupType.commandName() + ":" + lookupIdentifier
                    + ",sources=" + sources.size());
            for (int index = 0; index < sources.size(); index++) {
                lines.add("lookupSource[" + index + "]=" + source(sources.get(index))
                        + (index == sources.size() - 1 ? ",winner=true" : ""));
            }
        }
        if (full) {
            for (BedrockPackDiagnostics.PackSource pack : snapshot.packs()) {
                lines.add("pack[" + pack.loadIndex() + "]={outerIndex=" + pack.outerRequestIndex()
                        + ",outerId=" + pack.outerRequestId() + ",outerFile=" + pack.outerFile()
                        + ",embedded=" + pack.embeddedPath() + ",uuid=" + pack.packUuid()
                        + ",version=" + pack.packVersion() + ",name=" + pack.packName()
                        + ",sha256=" + BedrockPackDiagnostics.shortHash(pack.sha256())
                        + ",files=" + pack.fileCount() + ",manifestOrdered="
                        + pack.orderedByManifest() + "}");
            }
            for (var conflict : snapshot.conflicts()) {
                lines.add("conflict=" + conflict.getKey().type().commandName() + ":"
                        + conflict.getKey().identifier() + " -> "
                        + conflict.getValue().stream().map(VbuDebugReports::source).toList());
            }
        }
        return lines;
    }

    public static List<String> skin(PayloadHandler handler, UUID playerUuid, boolean full) {
        final List<String> lines = new ArrayList<>();
        final SkinDebugLog.Snapshot snapshot = handler.getSkinDebugLog().snapshot(playerUuid);
        final EntityRenderer<?, ?> renderer = handler.cachedPlayerRenderer(playerUuid);
        final PayloadHandler.CachedPlayerSkin cached = handler.getCachedPlayerSkins().get(playerUuid);
        lines.add("VBU skin diagnostics: uuid=" + playerUuid
                + ",renderer=" + identity(renderer)
                + ",model=" + (renderer instanceof CustomPlayerRenderer custom
                ? identity(custom.getPlayerModel()) : "unavailable")
                + ",cached=" + (cached != null) + ",wireTransferId=false");
        if (cached != null) {
            lines.add("cachedSkin={texture=" + cached.getTextureId() + ",slim=" + cached.isSlim()
                    + ",geometry=" + geometryIdentifier(cached.getResourcePatch())
                    + ",geometryHash=" + BedrockPackDiagnostics.shortHash(
                    BedrockPackDiagnostics.hashText(cached.getGeometryRaw()))
                    + ",patchHash=" + BedrockPackDiagnostics.shortHash(
                    BedrockPackDiagnostics.hashText(cached.getResourcePatch())) + "}");
        }
        lines.add("activeTransfer=" + transfer(snapshot.active()));
        lines.add("installed=" + installed(snapshot.installed()));
        if (full) {
            snapshot.events().forEach(event -> lines.add("skinEvent=" + skinEvent(event)));
        } else {
            snapshot.events().stream().skip(Math.max(0, snapshot.events().size() - 8L))
                    .forEach(event -> lines.add("skinEvent=" + skinEvent(event)));
        }
        return lines;
    }

    public static List<String> host(PayloadHandler handler, UUID playerUuid,
                                    String boneName, boolean full) {
        final List<String> lines = new ArrayList<>();
        final EntityRenderer<?, ?> renderer = handler.cachedPlayerRenderer(playerUuid);
        if (!(renderer instanceof CustomPlayerRenderer custom)) {
            return List.of("VBU host diagnostics: uuid=" + playerUuid + ",renderer=unavailable");
        }
        final BedrockPlayerModelMetadata metadata = BedrockPlayerModelMetadata.get(custom.getPlayerModel());
        if (metadata == null) {
            return List.of("VBU host diagnostics: uuid=" + playerUuid + ",metadata=unavailable");
        }
        final BedrockPlayerModelMetadata.Bone bone = metadata.bone(boneName);
        if (bone == null) {
            return List.of("VBU host diagnostics: uuid=" + playerUuid + ",bone=" + boneName
                    + ",available=" + metadata.bones().stream().map(BedrockPlayerModelMetadata.Bone::key).toList());
        }
        final AttachableHostContext host = new AttachableHostContext(metadata);
        final var runtime = custom.playerAnimationDebugSnapshot();
        lines.add("VBU host diagnostics: uuid=" + playerUuid + ",renderer=" + identity(custom)
                + ",model=" + identity(custom.getPlayerModel()) + ",slim=" + metadata.slim()
                + ",bone=" + bone.key() + ",lastSample="
                + (runtime == null ? "unavailable" : sample(runtime.lastSample())));
        lines.add("semanticChain=" + host.semanticChain(bone));
        lines.add("presentationChain=" + host.presentationChain(bone));
        lines.add("bindOrigin=" + origin(host.bindWorldMatrix(bone))
                + ",currentOrigin=" + origin(host.currentWorldMatrix(bone))
                + ",attachmentOrigin=" + origin(host.attachmentMatrix(bone))
                + ",firstPersonAttachmentOrigin=" + origin(host.firstPersonAttachmentMatrix(bone)));
        if (full) {
            for (BedrockPlayerModelMetadata.Bone entry : metadata.chainTo(bone)) {
                lines.add("hostBone=" + entry.key() + "{source=" + entry.originalName()
                        + ",semanticParent=" + entry.parentKey()
                        + ",presentationParent=" + entry.presentationParentKey()
                        + ",pivot=" + entry.pivot() + ",bind="
                        + matrix(host.bindWorldMatrix(entry)) + ",current="
                        + matrix(host.currentWorldMatrix(entry)) + ",deformation="
                        + matrix(host.deformationMatrix(entry)) + "}");
            }
            lines.add("attachmentMatrix=" + matrix(host.attachmentMatrix(bone)));
            lines.add("firstPersonAttachmentMatrix=" + matrix(host.firstPersonAttachmentMatrix(bone)));
        }
        return lines;
    }

    public static List<String> attachableHistory(AttachableRuntimeManager manager) {
        return attachableHistory(manager, Integer.MAX_VALUE);
    }

    public static List<String> attachableHistory(AttachableRuntimeManager manager, int limit) {
        final List<AttachableDebugLog.DebugAttempt> history = manager.debugHistory();
        final int retained = Math.max(0, Math.min(history.size(), limit));
        final List<String> lines = new ArrayList<>();
        lines.add("VBU attachable history: mode=" + manager.debugRenderMode()
                + ",events=" + history.size() + ",reported=" + retained);
        history.stream().skip(history.size() - retained)
                .forEach(attempt -> lines.add("history=" + formatAttempt(attempt)));
        return lines;
    }

    public static String formatAttempt(AttachableDebugLog.DebugAttempt attempt) {
        return "{owner=" + attempt.runtimeKey().ownerUuid() + ",hand=" + attempt.runtimeKey().hand()
                + ",packGeneration=" + attempt.packGeneration() + ",clientTick=" + attempt.clientTick()
                + ",item=" + attempt.itemIdentifier() + ",view=" + attempt.view()
                + ",stage=" + attempt.stage() + ",candidates=" + attempt.candidateCount()
                + ",attachable=" + attempt.attachableIdentifier() + ",passes=" + attempt.renderPasses()
                + ",binding=" + attempt.bindingBone() + ",detail=" + attempt.detail() + "}";
    }

    public static String formatRuntimeKey(AttachableRuntimeRegistry.RuntimeKey key) {
        return "{owner=" + key.ownerUuid() + ",hand=" + key.hand() + "}";
    }

    public static String formatRuntimeIdentity(AttachableRuntimeRegistry.RuntimeIdentity identity) {
        return "{item=" + identity.itemIdentifier() + ",attachable=" + identity.attachableIdentifier()
                + ",packGeneration=" + identity.packGeneration() + "}";
    }

    private static void appendWinner(List<String> lines, BedrockPackDiagnostics.Snapshot snapshot,
                                     BedrockPackDiagnostics.DefinitionType type, String identifier) {
        final BedrockPackDiagnostics.DefinitionSource winner = snapshot.winner(type, identifier);
        lines.add("winner=" + type.commandName() + ":" + identifier + " -> "
                + (winner == null ? "unavailable" : source(winner)));
    }

    private static String source(BedrockPackDiagnostics.DefinitionSource source) {
        return "pack[" + source.loadIndex() + "]/" + source.embeddedPath() + "/"
                + source.sourcePath() + "#" + BedrockPackDiagnostics.shortHash(source.sha256());
    }

    private static String identity(Object value) {
        return value == null ? "unavailable" : Integer.toHexString(System.identityHashCode(value));
    }

    private static String transfer(SkinDebugLog.TransferSnapshot value) {
        if (value == null) return "none";
        return "{sequence=" + value.sequence() + ",startedTick=" + value.startedTick()
                + ",size=" + value.width() + "x" + value.height()
                + ",expectedChunks=" + value.expectedChunks() + ",receivedChunks="
                + value.receivedChunks() + ",geometry=" + value.geometryIdentifier()
                + ",geometryHash=" + value.geometryHash() + ",patchHash="
                + value.resourcePatchHash() + "}";
    }

    private static String installed(SkinDebugLog.InstalledSkin value) {
        if (value == null) return "none";
        return "{sequence=" + value.sequence() + ",installedTick=" + value.installedTick()
                + ",geometry=" + value.geometryIdentifier() + ",slim=" + value.slim()
                + ",renderer=" + Integer.toHexString(value.rendererIdentity())
                + ",model=" + Integer.toHexString(value.modelIdentity())
                + ",texture=" + value.textureIdentifier() + ",geometryHash="
                + value.geometryHash() + ",patchHash=" + value.resourcePatchHash() + "}";
    }

    private static String skinEvent(SkinDebugLog.Event value) {
        return "{sequence=" + value.sequence() + ",player=" + value.playerUuid()
                + ",clientTick=" + value.clientTick() + ",kind=" + value.kind()
                + ",detail=" + value.detail() + "}";
    }

    private static String sample(
            org.oryxel.viabedrockutility.animation.PlayerAnimationRuntime.SampleDebugSnapshot value) {
        if (value == null) return "none";
        return "{view=" + value.view() + ",tick=" + value.tick()
                + ",partial=" + value.partialTick() + ",boneModel="
                + Integer.toHexString(value.boneModelIdentity()) + "}";
    }

    private static String origin(Matrix4f matrix) {
        return matrix.transformPosition(new Vector3f()).toString();
    }

    private static String matrix(Matrix4f matrix) {
        return Arrays.toString(matrix.get(new float[16]));
    }

    private static String geometryIdentifier(String resourcePatch) {
        try {
            final JsonObject patch = JsonParser.parseString(resourcePatch).getAsJsonObject();
            final JsonObject geometry = patch.getAsJsonObject("geometry");
            return geometry == null || !geometry.has("default")
                    ? "" : geometry.get("default").getAsString();
        } catch (Throwable ignored) {
            return "";
        }
    }
}

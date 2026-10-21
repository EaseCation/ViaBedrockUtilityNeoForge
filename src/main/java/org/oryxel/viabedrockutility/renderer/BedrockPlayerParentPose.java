package org.oryxel.viabedrockutility.renderer;

import net.minecraft.client.model.PlayerModel;
import org.oryxel.viabedrockutility.attachable.AttachableHostContext;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;

/** 在保留 Java 玩家部件入口的同时，恢复 Bedrock 父骨骼对第三人称网格的影响。 */
public final class BedrockPlayerParentPose {
    private BedrockPlayerParentPose() {
    }

    public static void update(PlayerModel model) {
        final BedrockPlayerModelMetadata metadata = BedrockPlayerModelMetadata.get(model);
        if (metadata == null) {
            return;
        }
        final AttachableHostContext host = new AttachableHostContext(metadata);
        for (BedrockPlayerModelMetadata.Bone bone : metadata.bones()) {
            ((IModelPart) (Object) bone.part()).viaBedrockUtility$setParentPose(
                    host.flattenedParentPose(bone));
        }
    }
}

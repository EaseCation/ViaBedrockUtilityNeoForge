package org.oryxel.viabedrockutility.mixin.impl.render;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.oryxel.viabedrockutility.mixin.interfaces.ICustomPlayerRendererHolder;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.renderer.CustomPlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerEntityModelMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void viaBedrockUtility$nameVanillaPlayerBones(ModelPart root, boolean slim, CallbackInfo ci) {
        final PlayerModel model = (PlayerModel) (Object) this;
        viaBedrockUtility$nameVanillaBone(model.head, "head");
        viaBedrockUtility$nameVanillaBone(model.body, "body");
        viaBedrockUtility$nameVanillaBone(model.rightArm, "rightarm");
        viaBedrockUtility$nameVanillaBone(model.leftArm, "leftarm");
        viaBedrockUtility$nameVanillaBone(model.rightLeg, "rightleg");
        viaBedrockUtility$nameVanillaBone(model.leftLeg, "leftleg");
    }

    @Unique
    private static void viaBedrockUtility$nameVanillaBone(ModelPart part, String name) {
        final IModelPart extension = (IModelPart) (Object) part;
        if (extension.viaBedrockUtility$getName() == null
                || extension.viaBedrockUtility$getName().isEmpty()) {
            extension.viaBedrockUtility$setName(name);
            extension.viaBedrockUtility$setVBUModel();
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)V", at = @At("TAIL"))
    private void applyBedrockAnimations(PlayerRenderState state, CallbackInfo ci) {
        final var runtime = viaBedrockUtility$runtime(state);
        if (runtime == null) {
            return;
        }
        final var snapshot = ((ICustomPlayerRendererHolder) state)
                .viaBedrockUtility$getPlayerAnimationState();
        runtime.sampleThirdPerson((PlayerModel) (Object) this, snapshot);
    }

    @Unique
    private static org.oryxel.viabedrockutility.animation.PlayerAnimationRuntime
    viaBedrockUtility$runtime(PlayerRenderState state) {
        final var renderer = ((ICustomPlayerRendererHolder) state)
                .viaBedrockUtility$getCustomPlayerRenderer();
        final var snapshot = ((ICustomPlayerRendererHolder) state)
                .viaBedrockUtility$getPlayerAnimationState();
        return renderer instanceof CustomPlayerRenderer custom
                ? custom.playerAnimationRuntime(snapshot) : null;
    }
}

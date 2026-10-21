package org.oryxel.viabedrockutility.mixin.impl.entity.player;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.payload.PayloadHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 仅阻止本地自主位移，服务端传送、角度、动画和网络tick继续正常处理。 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerInputLockMixin {
    @Inject(method = "aiStep", at = @At("HEAD"), require = 1)
    private void vbu$clearLockedMotionBeforePhysics(CallbackInfo ci) {
        if (this.vbu$isImmobile()) {
            LocalPlayer player = (LocalPlayer) (Object) this;
            player.setDeltaMovement(Vec3.ZERO);
            player.setSprinting(false);
        }
    }

    @Inject(method = "aiStep", at = @At("RETURN"), require = 1)
    private void vbu$clearLockedMotionAfterPhysics(CallbackInfo ci) {
        if (this.vbu$isImmobile()) ((LocalPlayer) (Object) this).setDeltaMovement(Vec3.ZERO);
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true, require = 1)
    private void vbu$holdPosition(MoverType type, Vec3 movement, CallbackInfo ci) {
        if (this.vbu$isImmobile()) {
            ((LocalPlayer) (Object) this).setDeltaMovement(Vec3.ZERO);
            ci.cancel();
        }
    }

    private boolean vbu$isImmobile() {
        final ViaBedrockUtility utility = ViaBedrockUtility.getInstance();
        final PayloadHandler handler = utility.getPayloadHandler();
        return utility.isViaBedrockPresent() && handler != null && handler.isImmobile();
    }
}

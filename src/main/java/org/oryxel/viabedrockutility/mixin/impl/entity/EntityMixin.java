package org.oryxel.viabedrockutility.mixin.impl.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
    @Shadow
    private Vec3 pos;

    @Shadow public float distanceTraveled;

    @Inject(method = "setPos", at = @At("HEAD"))
    private void injectSetPos(double x, double y, double z, CallbackInfo ci) {
        if (!(((Object)this) instanceof Display.ItemDisplay) || !ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            return;
        }

        float distanceMoved = (float) new Vec3(x, y, z).distanceTo(new Vec3(this.pos.x, y, this.pos.z));
        this.distanceTraveled += distanceMoved;
    }
}

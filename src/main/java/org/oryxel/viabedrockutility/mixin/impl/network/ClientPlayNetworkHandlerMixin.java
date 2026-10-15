package org.oryxel.viabedrockutility.mixin.impl.network;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {
    // RETURN 位于原版的线程检查之后，避免网络线程和客户端线程重复记录同一个包。
    @Inject(method = "handleDamageEvent", at = @At("RETURN"), require = 1)
    private void recordCustomEntityHurt(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        final ViaBedrockUtility utility = ViaBedrockUtility.getInstance();
        final Minecraft client = Minecraft.getInstance();
        if (!utility.isViaBedrockPresent() || client.level == null || utility.getPayloadHandler() == null) {
            return;
        }
        final Entity entity = client.level.getEntity(packet.entityId());
        if (entity != null && !entity.isRemoved()) {
            final CustomEntityPayloadHandler handler = utility.getPayloadHandler();
            handler.markCustomEntityHurt(entity.getUUID(), handler.getCustomEntityHurtTracker().currentTick(client.level));
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("RETURN"), require = 1)
    private void recordCustomEntityDeath(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (packet.getEventId() != EntityEvent.DEATH) {
            return;
        }
        final ViaBedrockUtility utility = ViaBedrockUtility.getInstance();
        final Minecraft client = Minecraft.getInstance();
        if (!utility.isViaBedrockPresent() || client.level == null || utility.getPayloadHandler() == null) {
            return;
        }
        final Entity entity = packet.getEntity(client.level);
        if (entity != null && !entity.isRemoved()) {
            final CustomEntityPayloadHandler handler = utility.getPayloadHandler();
            // 致死一击可能没有先收到 DAMAGE_EVENT；先绑定当前世界，避免首帧提取清掉死亡状态。
            handler.getCustomEntityHurtTracker().currentTick(client.level);
            handler.markCustomEntityDead(entity.getUUID());
        }
    }

    // NOTE: deliberately NOT clearing the cached player renderer on PLAYER_INFO_REMOVE. Servers with a
    // custom tab list (e.g. EaseCation) remove real players from the player-info list while they remain
    // in the world; dropping the renderer there reverts them to the vanilla model (the baked PlayerInfo
    // texture lingers). Nukkit-style Human NPCs also rely on the client keeping the Bedrock model cache
    // across RemoveEntity/AddPlayer; cleanup is therefore session-scoped and handled on disconnect.

    // Have to do this since you can't run custom command when playing in a server.
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void injectSendMessage(String content, CallbackInfo ci) {
        if (!ViaBedrockUtility.DEBUGGING || !content.startsWith("$animate")) {
            return;
        }

        ci.cancel();

        if (content.length() < "$animate ".length()) {
            return;
        }

        String[] split = content.substring("$animate ".length()).split(" ");
        if (split.length < 1) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.ENTITY) {
            System.out.println("No target!");
            return;
        }

        if (ViaBedrockUtility.getInstance().getPackManager() == null) {
            return;
        }

        final UUID uuid = ((EntityHitResult)client.hitResult).getEntity().getUUID();
        if (!ViaBedrockUtility.getInstance().getPayloadHandler().getCachedCustomEntities().containsKey(uuid)) {
            System.out.println("couldn't find");
            return;
        }

        CustomEntityTicker cache = ViaBedrockUtility.getInstance().getPayloadHandler().getCachedCustomEntities().get(uuid);
        if (split[0].equals("reset")) {
            cache.getRenderer().reset();
        } else if (split[0].equals("test") && split.length == 3) {
            cache.getRenderer().playExplicit(ViaBedrockUtility.getInstance().getPackManager().getAnimationDefinitions().getAnimations().get(split[2]));
        }
    }
}

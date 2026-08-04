package org.oryxel.viabedrockutility.mixin.impl.pack;

import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.util.Unit;
import org.oryxel.viabedrockutility.pack.ResourceReloadCloseFence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ReloadableResourceManager.class)
public abstract class ReloadableResourceManagerMixin {
    @Shadow
    private CloseableResourceManager resources;

    @Unique
    private final ResourceReloadCloseFence viabedrockutility$closeFence = new ResourceReloadCloseFence();

    @Redirect(
            method = "createReload",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/CloseableResourceManager;close()V"
            )
    )
    private void viabedrockutility$retirePreviousGeneration(CloseableResourceManager resources) {
        this.viabedrockutility$closeFence.retire(resources, resources::close);
    }

    @Redirect(
            method = "createReload",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/SimpleReloadInstance;create(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Z)Lnet/minecraft/server/packs/resources/ReloadInstance;"
            )
    )
    private ReloadInstance viabedrockutility$createFencedReload(
            ResourceManager resourceManager,
            List<PreparableReloadListener> listeners,
            Executor backgroundExecutor,
            Executor gameExecutor,
            CompletableFuture<Unit> waitingFor,
            boolean profiled
    ) {
        CloseableResourceManager resources = (CloseableResourceManager) resourceManager;
        ResourceReloadCloseFence.Generation generation = this.viabedrockutility$closeFence.begin(resources, resources::close);
        ReloadInstance reload;
        try {
            reload = SimpleReloadInstance.create(
                    resourceManager,
                    listeners,
                    generation.tracking(backgroundExecutor),
                    generation.tracking(gameExecutor),
                    waitingFor,
                    profiled
            );
        } catch (RuntimeException | Error failure) {
            generation.seal(CompletableFuture.failedFuture(failure));
            throw failure;
        }
        generation.seal(reload.done());
        return reload;
    }

    @Redirect(
            method = "close",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/CloseableResourceManager;close()V"
            )
    )
    private void viabedrockutility$retireCurrentGeneration(CloseableResourceManager resources) {
        this.viabedrockutility$closeFence.retire(resources, resources::close);
    }
}

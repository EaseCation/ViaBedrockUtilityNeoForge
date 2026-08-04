package org.oryxel.viabedrockutility.pack;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Unit;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadableResourceManagerCloseFenceTest {
    @Test
    @SuppressWarnings("deprecation")
    void closingManagerDuringReloadDoesNotCloseZipBeforePreparationRead() throws Exception {
        Path archive = createResourcePack();
        PackLocationInfo location = new PackLocationInfo(
                "close-fence-test",
                Component.literal("close-fence-test"),
                PackSource.BUILT_IN,
                Optional.empty()
        );
        PackResources pack = new FilePackResources.FileResourcesSupplier(archive).openPrimary(location);
        ResourceLocation value = ResourceLocation.fromNamespaceAndPath("close_fence_test", "value.txt");
        CountDownLatch resourceCaptured = new CountDownLatch(1);
        CountDownLatch allowRead = new CountDownLatch(1);
        CompletableFuture<String> contents = new CompletableFuture<>();
        ExecutorService background = Executors.newSingleThreadExecutor();
        ReloadableResourceManager manager = new ReloadableResourceManager(PackType.CLIENT_RESOURCES);

        manager.registerReloadListener((barrier, resources, preparationExecutor, applyExecutor) ->
                CompletableFuture.supplyAsync(() -> {
                    Resource resource = resources.getResource(value).orElseThrow();
                    resourceCaptured.countDown();
                    await(allowRead);
                    try (InputStream input = resource.open()) {
                        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                }, preparationExecutor).thenCompose(result -> {
                    contents.complete(result);
                    return barrier.wait(Unit.INSTANCE);
                }).thenAcceptAsync(ignored -> { }, applyExecutor)
        );

        try {
            ReloadInstance reload = manager.createReload(
                    background,
                    Runnable::run,
                    CompletableFuture.completedFuture(Unit.INSTANCE),
                    List.of(pack)
            );
            assertTrue(resourceCaptured.await(5, TimeUnit.SECONDS));

            manager.close();
            allowRead.countDown();

            assertDoesNotThrow(() -> reload.done().join());
            assertEquals("still-open", contents.join());
        } finally {
            allowRead.countDown();
            background.shutdownNow();
            assertTrue(background.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void replacingManagerDuringReloadKeepsRetiredGenerationReadable() throws Exception {
        Path archive = createResourcePack();
        PackLocationInfo location = new PackLocationInfo(
                "close-fence-test",
                Component.literal("close-fence-test"),
                PackSource.BUILT_IN,
                Optional.empty()
        );
        ResourceLocation value = ResourceLocation.fromNamespaceAndPath("close_fence_test", "value.txt");
        CountDownLatch firstResourceCaptured = new CountDownLatch(1);
        CountDownLatch allowFirstRead = new CountDownLatch(1);
        AtomicInteger invocation = new AtomicInteger();
        ExecutorService background = Executors.newSingleThreadExecutor();
        ReloadableResourceManager manager = new ReloadableResourceManager(PackType.CLIENT_RESOURCES);

        manager.registerReloadListener((barrier, resources, preparationExecutor, applyExecutor) -> {
            int reloadNumber = invocation.getAndIncrement();
            return CompletableFuture.supplyAsync(() -> {
                Resource resource = resources.getResource(value).orElseThrow();
                if (reloadNumber == 0) {
                    firstResourceCaptured.countDown();
                    await(allowFirstRead);
                }
                try (InputStream input = resource.open()) {
                    return new String(input.readAllBytes(), StandardCharsets.UTF_8);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }, preparationExecutor)
                    .thenCompose(barrier::wait)
                    .thenAcceptAsync(result -> assertEquals("still-open", result), applyExecutor);
        });

        try {
            ReloadInstance firstReload = manager.createReload(
                    background,
                    Runnable::run,
                    CompletableFuture.completedFuture(Unit.INSTANCE),
                    List.of(new FilePackResources.FileResourcesSupplier(archive).openPrimary(location))
            );
            assertTrue(firstResourceCaptured.await(5, TimeUnit.SECONDS));

            ReloadInstance secondReload = manager.createReload(
                    background,
                    Runnable::run,
                    CompletableFuture.completedFuture(Unit.INSTANCE),
                    List.of(new FilePackResources.FileResourcesSupplier(archive).openPrimary(location))
            );
            allowFirstRead.countDown();

            assertDoesNotThrow(() -> firstReload.done().join());
            assertDoesNotThrow(() -> secondReload.done().join());
        } finally {
            allowFirstRead.countDown();
            manager.close();
            background.shutdownNow();
            assertTrue(background.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void failedReloadStillLetsOtherPreparationReadBeforeClose() throws Exception {
        Path archive = createResourcePack();
        PackLocationInfo location = new PackLocationInfo(
                "close-fence-test",
                Component.literal("close-fence-test"),
                PackSource.BUILT_IN,
                Optional.empty()
        );
        ResourceLocation value = ResourceLocation.fromNamespaceAndPath("close_fence_test", "value.txt");
        CountDownLatch resourceCaptured = new CountDownLatch(1);
        CountDownLatch allowRead = new CountDownLatch(1);
        CompletableFuture<String> contents = new CompletableFuture<>();
        ExecutorService background = Executors.newSingleThreadExecutor();
        ReloadableResourceManager manager = new ReloadableResourceManager(PackType.CLIENT_RESOURCES);

        manager.registerReloadListener((barrier, resources, preparationExecutor, applyExecutor) ->
                CompletableFuture.failedFuture(new IllegalStateException("first listener failed"))
        );
        manager.registerReloadListener((barrier, resources, preparationExecutor, applyExecutor) ->
                CompletableFuture.supplyAsync(() -> {
                    Resource resource = resources.getResource(value).orElseThrow();
                    resourceCaptured.countDown();
                    await(allowRead);
                    try (InputStream input = resource.open()) {
                        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                }, preparationExecutor).thenCompose(result -> {
                    contents.complete(result);
                    return barrier.wait(Unit.INSTANCE);
                }).thenAcceptAsync(ignored -> { }, applyExecutor)
        );

        try {
            ReloadInstance reload = manager.createReload(
                    background,
                    Runnable::run,
                    CompletableFuture.completedFuture(Unit.INSTANCE),
                    List.of(new FilePackResources.FileResourcesSupplier(archive).openPrimary(location))
            );
            assertTrue(resourceCaptured.await(5, TimeUnit.SECONDS));

            manager.close();
            assertThrows(CompletionException.class, () -> reload.done().join());
            allowRead.countDown();

            assertEquals("still-open", contents.join());
        } finally {
            allowRead.countDown();
            background.shutdownNow();
            assertTrue(background.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static Path createResourcePack() throws Exception {
        Path archive = Files.createTempFile("reloadable-resource-manager-close-fence", ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("assets/close_fence_test/value.txt"));
            output.write("still-open".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        archive.toFile().deleteOnExit();
        return archive;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}

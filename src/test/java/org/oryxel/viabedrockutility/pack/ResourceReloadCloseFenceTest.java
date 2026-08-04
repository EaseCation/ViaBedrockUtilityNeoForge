package org.oryxel.viabedrockutility.pack;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceReloadCloseFenceTest {
    @Test
    void retirementWaitsForAsynchronousZipRead() throws Exception {
        Path archive = createArchive();
        ZipFile zipFile = new ZipFile(archive.toFile());
        ZipEntry entry = zipFile.getEntry("assets/test/value.txt");
        CountDownLatch readStarted = new CountDownLatch(1);
        CountDownLatch allowRead = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        Runnable closeZip = () -> {
            try {
                zipFile.close();
            } catch (java.io.IOException exception) {
                throw new UncheckedIOException(exception);
            } finally {
                closed.countDown();
            }
        };

        ResourceReloadCloseFence fence = new ResourceReloadCloseFence();
        ResourceReloadCloseFence.Generation generation = fence.begin(zipFile, closeZip);
        Executor executor = generation.tracking(CompletableFuture.delayedExecutor(0, TimeUnit.MILLISECONDS));
        CompletableFuture<String> reader = CompletableFuture.supplyAsync(() -> {
            readStarted.countDown();
            await(allowRead);
            try (InputStream input = zipFile.getInputStream(entry)) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, executor);
        generation.seal(reader);
        assertTrue(readStarted.await(5, TimeUnit.SECONDS));

        fence.retire(zipFile, closeZip);
        allowRead.countDown();

        assertEquals("still-open", reader.join());
        assertTrue(closed.await(5, TimeUnit.SECONDS));
        assertThrows(IllegalStateException.class, () -> zipFile.getInputStream(entry));
    }

    @Test
    void failFastLifetimeStillWaitsForSurvivingTrackedTask() throws Exception {
        ResourceReloadCloseFence fence = new ResourceReloadCloseFence();
        CompletableFuture<Void> failFastLifetime = new CompletableFuture<>();
        CountDownLatch survivorStarted = new CountDownLatch(1);
        CountDownLatch allowSurvivorToFinish = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicInteger closes = new AtomicInteger();
        Object identity = new Object();
        Runnable closeAction = () -> {
            closes.incrementAndGet();
            closed.countDown();
        };
        ResourceReloadCloseFence.Generation generation = fence.begin(identity, closeAction);
        Executor executor = generation.tracking(CompletableFuture.delayedExecutor(0, TimeUnit.MILLISECONDS));

        CompletableFuture<Void> survivor = CompletableFuture.runAsync(() -> {
            survivorStarted.countDown();
            await(allowSurvivorToFinish);
        }, executor);
        generation.seal(failFastLifetime);
        assertTrue(survivorStarted.await(5, TimeUnit.SECONDS));

        fence.retire(identity, closeAction);
        failFastLifetime.completeExceptionally(new IllegalStateException("first listener failed"));
        assertEquals(0, closes.get());

        allowSurvivorToFinish.countDown();
        survivor.join();
        assertTrue(closed.await(5, TimeUnit.SECONDS));
        assertEquals(1, closes.get());
    }

    @Test
    void untrackedAndCompletedGenerationsCloseImmediately() {
        ResourceReloadCloseFence fence = new ResourceReloadCloseFence();
        AtomicInteger closes = new AtomicInteger();

        fence.retire(new Object(), closes::incrementAndGet);
        assertEquals(1, closes.get());

        Object completedIdentity = new Object();
        ResourceReloadCloseFence.Generation generation = fence.begin(completedIdentity, closes::incrementAndGet);
        generation.seal(CompletableFuture.completedFuture(null));
        fence.retire(completedIdentity, closes::incrementAndGet);
        assertEquals(2, closes.get());
    }

    @Test
    void repeatedRetirementBeforeCompletionClosesOnce() {
        ResourceReloadCloseFence fence = new ResourceReloadCloseFence();
        CompletableFuture<Void> lifetime = new CompletableFuture<>();
        AtomicInteger closes = new AtomicInteger();
        Object identity = new Object();
        ResourceReloadCloseFence.Generation generation = fence.begin(identity, closes::incrementAndGet);

        generation.seal(lifetime);
        fence.retire(identity, closes::incrementAndGet);
        fence.retire(identity, closes::incrementAndGet);
        assertFalse(lifetime.isDone());

        lifetime.complete(null);
        assertEquals(1, closes.get());
    }

    private static Path createArchive() throws Exception {
        Path archive = Files.createTempFile("resource-reload-close-fence", ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("assets/test/value.txt"));
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

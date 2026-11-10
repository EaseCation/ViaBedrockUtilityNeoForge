package org.oryxel.viabedrockutility.payload;

import com.mojang.blaze3d.platform.NativeImage;
import net.easecation.bedrockmotion.pack.PackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.payload.impl.skin.BaseSkinPayload;
import org.oryxel.viabedrockutility.payload.impl.skin.SkinDataPayload;
import org.oryxel.viabedrockutility.renderer.CustomPlayerRenderer;
import org.oryxel.viabedrockutility.util.BundledPlayerModelFactory;
import org.oryxel.viabedrockutility.util.EntityRendererContextUtil;
import org.oryxel.viabedrockutility.util.ImageUtil;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkinInstallTest {
    static final String PATCH = "{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}";
    static final class Harness implements AutoCloseable {
        final List<int[]> uploads = new ArrayList<>();
        final PayloadHandler handler = new PayloadHandler() {
            @Override protected void writeSkinRows(DynamicTexture target, NativeImage source, int y, int rows) {
                assertTrue(rows > 0); assertTrue((long) rows * source.getWidth() * 4 <= 1_048_576);
                uploads.add(new int[]{y, rows});
            }
        };
        final ViaBedrockUtility utility = mock(ViaBedrockUtility.class);
        final Minecraft client = mock(Minecraft.class);
        final TextureManager textures = mock(TextureManager.class);
        final PackManager packs = mock(PackManager.class);
        final List<NativeImage> images = new ArrayList<>();
        final MockedStatic<ViaBedrockUtility> singleton = mockStatic(ViaBedrockUtility.class);
        final MockedStatic<Minecraft> minecraft = mockStatic(Minecraft.class);
        final MockedStatic<ImageUtil> conversion = mockStatic(ImageUtil.class);
        final MockedStatic<EntityRendererContextUtil> contexts = mockStatic(EntityRendererContextUtil.class);
        final MockedStatic<BundledPlayerModelFactory> models = mockStatic(BundledPlayerModelFactory.class);
        final MockedConstruction<CustomPlayerRenderer> renderers = mockConstruction(CustomPlayerRenderer.class);
        final MockedConstruction<DynamicTexture> dynamic = mockConstruction(DynamicTexture.class, (mock, context) -> {
            var owner = new java.util.concurrent.atomic.AtomicReference<NativeImage>();
            doAnswer(call -> { owner.set(call.getArgument(0)); return null; }).when(mock).setPixels(any());
            when(mock.getPixels()).thenAnswer(call -> owner.get());
            doAnswer(call -> { NativeImage image = owner.getAndSet(null); if (image != null) image.close(); return null; }).when(mock).close();
        });
        Harness() {
            singleton.when(ViaBedrockUtility::getInstance).thenReturn(utility);
            when(utility.getPackManager()).thenReturn(packs);
            when(utility.getConnectionEpoch()).thenReturn(1L);
            when(utility.isCurrentConnectionEpoch(1L)).thenReturn(true);
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            when(client.getTextureManager()).thenReturn(textures);
            conversion.when(() -> ImageUtil.beginConversion(any(), anyInt(), anyInt())).thenAnswer(call -> {
                var image = mock(NativeImage.class); images.add(image);
                when(image.getWidth()).thenReturn(call.getArgument(1)); when(image.getHeight()).thenReturn(call.getArgument(2));
                var work = mock(ImageUtil.RgbaConversion.class);
                when(work.step(anyInt())).thenReturn(true); when(work.take()).thenReturn(image);
                return work;
            });
            contexts.when(() -> EntityRendererContextUtil.build(client)).thenReturn(mock(EntityRendererProvider.Context.class));
            models.when(() -> BundledPlayerModelFactory.create(anyBoolean())).thenReturn(mock(PlayerModel.class));
        }
        void receive(UUID player, String patch) {
            handler.handle(new BaseSkinPayload(player, 1, 1, "", patch, 1));
            handler.handle(new SkinDataPayload(player, 0, 4, new byte[]{1, 2, 3, -1}));
        }
        void frame() { handler.beforeSkinFrame(null); }
        @Override public void close() {
            handler.resetConnectionState(); dynamic.close(); renderers.close(); models.close(); contexts.close();
            conversion.close(); minecraft.close(); singleton.close();
        }
    }
    @Test void receivingDoesNotConvertAndFakePlayersPublishOnlyAfterAllStages() {
        try (var h = new Harness()) {
            UUID player = UUID.randomUUID(); h.receive(player, PATCH);
            assertTrue(h.images.isEmpty()); assertTrue(h.handler.getCachedPlayerSkins().isEmpty());
            for (int i = 0; i < 6; i++) { h.frame(); verifyNoInteractions(h.textures); }
            h.frame(); verify(h.textures).register(any(), any());
            assertTrue(h.handler.getCachedPlayerSkins().containsKey(player));
            assertEquals(0, h.handler.skinWorkStatistics().get("pending"));
            verify(h.images.getFirst(), never()).close(); // Texture now owns the pixels.
        }
    }
    @Test void replacementAndDisconnectReleasePreparedImagesWithoutPublishingStaleWork() {
        try (var h = new Harness()) {
            UUID player = UUID.randomUUID(); h.receive(player, PATCH); h.frame();
            var old = h.images.getFirst(); h.receive(player, PATCH); verify(old).close();
            h.frame(); var current = h.images.getLast();
            h.handler.resetConnectionState(); verify(current).close();
            h.frame(); verifyNoInteractions(h.textures);
            assertEquals(0L, h.handler.skinWorkStatistics().get("reservedBytes"));
        }
    }
    @Test void failedPreparationPreservesInstalledSkinAndClosesUnpublishedPixels() {
        try (var h = new Harness()) {
            UUID player = UUID.randomUUID(); h.receive(player, PATCH);
            for (int i = 0; i < 7; i++) h.frame();
            var installed = h.handler.getCachedPlayerSkins().get(player);
            clearInvocations(h.textures);
            h.receive(player, "{\"geometry\":{\"default\":\"unsupported\"}}");
            h.frame(); h.frame();
            verify(h.images.getLast()).close(); verifyNoInteractions(h.textures);
            assertSame(installed, h.handler.getCachedPlayerSkins().get(player));
        }
    }
    @Test void resourceGenerationChangeRebuildsPreparedRendererBeforeInstall() {
        try (var h = new Harness()) {
            UUID player = UUID.randomUUID(); h.receive(player, PATCH);
            for (int i = 0; i < 5; i++) h.frame();
            assertEquals(1, h.renderers.constructed().size());
            h.handler.packManager = mock(PackManager.class);
            h.frame(); verifyNoInteractions(h.textures);
            for (int i = 0; i < 5; i++) h.frame();
            assertEquals(2, h.renderers.constructed().size()); verify(h.textures).register(any(), any());
        }
    }
    @Test void retiredConnectionCannotPublishEvenWithoutExplicitQueueClear() {
        try (var h = new Harness()) {
            h.receive(UUID.randomUUID(), PATCH); h.frame();
            when(h.utility.isCurrentConnectionEpoch(1L)).thenReturn(false);
            for (int i = 0; i < 7; i++) h.frame();
            verifyNoInteractions(h.textures); verify(h.images.getFirst()).close();
        }
    }
    @Test void receivesBoundedAssemblyBeforeResourcePackAndResumesAfterReady() {
        try (var h = new Harness()) {
            when(h.utility.getPackManager()).thenReturn(null);
            h.receive(UUID.randomUUID(), PATCH); h.frame();
            assertTrue(h.images.isEmpty()); assertEquals(1, h.handler.skinWorkStatistics().get("pending"));
            h.handler.packManager = h.packs;
            for (int i = 0; i < 7; i++) h.frame();
            verify(h.textures).register(any(), any());
        }
    }
    @Test void commandsWaitForTheMatchingSkinAndDoNotSurviveItsReplacement() {
        try (var h = new Harness()) {
            UUID player = UUID.randomUUID(); h.receive(player, PATCH);
            Runnable stale = mock(Runnable.class), fresh = mock(Runnable.class);
            assertTrue(h.handler.deferPlayerAnimation(player, 10, stale));
            h.receive(player, PATCH); assertTrue(h.handler.deferPlayerAnimation(player, 10, fresh));
            for (int i = 0; i < 7; i++) h.frame();
            verifyNoInteractions(stale, fresh);
            h.frame(); verify(fresh).run(); verifyNoInteractions(stale);
        }
    }
    @Test void capeConversionAndInstallationRemainSeparateAndTransferPixels() {
        try (var h = new Harness()) {
            UUID player = UUID.randomUUID();
            var id = net.minecraft.resources.ResourceLocation.parse("viabedrockutility:test_cape");
            h.handler.handle((BasePayload) new org.oryxel.viabedrockutility.payload.impl.skin.CapeDataPayload(
                    player, 1, 1, id, new byte[4]));
            assertTrue(h.images.isEmpty()); h.frame(); verifyNoInteractions(h.textures);
            h.frame(); verifyNoInteractions(h.textures);
            h.frame(); verify(h.textures).register(eq(id), any());
            verify(h.images.getFirst(), never()).close();
        }
    }
    @Test void animationSheetWaitsForBaseAndPublishesAfterSeparatePreparationStages() {
        try (var h = new Harness();
             var parsing = mockStatic(org.cube.converter.model.impl.bedrock.BedrockGeometryModel.class);
             var building = mockStatic(org.oryxel.viabedrockutility.util.GeometryUtil.class);
             var overlays = mockConstruction(org.oryxel.viabedrockutility.renderer.AnimatedSkinOverlay.class)) {
            var geometry = mock(org.cube.converter.model.impl.bedrock.BedrockGeometryModel.class);
            when(geometry.getIdentifier()).thenReturn("geometry.face");
            parsing.when(() -> org.cube.converter.model.impl.bedrock.BedrockGeometryModel.fromJson(any(com.google.gson.JsonObject.class))).thenReturn(List.of(geometry));
            building.when(() -> org.oryxel.viabedrockutility.util.GeometryUtil.buildModel(any(), eq(true), eq(false))).thenReturn(mock(PlayerModel.class));
            building.when(() -> org.oryxel.viabedrockutility.util.GeometryUtil.buildModel(any(), eq(true), eq(false), any())).thenReturn(mock(PlayerModel.class));
            UUID player = UUID.randomUUID();
            h.handler.handle(new BaseSkinPayload(player, 1, 1, "{}",
                    "{\"geometry\":{\"default\":\"geometry.humanoid.custom\",\"animated_face\":\"geometry.face\"}}", 1));
            h.handler.handle(new SkinDataPayload(player, 0, 4, new byte[4]));
            h.handler.handle((BasePayload) new org.oryxel.viabedrockutility.payload.impl.skin.SkinAnimationInfoPayload(player, 0, 1, 1, 0, 1, 1, 1));
            h.handler.handle(new org.oryxel.viabedrockutility.payload.impl.skin.SkinAnimationDataPayload(player, 0, 0, new byte[4]));
            for (int i = 0; i < 11; i++) h.frame();
            verify(h.textures, times(1)).register(any(), any());
            var renderer = h.renderers.constructed().getFirst(); verify(renderer, never()).setOverlay(anyInt(), any());
            h.frame(); verify(h.textures, times(2)).register(any(), any()); verify(renderer).setOverlay(eq(0), any());
            assertEquals(0, h.handler.skinWorkStatistics().get("pending"));
        }
    }

    @Test void largeGpuUploadPublishesOnlyAfterEveryRowAndCancellationClosesTheUnpublishedTexture() {
        for (boolean cancel : new boolean[]{false, true}) try (var h = new Harness()) {
            UUID player = UUID.randomUUID();
            h.handler.handle(new BaseSkinPayload(player, 1024, 512, "", PATCH, 1));
            h.handler.handle(new SkinDataPayload(player, 0, 1024 * 512 * 4, new byte[1024 * 512 * 4]));
            for (int i = 0; i < 7; i++) h.frame();
            verifyNoInteractions(h.textures);
            assertEquals(1, h.uploads.size()); assertArrayEquals(new int[]{0, 256}, h.uploads.getFirst());
            if (cancel) {
                h.handler.resetConnectionState(); h.frame();
                verify(h.dynamic.constructed().getFirst()).close(); verify(h.images.getFirst()).close();
                verifyNoInteractions(h.textures);
            } else {
                h.frame(); verify(h.textures).register(any(), any());
                assertArrayEquals(new int[]{256, 256}, h.uploads.getLast());
                verify(h.images.getFirst(), never()).close();
            }
        }
    }

}

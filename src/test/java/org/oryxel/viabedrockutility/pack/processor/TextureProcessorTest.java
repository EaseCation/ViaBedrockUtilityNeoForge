package org.oryxel.viabedrockutility.pack.processor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TextureProcessorTest {
    @Test
    void usesTheSameCanonicalPathForBedrockParticleTextures() {
        assertEquals("minecraft:textures/particle/bullet_laser",
                TextureProcessor.normalizeTextureIdentifier(
                        "textures/particle/Bullet_Laser.PNG").toString());
        assertEquals("easecation:textures/particle/trail",
                TextureProcessor.normalizeTextureIdentifier(
                        "easecation:textures/particle/trail.jpg").toString());
    }

    @Test
    void textureRegistrationDoesNotDecodeBeforeFirstUse() {
        final LazyBedrockTexture texture = new LazyBedrockTexture(
                () -> "test", new byte[]{1, 2, 3}, ignored -> {});

        assertFalse(texture.isLoaded());
        texture.close();
    }
    @Test
    void closeReleasesOwnedGpuHandlesExactlyOnceWithoutClosingTheSharedPlaceholder() throws Exception {
        var texture=new LazyBedrockTexture(()->"test",new byte[]{1},ignored->{});
        var gpu=org.mockito.Mockito.mock(com.mojang.blaze3d.textures.GpuTexture.class);
        var view=org.mockito.Mockito.mock(com.mojang.blaze3d.textures.GpuTextureView.class);
        var base=net.minecraft.client.renderer.texture.AbstractTexture.class;
        var imageField=base.getDeclaredField("texture");imageField.setAccessible(true);imageField.set(texture,gpu);
        var viewField=base.getDeclaredField("textureView");viewField.setAccessible(true);viewField.set(texture,view);
        texture.close();texture.close();
        org.mockito.Mockito.verify(gpu,org.mockito.Mockito.times(1)).close();
        org.mockito.Mockito.verify(view,org.mockito.Mockito.times(1)).close();
        assertFalse(texture.isLoaded());
    }
    /** Installs a completed registration snapshot without requiring a GPU in resolver tests. */
    public static AutoCloseable cachedAlpha(net.easecation.bedrockmotion.pack.PackManager owner,
            net.easecation.bedrockmotion.pack.content.Content source, String path,
            org.oryxel.viabedrockutility.util.GeometryUtil.TextureAlpha alpha) {
        long revision=TextureProcessor.beginAlphaUpdate();
        TextureProcessor.publishAlphas(revision,owner,java.util.Map.of(TextureProcessor.normalizeTextureIdentifier(path),
                new TextureProcessor.AlphaEntry(source,path,source.get(path),alpha)));
        return ()->TextureProcessor.discardAlphas(owner);
    }
    @Test void snapshotsAreAtomicAndOldCompletionCannotOverwriteNewOrSurviveClear() {
        var first=org.mockito.Mockito.mock(net.easecation.bedrockmotion.pack.PackManager.class);
        var second=org.mockito.Mockito.mock(net.easecation.bedrockmotion.pack.PackManager.class);
        var content=new net.easecation.bedrockmotion.pack.content.Content();String path="textures/"+java.util.UUID.randomUUID()+".png";
        content.put(path,new byte[]{1});var alpha=new org.oryxel.viabedrockutility.util.GeometryUtil.TextureAlpha(1,1,new byte[]{42});
        var key=TextureProcessor.normalizeTextureIdentifier(path);var values=new java.util.HashMap<net.minecraft.resources.ResourceLocation,TextureProcessor.AlphaEntry>();values.put(key,new TextureProcessor.AlphaEntry(content,path,content.get(path),alpha));
        long old=TextureProcessor.beginAlphaUpdate(),latest=TextureProcessor.beginAlphaUpdate();
        try {
            TextureProcessor.publishAlphas(latest,second,values);values.clear();
            org.junit.jupiter.api.Assertions.assertSame(alpha,TextureProcessor.resolveAlpha(second,content,path));
            TextureProcessor.publishAlphas(old,first,java.util.Map.of());
            TextureProcessor.discardAlphas(first); // Late failed publication of another manager must not clear current data.
            org.junit.jupiter.api.Assertions.assertSame(alpha,TextureProcessor.resolveAlpha(second,path));
            org.junit.jupiter.api.Assertions.assertNull(TextureProcessor.resolveAlpha(first,path));
            long pending=TextureProcessor.beginAlphaUpdate();TextureProcessor.clearAlphas();
            TextureProcessor.publishAlphas(pending,second,java.util.Map.of(key,new TextureProcessor.AlphaEntry(content,path,content.get(path),alpha)));
            org.junit.jupiter.api.Assertions.assertNull(TextureProcessor.resolveAlpha(second,path));
        } finally {TextureProcessor.clearAlphas();}
    }
    @Test void matchingOwnerStillRejectsChangedSourceOrChangedExtensionPreference() throws Exception {
        var owner=org.mockito.Mockito.mock(net.easecation.bedrockmotion.pack.PackManager.class);
        var content=new net.easecation.bedrockmotion.pack.content.Content();String base="textures/"+java.util.UUID.randomUUID();
        content.put(base+".jpg",new byte[]{1});var alpha=new org.oryxel.viabedrockutility.util.GeometryUtil.TextureAlpha(1,1,new byte[]{42});
        try(var ignored=cachedAlpha(owner,content,base+".jpg",alpha)) {
            org.junit.jupiter.api.Assertions.assertSame(alpha,TextureProcessor.resolveAlpha(owner,content,base));
            content.put(base+".png",new byte[]{2});
            org.junit.jupiter.api.Assertions.assertNull(TextureProcessor.resolveAlpha(owner,content,base));
            content.put(base+".jpg",new byte[]{3});
            org.junit.jupiter.api.Assertions.assertNull(TextureProcessor.resolveAlpha(owner,base));
        }
    }
    @Test void failedPublicationRetiresOnlyThatOwnersSnapshot() throws Exception {
        var owner=org.mockito.Mockito.mock(net.easecation.bedrockmotion.pack.PackManager.class);
        var c=new net.easecation.bedrockmotion.pack.content.Content();c.put("textures/a.png",new byte[]{1});
        try(var ignored=cachedAlpha(owner,c,"textures/a.png",new org.oryxel.viabedrockutility.util.GeometryUtil.TextureAlpha(1,1,new byte[]{1}))) {
            TextureProcessor.discardAlphas(owner);org.junit.jupiter.api.Assertions.assertNull(TextureProcessor.resolveAlpha(owner,"textures/a"));
        }
    }
    @Test void realRegistrationBindsSnapshotToItsManagerAndClearInvalidatesIt() throws Exception {
        var client=org.mockito.Mockito.mock(net.minecraft.client.Minecraft.class);
        var textures=org.mockito.Mockito.mock(net.minecraft.client.renderer.texture.TextureManager.class);
        org.mockito.Mockito.when(client.getTextureManager()).thenReturn(textures);
        var owner=org.mockito.Mockito.mock(net.easecation.bedrockmotion.pack.PackManager.class);
        var content=new net.easecation.bedrockmotion.pack.content.Content();
        String path="textures/"+java.util.UUID.randomUUID()+".png";
        var image=new java.awt.image.BufferedImage(2,1,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0x00123456);image.setRGB(1,0,0x7f123456);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);content.put(path,bytes.toByteArray());
        try(var mc=org.mockito.Mockito.mockStatic(net.minecraft.client.Minecraft.class)) {
            mc.when(net.minecraft.client.Minecraft::getInstance).thenReturn(client);
            try {
                TextureProcessor.process(java.util.List.of(content),owner);
                var cached=TextureProcessor.resolveAlpha(owner,content,path);
                org.junit.jupiter.api.Assertions.assertNotNull(cached);
                org.junit.jupiter.api.Assertions.assertArrayEquals(new byte[]{0,127},cached.alpha());
                org.mockito.Mockito.verify(textures).register(org.mockito.Mockito.eq(TextureProcessor.normalizeTextureIdentifier(path)),org.mockito.Mockito.any(LazyBedrockTexture.class));
                TextureProcessor.clear();
                org.junit.jupiter.api.Assertions.assertNull(TextureProcessor.resolveAlpha(owner,content,path));
            } finally {TextureProcessor.clear();}
        }
    }
}

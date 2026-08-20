package org.oryxel.viabedrockutility.pack.processor;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** Compressed server-pack texture that allocates RGBA/GPU storage only when first rendered. */
final class LazyBedrockTexture extends AbstractTexture {
    private final Supplier<String> label;
    private final Consumer<Throwable> decodeFailure;
    private byte[] encodedImage;
    private boolean closed;

    LazyBedrockTexture(Supplier<String> label, byte[] encodedImage, Consumer<Throwable> decodeFailure) {
        this.label = Objects.requireNonNull(label, "label");
        this.encodedImage = Objects.requireNonNull(encodedImage, "encodedImage");
        this.decodeFailure = Objects.requireNonNull(decodeFailure, "decodeFailure");
    }

    synchronized boolean isLoaded() {
        return this.texture != null;
    }

    synchronized void preload() {
        ensureLoaded();
    }

    @Override
    public synchronized GpuTexture getTexture() {
        ensureLoaded();
        return super.getTexture();
    }

    @Override
    public synchronized GpuTextureView getTextureView() {
        ensureLoaded();
        return super.getTextureView();
    }

    @Override
    public synchronized void setClamp(boolean clamp) {
        ensureLoaded();
        super.setClamp(clamp);
    }

    @Override
    public synchronized void setFilter(boolean blur, boolean mipmap) {
        ensureLoaded();
        super.setFilter(blur, mipmap);
    }

    @Override
    public synchronized void setUseMipmaps(boolean useMipmaps) {
        ensureLoaded();
        super.setUseMipmaps(useMipmaps);
    }

    @Override
    public synchronized void close() {
        this.closed = true;
        this.encodedImage = null;
        super.close();
    }

    private void ensureLoaded() {
        if (this.texture != null) {
            return;
        }
        if (this.closed || this.encodedImage == null) {
            throw new IllegalStateException("Texture has already been closed");
        }

        NativeImage decoded;
        try {
            decoded = NativeImage.read(this.encodedImage);
        } catch (IOException | RuntimeException exception) {
            this.decodeFailure.accept(exception);
            decoded = MissingTextureAtlasSprite.generateMissingImage();
        }
        try (NativeImage image = decoded) {
            final GpuDevice device = RenderSystem.getDevice();
            this.texture = device.createTexture(this.label, 5, TextureFormat.RGBA8,
                    image.getWidth(), image.getHeight(), 1, 1);
            this.texture.setTextureFilter(FilterMode.NEAREST, false);
            this.textureView = device.createTextureView(this.texture);
            device.createCommandEncoder().writeToTexture(this.texture, image);
            this.encodedImage = null;
        } catch (RuntimeException | Error failure) {
            super.close();
            throw failure;
        }
    }
}

package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Composites each complete player once, preserving armor/skin occlusion inside the silhouette. */
public final class PlayerTranslucencyRenderer {
    public static final RenderPipeline COMPOSITE = RenderPipeline.builder()
            .withLocation(ResourceLocation.fromNamespaceAndPath("viabedrockutility", "pipeline/player_translucency"))
            .withVertexShader("core/blit_screen")
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("viabedrockutility", "core/player_translucency"))
            .withSampler("InSampler")
            .withSampler("DepthSampler")
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .build();

    private static final List<PlayerTranslucencyBufferSource> POOL = new ArrayList<>();
    private static final List<PlayerTranslucencyBufferSource> PENDING = new ArrayList<>();
    private static TextureTarget target;
    private static int usedBuffers;
    private static boolean mainEntityPass;

    private PlayerTranslucencyRenderer() {
    }

    public static void beginFrame() {
        clearPending();
        mainEntityPass = true;
    }

    public static void endEntityPass() {
        mainEntityPass = false;
    }

    public static boolean isCapturing() {
        return mainEntityPass;
    }

    public static PlayerTranslucencyBufferSource capture(MultiBufferSource source, double distanceSquared) {
        if (usedBuffers == POOL.size()) {
            POOL.add(new PlayerTranslucencyBufferSource());
        }
        PlayerTranslucencyBufferSource buffer = POOL.get(usedBuffers++);
        buffer.begin(source, distanceSquared);
        return buffer;
    }

    public static void submit(PlayerTranslucencyBufferSource buffer) {
        PENDING.add(buffer);
    }

    public static void flush() {
        if (PENDING.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // Complete ordinary entities and block entities first, so both can occlude a ghost and be
        // seen through it regardless of their submission order.
        minecraft.renderBuffers().bufferSource().endBatch();
        RenderTarget scene = minecraft.getMainRenderTarget();
        if (target == null) {
            target = new TextureTarget("VBU translucent player", scene.width, scene.height, true);
        } else if (target.width != scene.width || target.height != scene.height) {
            target.resize(scene.width, scene.height);
        }

        GpuTextureView sceneColor = RenderSystem.outputColorTextureOverride != null
                ? RenderSystem.outputColorTextureOverride : scene.getColorTextureView();
        GpuTextureView sceneDepth = RenderSystem.outputDepthTextureOverride != null
                ? RenderSystem.outputDepthTextureOverride : scene.getDepthTextureView();
        GpuTextureView savedColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView savedDepth = RenderSystem.outputDepthTextureOverride;
        try {
            // Each player has its own silhouette, so one ghost does not erase a ghost behind it.
            PENDING.sort(Comparator.comparingDouble(PlayerTranslucencyBufferSource::distanceSquared).reversed());
            for (PlayerTranslucencyBufferSource player : PENDING) {
                RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                        target.getColorTexture(), 0, target.getDepthTexture(), 1.0);
                RenderSystem.outputColorTextureOverride = target.getColorTextureView();
                RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
                player.draw();
                RenderSystem.outputColorTextureOverride = savedColor;
                RenderSystem.outputDepthTextureOverride = savedDepth;
                composite(sceneColor, sceneDepth);
            }
        } finally {
            RenderSystem.outputColorTextureOverride = savedColor;
            RenderSystem.outputDepthTextureOverride = savedDepth;
            clearPending();
        }
    }

    private static void composite(GpuTextureView color, GpuTextureView depth) {
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(6);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Composite translucent player", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(COMPOSITE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setVertexBuffer(0, RenderSystem.getQuadVertexBuffer());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.bindSampler("InSampler", target.getColorTextureView());
            pass.bindSampler("DepthSampler", target.getDepthTextureView());
            pass.drawIndexed(0, 0, 6, 1);
        }
    }

    private static void clearPending() {
        PENDING.clear();
        for (int i = 0; i < usedBuffers; i++) {
            POOL.get(i).reset();
        }
        usedBuffers = 0;
    }

    public static void close() {
        mainEntityPass = false;
        PENDING.clear();
        POOL.forEach(PlayerTranslucencyBufferSource::close);
        POOL.clear();
        usedBuffers = 0;
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}

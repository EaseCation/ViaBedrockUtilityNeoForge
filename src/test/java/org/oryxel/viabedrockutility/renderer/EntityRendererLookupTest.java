package org.oryxel.viabedrockutility.renderer;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import org.objenesis.ObjenesisStd;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityRendererLookupTest {
    private static final ObjenesisStd OBJENESIS = new ObjenesisStd();
    private static final String MIXIN_CLASS =
            "/org/oryxel/viabedrockutility/mixin/impl/render/dispatcher/EntityRenderDispatcherMixin.class";
    private static final String LOOKUP_CLASS =
            "/org/oryxel/viabedrockutility/renderer/EntityRendererLookup.class";

    @Test
    void customPlayerRendererStillTakesPriority() {
        final UUID uuid = UUID.randomUUID();
        final CustomEntityPayloadHandler payloadHandler = new CustomEntityPayloadHandler();
        final RendererStub playerRenderer = newInstance(RendererStub.class);
        final CustomEntityTicker customEntity = customEntity(newInstance(RendererStub.class));
        payloadHandler.getCachedPlayerRenderers().put(uuid, playerRenderer);
        payloadHandler.getCachedCustomEntities().put(uuid, customEntity);

        final EntityRenderer<?, ?> selected = EntityRendererLookup.find(uuid, payloadHandler);

        assertSame(playerRenderer, selected);
    }

    @Test
    void customEntityRendererStillOverridesVanillaLookup() {
        final UUID uuid = UUID.randomUUID();
        final CustomEntityPayloadHandler payloadHandler = new CustomEntityPayloadHandler();
        final RendererStub customRenderer = newInstance(RendererStub.class);
        payloadHandler.getCachedCustomEntities().put(uuid, customEntity(customRenderer));

        final EntityRenderer<?, ?> selected = EntityRendererLookup.find(uuid, payloadHandler);

        assertSame(customRenderer, selected);
    }

    @Test
    void missingCustomRendererLeavesVanillaLookupUnchanged() {
        assertNull(EntityRendererLookup.find(UUID.randomUUID(), new CustomEntityPayloadHandler()));
    }

    @Test
    void repeatedCustomEntityLookupsCannotReachLoggerOrAppender() throws IOException {
        final UUID uuid = UUID.randomUUID();
        final CustomEntityPayloadHandler payloadHandler = new CustomEntityPayloadHandler();
        final RendererStub customRenderer = newInstance(RendererStub.class);
        payloadHandler.getCachedCustomEntities().put(uuid, customEntity(customRenderer));

        for (int call = 0; call < 100_000; call++) {
            assertSame(customRenderer, EntityRendererLookup.find(uuid, payloadHandler));
        }

        final String mixinBytecode = readClassBytecode(MIXIN_CLASS);
        assertTrue(mixinBytecode.contains("EntityRendererLookup"));
        assertNoLoggingReferences(mixinBytecode);
        assertNoLoggingReferences(readClassBytecode(LOOKUP_CLASS));
    }

    private static void assertNoLoggingReferences(final String bytecode) {
        assertFalse(bytecode.contains("org/slf4j/Logger"));
        assertFalse(bytecode.contains("Appender"));
        assertFalse(bytecode.contains("getRenderer intercepted"));
    }

    private static String readClassBytecode(final String resource) throws IOException {
        try (InputStream input = EntityRendererLookupTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "compiled class must be present: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    private static CustomEntityTicker customEntity(final CustomEntityRenderer<?> renderer) {
        final CustomEntityTicker ticker = newInstance(CustomEntityTicker.class);
        try {
            final Field rendererField = CustomEntityTicker.class.getDeclaredField("renderer");
            rendererField.setAccessible(true);
            rendererField.set(ticker, renderer);
            return ticker;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("failed to create constructor-free custom entity test fixture", exception);
        }
    }

    private static <T> T newInstance(final Class<T> type) {
        return OBJENESIS.newInstance(type);
    }

    private static final class RendererStub extends CustomEntityRenderer<Entity> {
        private RendererStub() {
            super(null, null, (EntityRendererProvider.Context) null);
        }
    }
}

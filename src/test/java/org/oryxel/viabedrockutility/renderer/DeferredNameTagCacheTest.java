package org.oryxel.viabedrockutility.renderer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class DeferredNameTagCacheTest {
    private final RecordingFont font = new RecordingFont();

    @AfterEach
    void clearCache() {
        DeferredNameTag.clearCache();
    }

    @Test
    void samePlayerChangesFromWhiteToYellowAndBack() throws Exception {
        Font.PreparedText white = prepare(name(ChatFormatting.WHITE));
        Font.PreparedText yellow = prepare(name(ChatFormatting.YELLOW));

        assertNotSame(white, yellow);
        assertEquals(0xFFFFFF, ((RecordedText) white).styles().getFirst().getColor().getValue());
        assertEquals(0xFFFF55, ((RecordedText) yellow).styles().getFirst().getColor().getValue());
        assertSame(white, prepare(name(ChatFormatting.WHITE)));
        assertSame(yellow, prepare(name(ChatFormatting.YELLOW)));
        assertEquals(2, font.preparations);
    }

    @Test
    void inheritedAndSiblingStylesHaveSeparateCachedGlyphs() throws Exception {
        Component white = Component.empty().withStyle(ChatFormatting.WHITE).append("Player");
        Component yellow = Component.empty().withStyle(ChatFormatting.YELLOW).append("Player");
        Component mixed = Component.literal("Pla").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("yer").withStyle(ChatFormatting.YELLOW));

        assertNotSame(prepare(white), prepare(yellow));
        assertNotSame(prepare(white), prepare(mixed));
        assertSame(prepare(yellow), prepare(name(ChatFormatting.YELLOW)));
    }

    @Test
    void mutatingOriginalSiblingDoesNotChangeStoredCacheKey() throws Exception {
        MutableComponent sibling = name(ChatFormatting.WHITE);
        Component original = Component.empty().append(sibling);
        Font.PreparedText white = prepare(original);

        sibling.withStyle(ChatFormatting.YELLOW);

        assertSame(white, prepare(name(ChatFormatting.WHITE)));
        assertNotSame(white, prepare(name(ChatFormatting.YELLOW)));
    }

    @Test
    void resourceReloadInvalidatesPreparedText() throws Exception {
        Font.PreparedText old = prepare(name(ChatFormatting.YELLOW));
        DeferredNameTag.clearCache();
        assertNotSame(old, prepare(name(ChatFormatting.YELLOW)));
    }

    private static MutableComponent name(ChatFormatting color) {
        return Component.literal("Player").withStyle(color);
    }

    private Font.PreparedText prepare(Component text) throws Exception {
        Class<?> entryClass = Class.forName(DeferredNameTag.class.getName() + "$Entry");
        Constructor<?> constructor = entryClass.getDeclaredConstructor(Font.class, Component.class,
                float.class, float.class, int.class, Matrix4f.class, Font.DisplayMode.class, int.class, int.class);
        constructor.setAccessible(true);
        Object entry = constructor.newInstance(font, text, -18F, 0F, 0xFFFFFFFF, new Matrix4f(),
                Font.DisplayMode.NORMAL, 0, 0);
        Method method = DeferredNameTag.class.getDeclaredMethod("prepared", entryClass);
        method.setAccessible(true);
        return (Font.PreparedText) method.invoke(null, entry);
    }

    private static final class RecordingFont extends Font {
        private int preparations;

        private RecordingFont() {
            super(location -> null, false);
        }

        @Override
        public PreparedText prepareText(FormattedCharSequence text, float x, float y, int color,
                                        boolean shadow, int backgroundColor) {
            preparations++;
            List<Style> styles = new ArrayList<>();
            text.accept((index, style, codePoint) -> {
                styles.add(style);
                return true;
            });
            return new RecordedText(styles);
        }
    }

    private record RecordedText(List<Style> styles) implements Font.PreparedText {
        @Override
        public void visit(Font.GlyphVisitor visitor) {
        }

        @Override
        public ScreenRectangle bounds() {
            return null;
        }
    }
}

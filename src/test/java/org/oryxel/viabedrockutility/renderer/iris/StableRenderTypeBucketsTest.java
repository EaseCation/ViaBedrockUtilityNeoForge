package org.oryxel.viabedrockutility.renderer.iris;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StableRenderTypeBucketsTest {
    private static final int OPAQUE = 0;
    private static final int GENERAL_TRANSPARENT = 2;
    private static final int DECAL = 3;

    @Test
    void ordersOpaqueArmorBeforeGlintRequestedEarlier() {
        final StableRenderTypeBuckets<String> buckets = new StableRenderTypeBuckets<>(6);

        buckets.add(DECAL, "armor_glint");
        buckets.add(OPAQUE, "armor_base");

        assertEquals(List.of("armor_base", "armor_glint"), buckets.ordered());
    }

    @Test
    void preservesFirstUseOrderWithinEachTransparencyBucket() {
        final StableRenderTypeBuckets<String> buckets = new StableRenderTypeBuckets<>(6);

        buckets.add(GENERAL_TRANSPARENT, "first_translucent");
        buckets.add(GENERAL_TRANSPARENT, "second_translucent");
        buckets.add(GENERAL_TRANSPARENT, "first_translucent");

        assertEquals(List.of("first_translucent", "second_translucent"), buckets.ordered());
    }

    @Test
    void clearingOneTransparencyTypeKeepsOtherBuckets() {
        final StableRenderTypeBuckets<String> buckets = new StableRenderTypeBuckets<>(6);
        buckets.add(OPAQUE, "armor_base");
        buckets.add(DECAL, "armor_glint");

        buckets.clear(DECAL);

        assertEquals(List.of("armor_base"), buckets.ordered());
    }
}

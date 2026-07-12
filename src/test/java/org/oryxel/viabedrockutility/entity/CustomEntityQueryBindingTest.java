package org.oryxel.viabedrockutility.entity;

import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.enums.bedrock.ActorFlags;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;

import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomEntityQueryBindingTest {
    private static final String MAPPED_ON_FIRE = "mapped_on_fire";
    private static final Map<ActorFlags, String> FLAG_QUERIES = Map.of(ActorFlags.ONFIRE, MAPPED_ON_FIRE);

    @Test
    void reusedBindingClearsNullableAndBooleanQueriesBetweenFrames() {
        final MutableObjectBinding binding = new MutableObjectBinding();
        final CustomEntityTicker.EntityQueryState previous = new CustomEntityTicker.EntityQueryState();

        CustomEntityTicker.populateEntityQueries(
                binding, previous, 7, 8, 9, EnumSet.of(ActorFlags.ONFIRE), FLAG_QUERIES);

        assertEquals(7.0, binding.get("variant").getAsNumber());
        assertEquals(8.0, binding.get("mark_variant").getAsNumber());
        assertEquals(9.0, binding.get("skin_id").getAsNumber());
        assertTrue(binding.get(MAPPED_ON_FIRE).getAsBoolean());
        assertTrue(binding.get("is_onfire").getAsBoolean());

        final Object unchangedVariant = binding.get("variant");
        final Object unchangedFlag = binding.get(MAPPED_ON_FIRE);
        CustomEntityTicker.populateEntityQueries(
                binding, previous, 7, 8, 9, EnumSet.of(ActorFlags.ONFIRE), FLAG_QUERIES);
        assertSame(unchangedVariant, binding.get("variant"));
        assertSame(unchangedFlag, binding.get(MAPPED_ON_FIRE));

        CustomEntityTicker.populateEntityQueries(
                binding, previous, null, null, null, EnumSet.noneOf(ActorFlags.class), FLAG_QUERIES);

        assertEquals(0.0, binding.get("variant").getAsNumber());
        assertEquals(0.0, binding.get("mark_variant").getAsNumber());
        assertEquals(0.0, binding.get("skin_id").getAsNumber());
        assertFalse(binding.get(MAPPED_ON_FIRE).getAsBoolean());
        assertFalse(binding.get("is_onfire").getAsBoolean());
    }
}

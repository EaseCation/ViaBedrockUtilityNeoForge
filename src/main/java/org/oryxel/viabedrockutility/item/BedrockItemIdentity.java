package org.oryxel.viabedrockutility.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Presentation identity only; the server still owns item instances and authority. */
public final class BedrockItemIdentity {
    private static final String IDENTIFIER = "viabedrock:bedrock_identifier";
    private static final int CACHE_LIMIT = 512;
    // Rendering reads identifiers; reload may clear them. Never retain mutable ItemStack/NBT trees.
    private static final Map<String, ResourceLocation> identifiers = new ConcurrentHashMap<>();

    private BedrockItemIdentity() {}

    public static ResourceLocation resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            // Read only; do not copy a complete custom-data tree for each render pass.
            String name = data.getUnsafe().getString(IDENTIFIER).orElse(null);
            if (name != null) {
                ResourceLocation cached = identifiers.get(name);
                if (cached != null) return cached;
                ResourceLocation parsed = ResourceLocation.tryParse(name);
                if (parsed != null) {
                    if (identifiers.size() >= CACHE_LIMIT) identifiers.clear();
                    // Long identifiers remain valid, but cannot dominate the bounded cache.
                    if (name.length() <= 256) identifiers.put(name, parsed);
                    return parsed;
                }
            }
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    public static void clear() { identifiers.clear(); }
}

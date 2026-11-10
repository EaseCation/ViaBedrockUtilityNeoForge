package org.oryxel.viabedrockutility.payload;

/** Shared admission for complete RGBA data and in-flight assemblies, before native allocation. */
final class SkinImageBudget {
    static final int MAX_SIDE = 4096;
    static final int MAX_CHUNKS = 1024;
    static long bytes(int width, int height) {
        if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE)
            throw new IllegalArgumentException("Unsupported skin dimensions");
        return (long) width * height * 4;
    }
    static long reservation(int width, int height, int chunks, String geometry, String patch) {
        if (chunks < 1 || chunks > MAX_CHUNKS) throw new IllegalArgumentException("Skin chunk budget");
        // Raw chunks, concatenation and native pixels can coexist during conversion.
        return bytes(width, height) * 3 + 2L * geometry.length() + 2L * patch.length();
    }
}

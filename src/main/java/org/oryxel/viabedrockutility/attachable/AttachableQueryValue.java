package org.oryxel.viabedrockutility.attachable;

/**
 * Dependency-free value crossing the attachable query SPI. Mocha conversion belongs to VBU's
 * expression binding and must not leak into an integration mod's class-loader boundary.
 */
public record AttachableQueryValue(Kind kind, double number, boolean booleanValue, String stringValue) {
    public enum Kind { NUMBER, BOOLEAN, STRING }

    public static AttachableQueryValue number(double value) {
        return new AttachableQueryValue(Kind.NUMBER, value, false, null);
    }

    public static AttachableQueryValue bool(boolean value) {
        return new AttachableQueryValue(Kind.BOOLEAN, value ? 1.0D : 0.0D, value, null);
    }

    public static AttachableQueryValue string(String value) {
        return new AttachableQueryValue(Kind.STRING, 0.0D, false, value == null ? "" : value);
    }
}

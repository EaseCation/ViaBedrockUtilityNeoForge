package org.oryxel.viabedrockutility.config;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Estimates render-thread capability without treating core count or GPU power as single-thread speed. */
public final class CpuRenderCapability {
    private static final Pattern AMD_RYZEN = Pattern.compile(
            "\\bryzen\\s+[3579]\\s+(?:pro\\s+)?(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern INTEL_CORE = Pattern.compile(
            "\\bi[3579][ -]?(\\d{4,5})", Pattern.CASE_INSENSITIVE);
    private static final Pattern INTEL_ULTRA = Pattern.compile(
            "\\bcore(?:\\(tm\\))?\\s+ultra\\s+[579]\\s+(\\d{3})", Pattern.CASE_INSENSITIVE);

    private CpuRenderCapability() {
    }

    public static int score(String cpuName, String microarchitecture, int physicalCores, long nominalFrequencyHz) {
        String name = normalize(cpuName);
        String architecture = normalize(microarchitecture);

        int score = knownCpuScore(name);
        if (score < 0) {
            score = architectureScore(architecture);
        }
        if (score < 0) {
            score = frequencyFallbackScore(nominalFrequencyHz);
        }

        // Additional cores help the rest of the client, but do not make Render Thread vertex writes faster.
        if (physicalCores < 4) {
            score--;
        }
        return Math.max(0, Math.min(8, score));
    }

    private static int knownCpuScore(String name) {
        if (containsAny(name, "celeron", "pentium", "athlon silver", "athlon gold", "intel n100",
                "intel n200", "intel n300", "intel n305")) {
            return 0;
        }

        Matcher ultra = INTEL_ULTRA.matcher(name);
        if (ultra.find()) {
            int series = Integer.parseInt(ultra.group(1)) / 100;
            return series >= 2 ? 7 : 6;
        }

        Matcher intel = INTEL_CORE.matcher(name);
        if (intel.find()) {
            int model = Integer.parseInt(intel.group(1));
            int generation = intelGeneration(model);
            if (generation >= 14) return 7;
            if (generation == 13) return 6;
            if (generation == 12) return 5;
            if (generation == 11) return 3;
            if (generation >= 8) return 2;
            return 1;
        }

        Matcher ryzen = AMD_RYZEN.matcher(name);
        if (ryzen.find()) {
            int model = Integer.parseInt(ryzen.group(1));
            return amdRyzenScore(model, name);
        }

        if (name.contains("apple m4")) return 8;
        if (name.contains("apple m3")) return 7;
        if (name.contains("apple m2")) return 6;
        if (name.contains("apple m1")) return 5;
        return -1;
    }

    private static int amdRyzenScore(int model, String name) {
        int series = model / 1000;
        boolean mobile = name.matches(".*\\d{4}(?:u|h|hs|hx)(?:\\s|$).*" );
        return switch (series) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> mobile ? 2 : 4;
            case 4 -> 3;
            case 5 -> isZen2Ryzen5000Mobile(model) ? 2 : (mobile ? 4 : 5);
            case 6 -> 5;
            case 7 -> amdRyzen7000Score(model, mobile, name);
            case 8 -> 6;
            default -> series >= 9 ? 8 : -1;
        };
    }

    private static int amdRyzen7000Score(int model, boolean mobile, String name) {
        if (!mobile) {
            return 7;
        }
        int architectureDigit = (model / 10) % 10;
        int score = switch (architectureDigit) {
            case 2 -> 2; // Zen 2: 7020/7520 series
            case 3 -> 4; // Zen 3/3+: 7030/7035 series
            case 4 -> 6; // Zen 4: 7040/7045 series
            case 5 -> 7;
            default -> 4;
        };
        if (name.contains("hx") && score >= 6) {
            score++;
        }
        return score;
    }

    private static boolean isZen2Ryzen5000Mobile(int model) {
        return model == 5300 || model == 5500 || model == 5700;
    }

    private static int intelGeneration(int model) {
        if (model >= 10_000) {
            return model / 1000;
        }
        if (model >= 1000 && model < 2000) {
            int mobileGeneration = model / 100;
            if (mobileGeneration >= 10) {
                return mobileGeneration;
            }
        }
        return model / 1000;
    }

    private static int architectureScore(String architecture) {
        if (architecture.isBlank() || architecture.contains("unknown")) return -1;
        if (containsAny(architecture, "zen 5", "arrow lake", "lunar lake")) return 8;
        if (containsAny(architecture, "zen 4", "raptor lake")) return 7;
        if (containsAny(architecture, "alder lake")) return 5;
        if (containsAny(architecture, "zen 3")) return 5;
        if (containsAny(architecture, "zen 2", "rocket lake", "tiger lake")) return 3;
        if (containsAny(architecture, "zen", "skylake", "kaby lake", "coffee lake")) return 2;
        return -1;
    }

    private static int frequencyFallbackScore(long frequencyHz) {
        double ghz = Math.max(0L, frequencyHz) / 1_000_000_000.0;
        if (ghz >= 4.5) return 4;
        if (ghz >= 3.8) return 3;
        if (ghz >= 3.0) return 2;
        if (ghz > 0.0) return 1;
        return 2;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}

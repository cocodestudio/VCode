package com.cocode.vcode.ide.utils;


import com.cocode.vcode.ide.core.completion.staticdata.StaticAssetReader;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing CSS color strings (named colors, hex, rgb/rgba, and hsl/hsla)
 * into Android {@link Color} integer values.
 */
public class ColorParser {

    private static final Object lock = new Object();
    private static final Map<String, Integer> NAMED_COLORS = new HashMap<>();
    private static final Pattern RGB_PATTERN = Pattern.compile("rgba?\\(\\s*([\\d.]+)(%?)\\s*[, ]\\s*([\\d.]+)(%?)\\s*[, ]\\s*([\\d.]+)(%?)(?:\\s*[,/]\\s*([\\d.]+)(%?))?\\s*\\)");
    private static final Pattern HSL_PATTERN = Pattern.compile("hsla?\\(\\s*([\\d.]+)(deg|rad|grad|turn)?\\s*[, ]\\s*([\\d.]+)%?\\s*[, ]\\s*([\\d.]+)%?(?:\\s*[,/]\\s*([\\d.]+)(%?))?\\s*\\)");
    private static final Pattern HWB_PATTERN = Pattern.compile("hwb\\(\\s*([\\d.]+)(deg|rad|grad|turn)?\\s*[, ]\\s*([\\d.]+)%?\\s*[, ]\\s*([\\d.]+)%?(?:\\s*[,/]\\s*([\\d.]+)(%?))?\\s*\\)");
    private static final Pattern LAB_PATTERN = Pattern.compile("lab\\(\\s*([\\d.]+)%?\\s*[, ]\\s*([-+]?[\\d.]+)%?\\s*[, ]\\s*([-+]?[\\d.]+)%?(?:\\s*[,/]\\s*([\\d.]+)(%?))?\\s*\\)");
    private static final Pattern LCH_PATTERN = Pattern.compile("lch\\(\\s*([\\d.]+)%?\\s*[, ]\\s*([\\d.]+)%?\\s*[, ]\\s*([-+]?[\\d.]+)(deg|rad|grad|turn)?(?:\\s*[,/]\\s*([\\d.]+)(%?))?\\s*\\)");
    private static final Pattern OKLAB_PATTERN = Pattern.compile("oklab\\(\\s*([\\d.]+)%?\\s*[, ]\\s*([-+]?[\\d.]+)%?\\s*[, ]\\s*([-+]?[\\d.]+)%?(?:\\s*[,/]\\s*([\\d.]+)(%?))?\\s*\\)");
    private static final Pattern OKLCH_PATTERN = Pattern.compile("oklch\\(\\s*([\\d.]+)%?\\s*[, ]\\s*([\\d.]+)%?\\s*[, ]\\s*([-+]?[\\d.]+)(deg|rad|grad|turn)?(?:\\s*[,/]\\s*([\\d.]+)(%?))?\\s*\\)");
    private static final Pattern COLOR_FN_PATTERN = Pattern.compile("color\\(\\s*([\\w-]+)\\s+([\\d.]+)%?\\s+([\\d.]+)%?\\s+([\\d.]+)%?(?:\\s*[,/]\\s*([\\d.]+)(%?))?\\s*\\)");
    private static volatile boolean loaded = false;

    private static void ensureLoaded() {
        if (loaded) return;
        synchronized (lock) {
            if (loaded) return;
            loadNamedColors();
            loaded = true;
        }
    }

    private static void loadNamedColors() {
        String jsonStr = StaticAssetReader.readAsset("completions/css_colors.json");
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            loadFallback();
            return;
        }
        try {
            JSONObject root = new JSONObject(jsonStr);
            if (root.has("named_hex")) {
                JSONObject obj = root.getJSONObject("named_hex");
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    String hex = obj.getString(name);
                    Integer val = parseHex(hex);
                    if (val != null) {
                        NAMED_COLORS.put(name, val);
                    }
                }
            }
        } catch (Exception ignored) {
            loadFallback();
        }
    }

    private static void loadFallback() {
        NAMED_COLORS.put("black", 0xFF000000);
        NAMED_COLORS.put("white", 0xFFFFFFFF);
        NAMED_COLORS.put("red", 0xFFFF0000);
        NAMED_COLORS.put("green", 0xFF008000);
        NAMED_COLORS.put("blue", 0xFF0000FF);
        NAMED_COLORS.put("transparent", 0x00000000);
    }

    public static Integer parse(String colorStr) {
        if (colorStr == null) return null;
        colorStr = colorStr.trim().toLowerCase();

        ensureLoaded();
        if (NAMED_COLORS.containsKey(colorStr)) {
            return NAMED_COLORS.get(colorStr);
        }

        if (colorStr.startsWith("#")) {
            return parseHex(colorStr);
        }

        if (colorStr.startsWith("rgb")) {
            return parseRgb(colorStr);
        }

        if (colorStr.startsWith("hsl")) {
            return parseHsl(colorStr);
        }

        if (colorStr.startsWith("hwb")) {
            return parseHwb(colorStr);
        }

        if (colorStr.startsWith("oklch")) {
            return parseOklch(colorStr);
        }

        if (colorStr.startsWith("oklab")) {
            return parseOklab(colorStr);
        }

        if (colorStr.startsWith("lch")) {
            return parseLch(colorStr);
        }

        if (colorStr.startsWith("lab")) {
            return parseLab(colorStr);
        }

        if (colorStr.startsWith("color-mix")) {
            return parseColorMix(colorStr);
        }

        if (colorStr.startsWith("color(")) {
            return parseColorFn(colorStr);
        }

        if (colorStr.startsWith("light-dark")) {
            return parseLightDark(colorStr);
        }

        return null;
    }

    private static int argb(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private static Integer parseHex(String hex) {
        try {
            if (hex.length() == 4) { // #RGB
                int r = Integer.parseInt(hex.substring(1, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 3), 16);
                int b = Integer.parseInt(hex.substring(3, 4), 16);
                return argb(255, r | (r << 4), g | (g << 4), b | (b << 4));
            } else if (hex.length() == 5) { // #RGBA
                int r = Integer.parseInt(hex.substring(1, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 3), 16);
                int b = Integer.parseInt(hex.substring(3, 4), 16);
                int a = Integer.parseInt(hex.substring(4, 5), 16);
                return argb(a | (a << 4), r | (r << 4), g | (g << 4), b | (b << 4));
            } else if (hex.length() == 7) { // #RRGGBB
                long val = Long.parseLong(hex.substring(1), 16);
                return (int) (0xFF000000L | val);
            } else if (hex.length() == 9) { // #RRGGBBAA
                long rgb = Long.parseLong(hex.substring(1, 7), 16);
                long a = Long.parseLong(hex.substring(7, 9), 16);
                return (int) ((a << 24) | rgb);
            }
        } catch (IllegalArgumentException e) {
            // Ignore
        }
        return null;
    }

    private static Integer parseRgb(String rgbStr) {
        Matcher matcher = RGB_PATTERN.matcher(rgbStr);
        if (matcher.find()) {
            try {
                float r = parseColorComponent(matcher.group(1), "%".equals(matcher.group(2)), 255);
                float g = parseColorComponent(matcher.group(3), "%".equals(matcher.group(4)), 255);
                float b = parseColorComponent(matcher.group(5), "%".equals(matcher.group(6)), 255);
                float a = 255f;
                if (matcher.group(7) != null) {
                    a = parseAlpha(matcher.group(7), "%".equals(matcher.group(8)));
                }
                return argb((int) a, (int) r, (int) g, (int) b);
            } catch (Exception e) {
                // Ignore
            }
        }
        return null;
    }

    private static Integer parseHsl(String hslStr) {
        Matcher matcher = HSL_PATTERN.matcher(hslStr);
        if (matcher.find()) {
            try {
                float h = parseHue(matcher.group(1), matcher.group(2));
                float s = Float.parseFloat(matcher.group(3)) / 100f;
                float l = Float.parseFloat(matcher.group(4)) / 100f;
                float a = 255f;
                if (matcher.group(5) != null) {
                    a = parseAlpha(matcher.group(5), "%".equals(matcher.group(6)));
                }

                // Convert HSL to RGB
                float c = (1 - Math.abs(2 * l - 1)) * s;
                float x = c * (1 - Math.abs((h / 60) % 2 - 1));
                float m = l - c / 2;
                float r = 0, g = 0, b = 0;
                if (0 <= h && h < 60) {
                    r = c;
                    g = x;
                    b = 0;
                } else if (60 <= h && h < 120) {
                    r = x;
                    g = c;
                    b = 0;
                } else if (120 <= h && h < 180) {
                    r = 0;
                    g = c;
                    b = x;
                } else if (180 <= h && h < 240) {
                    r = 0;
                    g = x;
                    b = c;
                } else if (240 <= h && h < 300) {
                    r = x;
                    g = 0;
                    b = c;
                } else if (300 <= h && h < 360) {
                    r = c;
                    g = 0;
                    b = x;
                }

                return argb((int) a, (int) ((r + m) * 255), (int) ((g + m) * 255), (int) ((b + m) * 255));
            } catch (Exception e) {
                // Ignore
            }
        }
        return null;
    }

    private static float parseColorComponent(String val, boolean isPercent, float max) {
        float f = Float.parseFloat(val);
        if (isPercent) {
            f = (f / 100f) * max;
        }
        return Math.max(0, Math.min(max, f));
    }

    private static float parseAlpha(String val, boolean isPercent) {
        float f = Float.parseFloat(val);
        if (isPercent) {
            f = f / 100f;
        }
        return Math.max(0, Math.min(255, f * 255f));
    }

    private static float parseHue(String val, String unit) {
        float h = Float.parseFloat(val);
        if ("rad".equals(unit)) {
            h = (float) Math.toDegrees(h);
        } else if ("grad".equals(unit)) {
            h = h * 360f / 400f;
        } else if ("turn".equals(unit)) {
            h = h * 360f;
        }
        h = h % 360;
        if (h < 0) h += 360;
        return h;
    }

    private static Integer parseHwb(String hwbStr) {
        Matcher matcher = HWB_PATTERN.matcher(hwbStr);
        if (matcher.find()) {
            try {
                float h = parseHue(matcher.group(1), matcher.group(2));
                float w = parsePercentOrRatio(matcher.group(3));
                float b = parsePercentOrRatio(matcher.group(4));
                float a = 255f;
                if (matcher.group(5) != null) {
                    a = parseAlpha(matcher.group(5), "%".equals(matcher.group(6)));
                }

                if (w + b >= 1.0f) {
                    int gray = clamp255((w / (w + b)) * 255f);
                    return argb((int) a, gray, gray, gray);
                }

                float r = hwbComponent(h, 0);
                float g = hwbComponent(h, 8);
                float bVal = hwbComponent(h, 4);

                r = r * (1.0f - w - b) + w;
                g = g * (1.0f - w - b) + w;
                bVal = bVal * (1.0f - w - b) + w;

                return argb((int) a, clamp255(r * 255f), clamp255(g * 255f), clamp255(bVal * 255f));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static float hwbComponent(float h, int n) {
        float k = (n + h / 30f) % 12f;
        if (k < 0) k += 12f;
        float val = Math.max(-1f, Math.min(Math.min(k - 3f, 9f - k), 1f));
        return 0.5f - 0.5f * val;
    }

    private static Integer parseLab(String labStr) {
        Matcher matcher = LAB_PATTERN.matcher(labStr);
        if (matcher.find()) {
            try {
                float l = Float.parseFloat(matcher.group(1));
                float a = Float.parseFloat(matcher.group(2));
                float b = Float.parseFloat(matcher.group(3));
                float alpha = 255f;
                if (matcher.group(4) != null) {
                    alpha = parseAlpha(matcher.group(4), "%".equals(matcher.group(5)));
                }
                return labToRgb(l, a, b, alpha);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Integer parseLch(String lchStr) {
        Matcher matcher = LCH_PATTERN.matcher(lchStr);
        if (matcher.find()) {
            try {
                float l = Float.parseFloat(matcher.group(1));
                float c = Float.parseFloat(matcher.group(2));
                float h = parseHue(matcher.group(3), matcher.group(4));
                float alpha = 255f;
                if (matcher.group(5) != null) {
                    alpha = parseAlpha(matcher.group(5), "%".equals(matcher.group(6)));
                }
                double rad = Math.toRadians(h);
                float a = (float) (c * Math.cos(rad));
                float b = (float) (c * Math.sin(rad));
                return labToRgb(l, a, b, alpha);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Integer labToRgb(float l, float a, float b, float alpha) {
        float fy = (l + 16f) / 116f;
        float fx = a / 500f + fy;
        float fz = fy - b / 200f;
        float eps = 216f / 24389f; // 0.008856
        float kap = 24389f / 27f;   // 903.3

        float xr = (fx * fx * fx > eps) ? (fx * fx * fx) : ((116f * fx - 16f) / kap);
        float yr = (l > kap * eps) ? (float) Math.pow(fy, 3) : (l / kap);
        float zr = (fz * fz * fz > eps) ? (fz * fz * fz) : ((116f * fz - 16f) / kap);

        // D65 reference white
        float X = xr * 0.95047f;
        float Y = yr;
        float Z = zr * 1.08883f;

        // XYZ to linear sRGB
        float rLin = 3.2404542f * X - 1.5371385f * Y - 0.4985314f * Z;
        float gLin = -0.9692660f * X + 1.8760108f * Y + 0.0415560f * Z;
        float bLin = 0.0556434f * X - 0.2040259f * Y + 1.0572252f * Z;

        return argb((int) alpha,
                clamp255(linearToSrgb(rLin) * 255f),
                clamp255(linearToSrgb(gLin) * 255f),
                clamp255(linearToSrgb(bLin) * 255f));
    }

    private static Integer parseOklab(String oklabStr) {
        Matcher matcher = OKLAB_PATTERN.matcher(oklabStr);
        if (matcher.find()) {
            try {
                float l = parsePercentOrRatio(matcher.group(1));
                float a = Float.parseFloat(matcher.group(2));
                float b = Float.parseFloat(matcher.group(3));
                float alpha = 255f;
                if (matcher.group(4) != null) {
                    alpha = parseAlpha(matcher.group(4), "%".equals(matcher.group(5)));
                }
                return oklabToRgb(l, a, b, alpha);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Integer parseOklch(String oklchStr) {
        Matcher matcher = OKLCH_PATTERN.matcher(oklchStr);
        if (matcher.find()) {
            try {
                float l = parsePercentOrRatio(matcher.group(1));
                float c = Float.parseFloat(matcher.group(2));
                float h = parseHue(matcher.group(3), matcher.group(4));
                float alpha = 255f;
                if (matcher.group(5) != null) {
                    alpha = parseAlpha(matcher.group(5), "%".equals(matcher.group(6)));
                }
                double rad = Math.toRadians(h);
                float a = (float) (c * Math.cos(rad));
                float b = (float) (c * Math.sin(rad));
                return oklabToRgb(l, a, b, alpha);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Integer oklabToRgb(float l, float a, float b, float alpha) {
        float l_ = l + 0.3963377774f * a + 0.2158037573f * b;
        float m_ = l - 0.1055613458f * a - 0.0638541728f * b;
        float s_ = l - 0.0894841775f * a - 1.2914855480f * b;

        float lLin = l_ * l_ * l_;
        float mLin = m_ * m_ * m_;
        float sLin = s_ * s_ * s_;

        float rLin = +4.0767416621f * lLin - 3.3077115913f * mLin + 0.2309699292f * sLin;
        float gLin = -1.2684380046f * lLin + 2.6097574011f * mLin - 0.3413193965f * sLin;
        float bLin = -0.0041960863f * lLin - 0.7034186147f * mLin + 1.7076147010f * sLin;

        return argb((int) alpha,
                clamp255(linearToSrgb(rLin) * 255f),
                clamp255(linearToSrgb(gLin) * 255f),
                clamp255(linearToSrgb(bLin) * 255f));
    }

    private static Integer parseColorFn(String colorFnStr) {
        Matcher matcher = COLOR_FN_PATTERN.matcher(colorFnStr);
        if (matcher.find()) {
            try {
                String space = matcher.group(1);
                float r = parsePercentOrRatio(matcher.group(2));
                float g = parsePercentOrRatio(matcher.group(3));
                float b = parsePercentOrRatio(matcher.group(4));
                float alpha = 255f;
                if (matcher.group(5) != null) {
                    alpha = parseAlpha(matcher.group(5), "%".equals(matcher.group(6)));
                }

                if ("display-p3".equalsIgnoreCase(space)) {
                    float rLinP3 = srgbToLinear(r);
                    float gLinP3 = srgbToLinear(g);
                    float bLinP3 = srgbToLinear(b);

                    float rLin = 1.2249f * rLinP3 - 0.2247f * gLinP3 - 0.0002f * bLinP3;
                    float gLin = -0.0420f * rLinP3 + 1.0419f * gLinP3 + 0.0001f * bLinP3;
                    float bLin = -0.0197f * rLinP3 - 0.0786f * gLinP3 + 1.0983f * bLinP3;

                    return argb((int) alpha,
                            clamp255(linearToSrgb(rLin) * 255f),
                            clamp255(linearToSrgb(gLin) * 255f),
                            clamp255(linearToSrgb(bLin) * 255f));
                } else {
                    // srgb default
                    return argb((int) alpha, clamp255(r * 255f), clamp255(g * 255f), clamp255(b * 255f));
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Integer parseColorMix(String colorMixStr) {
        int openParen = colorMixStr.indexOf('(');
        int closeParen = colorMixStr.lastIndexOf(')');
        if (openParen == -1 || closeParen <= openParen) return null;

        String inner = colorMixStr.substring(openParen + 1, closeParen).trim();
        java.util.List<String> parts = splitTopLevelCommas(inner);
        if (parts.size() < 3) return null;

        String part1 = parts.get(1).trim();
        String part2 = parts.get(2).trim();

        ColorWithWeight c1 = parseColorWithWeight(part1);
        ColorWithWeight c2 = parseColorWithWeight(part2);
        if (c1 == null || c2 == null) return null;

        float p1 = c1.weight;
        float p2 = c2.weight;

        if (p1 < 0 && p2 < 0) {
            p1 = 0.5f;
            p2 = 0.5f;
        } else if (p1 >= 0 && p2 < 0) {
            p2 = Math.max(0f, 1.0f - p1);
        } else if (p1 < 0 && p2 >= 0) {
            p1 = Math.max(0f, 1.0f - p2);
        } else {
            float sum = p1 + p2;
            if (sum > 0) {
                p1 /= sum;
                p2 /= sum;
            } else {
                p1 = 0.5f;
                p2 = 0.5f;
            }
        }

        int col1 = c1.color;
        int col2 = c2.color;

        int a1 = (col1 >>> 24) & 0xFF, r1 = (col1 >>> 16) & 0xFF, g1 = (col1 >>> 8) & 0xFF, b1 = col1 & 0xFF;
        int a2 = (col2 >>> 24) & 0xFF, r2 = (col2 >>> 16) & 0xFF, g2 = (col2 >>> 8) & 0xFF, b2 = col2 & 0xFF;

        int a = clamp255(a1 * p1 + a2 * p2);
        int r = clamp255(r1 * p1 + r2 * p2);
        int g = clamp255(g1 * p1 + g2 * p2);
        int b = clamp255(b1 * p1 + b2 * p2);

        return argb(a, r, g, b);
    }

    private static Integer parseLightDark(String lightDarkStr) {
        int openParen = lightDarkStr.indexOf('(');
        int closeParen = lightDarkStr.lastIndexOf(')');
        if (openParen == -1 || closeParen <= openParen) return null;

        String inner = lightDarkStr.substring(openParen + 1, closeParen).trim();
        java.util.List<String> parts = splitTopLevelCommas(inner);
        if (parts.isEmpty()) return null;

        Integer first = parse(parts.get(0).trim());
        if (first != null) return first;
        if (parts.size() > 1) {
            return parse(parts.get(1).trim());
        }
        return null;
    }

    public static java.util.List<String> splitTopLevelCommas(String str) {
        java.util.List<String> result = new java.util.ArrayList<>();
        int len = str.length();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                if (depth > 0) depth--;
            } else if (c == ',' && depth == 0) {
                result.add(str.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < len) {
            result.add(str.substring(start).trim());
        }
        return result;
    }

    private static ColorWithWeight parseColorWithWeight(String input) {
        input = input.trim();
        int lastSpace = input.lastIndexOf(' ');
        if (lastSpace != -1) {
            String candidateWeight = input.substring(lastSpace + 1).trim();
            if (candidateWeight.endsWith("%")) {
                try {
                    float w = Float.parseFloat(candidateWeight.substring(0, candidateWeight.length() - 1)) / 100f;
                    String colorPart = input.substring(0, lastSpace).trim();
                    Integer col = parse(colorPart);
                    if (col != null) {
                        return new ColorWithWeight(col, w);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        Integer col = parse(input);
        if (col != null) {
            return new ColorWithWeight(col, -1f);
        }
        return null;
    }

    private static int clamp255(float val) {
        return Math.max(0, Math.min(255, Math.round(val)));
    }

    private static float parsePercentOrRatio(String val) {
        if (val == null) return 0f;
        val = val.trim();
        if (val.endsWith("%")) {
            return Float.parseFloat(val.substring(0, val.length() - 1)) / 100f;
        }
        float f = Float.parseFloat(val);
        return f > 1.0f ? f / 100f : f;
    }

    private static float linearToSrgb(float c) {
        c = Math.max(0f, Math.min(1f, c));
        return (c <= 0.0031308f) ? (12.92f * c) : (1.055f * (float) Math.pow(c, 1.0 / 2.4) - 0.055f);
    }

    private static float srgbToLinear(float c) {
        c = Math.max(0f, Math.min(1f, c));
        return (c <= 0.04045f) ? (c / 12.92f) : (float) Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static class ColorWithWeight {
        final int color;
        final float weight;

        ColorWithWeight(int color, float weight) {
            this.color = color;
            this.weight = weight;
        }
    }
}

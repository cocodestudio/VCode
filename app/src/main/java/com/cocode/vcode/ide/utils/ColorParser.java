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
    private static volatile boolean loaded = false;
    private static final Map<String, Integer> NAMED_COLORS = new HashMap<>();
    private static final Pattern RGB_PATTERN = Pattern.compile("rgba?\\(\\s*([\\d.]+)(%?)\\s*[, ]\\s*([\\d.]+)(%?)\\s*[, ]\\s*([\\d.]+)(%?)(?:\\s*[,/]\\s*([\\d.]+)(%?))?\\s*\\)");
    private static final Pattern HSL_PATTERN = Pattern.compile("hsla?\\(\\s*([\\d.]+)(deg|rad|grad|turn)?\\s*[, ]\\s*([\\d.]+)%\\s*[, ]\\s*([\\d.]+)%(?:\\s*[,/]\\s*([\\d.]+)(%?))?\\s*\\)");

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
}

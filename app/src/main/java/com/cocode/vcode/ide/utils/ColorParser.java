package com.cocode.vcode.ide.utils;

import android.graphics.Color;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing CSS color strings (named colors, hex, rgb/rgba, and hsl/hsla)
 * into Android {@link Color} integer values.
 */
public class ColorParser {

    private static final Map<String, Integer> NAMED_COLORS = new HashMap<>();
    private static final Pattern RGB_PATTERN = Pattern.compile("rgba?\\(\\s*([\\d.]+)(%?)\\s*[, ]\\s*([\\d.]+)(%?)\\s*[, ]\\s*([\\d.]+)(%?)(?:\\s*[,/]\\s*([\\d.]+)(%?))?\\s*\\)");
    private static final Pattern HSL_PATTERN = Pattern.compile("hsla?\\(\\s*([\\d.]+)(deg|rad|grad|turn)?\\s*[, ]\\s*([\\d.]+)%\\s*[, ]\\s*([\\d.]+)%(?:\\s*[,/]\\s*([\\d.]+)(%?))?\\s*\\)");

    static {
        NAMED_COLORS.put("aliceblue", 0xFFF0F8FF);
        NAMED_COLORS.put("antiquewhite", 0xFFFAEBD7);
        NAMED_COLORS.put("aqua", 0xFF00FFFF);
        NAMED_COLORS.put("aquamarine", 0xFF7FFFD4);
        NAMED_COLORS.put("azure", 0xFFF0FFFF);
        NAMED_COLORS.put("beige", 0xFFF5F5DC);
        NAMED_COLORS.put("bisque", 0xFFFFE4C4);
        NAMED_COLORS.put("black", 0xFF000000);
        NAMED_COLORS.put("blanchedalmond", 0xFFFFEBCD);
        NAMED_COLORS.put("blue", 0xFF0000FF);
        NAMED_COLORS.put("blueviolet", 0xFF8A2BE2);
        NAMED_COLORS.put("brown", 0xFFA52A2A);
        NAMED_COLORS.put("burlywood", 0xFFDEB887);
        NAMED_COLORS.put("cadetblue", 0xFF5F9EA0);
        NAMED_COLORS.put("chartreuse", 0xFF7FFF00);
        NAMED_COLORS.put("chocolate", 0xFFD2691E);
        NAMED_COLORS.put("coral", 0xFFFF7F50);
        NAMED_COLORS.put("cornflowerblue", 0xFF6495ED);
        NAMED_COLORS.put("cornsilk", 0xFFFFF8DC);
        NAMED_COLORS.put("crimson", 0xFFDC143C);
        NAMED_COLORS.put("cyan", 0xFF00FFFF);
        NAMED_COLORS.put("darkblue", 0xFF00008B);
        NAMED_COLORS.put("darkcyan", 0xFF008B8B);
        NAMED_COLORS.put("darkgoldenrod", 0xFFB8860B);
        NAMED_COLORS.put("darkgray", 0xFFA9A9A9);
        NAMED_COLORS.put("darkgreen", 0xFF006400);
        NAMED_COLORS.put("darkgrey", 0xFFA9A9A9);
        NAMED_COLORS.put("darkkhaki", 0xFFBDB76B);
        NAMED_COLORS.put("darkmagenta", 0xFF8B008B);
        NAMED_COLORS.put("darkolivegreen", 0xFF556B2F);
        NAMED_COLORS.put("darkorange", 0xFFFF8C00);
        NAMED_COLORS.put("darkorchid", 0xFF9932CC);
        NAMED_COLORS.put("darkred", 0xFF8B0000);
        NAMED_COLORS.put("darksalmon", 0xFFE9967A);
        NAMED_COLORS.put("darkseagreen", 0xFF8FBC8F);
        NAMED_COLORS.put("darkslateblue", 0xFF483D8B);
        NAMED_COLORS.put("darkslategray", 0xFF2F4F4F);
        NAMED_COLORS.put("darkslategrey", 0xFF2F4F4F);
        NAMED_COLORS.put("darkturquoise", 0xFF00CED1);
        NAMED_COLORS.put("darkviolet", 0xFF9400D3);
        NAMED_COLORS.put("deeppink", 0xFFFF1493);
        NAMED_COLORS.put("deepskyblue", 0xFF00BFFF);
        NAMED_COLORS.put("dimgray", 0xFF696969);
        NAMED_COLORS.put("dimgrey", 0xFF696969);
        NAMED_COLORS.put("dodgerblue", 0xFF1E90FF);
        NAMED_COLORS.put("firebrick", 0xFFB22222);
        NAMED_COLORS.put("floralwhite", 0xFFFFFAF0);
        NAMED_COLORS.put("forestgreen", 0xFF228B22);
        NAMED_COLORS.put("fuchsia", 0xFFFF00FF);
        NAMED_COLORS.put("gainsboro", 0xFFDCDCDC);
        NAMED_COLORS.put("ghostwhite", 0xFFF8F8FF);
        NAMED_COLORS.put("gold", 0xFFFFD700);
        NAMED_COLORS.put("goldenrod", 0xFFDAA520);
        NAMED_COLORS.put("gray", 0xFF808080);
        NAMED_COLORS.put("green", 0xFF008000);
        NAMED_COLORS.put("greenyellow", 0xFFADFF2F);
        NAMED_COLORS.put("grey", 0xFF808080);
        NAMED_COLORS.put("honeydew", 0xFFF0FFF0);
        NAMED_COLORS.put("hotpink", 0xFFFF69B4);
        NAMED_COLORS.put("indianred", 0xFFCD5C5C);
        NAMED_COLORS.put("indigo", 0xFF4B0082);
        NAMED_COLORS.put("ivory", 0xFFFFFFF0);
        NAMED_COLORS.put("khaki", 0xFFF0E68C);
        NAMED_COLORS.put("lavender", 0xFFE6E6FA);
        NAMED_COLORS.put("lavenderblush", 0xFFFFF0F5);
        NAMED_COLORS.put("lawngreen", 0xFF7CFC00);
        NAMED_COLORS.put("lemonchiffon", 0xFFFFFACD);
        NAMED_COLORS.put("lightblue", 0xFFADD8E6);
        NAMED_COLORS.put("lightcoral", 0xFFF08080);
        NAMED_COLORS.put("lightcyan", 0xFFE0FFFF);
        NAMED_COLORS.put("lightgoldenrodyellow", 0xFFFAFAD2);
        NAMED_COLORS.put("lightgray", 0xFFD3D3D3);
        NAMED_COLORS.put("lightgreen", 0xFF90EE90);
        NAMED_COLORS.put("lightgrey", 0xFFD3D3D3);
        NAMED_COLORS.put("lightpink", 0xFFFFB6C1);
        NAMED_COLORS.put("lightsalmon", 0xFFFFA07A);
        NAMED_COLORS.put("lightseagreen", 0xFF20B2AA);
        NAMED_COLORS.put("lightskyblue", 0xFF87CEFA);
        NAMED_COLORS.put("lightslategray", 0xFF778899);
        NAMED_COLORS.put("lightslategrey", 0xFF778899);
        NAMED_COLORS.put("lightsteelblue", 0xFFB0C4DE);
        NAMED_COLORS.put("lightyellow", 0xFFFFFFE0);
        NAMED_COLORS.put("lime", 0xFF00FF00);
        NAMED_COLORS.put("limegreen", 0xFF32CD32);
        NAMED_COLORS.put("linen", 0xFFFAF0E6);
        NAMED_COLORS.put("magenta", 0xFFFF00FF);
        NAMED_COLORS.put("maroon", 0xFF800000);
        NAMED_COLORS.put("mediumaquamarine", 0xFF66CDAA);
        NAMED_COLORS.put("mediumblue", 0xFF0000CD);
        NAMED_COLORS.put("mediumorchid", 0xFFBA55D3);
        NAMED_COLORS.put("mediumpurple", 0xFF9370DB);
        NAMED_COLORS.put("mediumseagreen", 0xFF3CB371);
        NAMED_COLORS.put("mediumslateblue", 0xFF7B68EE);
        NAMED_COLORS.put("mediumspringgreen", 0xFF00FA9A);
        NAMED_COLORS.put("mediumturquoise", 0xFF48D1CC);
        NAMED_COLORS.put("mediumvioletred", 0xFFC71585);
        NAMED_COLORS.put("midnightblue", 0xFF191970);
        NAMED_COLORS.put("mintcream", 0xFFF5FFFA);
        NAMED_COLORS.put("mistyrose", 0xFFFFE4E1);
        NAMED_COLORS.put("moccasin", 0xFFFFE4B5);
        NAMED_COLORS.put("navajowhite", 0xFFFFDEAD);
        NAMED_COLORS.put("navy", 0xFF000080);
        NAMED_COLORS.put("oldlace", 0xFFFDF5E6);
        NAMED_COLORS.put("olive", 0xFF808000);
        NAMED_COLORS.put("olivedrab", 0xFF6B8E23);
        NAMED_COLORS.put("orange", 0xFFFFA500);
        NAMED_COLORS.put("orangered", 0xFFFF4500);
        NAMED_COLORS.put("orchid", 0xFFDA70D6);
        NAMED_COLORS.put("palegoldenrod", 0xFFEEE8AA);
        NAMED_COLORS.put("palegreen", 0xFF98FB98);
        NAMED_COLORS.put("paleturquoise", 0xFFAFEEEE);
        NAMED_COLORS.put("palevioletred", 0xFFDB7093);
        NAMED_COLORS.put("papayawhip", 0xFFFFEFD5);
        NAMED_COLORS.put("peachpuff", 0xFFFFDAB9);
        NAMED_COLORS.put("peru", 0xFFCD853F);
        NAMED_COLORS.put("pink", 0xFFFFC0CB);
        NAMED_COLORS.put("plum", 0xFFDDA0DD);
        NAMED_COLORS.put("powderblue", 0xFFB0E0E6);
        NAMED_COLORS.put("purple", 0xFF800080);
        NAMED_COLORS.put("rebeccapurple", 0xFF663399);
        NAMED_COLORS.put("red", 0xFFFF0000);
        NAMED_COLORS.put("rosybrown", 0xFFBC8F8F);
        NAMED_COLORS.put("royalblue", 0xFF4169E1);
        NAMED_COLORS.put("saddlebrown", 0xFF8B4513);
        NAMED_COLORS.put("salmon", 0xFFFA8072);
        NAMED_COLORS.put("sandybrown", 0xFFF4A460);
        NAMED_COLORS.put("seagreen", 0xFF2E8B57);
        NAMED_COLORS.put("seashell", 0xFFFFF5EE);
        NAMED_COLORS.put("sienna", 0xFFA0522D);
        NAMED_COLORS.put("silver", 0xFFC0C0C0);
        NAMED_COLORS.put("skyblue", 0xFF87CEEB);
        NAMED_COLORS.put("slateblue", 0xFF6A5ACD);
        NAMED_COLORS.put("slategray", 0xFF708090);
        NAMED_COLORS.put("slategrey", 0xFF708090);
        NAMED_COLORS.put("snow", 0xFFFFFAFA);
        NAMED_COLORS.put("springgreen", 0xFF00FF7F);
        NAMED_COLORS.put("steelblue", 0xFF4682B4);
        NAMED_COLORS.put("tan", 0xFFD2B48C);
        NAMED_COLORS.put("teal", 0xFF008080);
        NAMED_COLORS.put("thistle", 0xFFD8BFD8);
        NAMED_COLORS.put("tomato", 0xFFFF6347);
        NAMED_COLORS.put("turquoise", 0xFF40E0D0);
        NAMED_COLORS.put("violet", 0xFFEE82EE);
        NAMED_COLORS.put("wheat", 0xFFF5DEB3);
        NAMED_COLORS.put("white", 0xFFFFFFFF);
        NAMED_COLORS.put("whitesmoke", 0xFFF5F5F5);
        NAMED_COLORS.put("yellow", 0xFFFFFF00);
        NAMED_COLORS.put("yellowgreen", 0xFF9ACD32);
        NAMED_COLORS.put("transparent", 0x00000000);
    }

    public static Integer parse(String colorStr) {
        if (colorStr == null) return null;
        colorStr = colorStr.trim().toLowerCase();

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

    private static Integer parseHex(String hex) {
        try {
            if (hex.length() == 4) { // #RGB
                int r = Integer.parseInt(hex.substring(1, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 3), 16);
                int b = Integer.parseInt(hex.substring(3, 4), 16);
                return Color.argb(255, r | (r << 4), g | (g << 4), b | (b << 4));
            } else if (hex.length() == 5) { // #RGBA
                int r = Integer.parseInt(hex.substring(1, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 3), 16);
                int b = Integer.parseInt(hex.substring(3, 4), 16);
                int a = Integer.parseInt(hex.substring(4, 5), 16);
                return Color.argb(a | (a << 4), r | (r << 4), g | (g << 4), b | (b << 4));
            } else if (hex.length() == 7) { // #RRGGBB
                return Color.parseColor(hex);
            } else if (hex.length() == 9) { // #RRGGBBAA
                String a = hex.substring(7, 9);
                String rgb = hex.substring(1, 7);
                return Color.parseColor("#" + a + rgb);
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
                return Color.argb((int) a, (int) r, (int) g, (int) b);
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

                return Color.argb((int) a, (int) ((r + m) * 255), (int) ((g + m) * 255), (int) ((b + m) * 255));
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

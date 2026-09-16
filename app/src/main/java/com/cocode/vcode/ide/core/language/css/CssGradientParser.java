package com.cocode.vcode.ide.core.language.css;

import com.cocode.vcode.ide.core.editor.highlight.GradientPreview;
import com.cocode.vcode.ide.utils.ColorParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for CSS gradient functions into {@link GradientPreview} objects for live editor preview circles.
 * Supports linear-gradient, repeating-linear-gradient, radial-gradient, repeating-radial-gradient,
 * conic-gradient, and repeating-conic-gradient.
 */
public final class CssGradientParser {

    private CssGradientParser() {}

    public static boolean isGradientFunction(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return "linear-gradient".equals(lower)
                || "repeating-linear-gradient".equals(lower)
                || "radial-gradient".equals(lower)
                || "repeating-radial-gradient".equals(lower)
                || "conic-gradient".equals(lower)
                || "repeating-conic-gradient".equals(lower);
    }

    public static boolean isColorFunction(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return "rgb".equals(lower)
                || "rgba".equals(lower)
                || "hsl".equals(lower)
                || "hsla".equals(lower)
                || "hwb".equals(lower)
                || "lab".equals(lower)
                || "lch".equals(lower)
                || "oklab".equals(lower)
                || "oklch".equals(lower)
                || "color".equals(lower)
                || "color-mix".equals(lower)
                || "light-dark".equals(lower);
    }

    public static GradientPreview parse(String gradientExpr) {
        if (gradientExpr == null) return null;
        gradientExpr = gradientExpr.trim();

        int openParen = gradientExpr.indexOf('(');
        int closeParen = gradientExpr.lastIndexOf(')');
        if (openParen == -1 || closeParen <= openParen) return null;

        String fnName = gradientExpr.substring(0, openParen).trim().toLowerCase();
        if (!isGradientFunction(fnName)) return null;

        boolean repeating = fnName.startsWith("repeating-");
        int type;
        if (fnName.contains("linear")) {
            type = GradientPreview.TYPE_LINEAR;
        } else if (fnName.contains("radial")) {
            type = GradientPreview.TYPE_RADIAL;
        } else if (fnName.contains("conic")) {
            type = GradientPreview.TYPE_CONIC;
        } else {
            return null;
        }

        String inner = gradientExpr.substring(openParen + 1, closeParen).trim();
        List<String> args = ColorParser.splitTopLevelCommas(inner);
        if (args.isEmpty()) return null;

        float angleDegrees = (type == GradientPreview.TYPE_LINEAR) ? 180f : 0f;
        int stopStartIndex = 0;

        String firstArg = args.get(0).trim().toLowerCase();
        if (type == GradientPreview.TYPE_LINEAR) {
            Float parsedAngle = parseLinearDirection(firstArg);
            if (parsedAngle != null) {
                angleDegrees = parsedAngle;
                stopStartIndex = 1;
            }
        } else if (type == GradientPreview.TYPE_RADIAL) {
            if (isRadialShapeOrPosition(firstArg)) {
                stopStartIndex = 1;
            }
        } else { // CONIC
            Float parsedAngle = parseConicStartAngle(firstArg);
            if (parsedAngle != null) {
                angleDegrees = parsedAngle;
                stopStartIndex = 1;
            }
        }

        List<Integer> colorsList = new ArrayList<>();
        List<Float> positionsList = new ArrayList<>();
        boolean hasExplicitPosition = false;

        for (int i = stopStartIndex; i < args.size(); i++) {
            String stopStr = args.get(i).trim();
            if (stopStr.isEmpty()) continue;

            ParsedStop stop = parseStop(stopStr);
            if (stop != null) {
                colorsList.add(stop.color);
                positionsList.add(stop.position);
                if (stop.position != null) {
                    hasExplicitPosition = true;
                }
            }
        }

        if (colorsList.isEmpty()) return null;

        int[] colors;
        float[] positions = null;

        if (colorsList.size() == 1) {
            int c = colorsList.get(0);
            colors = new int[]{c, c};
        } else {
            colors = new int[colorsList.size()];
            for (int i = 0; i < colorsList.size(); i++) {
                colors[i] = colorsList.get(i);
            }

            if (hasExplicitPosition) {
                positions = new float[colors.length];
                float lastPos = 0f;
                for (int i = 0; i < colors.length; i++) {
                    Float p = positionsList.get(i);
                    if (p != null) {
                        lastPos = Math.max(lastPos, p);
                        positions[i] = Math.min(1.0f, lastPos);
                    } else {
                        positions[i] = (colors.length > 1) ? ((float) i / (colors.length - 1)) : 0f;
                    }
                }
            }
        }

        return new GradientPreview(type, colors, positions, angleDegrees, repeating);
    }

    private static Float parseLinearDirection(String arg) {
        if (arg.startsWith("to ")) {
            String dir = arg.substring(3).trim();
            if ("top".equals(dir)) return 0f;
            if ("right".equals(dir)) return 90f;
            if ("bottom".equals(dir)) return 180f;
            if ("left".equals(dir)) return 270f;
            if ("top right".equals(dir) || "right top".equals(dir)) return 45f;
            if ("bottom right".equals(dir) || "right bottom".equals(dir)) return 135f;
            if ("bottom left".equals(dir) || "left bottom".equals(dir)) return 225f;
            if ("top left".equals(dir) || "left top".equals(dir)) return 315f;
        }

        return parseAngle(arg);
    }

    private static Float parseConicStartAngle(String arg) {
        if (arg.startsWith("from ")) {
            String remainder = arg.substring(5).trim();
            int atIdx = remainder.indexOf("at ");
            if (atIdx != -1) {
                remainder = remainder.substring(0, atIdx).trim();
            }
            return parseAngle(remainder);
        }
        return null;
    }

    private static Float parseAngle(String arg) {
        try {
            if (arg.endsWith("deg")) {
                return Float.parseFloat(arg.substring(0, arg.length() - 3).trim());
            } else if (arg.endsWith("rad")) {
                float rad = Float.parseFloat(arg.substring(0, arg.length() - 3).trim());
                return (float) Math.toDegrees(rad);
            } else if (arg.endsWith("turn")) {
                float turn = Float.parseFloat(arg.substring(0, arg.length() - 4).trim());
                return turn * 360f;
            } else if (arg.endsWith("grad")) {
                float grad = Float.parseFloat(arg.substring(0, arg.length() - 4).trim());
                return grad * 360f / 400f;
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static boolean isRadialShapeOrPosition(String arg) {
        return arg.startsWith("circle") || arg.startsWith("ellipse") || arg.startsWith("at ");
    }

    private static class ParsedStop {
        final int color;
        final Float position;

        ParsedStop(int color, Float position) {
            this.color = color;
            this.position = position;
        }
    }

    private static ParsedStop parseStop(String stopStr) {
        stopStr = stopStr.trim();
        int lastSpace = stopStr.lastIndexOf(' ');
        if (lastSpace != -1) {
            String candidatePos = stopStr.substring(lastSpace + 1).trim();
            Float pos = null;
            if (candidatePos.endsWith("%")) {
                try {
                    pos = Float.parseFloat(candidatePos.substring(0, candidatePos.length() - 1)) / 100f;
                } catch (NumberFormatException ignored) {
                }
            }

            if (pos != null) {
                String colorPart = stopStr.substring(0, lastSpace).trim();
                Integer col = ColorParser.parse(colorPart);
                if (col != null) {
                    return new ParsedStop(col, pos);
                }
            }
        }

        Integer col = ColorParser.parse(stopStr);
        if (col != null) {
            return new ParsedStop(col, null);
        }

        return null;
    }
}

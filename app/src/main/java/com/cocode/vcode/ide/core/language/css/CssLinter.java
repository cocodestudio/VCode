package com.cocode.vcode.ide.core.language.css;

import com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader;
import com.cocode.vcode.ide.core.diagnostic.util.KnownElements;
import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real-time zero-allocation AST linter for CSS.
 * Operates directly on {@link CssSyntaxTree} and {@link CssTokenStream},
 * providing comprehensive validation for rules, selectors, properties,
 * values, color formats, and best practices without regex token masks.
 */
public class CssLinter {

    private static boolean isValidColor(String v, String prop) {
        if (v == null || v.isEmpty()) return true;
        String lo = v.toLowerCase().trim();
        if ("invert".equals(lo) && "outline-color".equals(prop)) return true;
        if ("border-color".equals(prop) && lo.contains(" ")) {
            for (String part : lo.split("\\s+")) {
                if (!part.isEmpty() && !isSingleColor(part)) return false;
            }
            return true;
        }
        return isSingleColor(lo);
    }

    private static boolean isSingleColor(String lo) {
        if (lo.startsWith("#") || lo.startsWith("rgb(") || lo.startsWith("rgba(")
                || lo.startsWith("hsl(") || lo.startsWith("hsla(") || lo.startsWith("oklch(")
                || lo.startsWith("oklab(") || lo.startsWith("lch(") || lo.startsWith("lab(")
                || lo.startsWith("color(") || lo.startsWith("color-mix(") || lo.startsWith("var(")
                || lo.startsWith("light-dark(") || lo.startsWith("hwb(")) return true;
        return KnownElements.CSS_NAMED_COLORS.contains(lo);
    }

    public static List<Problem> analyze(File file, String text) {
        if (text == null || text.trim().isEmpty()) return new ArrayList<>();
        CssTokenStream tokens = CssLexer.tokenize(text);
        CssSyntaxTree tree = CssParser.parse(tokens, text);
        return analyze(file, text, tree, tokens);
    }

    public static List<Problem> analyze(File file, String text, CssSyntaxTree tree, CssTokenStream tokens) {
        if (text == null || text.trim().isEmpty()) return new ArrayList<>();
        List<Problem> problems = new ArrayList<>();

        int mode = com.cocode.vcode.ide.core.language.base.ParseModeGate.selectByLineCount(text);
        if (mode == com.cocode.vcode.ide.core.language.js.ParseResult.MODE_TOKENIZE_ONLY) {
            return problems;
        }

        if (tree == null || tree.nodeCount <= 1) {
            return problems;
        }

        boolean pastFirstRule = false;
        Set<String> declaredVars = new HashSet<>();
        Set<String> usedVars = new HashSet<>();

        int nodeCount = tree.nodeCount;
        for (int i = 1; i < nodeCount; i++) {
            int type = tree.nodeType[i];
            int start = tree.nodeStart[i];
            int end = tree.nodeEnd[i];
            int length = Math.max(1, end - start);

            if (type == CssSyntaxTree.N_ERROR) {
                int line = LinterUtils.getLine(text, start);
                int col = LinterUtils.getColumn(text, start);
                String msg = tree.nodeName[i] != null ? tree.nodeName[i] : "Syntax error in CSS";
                problems.add(new Problem(file, line, col, length, msg, Problem.Severity.ERROR));
                continue;
            }

            if (type == CssSyntaxTree.N_RULE || type == CssSyntaxTree.N_AT_RULE) {
                int selectorChild = 0;
                int child = tree.nodeChild[i];
                int declCount = 0;
                int nestedRuleCount = 0;

                // Scan children to locate selector and count declarations/nested rules
                int childScanLoop = 0;
                while (child > 0 && child < tree.nodeCount && ++childScanLoop <= tree.nodeCount) {
                    int cType = tree.nodeType[child];
                    if (cType == CssSyntaxTree.N_SELECTOR && selectorChild == 0) {
                        selectorChild = child;
                    } else if (cType == CssSyntaxTree.N_DECLARATION) {
                        declCount++;
                    } else if (cType == CssSyntaxTree.N_RULE || cType == CssSyntaxTree.N_AT_RULE) {
                        nestedRuleCount++;
                    }
                    child = tree.nodeSibling[child];
                }

                String selector = selectorChild != 0 ? tree.nodeName[selectorChild] : null;
                if (selector != null) {
                    int selStart = tree.nodeStart[selectorChild];
                    int selLine = LinterUtils.getLine(text, selStart);
                    int selCol = LinterUtils.getColumn(text, selStart);
                    int selLen = Math.max(1, tree.nodeEnd[selectorChild] - selStart);

                    // @media breakpoint in px
                    if (selector.startsWith("@media") && hasPixelValue(selector)) {
                        problems.add(new Problem(file, selLine, selCol, selLen,
                                "'@media' breakpoint in 'px': consider 'em' or 'rem' for accessibility scaling",
                                Problem.Severity.INFO));
                    }

                    // ID selector check
                    if (selector.contains("#") && !selector.startsWith("@") && !isInsideSpecialAtRule(tree, i)) {
                        problems.add(new Problem(file, selLine, selCol, selLen,
                                "Avoid using ID selectors ('#id') for styling: prefer class selectors",
                                Problem.Severity.INFO));
                    }

                    // Overly specific selector
                    if (selectorSpecificityTooHigh(selector)) {
                        problems.add(new Problem(file, selLine, selCol, selLen,
                                "Overly specific selector '" + selector + "': hard to override, prefer simpler class selectors",
                                Problem.Severity.WARNING));
                    }

                    // @import order
                    if (selector.startsWith("@import")) {
                        if (pastFirstRule) {
                            problems.add(new Problem(file, selLine, selCol, selLen,
                                "'@import' must appear before all other rules",
                                Problem.Severity.ERROR));
                        }
                    } else if (!selector.startsWith("@") && !selector.isEmpty()) {
                        pastFirstRule = true;
                    }

                    // Empty rule check
                    if (type == CssSyntaxTree.N_RULE && declCount == 0 && nestedRuleCount == 0 && !selector.isEmpty() && !selector.startsWith("@")) {
                        problems.add(new Problem(file, selLine, selCol, selLen,
                                "Empty rule for selector '" + selector + "': remove or add declarations",
                                Problem.Severity.WARNING));
                    }
                }

                // Per-block declaration tracking
                Map<String, Integer> propsInBlock = new LinkedHashMap<>();
                Map<String, Integer> propLineInBlock = new LinkedHashMap<>();
                Set<String> vendorPropsInBlock = new HashSet<>();
                boolean blockHasColor = false;
                boolean blockHasBgColor = false;
                boolean blockHasFlex = false;
                boolean blockHasGrid = false;
                boolean blockHasGap = false;
                boolean blockHasMarginLeftAuto = false;
                boolean blockHasMarginRightAuto = false;

                child = tree.nodeChild[i];
                int childDeclLoop = 0;
                while (child > 0 && child < tree.nodeCount && ++childDeclLoop <= tree.nodeCount) {
                    if (tree.nodeType[child] == CssSyntaxTree.N_DECLARATION) {
                        int declChild = tree.nodeChild[child];
                        int propNode = 0;
                        int valNode = 0;

                        int declLoop = 0;
                        while (declChild > 0 && declChild < tree.nodeCount && ++declLoop <= tree.nodeCount) {
                            if (tree.nodeType[declChild] == CssSyntaxTree.N_PROPERTY) {
                                propNode = declChild;
                            } else if (tree.nodeType[declChild] == CssSyntaxTree.N_VALUE) {
                                valNode = declChild;
                            }
                            declChild = tree.nodeSibling[declChild];
                        }

                        if (propNode != 0) {
                            String prop = tree.nodeName[propNode];
                            String value = valNode != 0 ? tree.nodeValue[valNode] : "";
                            int propStart = tree.nodeStart[propNode];
                            int propLine = LinterUtils.getLine(text, propStart);
                            int propCol = LinterUtils.getColumn(text, propStart);
                            int propLen = Math.max(1, tree.nodeEnd[propNode] - propStart);

                            processDeclaration(file, prop, value, propLine, propCol, propLen,
                                    propsInBlock, propLineInBlock, vendorPropsInBlock,
                                    declaredVars, usedVars, problems);

                            // Update block flags
                            String pLo = prop != null ? prop.toLowerCase() : "";
                            String vLo = value != null ? value.toLowerCase() : "";

                            if ("color".equals(pLo)) blockHasColor = true;
                            if ("background-color".equals(pLo)) blockHasBgColor = true;
                            if ("display".equals(pLo) && vLo.contains("flex")) blockHasFlex = true;
                            if ("display".equals(pLo) && vLo.contains("grid")) blockHasGrid = true;
                            if ("gap".equals(pLo) || "row-gap".equals(pLo) || "column-gap".equals(pLo)) blockHasGap = true;
                            if ("margin-left".equals(pLo) && "auto".equals(vLo)) blockHasMarginLeftAuto = true;
                            if ("margin-right".equals(pLo) && "auto".equals(vLo)) blockHasMarginRightAuto = true;
                        }
                    }
                    child = tree.nodeSibling[child];
                }

                int ruleLine = LinterUtils.getLine(text, start);

                // Vendor prefix check
                for (Map.Entry<String, String> vpe : KnownElements.VENDOR_PREFIX_NEEDED.entrySet()) {
                    String prop = vpe.getKey();
                    String vendor = vpe.getValue();
                    if (propsInBlock.containsKey(prop) && !vendorPropsInBlock.contains(vendor)) {
                        int pline = propsInBlock.get(prop);
                        problems.add(new Problem(file, pline, 1, prop.length(),
                                "'" + prop + "' may need '" + vendor + "' for broader browser support",
                                Problem.Severity.INFO));
                    }
                }

                // Color without background-color and vice versa (scoped to root/body/html selectors)
                if (selector != null && (selector.equals("body") || selector.equals("html") || selector.equals(":root"))) {
                    if (blockHasColor && !blockHasBgColor) {
                        problems.add(new Problem(file, ruleLine, 1, 5,
                                "'color' is set without 'background-color': may cause readability issues on some themes",
                                Problem.Severity.INFO));
                    }
                    if (blockHasBgColor && !blockHasColor) {
                        problems.add(new Problem(file, ruleLine, 1, 16,
                                "'background-color' is set without 'color': may cause readability issues on some themes",
                                Problem.Severity.INFO));
                    }
                }

                // Flex/grid without gap
                if ((blockHasFlex || blockHasGrid) && !blockHasGap) {
                    problems.add(new Problem(file, ruleLine, 1, 7,
                            "No 'gap' property in flex/grid rule: consider 'gap' instead of margin-based spacing",
                            Problem.Severity.INFO));
                }

                // Margin auto without shorthand
                if (blockHasMarginLeftAuto && blockHasMarginRightAuto) {
                    problems.add(new Problem(file, ruleLine, 1, 6,
                            "Consider 'margin: 0 auto' or flexbox centering instead of separate margin declarations",
                            Problem.Severity.INFO));
                }
            }
        }

        // CSS variable unused check
        for (String varName : declaredVars) {
            if (!usedVars.contains(varName)) {
                problems.add(new Problem(file, 1, 1, varName.length(),
                        "CSS variable '" + varName + "' is declared but never used in this file",
                        Problem.Severity.INFO));
            }
        }

        // Shortenable hex colors check
        checkShortenableHexTokens(file, text, tree, problems);

        return problems;
    }

    private static void processDeclaration(File file, String prop, String value,
                                           int propLine, int propCol, int propLen,
                                           Map<String, Integer> propsInBlock, Map<String, Integer> propLineInBlock,
                                           Set<String> vendorPropsInBlock,
                                           Set<String> declaredVars, Set<String> usedVars,
                                           List<Problem> problems) {
        if (prop == null || prop.isEmpty()) return;
        String pLo = prop.toLowerCase();

        // Unknown property check
        if (!prop.startsWith("--") && !prop.startsWith("-webkit-") && !prop.startsWith("-moz-")
                && !prop.startsWith("-ms-") && !prop.startsWith("-o-")) {
            if (!KnownElements.VALID_CSS_PROPERTIES.contains(pLo) && StaticCompletionLoader.getCssProperty(prop) == null) {
                if ("clip".equals(pLo)) {
                    problems.add(new Problem(file, propLine, propCol, propLen,
                            "'clip' is deprecated — use 'clip-path' instead",
                            Problem.Severity.WARNING));
                } else if ("zoom".equals(pLo)) {
                    problems.add(new Problem(file, propLine, propCol, propLen,
                            "'zoom' is deprecated — use 'transform: scale()' instead",
                            Problem.Severity.WARNING));
                } else {
                    problems.add(new Problem(file, propLine, propCol, propLen,
                            "Unknown CSS property '" + prop + "': not a standard CSS property",
                            Problem.Severity.WARNING));
                }
            }
        } else if (prop.startsWith("-")) {
            vendorPropsInBlock.add(prop);
        }

        // Duplicate property in same rule
        if (propsInBlock.containsKey(prop)) {
            int firstLine = propsInBlock.get(prop);
            problems.add(new Problem(file, propLine, propCol, propLen,
                    "Duplicate property '" + prop + "' in same rule (also on line " + firstLine + ")",
                    Problem.Severity.WARNING));
        } else {
            propsInBlock.put(prop, propLine);
            propLineInBlock.put(prop, propLine);
        }

        if (value == null) return;
        String valTrimmed = value.trim();

        // Track CSS variables
        if (prop.startsWith("--")) {
            declaredVars.add(prop);
        }
        if (valTrimmed.contains("var(")) {
            extractVarUsages(valTrimmed, usedVars);
        }

        // !important warning
        if (valTrimmed.contains("!important")) {
            problems.add(new Problem(file, propLine, propCol, propLen,
                    "'!important' on '" + prop + "': overrides cascade, makes maintenance difficult",
                    Problem.Severity.WARNING));
        }

        // 0px warning
        if (hasZeroPx(valTrimmed)) {
            problems.add(new Problem(file, propLine, propCol, propLen,
                    "'0px' — units are unnecessary on zero values, use '0'",
                    Problem.Severity.INFO));
        }

        // pt unit warning
        if (hasPtUnit(valTrimmed)) {
            problems.add(new Problem(file, propLine, propCol, propLen,
                    "Avoid using 'pt' units for screen layouts: prefer 'px', 'em', or 'rem'",
                    Problem.Severity.INFO));
        }

        // var() without fallback warning
        if (hasVarWithoutFallback(valTrimmed)) {
            problems.add(new Problem(file, propLine, propCol, propLen,
                    "CSS variable in '" + prop + "' used without a fallback value",
                    Problem.Severity.INFO));
        }

        // Invalid color value check
        if (KnownElements.isCssColorProperty(pLo)) {
            String v = valTrimmed.replace("!important", "").trim();
            if (!v.isEmpty() && !isValidColor(v, pLo)) {
                problems.add(new Problem(file, propLine, propCol, propLen,
                        "Invalid color value '" + v + "' for '" + prop + "'",
                        Problem.Severity.ERROR));
            }
        }

        // Shorthand / longhand ordering (warn only if shorthand overrides an earlier longhand)
        for (Map.Entry<String, Set<String>> entry : KnownElements.CSS_SHORTHAND_LONGHANDS.entrySet()) {
            String shorthand = entry.getKey();
            Set<String> longhands = entry.getValue();
            if (shorthand.equals(pLo)) {
                for (String lh : longhands) {
                    if (propLineInBlock.containsKey(lh)) {
                        int lhLine = propLineInBlock.get(lh);
                        problems.add(new Problem(file, propLine, propCol, propLen,
                                "'" + shorthand + "' overrides previously set '" + lh + "' on line " + lhLine,
                                Problem.Severity.WARNING));
                    }
                }
            }
        }
    }

    private static boolean hasPixelValue(String s) {
        if (s == null) return false;
        int idx = s.indexOf("px");
        while (idx > 0) {
            if (Character.isDigit(s.charAt(idx - 1))) return true;
            idx = s.indexOf("px", idx + 2);
        }
        return false;
    }

    private static boolean hasZeroPx(String s) {
        if (s == null) return false;
        int idx = s.indexOf("0px");
        while (idx >= 0) {
            boolean startBound = (idx == 0) || !Character.isLetterOrDigit(s.charAt(idx - 1));
            boolean endBound = (idx + 3 >= s.length()) || !Character.isLetterOrDigit(s.charAt(idx + 3));
            if (startBound && endBound) return true;
            idx = s.indexOf("0px", idx + 3);
        }
        return false;
    }

    private static boolean hasPtUnit(String s) {
        if (s == null) return false;
        int idx = s.indexOf("pt");
        while (idx > 0) {
            boolean isNumBefore = Character.isDigit(s.charAt(idx - 1));
            boolean endBound = (idx + 2 >= s.length()) || !Character.isLetterOrDigit(s.charAt(idx + 2));
            if (isNumBefore && endBound) return true;
            idx = s.indexOf("pt", idx + 2);
        }
        return false;
    }

    private static int findMatchingParen(String s, int openParenIdx) {
        int depth = 0;
        for (int i = openParenIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static boolean hasFallbackComma(String inner) {
        int parenDepth = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '(') parenDepth++;
            else if (c == ')') {
                if (parenDepth > 0) parenDepth--;
            } else if (c == ',' && parenDepth == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasVarWithoutFallback(String s) {
        if (s == null) return false;
        int start = s.indexOf("var(");
        while (start >= 0) {
            int openParen = start + 3;
            int close = findMatchingParen(s, openParen);
            if (close > openParen) {
                String inner = s.substring(openParen + 1, close);
                if (!hasFallbackComma(inner)) return true;
            }
            start = s.indexOf("var(", start + 4);
        }
        return false;
    }

    private static void extractVarUsages(String s, Set<String> usedVars) {
        int start = s.indexOf("var(");
        while (start >= 0) {
            int openParen = start + 3;
            int close = findMatchingParen(s, openParen);
            if (close > openParen) {
                String inner = s.substring(openParen + 1, close).trim();
                int comma = -1;
                int parenDepth = 0;
                for (int i = 0; i < inner.length(); i++) {
                    char c = inner.charAt(i);
                    if (c == '(') parenDepth++;
                    else if (c == ')') {
                        if (parenDepth > 0) parenDepth--;
                    } else if (c == ',' && parenDepth == 0) {
                        comma = i;
                        break;
                    }
                }
                String varName = comma >= 0 ? inner.substring(0, comma).trim() : inner;
                if (varName.startsWith("--")) {
                    usedVars.add(varName);
                }
            }
            start = s.indexOf("var(", start + 4);
        }
    }

    private static boolean selectorSpecificityTooHigh(String selector) {
        if (selector == null || selector.isEmpty() || selector.startsWith("@")) return false;
        for (String group : selector.split(",")) {
            int depth = 0;
            for (String part : group.trim().split("\\s+")) {
                if (!part.isEmpty()) depth++;
            }
            if (depth > 3) return true;
        }
        return false;
    }

    private static boolean isInsideSpecialAtRule(CssSyntaxTree tree, int node) {
        int parent = tree.nodeParent[node];
        while (parent > 0 && parent < tree.nodeCount) {
            if (tree.nodeType[parent] == CssSyntaxTree.N_AT_RULE) {
                int child = tree.nodeChild[parent];
                int loop = 0;
                while (child > 0 && child < tree.nodeCount && ++loop <= tree.nodeCount) {
                    if (tree.nodeType[child] == CssSyntaxTree.N_SELECTOR) {
                        String sel = tree.nodeName[child];
                        if (sel != null && (sel.startsWith("@keyframes") || sel.startsWith("@font-face") || sel.startsWith("@-webkit-keyframes"))) {
                            return true;
                        }
                    }
                    child = tree.nodeSibling[child];
                }
            }
            parent = tree.nodeParent[parent];
        }
        return false;
    }

    private static void checkShortenableHexTokens(File file, String text, CssSyntaxTree tree, List<Problem> problems) {
        int nodeCount = tree.nodeCount;
        for (int i = 1; i < nodeCount; i++) {
            if (tree.nodeType[i] == CssSyntaxTree.N_VALUE) {
                String val = tree.nodeValue[i];
                if (val != null && val.contains("#")) {
                    int valStart = tree.nodeStart[i];
                    int hexIdx = val.indexOf('#');
                    while (hexIdx >= 0 && hexIdx + 6 < val.length()) {
                        String hex = val.substring(hexIdx + 1, hexIdx + 7);
                        if (isHex6(hex)) {
                            char r1 = hex.charAt(0), r2 = hex.charAt(1);
                            char g1 = hex.charAt(2), g2 = hex.charAt(3);
                            char b1 = hex.charAt(4), b2 = hex.charAt(5);
                            if (r1 == r2 && g1 == g2 && b1 == b2) {
                                String full = "#" + hex;
                                String shortHex = "#" + r1 + g1 + b1;
                                int absOffset = valStart + hexIdx;
                                int line = LinterUtils.getLine(text, absOffset);
                                int col = LinterUtils.getColumn(text, absOffset);
                                problems.add(new Problem(file, line, col, full.length(),
                                        "Color '" + full + "' can be shortened to '" + shortHex + "'",
                                        Problem.Severity.INFO));
                            }
                        }
                        hexIdx = val.indexOf('#', hexIdx + 7);
                    }
                }
            }
        }
    }

    private static boolean isHex6(String s) {
        if (s == null || s.length() != 6) return false;
        for (int i = 0; i < 6; i++) {
            char c = s.charAt(i);
            boolean isHexChar = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!isHexChar) return false;
        }
        return true;
    }
}

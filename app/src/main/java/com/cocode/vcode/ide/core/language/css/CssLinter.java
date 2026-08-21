package com.cocode.vcode.ide.core.language.css;

import com.cocode.vcode.ide.core.diagnostic.util.KnownElements;
import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.diagnostic.util.TokenMask;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real-time linter for CSS, checking for syntax errors, unknown properties, missing semicolons, and invalid values.
 */
public class CssLinter {

    // Patterns compiled once
    private static final Pattern PAT_ZERO_PX = Pattern.compile("\\b0px\\b");
    private static final Pattern PAT_VAR_NO_FB = Pattern.compile("var\\(\\s*(--[\\w-]+)\\s*\\)(?!\\s*,)(?![^)]*,)");
    private static final Pattern PAT_MEDIA_PX = Pattern.compile("@media[^{]*\\b\\d+px\\b");
    private static final Pattern PAT_HEX_SHORT = Pattern.compile("#([0-9a-fA-F]{6})\\b");
    private static final Pattern PAT_CSS_VAR_DECL = Pattern.compile("(--[\\w-]+)\\s*:");
    private static final Pattern PAT_CSS_VAR_USE = Pattern.compile("var\\(\\s*(--[\\w-]+)");
    private static final Set<String> COLOR_PROPS = new HashSet<>(java.util.Arrays.asList(
            "color", "background-color", "border-color", "border-top-color", "border-right-color",
            "border-bottom-color", "border-left-color", "outline-color", "text-decoration-color", "caret-color"));

    private static boolean isValidColor(String v) {
        if (v.isEmpty()) return true;
        String lo = v.toLowerCase();
        if (lo.startsWith("#") || lo.startsWith("rgb(") || lo.startsWith("rgba(")
                || lo.startsWith("hsl(") || lo.startsWith("hsla(") || lo.startsWith("oklch(")
                || lo.startsWith("oklab(") || lo.startsWith("lch(") || lo.startsWith("lab(")
                || lo.startsWith("color(") || lo.startsWith("color-mix(") || lo.startsWith("var(")
                || lo.startsWith("light-dark(") || lo.startsWith("hwb(")) return true;
        return KnownElements.CSS_NAMED_COLORS.contains(lo);
    }

    public static List<Problem> analyze(File file, String text) {
        if (text == null || text.trim().isEmpty()) return new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        TokenMask mask = TokenMask.build(text, "css");

        Deque<Integer> braceStack = new ArrayDeque<>();
        StringBuilder selectorBuf = new StringBuilder();
        int selectorLine = 1;
        int selectorCol = 1;
        boolean pastFirstRule = false;

        // per-block tracking
        Map<String, Integer> propsInBlock = new LinkedHashMap<>(); // prop -> line first seen
        Map<String, Integer> propLineInBlock = new LinkedHashMap<>();
        Set<String> vendorPropsInBlock = new HashSet<>();
        boolean blockHasColor = false;
        boolean blockHasBgColor = false;
        boolean blockHasFlex = false;
        boolean blockHasGrid = false;
        boolean blockHasGap = false;
        boolean blockHasMarginLeftAuto = false;
        boolean blockHasMarginRightAuto = false;

        StringBuilder declBuf = new StringBuilder();
        int declLine = 1;
        int declCol = 1;

        // Track CSS variable declarations and usages for CSS-I001
        Set<String> declaredVars = new HashSet<>();
        Set<String> usedVars = new HashSet<>();

        int i = 0;
        int len = text.length();

        while (i < len) {
            if (mask.inComment[i]) {
                i++;
                continue;
            }
            char c = text.charAt(i);

            if (c == '{') {
                int line = LinterUtils.getLine(text, i);
                braceStack.push(line);

                String selector = selectorBuf.toString().trim();
                // selectorLine/selectorCol already point to selector start (tracked below)

                // CSS-W010: @media breakpoint in px
                Matcher mediaMatcher = PAT_MEDIA_PX.matcher(selector);
                if (mediaMatcher.find()) {
                    problems.add(new Problem(file, selectorLine, selectorCol, selector.length(),
                            "'@media' breakpoint in 'px': consider 'em' or 'rem' for accessibility scaling",
                            Problem.Severity.WARNING));
                }

                if (selector.contains("#")) {
                    problems.add(new Problem(file, selectorLine, selectorCol, selector.length(),
                            "Avoid using ID selectors ('#id') for styling: prefer class selectors",
                            Problem.Severity.INFO));
                }

                // CSS-W011: overly specific selector
                if (selectorSpecificityTooHigh(selector)) {
                    problems.add(new Problem(file, selectorLine, selectorCol, selector.length(),
                            "Overly specific selector '" + selector + "': hard to override, prefer simpler class selectors",
                            Problem.Severity.WARNING));
                }

                // reset block state
                propsInBlock.clear();
                propLineInBlock.clear();
                vendorPropsInBlock.clear();
                blockHasColor = false;
                blockHasBgColor = false;
                blockHasFlex = false;
                blockHasGrid = false;
                blockHasGap = false;
                blockHasMarginLeftAuto = false;
                blockHasMarginRightAuto = false;
                declBuf.setLength(0);
                // Reset so first declaration in this block gets its own line/col
                declLine = LinterUtils.getLine(text, i + 1);
                declCol = 1;

                if (selector.startsWith("@import")) {
                    // handled below
                } else if (!selector.startsWith("@") && !selector.isEmpty()) {
                    pastFirstRule = true;
                }
                selectorBuf.setLength(0);
                selectorLine = 1;
                selectorCol = 1;
                i++;
                continue;
            }

            if (c == '}') {
                int line = LinterUtils.getLine(text, i);
                if (braceStack.isEmpty()) {
                    problems.add(new Problem(file, line, LinterUtils.getColumn(text, i), 1,
                            "Unexpected '}' with no matching '{'",
                            Problem.Severity.ERROR));
                } else {
                    braceStack.pop();

                    // process last declaration if no trailing semicolon
                    String lastDecl = declBuf.toString().trim();
                    if (!lastDecl.isEmpty()) {
                        processDeclaration(file, lastDecl, declLine, declCol, propsInBlock, propLineInBlock,
                                vendorPropsInBlock, problems);
                        updateBlockFlags(
                        );
                    }
                    declBuf.setLength(0);

                    // Empty block check
                    String selector = selectorBuf.toString().trim();
                    if (propsInBlock.isEmpty() && !selector.isEmpty()) {
                        // look back to find selector
                    }

                    // CSS-W002: vendor prefix check
                    for (Map.Entry<String, String> vpe : KnownElements.VENDOR_PREFIX_NEEDED.entrySet()) {
                        String prop = vpe.getKey();
                        String vendor = vpe.getValue();
                        if (propsInBlock.containsKey(prop) && !vendorPropsInBlock.contains(vendor)) {
                            int pline = propsInBlock.get(prop);
                            problems.add(new Problem(file, pline, 1, prop.length(),
                                    "'" + prop + "' may need '" + vendor + "' for broader browser support",
                                    Problem.Severity.WARNING));
                        }
                    }

                    // CSS-W005: color without background-color
                    if (blockHasColor && !blockHasBgColor) {
                        problems.add(new Problem(file, line, 1, 5,
                                "'color' is set without 'background-color': may cause readability issues on some themes",
                                Problem.Severity.WARNING));
                    }
                    if (blockHasBgColor && !blockHasColor) {
                        problems.add(new Problem(file, line, 1, 16,
                                "'background-color' is set without 'color': may cause readability issues on some themes",
                                Problem.Severity.WARNING));
                    }

                    // CSS-I004: flex/grid without gap
                    if ((blockHasFlex || blockHasGrid) && !blockHasGap) {
                        problems.add(new Problem(file, line, 1, 7,
                                "No 'gap' property in flex/grid rule: consider 'gap' instead of margin-based spacing",
                                Problem.Severity.INFO));
                    }

                    // CSS-I003: margin-left/right auto without shorthand
                    if (blockHasMarginLeftAuto && blockHasMarginRightAuto) {
                        problems.add(new Problem(file, line, 1, 6,
                                "Consider 'margin: 0 auto' or flexbox centering instead of separate margin declarations",
                                Problem.Severity.INFO));
                    }
                }
                selectorBuf.setLength(0);
                selectorLine = 1;
                selectorCol = 1;
                i++;
                continue;
            }

            if (c == ';' && !braceStack.isEmpty()) {
                String declText = declBuf.toString().trim();
                if (!declText.isEmpty()) {
                    processDeclaration(file, declText, declLine, declCol, propsInBlock, propLineInBlock,
                            vendorPropsInBlock, problems);
                    // update block flags
                    String propPart = declText.contains(":") ? declText.substring(0, declText.indexOf(':')).trim() : "";
                    String valPart = declText.contains(":") ? declText.substring(declText.indexOf(':') + 1).trim() : "";
                    if ("color".equals(propPart)) blockHasColor = true;
                    if ("background-color".equals(propPart)) blockHasBgColor = true;
                    if ("display".equals(propPart) && "flex".equals(valPart)) blockHasFlex = true;
                    if ("display".equals(propPart) && "grid".equals(valPart)) blockHasGrid = true;
                    if ("gap".equals(propPart) || "row-gap".equals(propPart) || "column-gap".equals(propPart))
                        blockHasGap = true;
                    if ("margin-left".equals(propPart)) {
                        if ("auto".equals(valPart)) blockHasMarginLeftAuto = true;
                    }
                    if ("margin-right".equals(propPart)) {
                        if ("auto".equals(valPart)) blockHasMarginRightAuto = true;
                    }

                    // CSS variable tracking
                    Matcher vm = PAT_CSS_VAR_DECL.matcher(declText);
                    while (vm.find()) declaredVars.add(vm.group(1));
                    Matcher vu = PAT_CSS_VAR_USE.matcher(declText);
                    while (vu.find()) usedVars.add(vu.group(1));
                }
                declBuf.setLength(0);
                declLine = LinterUtils.getLine(text, i + 1);
                declCol = LinterUtils.getColumn(text, i + 1);
                i++;
                continue;
            }

            if (!braceStack.isEmpty()) {
                // inside block: skip leading whitespace so declLine/declCol capture the property position
                if (declBuf.length() == 0 && Character.isWhitespace(c)) {
                    i++;
                    continue;
                }
                if (declBuf.length() == 0) {
                    declLine = LinterUtils.getLine(text, i);
                    declCol = LinterUtils.getColumn(text, i);
                }
                declBuf.append(c);
            } else {
                // outside block: accumulate selector / @rule
                if (selectorBuf.length() == 0 && !Character.isWhitespace(c)) {
                    selectorLine = LinterUtils.getLine(text, i);
                    selectorCol = LinterUtils.getColumn(text, i);
                }
                selectorBuf.append(c);
                // CSS-E005: @import after first rule
                if (c == '@' && i + 6 < len && text.startsWith("@import", i)) {
                    if (pastFirstRule) {
                        int line = LinterUtils.getLine(text, i);
                        problems.add(new Problem(file, line, LinterUtils.getColumn(text, i), 7,
                                "'@import' must appear before all other rules",
                                Problem.Severity.ERROR));
                    }
                }
            }
            i++;
        }

        // CSS-E001: unclosed block
        for (int openLine : braceStack) {
            problems.add(new Problem(file, openLine, 1, 1,
                    "Unclosed CSS block '{' opened on line " + openLine,
                    Problem.Severity.ERROR));
        }

        // CSS-I001: declared vars never used
        for (String varName : declaredVars) {
            if (!usedVars.contains(varName)) {
                problems.add(new Problem(file, 1, 1, varName.length(),
                        "CSS variable '" + varName + "' is declared but never used in this file",
                        Problem.Severity.INFO));
            }
        }

        // CSS-W004: empty selectors
        checkEmptySelectors(file, text, mask, problems);

        // CSS-I002: shorthand hex colors
        checkShortenableHex(file, text, mask, problems);

        // CSS-W010: @media px (from selector scan)
        // already handled in block scanner above

        return problems;
    }

    private static void processDeclaration(File file, String decl, int declLine, int declCol,
                                           Map<String, Integer> propsInBlock, Map<String, Integer> propLineInBlock,
                                           Set<String> vendorPropsInBlock, List<Problem> problems) {
        if (decl.isEmpty()) return;

        // CSS-E003: missing colon
        if (!decl.contains(":")) {
            problems.add(new Problem(file, declLine, declCol, decl.length(),
                    "Missing ':' in CSS declaration — property and value must be separated by ':'",
                    Problem.Severity.ERROR));
            return;
        }

        int colonIdx = decl.indexOf(':');
        String prop = decl.substring(0, colonIdx).trim();
        String value = decl.substring(colonIdx + 1).trim();
        if (prop.isEmpty()) return;

        // CSS-E004: unknown property
        if (!prop.startsWith("--") && !prop.startsWith("-webkit-") && !prop.startsWith("-moz-")
                && !prop.startsWith("-ms-") && !prop.startsWith("-o-")) {
            if (!KnownElements.VALID_CSS_PROPERTIES.contains(prop)) {
                if ("clip".equals(prop)) {
                    problems.add(new Problem(file, declLine, declCol, prop.length(),
                            "'clip' is deprecated — use 'clip-path' instead",
                            Problem.Severity.WARNING));
                } else if ("zoom".equals(prop)) {
                    problems.add(new Problem(file, declLine, declCol, prop.length(),
                            "'zoom' is deprecated — use 'transform: scale()' instead",
                            Problem.Severity.WARNING));
                } else {
                    problems.add(new Problem(file, declLine, declCol, prop.length(),
                            "Unknown CSS property '" + prop + "': not a standard CSS property",
                            Problem.Severity.ERROR));
                }
            }
        } else if (prop.startsWith("-webkit-") || prop.startsWith("-moz-") || prop.startsWith("-ms-") || prop.startsWith("-o-")) {
            vendorPropsInBlock.add(prop);
        }

        if (prop.startsWith("-")) vendorPropsInBlock.add(prop);

        // CSS-W001: duplicate property
        if (propsInBlock.containsKey(prop)) {
            int firstLine = propsInBlock.get(prop);
            problems.add(new Problem(file, declLine, declCol, prop.length(),
                    "Duplicate property '" + prop + "' in same rule (also on line " + firstLine + ")",
                    Problem.Severity.WARNING));
        } else {
            propsInBlock.put(prop, declLine);
            propLineInBlock.put(prop, declLine);
        }

        // CSS-W003: !important
        if (value.contains("!important")) {
            problems.add(new Problem(file, declLine, declCol, prop.length(),
                    "'!important' on '" + prop + "': overrides cascade, makes maintenance difficult",
                    Problem.Severity.WARNING));
        }

        // CSS-W006: 0px
        Matcher zeroMatcher = PAT_ZERO_PX.matcher(value);
        if (zeroMatcher.find()) {
            // column of the value = declCol + colonIdx + 1 (approx)
            int valCol = declCol + colonIdx + 1;
            problems.add(new Problem(file, declLine, valCol, 3,
                    "'0px' — units are unnecessary on zero values, use '0'",
                    Problem.Severity.WARNING));
        }

        if (value.contains("pt")) {
            problems.add(new Problem(file, declLine, declCol, prop.length(),
                    "Avoid using 'pt' units for screen layouts: prefer 'px', 'em', or 'rem'",
                    Problem.Severity.WARNING));
        }

        // CSS-W007: var() without fallback
        Matcher varMatcher = PAT_VAR_NO_FB.matcher(value);
        if (varMatcher.find()) {
            String varName = varMatcher.group(1);
            int valCol = declCol + colonIdx + 1 + varMatcher.start();
            problems.add(new Problem(file, declLine, valCol, varMatcher.group().length(),
                    "CSS variable 'var(" + varName + ")' used without a fallback value",
                    Problem.Severity.WARNING));
        }

        // CSS-E006: invalid color value
        if (COLOR_PROPS.contains(prop)) {
            String v = value.replace("!important", "").trim();
            if (!v.isEmpty() && !isValidColor(v)) {
                int valCol = declCol + colonIdx + 1;
                problems.add(new Problem(file, declLine, valCol, v.length(),
                        "Invalid color value '" + v + "' for '" + prop + "'",
                        Problem.Severity.ERROR));
            }
        }

        // CSS-W008/W009: shorthand/longhand ordering
        for (Map.Entry<String, java.util.Set<String>> entry : KnownElements.CSS_SHORTHAND_LONGHANDS.entrySet()) {
            String shorthand = entry.getKey();
            Set<String> longhands = entry.getValue();
            if (shorthand.equals(prop)) {
                // shorthand appears — check if any longhands were set before
                for (String lh : longhands) {
                    if (propLineInBlock.containsKey(lh)) {
                        int lhLine = propLineInBlock.get(lh);
                        problems.add(new Problem(file, declLine, declCol, prop.length(),
                                "'" + shorthand + "' overrides previously set '" + lh + "' on line " + lhLine,
                                Problem.Severity.WARNING));
                    }
                }
            } else if (longhands.contains(prop) && propLineInBlock.containsKey(shorthand)) {
                int shLine = propLineInBlock.get(shorthand);
                problems.add(new Problem(file, declLine, declCol, prop.length(),
                        "'" + prop + "' is overridden by shorthand '" + shorthand + "' on line " + shLine,
                        Problem.Severity.WARNING));
            }
        }
    }

    private static void updateBlockFlags() {
        // This is a no-op helper kept for symmetry; actual updates happen inline in ';' case.
    }

    private static boolean selectorSpecificityTooHigh(String selector) {
        if (selector.isEmpty() || selector.startsWith("@")) return false;
        int depth = 0;
        for (String part : selector.split("\\s+")) {
            if (!part.isEmpty()) depth++;
        }
        return depth > 3;
    }

    private static void checkEmptySelectors(File file, String text, TokenMask mask, List<Problem> problems) {
        int i = 0;
        int len = text.length();
        while (i < len) {
            if (mask.inComment[i]) {
                i++;
                continue;
            }
            if (text.charAt(i) == '{') {
                // find selector before
                int bracePos = i;
                int k = bracePos - 1;
                while (k >= 0 && text.charAt(k) != '}' && text.charAt(k) != '{') k--;
                String selector = text.substring(k + 1, bracePos).trim();
                // find closing
                int j = bracePos + 1;
                while (j < len && text.charAt(j) != '}') {
                    if (!Character.isWhitespace(text.charAt(j))) {
                        i++;
                        break;
                    }
                    j++;
                }
                if (j < len && text.charAt(j) == '}') {
                    // check all whitespace
                    boolean empty = true;
                    for (int x = bracePos + 1; x < j; x++) {
                        if (!Character.isWhitespace(text.charAt(x))) {
                            empty = false;
                            break;
                        }
                    }
                    if (empty && !selector.isEmpty() && !selector.startsWith("@")) {
                        int line = LinterUtils.getLine(text, bracePos);
                        problems.add(new Problem(file, line, 1, selector.length(),
                                "Empty rule for selector '" + selector + "': remove or add declarations",
                                Problem.Severity.WARNING));
                    }
                }
            }
            i++;
        }
    }

    private static void checkShortenableHex(File file, String text, TokenMask mask, List<Problem> problems) {
        Matcher m = PAT_HEX_SHORT.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            String hex = m.group(1);
            assert hex != null;
            char r1 = hex.charAt(0), r2 = hex.charAt(1);
            char g1 = hex.charAt(2), g2 = hex.charAt(3);
            char b1 = hex.charAt(4), b2 = hex.charAt(5);
            if (r1 == r2 && g1 == g2 && b1 == b2) {
                String full = "#" + hex;
                String short_ = "#" + r1 + g1 + b1;
                int line = LinterUtils.getLine(text, m.start());
                int col = LinterUtils.getColumn(text, m.start());
                problems.add(new Problem(file, line, col, full.length(),
                        "Color '" + full + "' can be shortened to '" + short_ + "'",
                        Problem.Severity.INFO));
            }
        }
    }
}

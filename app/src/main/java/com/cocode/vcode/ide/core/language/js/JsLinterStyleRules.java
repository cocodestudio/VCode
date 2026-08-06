package com.cocode.vcode.ide.core.language.js;

import androidx.annotation.NonNull;

import com.cocode.vcode.ide.core.diagnostic.util.KnownElements;
import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.diagnostic.util.TokenMask;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsLinterStyleRules {
    public static final Pattern PAT_TYPEOF_CMP = Pattern.compile("\\btypeof\\b[^=]*===?\\s*(undefined|null|NaN|true|false|\\d+)");
    public static final Pattern PAT_TODO = Pattern.compile("(?://|/\\*).*?(TODO|FIXME)([^\n]*)");
    public static final Pattern PAT_ARROW_SIMP = Pattern.compile("function\\s*(\\w*)\\s*\\([^)]*\\)\\s*\\{\\s*return\\s+[^;{]+;\\s*\\}");
    public static final Pattern PAT_STR_CONCAT = Pattern.compile("\"[^\"]*\"\\s*\\+\\s*\\w+|\\w+\\s*\\+\\s*\"[^\"]*\"|'[^']*'\\s*\\+\\s*\\w+|\\w+\\s*\\+\\s*'[^']*'");
    public static final Pattern PAT_OPT_CHAIN = Pattern.compile("(\\w+)\\s*&&\\s*\\1\\.(\\w+)");
    public static final Pattern PAT_NULLISH = Pattern.compile("\\|\\|\\s*(null|undefined|''|\"\"|0)\\b");
    public static final Pattern PAT_DIV_ZERO = Pattern.compile("([^/])\\s*/\\s*0\\b");
    public static final Pattern PAT_FUNC_DECL = Pattern.compile("\\bfunction\\s+(\\w+)\\s*\\(([^)]*)\\)");
    public static final Pattern PAT_ARROW_DECL = Pattern.compile("(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s*)?(?:\\(([^)]*)\\)|\\w+)\\s*=>");
    public static final Pattern PAT_LET_CONST = Pattern.compile("\\b(let|const)\\s+([a-zA-Z_$][\\w$]*)");
    public static final Pattern PAT_CONST_INIT = Pattern.compile("\\bconst\\s+([a-zA-Z_$][\\w$]*)\\s*(?!\\s*=)(?=[;,\\n])");
    public static final Pattern PAT_STR_CONCAT_LOOP = Pattern.compile("\\w+\\s*\\+=\\s*['\"`]|['\"`][^'\"`]*['\"`]\\s*\\+");

    public static void checkTypeofComparison(File file, String text,
                                             TokenMask mask, List<Problem> out) {
        Matcher m = PAT_TYPEOF_CMP.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, m.group().length(),
                    "'typeof' always returns a string: compare to '\"undefined\"' not 'undefined'",
                    Problem.Severity.WARNING));
        }
    }

    public static void checkTodoFixme(File file, String text, List<Problem> out) {
        Matcher m = PAT_TODO.matcher(text);
        while (m.find()) {
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            String txt = (m.group(2) != null ? Objects.requireNonNull(m.group(2)).trim() : "");
            out.add(new Problem(file, line, col, Objects.requireNonNull(m.group(1)).length(),
                    "TODO/FIXME found: '" + m.group(1) + (txt.isEmpty() ? "" : " " + txt) + "'",
                    Problem.Severity.INFO));
        }
    }

    public static void checkFunctionParams(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_FUNC_DECL.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            String params = Objects.requireNonNull(m.group(2)).trim();
            if (params.isEmpty()) continue;
            int count = params.split(",").length;
            if (count > 4) {
                int nameStart = m.start(1);
                int line = LinterUtils.getLine(text, nameStart);
                int col = LinterUtils.getColumn(text, nameStart);
                out.add(new Problem(file, line, col, Objects.requireNonNull(m.group(1)).length(),
                        "Function '" + m.group(1) + "' has " + count + " parameters: consider a config object for readability",
                        Problem.Severity.WARNING));
            }
        }
        Matcher m2 = PAT_ARROW_DECL.matcher(text);
        while (m2.find()) {
            if (mask.isMasked(m2.start())) continue;
            String params = m2.group(2) != null ? Objects.requireNonNull(m2.group(2)).trim() : "";
            if (params.isEmpty()) continue;
            int count = params.split(",").length;
            if (count > 4) {
                int line = LinterUtils.getLine(text, m2.start());
                int col = LinterUtils.getColumn(text, m2.start());
                out.add(new Problem(file, line, col, Objects.requireNonNull(m2.group(1)).length(),
                        "Function '" + m2.group(1) + "' has " + count + " parameters: consider a config object for readability",
                        Problem.Severity.WARNING));
            }
        }
    }

    public static void checkDivisionByZero(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_DIV_ZERO.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, m.group().trim().length(),
                    "Division by zero: '" + m.group().trim() + "'",
                    Problem.Severity.ERROR));
        }
    }

    public static void checkUnreachableCode(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Pattern terminators = Pattern.compile("\\b(return|throw|break|continue)\\b[^;{\\n]*(;|$)");
        Matcher m = terminators.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int termLine = LinterUtils.getLine(text, m.start());
            // check if next non-empty line is code (not closing brace or comment)
            if (termLine < lines.length) {
                for (int next = termLine; next < lines.length && next < termLine + 3; next++) {
                    String nextLine = lines[next].trim();
                    if (nextLine.isEmpty() || nextLine.startsWith("//") || nextLine.startsWith("*"))
                        continue;
                    if (nextLine.startsWith("}") || nextLine.startsWith(")") || nextLine.startsWith("]"))
                        break;
                    // there's code after a terminator
                    out.add(new Problem(file, next + 1, 1, nextLine.length(),
                            "Unreachable code after '" + m.group(1) + "' on line " + termLine,
                            Problem.Severity.WARNING));
                    break;
                }
            }
        }
    }

    public static void checkConstReassign(File file, String text, TokenMask mask, List<Problem> out) {
        // JS-E004: const with no initializer
        Matcher m = PAT_CONST_INIT.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int nameStart = m.start(1);
            int line = LinterUtils.getLine(text, nameStart);
            int col = LinterUtils.getColumn(text, nameStart);
            out.add(new Problem(file, line, col, Objects.requireNonNull(m.group(1)).length(),
                    "'const " + m.group(1) + "' must be initialized at declaration",
                    Problem.Severity.ERROR));
        }
        // JS-E005: const reassignment — collect all const names with their declaration lines
        Map<String, Integer> constDecls = new LinkedHashMap<>();
        Matcher decl = Pattern.compile("\\bconst\\s+([a-zA-Z_$][\\w$]*)\\s*=").matcher(text);
        while (decl.find()) {
            if (!mask.isMasked(decl.start()))
                constDecls.put(decl.group(1), LinterUtils.getLine(text, decl.start()));
        }
        for (Map.Entry<String, Integer> e : constDecls.entrySet()) {
            String name = e.getKey();
            int declLine = e.getValue();
            // find reassignment: name = (not ==, !=, <=, >=, =>, +=, -=, *=, /=)
            Pattern reassign = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*(?<![=!<>+\\-*/])=(?![=>])");
            Matcher rm = reassign.matcher(text);
            while (rm.find()) {
                if (mask.isMasked(rm.start())) continue;
                int rLine = LinterUtils.getLine(text, rm.start());
                if (rLine == declLine) continue; // skip declaration itself
                int col = LinterUtils.getColumn(text, rm.start());
                out.add(new Problem(file, rLine, col, name.length(),
                        "Cannot reassign 'const' variable '" + name + "' declared on line " + declLine,
                        Problem.Severity.ERROR));
            }
        }
    }


    public static void checkUnclosedString(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineOff = LinterUtils.lineStartOffset(text, i + 1);
            char openQuote = 0;
            int openPos = -1;
            for (int j = 0; j < line.length(); j++) {
                int absOff = lineOff + j;
                char c = line.charAt(j);
                if (openQuote == 0) {
                    if ((c == '\'' || c == '"') && !mask.inComment[absOff]) {
                        openQuote = c;
                        openPos = j;
                    }
                } else {
                    if (c == '\\') {
                        j++;
                        continue;
                    } // skip escaped char
                    if (c == openQuote) {
                        openQuote = 0;
                        openPos = -1;
                    }
                }
            }
            if (openQuote != 0 && !mask.inComment[lineOff]) {
                out.add(new Problem(file, i + 1, openPos + 1, 1,
                        "Unclosed string literal: string opened with '" + openQuote + "' is not closed on this line",
                        Problem.Severity.ERROR));
            }
        }
    }

    public static void checkReturnOutsideFunction(File file, String text, TokenMask mask, List<Problem> out) {
        // Track function depth via { }; return at depth 0 is outside any function
        int fnDepth = 0;
        int i = 0;
        while (i < text.length()) {
            if (mask.isMasked(i)) {
                i++;
                continue;
            }
            char c = text.charAt(i);
            if (c == '{') {
                fnDepth++;
                i++;
                continue;
            }
            if (c == '}') {
                if (fnDepth > 0) fnDepth--;
                i++;
                continue;
            }
            // check for 'function' keyword or arrow
            if (c == 'f' && text.startsWith("function", i)) {
                i += 8;
                continue;
            }
            if (c == 'r' && text.startsWith("return", i)) {
                if (fnDepth == 0) {
                    // ensure it's a word boundary
                    boolean before = i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1));
                    boolean after = i + 6 >= text.length() || !Character.isLetterOrDigit(text.charAt(i + 6));
                    if (before && after) {
                        int line = LinterUtils.getLine(text, i);
                        int col = LinterUtils.getColumn(text, i);
                        out.add(new Problem(file, line, col, 6,
                                "'return' outside of a function body", Problem.Severity.ERROR));
                    }
                }
                i += 6;
                continue;
            }
            i++;
        }
    }

    public static void checkBreakContinue(File file, String text, TokenMask mask, List<Problem> out) {
        int loopDepth = 0;
        int i = 0;
        while (i < text.length()) {
            if (mask.isMasked(i)) {
                i++;
                continue;
            }
            char c = text.charAt(i);
            // detect loop/switch start keywords
            if (c == 'f' && text.startsWith("for", i) && (i + 3 >= text.length() || !Character.isLetterOrDigit(text.charAt(i + 3)))) {
                loopDepth++;
                i += 3;
                continue;
            }
            if (c == 'w' && text.startsWith("while", i) && (i + 5 >= text.length() || !Character.isLetterOrDigit(text.charAt(i + 5)))) {
                loopDepth++;
                i += 5;
                continue;
            }
            if (c == 'd' && text.startsWith("do", i) && (i + 2 >= text.length() || !Character.isLetterOrDigit(text.charAt(i + 2)))) {
                loopDepth++;
                i += 2;
                continue;
            }
            if (c == 's' && text.startsWith("switch", i) && (i + 6 >= text.length() || !Character.isLetterOrDigit(text.charAt(i + 6)))) {
                loopDepth++;
                i += 6;
                continue;
            }
            if (c == '{') {
                i++;
                continue;
            }
            if (c == '}') {
                if (loopDepth > 0) loopDepth--;
                i++;
                continue;
            }
            if ((c == 'b' && text.startsWith("break", i)) || (c == 'c' && text.startsWith("continue", i))) {
                int kwLen = c == 'b' ? 5 : 8;
                boolean before = i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1));
                boolean after = i + kwLen >= text.length() || !Character.isLetterOrDigit(text.charAt(i + kwLen));
                if (before && after && loopDepth == 0) {
                    String kw = c == 'b' ? "break" : "continue";
                    int line = LinterUtils.getLine(text, i);
                    int col = LinterUtils.getColumn(text, i);
                    out.add(new Problem(file, line, col, kwLen,
                            "'" + kw + "' used outside of a loop or switch statement",
                            Problem.Severity.ERROR));
                }
                i += kwLen;
                continue;
            }
            i++;
        }
    }

    public static void checkUnusedVars(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_LET_CONST.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            String name = m.group(2);
            if (KnownElements.JS_GLOBALS.contains(name)) continue;
            int declEnd = m.end();
            // count usages after declaration
            assert name != null;
            Pattern use = Pattern.compile("\\b" + Pattern.quote(name) + "\\b");
            Matcher um = use.matcher(text);
            int usages = 0;
            while (um.find()) {
                if (mask.isMasked(um.start())) continue;
                if (um.start() >= declEnd) usages++;
            }
            if (usages == 0) {
                int nameStart = m.start(2);
                int line = LinterUtils.getLine(text, nameStart);
                int col = LinterUtils.getColumn(text, nameStart);
                out.add(new Problem(file, line, col, name.length(),
                        "Variable '" + name + "' is declared but never used",
                        Problem.Severity.WARNING));
            }
        }
    }

    public static void checkArrowSimplification(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_ARROW_SIMP.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, 8,
                    "Arrow function simplification: '() => expr' instead of 'function() { return expr; }'",
                    Problem.Severity.INFO));
        }
    }

    public static void checkStringConcat(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_STR_CONCAT.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, m.group().length(),
                    "Prefer template literal over string concatenation: use backticks",
                    Problem.Severity.INFO));
        }
    }

    public static void checkOptionalChaining(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_OPT_CHAIN.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, m.group().length(),
                    "Consider optional chaining: '" + m.group(1) + " && " + m.group(1) + "." + m.group(2) + "' → '" + m.group(1) + "?." + m.group(2) + "'",
                    Problem.Severity.INFO));
        }
    }

    public static void checkNullishCoalescing(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_NULLISH.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, m.group().length(),
                    "Consider nullish coalescing '??' if the left side being 0 or '' should not use the default",
                    Problem.Severity.INFO));
        }
    }

    public static void checkStringConcatInLoop(File file, String text, TokenMask mask, List<Problem> out) {
        // Find for/while loops and check body for string concatenation
        Pattern loopPat = Pattern.compile("\\b(for|while)\\s*\\(");
        Matcher m = loopPat.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int braceStart = text.indexOf('{', m.end());
            if (braceStart < 0) continue;
            Matcher cm = getMatcher(text, braceStart);
            while (cm.find()) {
                int absOff = braceStart + cm.start();
                if (mask.isMasked(absOff)) continue;
                int line = LinterUtils.getLine(text, absOff);
                int col = LinterUtils.getColumn(text, absOff);
                out.add(new Problem(file, line, col, cm.group().length(),
                        "String concatenation in loop: use array.push() + join() or template literals",
                        Problem.Severity.WARNING));
            }
        }
    }

    @NonNull
    private static Matcher getMatcher(String text, int braceStart) {
        int depth = 0, end = braceStart;
        for (int i = braceStart; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }
        String body = text.substring(braceStart, end);
        return PAT_STR_CONCAT_LOOP.matcher(body);
    }
}

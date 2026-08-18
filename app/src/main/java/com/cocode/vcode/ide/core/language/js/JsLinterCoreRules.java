package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.diagnostic.util.TokenMask;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsLinterCoreRules {
    public static final Pattern PAT_VAR = Pattern.compile("\\bvar\\s+([a-zA-Z_$][\\w$]*)");
    public static final Pattern PAT_CONSOLE = Pattern.compile("\\bconsole\\.(\\w+)");
    public static final Pattern PAT_DEBUGGER = Pattern.compile("\\bdebugger\\s*;?");
    public static final Pattern PAT_LOOSE_EQ = Pattern.compile("(?<![=!<>])==(?!=)");
    public static final Pattern PAT_LOOSE_NEQ = Pattern.compile("!=(?!=)");
    public static final Pattern PAT_EVAL = Pattern.compile("\\beval\\s*\\(");
    public static final Pattern PAT_WITH = Pattern.compile("\\bwith\\s*\\(");
    public static final Pattern PAT_NAN_CMP = Pattern.compile("===\\s*NaN|NaN\\s*===|==\\s*NaN|NaN\\s*==");
    public static final Pattern PAT_EMPTY_CATCH = Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}");
    public static final Pattern PAT_INF_LOOP = Pattern.compile("\\bwhile\\s*\\(\\s*true\\s*\\)|\\bfor\\s*\\(\\s*;\\s*;\\s*\\)");
    public static final Pattern PAT_SWITCH = Pattern.compile("\\bswitch\\s*\\(");
    public static final Pattern PAT_THEN = Pattern.compile("\\.then\\s*\\(");
    public static final Pattern PAT_ASYNC_FN = Pattern.compile("\\basync\\s+function\\s*(\\w*)|\\basync\\s*\\(|\\basync\\s+([a-zA-Z_$][\\w$]*)\\s*=>");
    public static final Pattern PAT_SET_TIMEOUT_EVAL = Pattern.compile("\\b(setTimeout|setInterval)\\s*\\(\\s*['\"`]");
    public static final Pattern PAT_NEW_OBJ_ARR = Pattern.compile("\\bnew\\s+(Object|Array)\\s*\\(");

    public static void checkVarUsage(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_VAR.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, 3,
                    "'var' is function-scoped: prefer 'const' for values that don't change, or 'let'",
                    Problem.Severity.WARNING));
        }
    }

    public static void checkConsole(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_CONSOLE.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, m.group().length(),
                    "Debug statement 'console." + m.group(1) + "(...)' left in code: remove before production",
                    Problem.Severity.WARNING));
        }
    }

    public static void checkDebugger(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_DEBUGGER.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, 8,
                    "'debugger' statement must be removed before production",
                    Problem.Severity.WARNING));
        }
    }

    public static void checkLooseEquality(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_LOOSE_EQ.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, 2,
                    "Loose equality '==': use '===' to avoid type coercion bugs",
                    Problem.Severity.WARNING));
        }
        Matcher m2 = PAT_LOOSE_NEQ.matcher(text);
        while (m2.find()) {
            if (mask.isMasked(m2.start())) continue;
            // ensure it's not !==
            if (m2.start() + 2 < text.length() && text.charAt(m2.start() + 2) == '=') continue;
            int line = LinterUtils.getLine(text, m2.start());
            int col = LinterUtils.getColumn(text, m2.start());
            out.add(new Problem(file, line, col, 2,
                    "Loose inequality '!=': use '!==' to avoid type coercion bugs",
                    Problem.Severity.WARNING));
        }
    }

    public static void checkEval(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_EVAL.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, 4,
                    "'eval' is harmful: it creates performance and security risks",
                    Problem.Severity.ERROR));
        }
    }

    public static void checkSetTimeoutString(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_SET_TIMEOUT_EVAL.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            String func = m.group(1);
            out.add(new Problem(file, line, col, func.length(),
                    "Passing strings to '" + func + "' is similar to 'eval' and poses security risks. Pass a function instead.",
                    Problem.Severity.WARNING));
        }
    }

    public static void checkNewObjectArray(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_NEW_OBJ_ARR.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            String type = m.group(1);
            String fix = type.equals("Object") ? "{}" : "[]";
            out.add(new Problem(file, line, col, m.group().length(),
                    "Avoid 'new " + type + "()'. Use literal '" + fix + "' instead.",
                    Problem.Severity.WARNING));
        }
    }

    public static void checkWith(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_WITH.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, 4,
                    "'with' statement is deprecated and disallowed in strict mode",
                    Problem.Severity.WARNING));
        }
    }

    public static void checkNaNComparison(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_NAN_CMP.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, m.group().length(),
                    "Direct comparison to 'NaN' is always false: use 'Number.isNaN()' or 'isNaN()'",
                    Problem.Severity.WARNING));
        }
    }

    public static void checkEmptyCatch(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_EMPTY_CATCH.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, 5,
                    "Empty 'catch' block: errors are silently swallowed — add handling or logging",
                    Problem.Severity.WARNING));
        }
    }

    public static void checkInfiniteLoop(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_INF_LOOP.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            // find closing brace of this loop body and check for break/return
            int braceStart = text.indexOf('{', m.end());
            if (braceStart < 0) continue;
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
            if (!body.contains("break") && !body.contains("return")) {
                int line = LinterUtils.getLine(text, m.start());
                int col = LinterUtils.getColumn(text, m.start());
                out.add(new Problem(file, line, col, m.group().length(),
                        "Potential infinite loop: 'while(true)' has no visible 'break' or 'return'",
                        Problem.Severity.WARNING));
            }
        }
    }

    public static void checkSwitchDefault(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_SWITCH.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int braceStart = text.indexOf('{', m.end());
            if (braceStart < 0) continue;
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
            if (!body.contains("default:") && !body.contains("default :")) {
                int line = LinterUtils.getLine(text, m.start());
                int col = LinterUtils.getColumn(text, m.start());
                out.add(new Problem(file, line, col, 6,
                        "'switch' is missing a 'default' case: unhandled values will silently pass through",
                        Problem.Severity.WARNING));
            }
        }
    }


    public static void checkPromiseChain(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_THEN.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            // look for .catch( within 200 chars after this .then(
            int searchEnd = Math.min(text.length(), m.start() + 300);
            String region = text.substring(m.start(), searchEnd);
            if (!region.contains(".catch(") && !region.contains(".catch (")) {
                // also check for try block before
                int searchStart = Math.max(0, m.start() - 200);
                String before = text.substring(searchStart, m.start());
                if (!before.contains("try")) {
                    int line = LinterUtils.getLine(text, m.start());
                    int col = LinterUtils.getColumn(text, m.start());
                    out.add(new Problem(file, line, col, 5,
                            "Promise chain missing '.catch()': unhandled rejections can cause silent failures",
                            Problem.Severity.WARNING));
                }
            }
        }
    }

    public static void checkMissingAwait(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        // Check fetch( without await on same line
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineOff = LinterUtils.lineStartOffset(text, i + 1);
            if (line.contains("fetch(") && !line.contains("await") && !line.contains(".then(")) {
                int idx = line.indexOf("fetch(");
                int absOff = lineOff + idx;
                if (!mask.isMasked(absOff)) {
                    out.add(new Problem(file, i + 1, idx + 1, 5,
                            "Missing 'await' before 'fetch(': result will be an unresolved Promise",
                            Problem.Severity.WARNING));
                }
            }
        }
    }

    public static void checkAsyncNoAwait(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_ASYNC_FN.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            String name = m.group(1) != null ? m.group(1) : (m.group(2) != null ? m.group(2) : "<anonymous>");
            int braceStart = text.indexOf('{', m.end());
            if (braceStart < 0) continue;
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
            if (!body.contains("await ")) {
                int line = LinterUtils.getLine(text, m.start());
                int col = LinterUtils.getColumn(text, m.start());
                out.add(new Problem(file, line, col, m.group().length(),
                        "'async' function '" + name + "' has no 'await': remove 'async' or add awaited calls",
                        Problem.Severity.WARNING));
            }
        }
    }

}

package com.cocode.vcode.ide.core.quickfix;

import com.cocode.vcode.ide.core.diagnostic.ProblemCode;
import com.cocode.vcode.ide.core.lsp.model.CodeAction;
import com.cocode.vcode.ide.core.lsp.model.CodeActionKind;
import com.cocode.vcode.ide.core.lsp.model.Diagnostic;
import com.cocode.vcode.ide.core.lsp.model.DiagnosticSeverity;
import com.cocode.vcode.ide.core.lsp.model.LspPosition;
import com.cocode.vcode.ide.core.lsp.model.LspRange;
import com.cocode.vcode.ide.core.lsp.model.TextEdit;
import com.cocode.vcode.ide.core.lsp.model.WorkspaceEdit;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central registry and generator of quick-fix intentions across all supported languages.
 * <p>
 * Diagnostics in VCode can yield 0, 1, or multiple candidate quick fixes depending on
 * whether the fix is deterministic (e.g. inserting a missing charset meta tag) or provides
 * design alternatives (e.g. defer vs async on scripts, px vs rem on pt units, or comment out vs delete console calls).
 */
public final class QuickFixRegistry {

    private QuickFixRegistry() {}

    /**
     * Resolves all available {@link QuickFix} intentions for the given diagnostic problem.
     * Operates either against the in-memory editor content or directly from the problem metadata.
     */
    public static List<QuickFix> getQuickFixes(Problem problem, String docText) {
        if (problem == null) return Collections.emptyList();

        List<QuickFix> fixes = new ArrayList<>();

        // If the problem already carries custom registered quick fixes, include them first
        List<QuickFix> customFixes = problem.getCustomQuickFixes();
        if (customFixes != null && !customFixes.isEmpty()) {
            fixes.addAll(customFixes);
        }

        ProblemCode code = problem.getProblemCode();
        String codeStr = problem.getCode();
        String message = problem.getMessage() != null ? problem.getMessage() : "";

        // Resolve 0-based coordinate bounds
        int startLine = Math.max(0, problem.getLine() - 1);
        int startCol = Math.max(0, problem.getColumn() - 1);
        int length = Math.max(1, problem.getLength());
        int endLine = startLine;
        int endCol = startCol + length;

        String text = (docText != null) ? docText : "";

        // ---------------------------------------------------------------------
        // HTML Quick Fixes
        // ---------------------------------------------------------------------
        if (isCode(code, codeStr, ProblemCode.HTML_MISSING_ALT) || message.contains("missing 'alt'")) {
            int off = positionToOffset(text, startLine, startCol);
            int tagStart = findTagStart(text, off, "<img");
            if (tagStart == -1) tagStart = off;
            int gt = text.indexOf('>', tagStart);
            if (gt > tagStart) {
                int insOff = (gt > 0 && text.charAt(gt - 1) == '/') ? gt - 1 : gt;
                int[] pos = offsetToPosition(text, insOff);
                fixes.add(new TextReplacementQuickFix("Add alt=\"\" attribute", pos[0], pos[1], pos[0], pos[1], " alt=\"\""));
            }
        } else if (isCode(code, codeStr, ProblemCode.HTML_BUTTON_MISSING_TYPE) || (message.contains("missing 'type'") && message.contains("button"))) {
            int off = positionToOffset(text, startLine, startCol);
            int tagStart = findTagStart(text, off, "<button");
            if (tagStart == -1) tagStart = off;
            int gt = text.indexOf('>', tagStart);
            if (gt > tagStart) {
                int insOff = (gt > 0 && text.charAt(gt - 1) == '/') ? gt - 1 : gt;
                int[] pos = offsetToPosition(text, insOff);
                fixes.add(new TextReplacementQuickFix("Add type=\"button\" attribute", pos[0], pos[1], pos[0], pos[1], " type=\"button\""));
            }
        } else if (isCode(code, codeStr, ProblemCode.HTML_MISSING_CHARSET) || message.contains("charset")) {
            insertHeadElementFix(fixes, text, "Add <meta charset=\"UTF-8\">", "<meta charset=\"UTF-8\">");
        } else if (isCode(code, codeStr, ProblemCode.HTML_MISSING_VIEWPORT) || message.contains("viewport")) {
            insertHeadElementFix(fixes, text, "Add viewport <meta> tag", "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        } else if (isCode(code, codeStr, ProblemCode.HTML_MISSING_TITLE) || message.contains("Missing <title>")) {
            insertHeadElementFix(fixes, text, "Add <title> tag", "<title>Document</title>");
        } else if (isCode(code, codeStr, ProblemCode.HTML_MISSING_META_DESCRIPTION) || message.contains("meta description")) {
            insertHeadElementFix(fixes, text, "Add meta description tag", "<meta name=\"description\" content=\"\">");
        } else if (isCode(code, codeStr, ProblemCode.HTML_MISSING_LANG) || message.contains("missing 'lang'")) {
            int htmlIdx = indexOfIgnoreCase(text, "<html");
            if (htmlIdx >= 0) {
                int insOff = htmlIdx + 5;
                int[] pos = offsetToPosition(text, insOff);
                fixes.add(new TextReplacementQuickFix("Add lang=\"en\" to <html>", pos[0], pos[1], pos[0], pos[1], " lang=\"en\""));
            }
        } else if (isCode(code, codeStr, ProblemCode.HTML_TARGET_BLANK_SECURITY) || message.contains("rel=\"noopener noreferrer\"") || message.contains("target=\"_blank\"")) {
            int off = positionToOffset(text, startLine, startCol);
            int tagStart = findTagStart(text, off, "<a");
            if (tagStart == -1) tagStart = off;
            int gt = text.indexOf('>', tagStart);
            if (gt > tagStart) {
                int insOff = (gt > 0 && text.charAt(gt - 1) == '/') ? gt - 1 : gt;
                int[] pos = offsetToPosition(text, insOff);
                fixes.add(new TextReplacementQuickFix("Add rel=\"noopener noreferrer\"", pos[0], pos[1], pos[0], pos[1], " rel=\"noopener noreferrer\""));
            }
        } else if (isCode(code, codeStr, ProblemCode.HTML_VOID_ELEMENT_CLOSING) || message.contains("self-closing slash")) {
            int off = positionToOffset(text, startLine, startCol);
            int gt = text.indexOf('>', off);
            if (gt > 0 && text.charAt(gt - 1) == '/') {
                int[] pStart = offsetToPosition(text, gt - 1);
                int[] pEnd = offsetToPosition(text, gt);
                fixes.add(new TextReplacementQuickFix("Remove self-closing slash", pStart[0], pStart[1], pEnd[0], pEnd[1], ""));
            }
        } else if (isCode(code, codeStr, ProblemCode.HTML_DEPRECATED_TAG) || message.contains("Deprecated HTML tag")) {
            String tagName = extractTagName(message, text, startLine, startCol);
            if ("center".equalsIgnoreCase(tagName)) {
                addDeprecatedTagFix(fixes, text, startLine, startCol, endLine, endCol, "center", "div");
            } else if ("font".equalsIgnoreCase(tagName)) {
                addDeprecatedTagFix(fixes, text, startLine, startCol, endLine, endCol, "font", "span");
            } else if ("strike".equalsIgnoreCase(tagName)) {
                // Two design options for strike-through: s vs del
                addDeprecatedTagFix(fixes, text, startLine, startCol, endLine, endCol, "strike", "s");
                addDeprecatedTagFix(fixes, text, startLine, startCol, endLine, endCol, "strike", "del");
            } else if ("tt".equalsIgnoreCase(tagName)) {
                addDeprecatedTagFix(fixes, text, startLine, startCol, endLine, endCol, "tt", "code");
            } else if ("big".equalsIgnoreCase(tagName)) {
                addDeprecatedTagFix(fixes, text, startLine, startCol, endLine, endCol, "big", "span");
            }
        } else if (isCode(code, codeStr, ProblemCode.HTML_DEPRECATED_ATTR) || message.contains("Deprecated attribute")) {
            fixes.add(new TextReplacementQuickFix("Remove deprecated attribute", startLine, startCol, endLine, endCol, ""));
        } else if (isCode(code, codeStr, ProblemCode.HTML_DUPLICATE_ATTR) || message.contains("Duplicate attribute")) {
            fixes.add(new TextReplacementQuickFix("Remove duplicate attribute", startLine, startCol, endLine, endCol, ""));
        } else if (isCode(code, codeStr, ProblemCode.HTML_REDUNDANT_TYPE_ATTR) || message.contains("type attribute is unnecessary") || (message.contains("Redundant") && message.contains("type attribute"))) {
            fixes.add(new TextReplacementQuickFix("Remove redundant type attribute", startLine, startCol, endLine, endCol, ""));
        } else if (isCode(code, codeStr, ProblemCode.HTML_SCRIPT_MISSING_DEFER) || message.contains("missing defer") || message.contains("blocking script")) {
            // Two alternatives for script loading
            int off = positionToOffset(text, startLine, startCol);
            int tagStart = findTagStart(text, off, "<script");
            if (tagStart == -1) tagStart = off;
            int gt = text.indexOf('>', tagStart);
            if (gt > tagStart) {
                int insOff = (gt > 0 && text.charAt(gt - 1) == '/') ? gt - 1 : gt;
                int[] pos = offsetToPosition(text, insOff);
                fixes.add(new TextReplacementQuickFix("Add 'defer' attribute to <script>", pos[0], pos[1], pos[0], pos[1], " defer"));
                fixes.add(new TextReplacementQuickFix("Add 'async' attribute to <script>", pos[0], pos[1], pos[0], pos[1], " async"));
            }
        } else if (isCode(code, codeStr, ProblemCode.HTML_ANCHOR_AS_BUTTON) || message.contains("Anchor tag used as button") || message.contains("href=\"#\"")) {
            int off = positionToOffset(text, startLine, startCol);
            int aTagStart = findTagStart(text, off, "<a");
            if (aTagStart == -1) aTagStart = off;

            // Choice 1: Add role="button" right after "<a"
            int insRoleOff = Math.min(text.length(), aTagStart + 2);
            int[] posRole = offsetToPosition(text, insRoleOff);
            fixes.add(new TextReplacementQuickFix("Add role=\"button\" to anchor", posRole[0], posRole[1], posRole[0], posRole[1], " role=\"button\""));

            // Choice 2: Convert <a> to <button type="button">, remove href="#", and convert closing </a>
            int[] openStart = offsetToPosition(text, aTagStart);
            int[] openEnd = offsetToPosition(text, Math.min(text.length(), aTagStart + 2));
            TextReplacementQuickFix btnFix = new TextReplacementQuickFix(
                    "Convert <a> to <button type=\"button\">",
                    openStart[0], openStart[1], openEnd[0], openEnd[1], "<button type=\"button\"");

            int gt = text.indexOf('>', aTagStart);
            if (gt > aTagStart) {
                String tagSlice = text.substring(aTagStart, gt);
                Matcher hrefMatcher = Pattern.compile("(?i)(\\s*href\\s*=\\s*[\"']#[\"'])").matcher(tagSlice);
                if (hrefMatcher.find()) {
                    int hStart = aTagStart + hrefMatcher.start();
                    int hEnd = aTagStart + hrefMatcher.end();
                    int[] phStart = offsetToPosition(text, hStart);
                    int[] phEnd = offsetToPosition(text, hEnd);
                    btnFix.addEdit(phStart[0], phStart[1], phEnd[0], phEnd[1], "");
                }

                int closeA = text.toLowerCase(Locale.ROOT).indexOf("</a>", gt);
                if (closeA >= 0) {
                    int[] pcStart = offsetToPosition(text, closeA);
                    int[] pcEnd = offsetToPosition(text, closeA + 4);
                    btnFix.addEdit(pcStart[0], pcStart[1], pcEnd[0], pcEnd[1], "</button>");
                }
            }
            fixes.add(btnFix);
        } else if (isCode(code, codeStr, ProblemCode.HTML_EMPTY_TAG) || message.contains("Empty element")) {
            fixes.add(new TextReplacementQuickFix("Remove empty tag", startLine, startCol, endLine, endCol, ""));
        } else if (isCode(code, codeStr, ProblemCode.HTML_LAZY_LOADING) || message.contains("loading=\"lazy\"")) {
            int off = positionToOffset(text, startLine, startCol);
            int gt = text.indexOf('>', off);
            if (gt > off) {
                int insOff = (gt > 0 && text.charAt(gt - 1) == '/') ? gt - 1 : gt;
                int[] pos = offsetToPosition(text, insOff);
                fixes.add(new TextReplacementQuickFix("Add loading=\"lazy\" attribute", pos[0], pos[1], pos[0], pos[1], " loading=\"lazy\""));
            }
        } else if (isCode(code, codeStr, ProblemCode.HTML_UNCLOSED_TAG) || message.contains("Unclosed tag")) {
            String tagName = extractTagNameFromMsg(message);
            if (!tagName.isEmpty()) {
                int off = positionToOffset(text, endLine, endCol);
                int lineEnd = text.indexOf('\n', Math.max(0, off));
                if (lineEnd == -1) lineEnd = text.length();
                int[] pos = offsetToPosition(text, lineEnd);
                fixes.add(new TextReplacementQuickFix("Close tag </" + tagName + ">", pos[0], pos[1], pos[0], pos[1], "</" + tagName + ">"));
            }
        }

        // ---------------------------------------------------------------------
        // CSS Quick Fixes
        // ---------------------------------------------------------------------
        else if (isCode(code, codeStr, ProblemCode.CSS_ZERO_UNIT) || message.contains("Unit unnecessary on zero")) {
            int startOff = positionToOffset(text, startLine, startCol);
            int endOff = positionToOffset(text, endLine, endCol);
            String slice = safeSubstring(text, startOff, endOff);
            String replacement = slice.replaceAll("(?i)^0[a-z%]+$", "0");
            fixes.add(new TextReplacementQuickFix("Remove unit from zero value", startLine, startCol, endLine, endCol, replacement.isEmpty() ? "0" : replacement));
        } else if (isCode(code, codeStr, ProblemCode.CSS_PT_UNIT) || message.contains("pt unit")) {
            int startOff = positionToOffset(text, startLine, startCol);
            int endOff = positionToOffset(text, endLine, endCol);
            String slice = safeSubstring(text, startOff, endOff).trim();
            float val = parseLeadingNumber(slice, 12f);
            int px = Math.round(val * 1.3333f);
            float rem = Math.round((val / 12f) * 100f) / 100f;
            // Two conversion choices: px vs rem
            fixes.add(new TextReplacementQuickFix("Convert 'pt' to 'px' (" + px + "px)", startLine, startCol, endLine, endCol, px + "px"));
            fixes.add(new TextReplacementQuickFix("Convert 'pt' to 'rem' (" + rem + "rem)", startLine, startCol, endLine, endCol, rem + "rem"));
        } else if (isCode(code, codeStr, ProblemCode.CSS_IMPORTANT_USAGE) || message.contains("!important")) {
            int startOff = positionToOffset(text, startLine, startCol);
            int endOff = positionToOffset(text, endLine, endCol);
            while (startOff > 0 && Character.isWhitespace(text.charAt(startOff - 1)) && text.charAt(startOff - 1) != '\n') {
                startOff--;
            }
            int[] pos = offsetToPosition(text, startOff);
            fixes.add(new TextReplacementQuickFix("Remove '!important'", pos[0], pos[1], endLine, endCol, ""));
        } else if (isCode(code, codeStr, ProblemCode.CSS_HEX_SHORTENABLE) || message.contains("can be shortened")) {
            int startOff = positionToOffset(text, startLine, startCol);
            int endOff = positionToOffset(text, endLine, endCol);
            String hex = safeSubstring(text, startOff, endOff).trim();
            String shortened = shortenHex(hex);
            fixes.add(new TextReplacementQuickFix("Shorten hex color to '" + shortened + "'", startLine, startCol, endLine, endCol, shortened));
        } else if (isCode(code, codeStr, ProblemCode.CSS_DUPLICATE_PROPERTY) || message.contains("Duplicate property")) {
            fixes.add(new TextReplacementQuickFix("Remove duplicate property declaration", startLine, startCol, endLine, endCol, ""));
        } else if (isCode(code, codeStr, ProblemCode.CSS_VENDOR_PREFIX_NEEDED) || message.contains("vendor prefix") || message.contains("broader browser support") || message.contains("Missing standard property")) {
            String targetProp = extractVendorTargetProperty(message);
            int startOff = positionToOffset(text, startLine, startCol);
            int semi = text.indexOf(';', startOff);
            String val = "scale(1)";
            int colon = text.indexOf(':', startOff);
            if (colon != -1 && semi != -1 && colon < semi) {
                val = text.substring(colon + 1, semi).trim();
            }
            int insertOff = (semi != -1) ? semi + 1 : positionToOffset(text, endLine, endCol);
            int[] insPos = offsetToPosition(text, insertOff);
            fixes.add(new TextReplacementQuickFix("Add standard property declaration", insPos[0], insPos[1], insPos[0], insPos[1], "\n  " + targetProp + ": " + val + ";"));
        } else if (isCode(code, codeStr, ProblemCode.CSS_VAR_NO_FALLBACK) || message.contains("custom property lacks a fallback")) {
            int startOff = positionToOffset(text, startLine, startCol);
            int endOff = positionToOffset(text, endLine, endCol);
            String varSlice = safeSubstring(text, startOff, endOff).trim();
            if (varSlice.endsWith(")")) {
                String inner = varSlice.substring(0, varSlice.length() - 1);
                // Two fallback choices: initial vs inherit
                fixes.add(new TextReplacementQuickFix("Add 'initial' fallback to var()", startLine, startCol, endLine, endCol, inner + ", initial)"));
                fixes.add(new TextReplacementQuickFix("Add 'inherit' fallback to var()", startLine, startCol, endLine, endCol, inner + ", inherit)"));
            }
        } else if (isCode(code, codeStr, ProblemCode.CSS_VAR_UNUSED) || message.contains("Unused CSS custom property")) {
            fixes.add(new TextReplacementQuickFix("Remove unused CSS custom property", startLine, startCol, endLine, endCol, ""));
        }

        // ---------------------------------------------------------------------
        // JavaScript Quick Fixes
        // ---------------------------------------------------------------------
        else if (isCode(code, codeStr, ProblemCode.JS_UNUSED_VARIABLE) || message.contains("is declared but never used") || message.contains("Unused variable")) {
            fixes.add(new TextReplacementQuickFix("Remove unused variable", startLine, startCol, endLine, endCol, ""));
        } else if (isCode(code, codeStr, ProblemCode.JS_CONST_REASSIGNMENT) || message.contains("Assignment to constant variable")) {
            String varName = extractQuotedName(message);
            if (!varName.isEmpty() && !text.isEmpty()) {
                int declIdx = text.indexOf("const " + varName);
                if (declIdx >= 0) {
                    int[] pStart = offsetToPosition(text, declIdx);
                    int[] pEnd = offsetToPosition(text, declIdx + 5);
                    fixes.add(new TextReplacementQuickFix("Change 'const " + varName + "' declaration to 'let'", pStart[0], pStart[1], pEnd[0], pEnd[1], "let"));
                }
            }
        } else if (isCode(code, codeStr, ProblemCode.JS_MISSING_SEMICOLON) || message.contains("Missing semicolon")) {
            fixes.add(new TextReplacementQuickFix("Insert missing semicolon ';'", endLine, endCol, endLine, endCol, ";"));
        } else if (isCode(code, codeStr, ProblemCode.JS_CONSOLE_STATEMENT) || message.contains("Unexpected console statement")) {
            // Two handling options for console statements: remove vs comment out
            fixes.add(createFullLineDeletionFix("Remove console statement", startLine, endLine, text));
            fixes.add(new TextReplacementQuickFix("Comment out console statement", startLine, startCol, startLine, startCol, "// "));
        } else if (isCode(code, codeStr, ProblemCode.JS_TYPEOF_UNDEFINED_COMPARISON)
                || message.contains("typeof comparison with undefined")
                || (message.contains("typeof") && message.contains("undefined"))) {
            fixes.add(new TextReplacementQuickFix("Wrap 'undefined' in quotes ('\"undefined\"')", startLine, startCol, endLine, endCol, "\"undefined\""));
        } else if (isCode(code, codeStr, ProblemCode.JS_DUPLICATE_DECLARATION) || message.contains("Identifier has already been declared") || message.contains("already been declared")) {
            fixes.add(createFullLineDeletionFix("Remove duplicate declaration", startLine, endLine, text));
        } else if (isCode(code, codeStr, ProblemCode.JS_TOO_MANY_ARGUMENTS) || message.contains("Expected at most")) {
            fixes.add(new TextReplacementQuickFix("Remove extraneous arguments", startLine, startCol, endLine, endCol, ""));
        }

        // ---------------------------------------------------------------------
        // TypeScript Quick Fixes
        // ---------------------------------------------------------------------
        else if (isCode(code, codeStr, ProblemCode.TS_EXPLICIT_ANY) || message.contains("Unexpected any") || message.contains("Explicit 'any'")) {
            // Two replacement options: unknown vs Record<string, unknown>
            fixes.add(new TextReplacementQuickFix("Replace 'any' with 'unknown'", startLine, startCol, endLine, endCol, "unknown"));
            fixes.add(new TextReplacementQuickFix("Replace 'any' with 'Record<string, unknown>'", startLine, startCol, endLine, endCol, "Record<string, unknown>"));
        } else if (isCode(code, codeStr, ProblemCode.TS_REDUNDANT_TYPE) || message.contains("Redundant type annotation")) {
            fixes.add(new TextReplacementQuickFix("Remove redundant type annotation", startLine, startCol, endLine, endCol, ""));
        } else if (isCode(code, codeStr, ProblemCode.TS_UNION_UNDEFINED) || message.contains("Parameter can be optional instead of union with undefined") || message.contains("union with undefined")) {
            TextReplacementQuickFix fix = new TextReplacementQuickFix("Convert parameter to optional '?:'", endLine, endCol, endLine, endCol, "?");
            int pEndOff = positionToOffset(text, endLine, endCol);
            int colonIdx = text.indexOf(':', pEndOff);
            if (colonIdx >= 0) {
                int pipeIdx = text.indexOf('|', colonIdx);
                if (pipeIdx >= 0) {
                    int undefIdx = text.indexOf("undefined", pipeIdx);
                    if (undefIdx >= 0) {
                        int delStart = pipeIdx;
                        while (delStart > colonIdx + 1 && Character.isWhitespace(text.charAt(delStart - 1))) {
                            delStart--;
                        }
                        int delEnd = undefIdx + "undefined".length();
                        int[] pStart = offsetToPosition(text, delStart);
                        int[] pEnd = offsetToPosition(text, delEnd);
                        fix.addEdit(pStart[0], pStart[1], pEnd[0], pEnd[1], "");
                    }
                }
            }
            fixes.add(fix);
        } else if (isCode(code, codeStr, ProblemCode.TS_READONLY_ARRAY) || message.contains("Array<T> should be readonly") || message.contains("should be readonly")) {
            fixes.add(new TextReplacementQuickFix("Convert to 'readonly T[]'", startLine, startCol, startLine, startCol, "readonly "));
        } else if (isCode(code, codeStr, ProblemCode.TS_NON_NULL_ASSERTION) || message.contains("Forbidden non-null assertion")) {
            // Two options: optional chaining vs removal
            fixes.add(new TextReplacementQuickFix("Replace '!' with optional chaining '?.'", startLine, startCol, endLine, endCol, "?."));
            fixes.add(new TextReplacementQuickFix("Remove non-null assertion '!'", startLine, startCol, endLine, endCol, ""));
        } else if (isCode(code, codeStr, ProblemCode.TS_MISSING_RETURN_TYPE) || message.contains("Missing return type")) {
            int fnEndOff = positionToOffset(text, endLine, endCol);
            int openParen = text.indexOf('(', fnEndOff);
            int closeParen = (openParen >= 0) ? text.indexOf(')', openParen) : -1;
            int insOff = (closeParen >= 0) ? closeParen + 1 : fnEndOff;
            int[] pos = offsetToPosition(text, insOff);
            fixes.add(new TextReplacementQuickFix("Add explicit return type annotation (': void')", pos[0], pos[1], pos[0], pos[1], ": void"));
        } else if (isCode(code, codeStr, ProblemCode.TS_INTERFACE_NOT_IMPLEMENTED) || message.contains("incorrectly implements interface")) {
            int startOff = positionToOffset(text, startLine, startCol);
            int closeBrace = text.indexOf('}', startOff);
            int insOff = (closeBrace >= 0) ? closeBrace : positionToOffset(text, endLine, endCol);
            int[] pos = offsetToPosition(text, insOff);
            fixes.add(new TextReplacementQuickFix("Implement missing members", pos[0], pos[1], pos[0], pos[1], "\n  // TODO: implement interface members\n"));
        } else if (isCode(code, codeStr, ProblemCode.TS_MISSING_PROPERTIES) || message.contains("missing the following properties")) {
            int startOff = positionToOffset(text, startLine, startCol);
            int closeBrace = text.indexOf('}', startOff);
            int insOff = (closeBrace >= 0) ? closeBrace : positionToOffset(text, endLine, endCol);
            int[] pos = offsetToPosition(text, insOff);
            fixes.add(new TextReplacementQuickFix("Add missing properties", pos[0], pos[1], pos[0], pos[1], "\n  // TODO: implement interface members\n"));
        }

        // ---------------------------------------------------------------------
        // Markdown Quick Fixes
        // ---------------------------------------------------------------------
        else if (isCode(code, codeStr, ProblemCode.MD_MISSING_IMAGE_ALT) || message.contains("Image missing alt text") || message.contains("empty alt text")) {
            // Two alternatives: empty alt vs descriptive placeholder
            fixes.add(new TextReplacementQuickFix("Add empty alt text: ![]", startLine, startCol, endLine, endCol, "![]"));
            fixes.add(new TextReplacementQuickFix("Add descriptive alt text: ![Image]", startLine, startCol, endLine, endCol, "![Image]"));
        } else if (isCode(code, codeStr, ProblemCode.MD_USELESS_IMAGE_ALT) || message.contains("Image alt text contains redundant words")) {
            int startOff = positionToOffset(text, startLine, startCol);
            int endOff = positionToOffset(text, endLine, endCol);
            String slice = safeSubstring(text, startOff, endOff);
            String cleaned = slice.replaceAll("(?i)\\b(image|photo|picture)\\s+(of\\s+)?", "");
            fixes.add(new TextReplacementQuickFix("Remove redundant 'image'/'photo' prefix", startLine, startCol, endLine, endCol, cleaned));
        } else if (isCode(code, codeStr, ProblemCode.MD_EMPTY_LINK_TEXT) || message.contains("Empty link text")) {
            fixes.add(new TextReplacementQuickFix("Add link text placeholder '[Link]'", startLine, startCol, endLine, endCol, "[Link]"));
        } else if (isCode(code, codeStr, ProblemCode.MD_RAW_URL) || message.contains("Bare URL found")) {
            int startOff = positionToOffset(text, startLine, startCol);
            int endOff = positionToOffset(text, endLine, endCol);
            String rawUrl = safeSubstring(text, startOff, endOff).trim();
            // Two formatting choices: angle brackets vs markdown link
            fixes.add(new TextReplacementQuickFix("Enclose URL in angle brackets <" + rawUrl + ">", startLine, startCol, endLine, endCol, "<" + rawUrl + ">"));
            fixes.add(new TextReplacementQuickFix("Convert URL to Markdown link [" + rawUrl + "](" + rawUrl + ")", startLine, startCol, endLine, endCol, "[" + rawUrl + "](" + rawUrl + ")"));
        } else if (isCode(code, codeStr, ProblemCode.MD_TRAILING_WHITESPACE) || message.contains("Trailing whitespace")) {
            fixes.add(new TextReplacementQuickFix("Trim trailing whitespace", startLine, startCol, endLine, endCol, ""));
        } else if (isCode(code, codeStr, ProblemCode.MD_HARD_TAB) || message.contains("Hard tab characters")) {
            // Two tab conversion options: 2 spaces vs 4 spaces
            fixes.add(new TextReplacementQuickFix("Convert tab to 2 spaces", startLine, startCol, endLine, endCol, "  "));
            fixes.add(new TextReplacementQuickFix("Convert tab to 4 spaces", startLine, startCol, endLine, endCol, "    "));
        } else if (isCode(code, codeStr, ProblemCode.MD_INCONSISTENT_LIST_MARKER) || message.contains("Inconsistent list marker")) {
            String marker = "-";
            int expIdx = message.indexOf("Expected '");
            if (expIdx != -1) {
                int qEnd = message.indexOf('\'', expIdx + 10);
                if (qEnd != -1) {
                    marker = message.substring(expIdx + 10, qEnd);
                }
            }
            fixes.add(new TextReplacementQuickFix("Normalize list marker to '" + marker + "'", startLine, startCol, endLine, endCol, marker));
        } else if (isCode(code, codeStr, ProblemCode.MD_EMPTY_BLOCKQUOTE) || message.contains("Empty blockquote line")) {
            fixes.add(createFullLineDeletionFix("Remove empty blockquote marker", startLine, endLine, text));
        } else if (isCode(code, codeStr, ProblemCode.MD_HEADING_INCREMENT) || message.contains("Heading levels should only increment by one level")) {
            int startOff = positionToOffset(text, startLine, startCol);
            int hashCount = 0;
            while (startOff + hashCount < text.length() && text.charAt(startOff + hashCount) == '#') {
                hashCount++;
            }
            int[] endPos = offsetToPosition(text, startOff + Math.max(1, hashCount));
            fixes.add(new TextReplacementQuickFix("Adjust heading level to increment by 1", startLine, startCol, endPos[0], endPos[1], "##"));
        } else if (isCode(code, codeStr, ProblemCode.MD_UNCLOSED_CODE_BLOCK) || message.contains("Unclosed code block")) {
            int len = text.length();
            int[] pos = offsetToPosition(text, len);
            fixes.add(new TextReplacementQuickFix("Close code block", pos[0], pos[1], pos[0], pos[1], "\n```\n"));
        } else if (isCode(code, codeStr, ProblemCode.MD_BROKEN_LINK) || message.contains("Broken link target")) {
            String path = extractQuotedName(message);
            fixes.add(new QuickFix() {
                @Override public String getTitle() { return "Create referenced file '" + path + "'"; }
                @Override public void apply(CodeEditorTarget editor, Problem problem) {
                    if (problem != null && problem.getFile() != null && !path.isEmpty()) {
                        File target = new File(problem.getFile().getParentFile(), path);
                        try {
                            target.getParentFile().mkdirs();
                            target.createNewFile();
                        } catch (Exception ignored) {}
                    }
                }
            });
        } else if (isCode(code, codeStr, ProblemCode.MD_BROKEN_ANCHOR) || message.contains("Broken anchor reference")) {
            String anchor = extractQuotedName(message);
            int len = text.length();
            int[] pos = offsetToPosition(text, len);
            fixes.add(new TextReplacementQuickFix("Append anchor '#" + anchor + "' at end of document", pos[0], pos[1], pos[0], pos[1], "\n\n## " + anchor + "\n"));
        }

        // ---------------------------------------------------------------------
        // JSON Quick Fixes
        // ---------------------------------------------------------------------
        else if (isCode(code, codeStr, ProblemCode.JSON_TRAILING_COMMA) || message.toLowerCase(Locale.ROOT).contains("trailing comma")) {
            int startOffset = positionToOffset(text, startLine, startCol);
            int endOffset = positionToOffset(text, endLine, endCol);
            int commaIdx = -1;
            for (int k = startOffset; k <= Math.min(text.length() - 1, endOffset); k++) {
                if (text.charAt(k) == ',') {
                    commaIdx = k;
                    break;
                }
            }
            if (commaIdx == -1) {
                for (int k = Math.max(0, startOffset - 2); k <= Math.min(text.length() - 1, startOffset + 2); k++) {
                    if (text.charAt(k) == ',') {
                        commaIdx = k;
                        break;
                    }
                }
            }
            if (commaIdx >= 0) {
                int delEnd = commaIdx + 1;
                while (delEnd < text.length() && (text.charAt(delEnd) == ' ' || text.charAt(delEnd) == '\t')) {
                    delEnd++;
                }
                int[] pStart = offsetToPosition(text, commaIdx);
                int[] pEnd = offsetToPosition(text, delEnd);
                fixes.add(new TextReplacementQuickFix("Remove trailing comma", pStart[0], pStart[1], pEnd[0], pEnd[1], ""));
            }
        } else if (isCode(code, codeStr, ProblemCode.JSON_SINGLE_QUOTES) || message.contains("single quotes") || message.contains("Strings must use double quotes")) {
            int startOffset = positionToOffset(text, startLine, startCol);
            int endOffset = positionToOffset(text, endLine, endCol);
            String target = safeSubstring(text, startOffset, endOffset).trim();
            if (target.startsWith("'") && target.endsWith("'") && target.length() >= 2) {
                String inner = target.substring(1, target.length() - 1);
                String converted = "\"" + inner.replace("\"", "\\\"") + "\"";
                fixes.add(new TextReplacementQuickFix("Convert single quotes to valid double quotes", startLine, startCol, endLine, endCol, converted));
            }
        } else if (isCode(code, codeStr, ProblemCode.JSON_UNQUOTED_KEY) || message.contains("Unquoted key") || message.contains("Enclose key in double quotes")) {
            int startOffset = positionToOffset(text, startLine, startCol);
            int endOffset = positionToOffset(text, endLine, endCol);
            String target = safeSubstring(text, startOffset, endOffset).trim();
            if (!target.startsWith("\"")) {
                fixes.add(new TextReplacementQuickFix("Enclose key in double quotes", startLine, startCol, endLine, endCol, "\"" + target + "\""));
            }
        } else if (isCode(code, codeStr, ProblemCode.JSON_MISSING_PROPERTY) || message.contains("Missing required property") || message.contains("missing property")) {
            String propName = extractQuotedName(message);
            if (!propName.isEmpty()) {
                int startOff = positionToOffset(text, startLine, startCol);
                int openBrace = text.indexOf('{', startOff);
                if (openBrace == -1) openBrace = text.lastIndexOf('{', startOff);
                int insOff = (openBrace >= 0) ? openBrace + 1 : startOff;
                int[] pos = offsetToPosition(text, insOff);
                fixes.add(new TextReplacementQuickFix("Add missing required property '" + propName + "'", pos[0], pos[1], pos[0], pos[1], "\n  \"" + propName + "\": \"\","));
            }
        } else if (isCode(code, codeStr, ProblemCode.JSON_DUPLICATE_KEY) || message.contains("Duplicate key") || message.contains("Duplicate object key")) {
            fixes.add(createFullLineDeletionFix("Remove duplicate key", startLine, endLine, text));
        } else if (isCode(code, codeStr, ProblemCode.JSON_COMMENTS_DISALLOWED) || message.contains("Comments are not permitted in standard JSON") || message.contains("Comments are not permitted")) {
            fixes.add(createFullLineDeletionFix("Remove disallowed JSON comment", startLine, endLine, text));
        } else if (isCode(code, codeStr, ProblemCode.JSON_MULTIPLE_ROOTS) || message.contains("Multiple JSON root values")) {
            int len = text.length();
            int[] endPos = offsetToPosition(text, len);
            fixes.add(new TextReplacementQuickFix("Wrap multiple JSON values in array '[ ... ]'", 0, 0, endPos[0], endPos[1], "[\n" + text.trim() + "\n]"));
        }

        return fixes;
    }

    /**
     * Resolves all available LSP {@link CodeAction} quick fixes for the given problem.
     */
    public static List<CodeAction> getCodeActions(Problem problem, String uri, String docText) {
        if (problem == null) return Collections.emptyList();

        List<QuickFix> fixes = getQuickFixes(problem, docText);
        if (fixes.isEmpty()) return Collections.emptyList();

        List<CodeAction> actions = new ArrayList<>();
        String targetUri = uri != null ? uri : (problem.getFile() != null ? problem.getFile().getAbsolutePath() : "");

        int startLine = Math.max(0, problem.getLine() - 1);
        int startCol = Math.max(0, problem.getColumn() - 1);
        int length = Math.max(1, problem.getLength());
        int endLine = startLine;
        int endCol = startCol + length;

        for (QuickFix fix : fixes) {
            if (fix instanceof TextReplacementQuickFix) {
                TextReplacementQuickFix trFix = (TextReplacementQuickFix) fix;
                List<TextEdit> textEdits = new ArrayList<>();
                LspRange range = new LspRange(trFix.startLine, trFix.startCol, trFix.endLine, trFix.endCol);
                textEdits.add(new TextEdit(range, trFix.replacement));
                if (trFix.getAdditionalEdits() != null) {
                    for (TextReplacementQuickFix.EditChunk chunk : trFix.getAdditionalEdits()) {
                        textEdits.add(new TextEdit(new LspRange(chunk.startLine, chunk.startCol, chunk.endLine, chunk.endCol), chunk.replacement));
                    }
                }
                WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(targetUri, textEdits));

                Diagnostic diag = new Diagnostic(
                        range,
                        mapSeverityToLsp(problem.getSeverity()),
                        problem.getCode() != null ? problem.getCode() : "",
                        "vcode",
                        problem.getMessage() != null ? problem.getMessage() : ""
                );

                actions.add(new CodeAction(
                        trFix.getTitle(),
                        CodeActionKind.QUICKFIX,
                        Collections.singletonList(diag),
                        true,
                        wsEdit,
                        null,
                        null
                ));
            } else {
                Diagnostic diag = new Diagnostic(
                        new LspRange(startLine, startCol, endLine, endCol),
                        mapSeverityToLsp(problem.getSeverity()),
                        problem.getCode() != null ? problem.getCode() : "",
                        "vcode",
                        problem.getMessage() != null ? problem.getMessage() : ""
                );
                actions.add(new CodeAction(
                        fix.getTitle(),
                        CodeActionKind.QUICKFIX,
                        Collections.singletonList(diag),
                        true,
                        null,
                        null,
                        fix
                ));
            }
        }

        return actions;
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    private static boolean isCode(ProblemCode actual, String codeStr, ProblemCode expected) {
        if (actual == expected) return true;
        return expected != null && expected.name().equals(codeStr);
    }

    private static void insertHeadElementFix(List<QuickFix> fixes, String text, String title, String tag) {
        int headIdx = indexOfIgnoreCase(text, "<head>");
        int insOff;
        String insertText;
        if (headIdx >= 0) {
            insOff = headIdx + 6;
            insertText = "\n    " + tag;
        } else {
            int htmlIdx = indexOfIgnoreCase(text, "<html>");
            if (htmlIdx >= 0) {
                insOff = htmlIdx + 6;
                insertText = "\n<head>\n    " + tag + "\n</head>";
            } else {
                insOff = 0;
                insertText = tag + "\n";
            }
        }
        int[] pos = offsetToPosition(text, insOff);
        fixes.add(new TextReplacementQuickFix(title, pos[0], pos[1], pos[0], pos[1], insertText));
    }

    private static void addDeprecatedTagFix(List<QuickFix> fixes, String text,
                                            int startLine, int startCol, int endLine, int endCol,
                                            String oldTag, String newTag) {
        int startOff = positionToOffset(text, startLine, startCol);
        int endOff = positionToOffset(text, endLine, endCol);
        boolean hasOpen = startOff < text.length() && text.charAt(startOff) == '<';
        boolean hasClose = endOff > startOff && text.charAt(endOff - 1) == '>';
        String repl;
        if (hasOpen && hasClose) {
            repl = "<" + newTag + ">";
        } else if (hasOpen) {
            repl = "<" + newTag;
        } else {
            repl = newTag;
        }
        TextReplacementQuickFix fix = new TextReplacementQuickFix(
                "Replace <" + oldTag + "> with <" + newTag + ">",
                startLine, startCol, endLine, endCol, repl);

        String lowerText = text.toLowerCase(Locale.ROOT);
        String closeTarget = "</" + oldTag.toLowerCase(Locale.ROOT) + ">";
        int closeIdx = lowerText.indexOf(closeTarget, endOff);
        if (closeIdx >= 0) {
            int[] cStart = offsetToPosition(text, closeIdx);
            int[] cEnd = offsetToPosition(text, closeIdx + closeTarget.length());
            fix.addEdit(cStart[0], cStart[1], cEnd[0], cEnd[1], "</" + newTag + ">");
        }
        fixes.add(fix);
    }

    private static String extractTagName(String msg, String text, int line, int col) {
        if (msg != null) {
            int q1 = msg.indexOf('\'');
            if (q1 >= 0) {
                int q2 = msg.indexOf('\'', q1 + 1);
                if (q2 > q1) return msg.substring(q1 + 1, q2).replace("<", "").replace(">", "").trim();
            }
            int l1 = msg.indexOf('<');
            if (l1 >= 0) {
                int l2 = msg.indexOf('>', l1 + 1);
                if (l2 > l1) return msg.substring(l1 + 1, l2).trim();
            }
        }
        return "";
    }

    private static String extractTagNameFromMsg(String msg) {
        if (msg == null) return "";
        int start = msg.indexOf('\'');
        if (start >= 0) {
            int end = msg.indexOf('\'', start + 1);
            if (end > start) {
                return msg.substring(start + 1, end).replace("<", "").replace(">", "").trim();
            }
        }
        return "";
    }

    private static String extractQuotedName(String msg) {
        if (msg == null) return "";
        int q1 = msg.indexOf('\'');
        if (q1 >= 0) {
            int q2 = msg.indexOf('\'', q1 + 1);
            if (q2 > q1) return msg.substring(q1 + 1, q2);
        }
        return "";
    }

    private static String shortenHex(String hex) {
        if (hex == null) return "#fff";
        hex = hex.trim();
        if (hex.length() == 7 && hex.startsWith("#")) {
            char r1 = hex.charAt(1), r2 = hex.charAt(2);
            char g1 = hex.charAt(3), g2 = hex.charAt(4);
            char b1 = hex.charAt(5), b2 = hex.charAt(6);
            if (r1 == r2 && g1 == g2 && b1 == b2) {
                return "#" + r1 + g1 + b1;
            }
        }
        return hex;
    }

    private static float parseLeadingNumber(String s, float fallback) {
        if (s == null) return fallback;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.') {
                sb.append(c);
            } else if (sb.length() > 0) {
                break;
            }
        }
        try {
            return Float.parseFloat(sb.toString());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int indexOfIgnoreCase(String text, String needle) {
        if (text == null || needle == null) return -1;
        return text.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private static String safeSubstring(String text, int start, int end) {
        if (text == null) return "";
        int s = Math.max(0, Math.min(start, text.length()));
        int e = Math.max(s, Math.min(end, text.length()));
        return text.substring(s, e);
    }

    private static int findTagStart(String text, int off, String tagPrefix) {
        if (text == null || tagPrefix == null) return -1;
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerPrefix = tagPrefix.toLowerCase(Locale.ROOT);
        int maxLookahead = Math.min(lowerText.length(), off + 20);
        int tagStart = lowerText.lastIndexOf(lowerPrefix, maxLookahead);
        if (tagStart == -1) {
            int minLookbehind = Math.max(0, off - 20);
            tagStart = lowerText.indexOf(lowerPrefix, minLookbehind);
        }
        return tagStart;
    }

    private static TextReplacementQuickFix createFullLineDeletionFix(String title, int startLine, int endLine, String text) {
        int lineCount = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') lineCount++;
        }
        if (endLine + 1 < lineCount) {
            return new TextReplacementQuickFix(title, startLine, 0, endLine + 1, 0, "");
        } else {
            int lastLineIdx = Math.max(0, lineCount - 1);
            int lastLineOffset = positionToOffset(text, lastLineIdx, 0);
            int lastLineLen = text.length() - lastLineOffset;
            return new TextReplacementQuickFix(title, startLine, 0, lastLineIdx, lastLineLen, "");
        }
    }

    private static String extractVendorTargetProperty(String msg) {
        if (msg == null) return "transform";
        int needIdx = msg.indexOf("may need");
        if (needIdx != -1) {
            int q1 = msg.indexOf('\'', needIdx);
            if (q1 != -1) {
                int q2 = msg.indexOf('\'', q1 + 1);
                if (q2 > q1) return msg.substring(q1 + 1, q2);
            }
        }
        int q1 = msg.indexOf('\'');
        if (q1 != -1) {
            int q2 = msg.indexOf('\'', q1 + 1);
            if (q2 > q1) return msg.substring(q1 + 1, q2);
        }
        return "transform";
    }

    public static int positionToOffset(String text, int line, int col) {
        if (text == null || line < 0) return 0;
        int curLine = 0;
        int offset = 0;
        int len = text.length();
        while (offset < len && curLine < line) {
            if (text.charAt(offset) == '\n') {
                curLine++;
            }
            offset++;
        }
        return Math.min(len, offset + Math.max(0, col));
    }

    public static int[] offsetToPosition(String text, int offset) {
        if (text == null || offset <= 0) return new int[]{0, 0};
        int line = 0;
        int col = 0;
        int target = Math.min(offset, text.length());
        for (int i = 0; i < target; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 0;
            } else {
                col++;
            }
        }
        return new int[]{line, col};
    }

    private static DiagnosticSeverity mapSeverityToLsp(Problem.Severity severity) {
        if (severity == null) return DiagnosticSeverity.WARNING;
        switch (severity) {
            case ERROR: return DiagnosticSeverity.ERROR;
            case INFO: return DiagnosticSeverity.INFORMATION;
            case WARNING:
            default:
                return DiagnosticSeverity.WARNING;
        }
    }

    // -------------------------------------------------------------------------
    // Standard Text Replacement Quick Fix Implementation
    // -------------------------------------------------------------------------

    /**
     * Reusable, atomic text-replacement implementation of {@link QuickFix}.
     */
    public static class TextReplacementQuickFix implements QuickFix {
        public static class EditChunk {
            public final int startLine;
            public final int startCol;
            public final int endLine;
            public final int endCol;
            public final String replacement;

            public EditChunk(int startLine, int startCol, int endLine, int endCol, String replacement) {
                this.startLine = startLine;
                this.startCol = startCol;
                this.endLine = endLine;
                this.endCol = endCol;
                this.replacement = replacement != null ? replacement : "";
            }
        }

        public final String title;
        public final int startLine;
        public final int startCol;
        public final int endLine;
        public final int endCol;
        public final String replacement;
        private List<EditChunk> additionalEdits;

        public TextReplacementQuickFix(String title, int startLine, int startCol, int endLine, int endCol, String replacement) {
            this.title = title != null ? title : "";
            this.startLine = startLine;
            this.startCol = startCol;
            this.endLine = endLine;
            this.endCol = endCol;
            this.replacement = replacement != null ? replacement : "";
        }

        public TextReplacementQuickFix addEdit(int sLine, int sCol, int eLine, int eCol, String repl) {
            if (additionalEdits == null) {
                additionalEdits = new ArrayList<>();
            }
            additionalEdits.add(new EditChunk(sLine, sCol, eLine, eCol, repl));
            return this;
        }

        public List<EditChunk> getAdditionalEdits() {
            return additionalEdits;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public void apply(CodeEditorTarget editor, Problem problem) {
            if (editor == null) return;
            editor.beginAtomicGroup();
            try {
                if (additionalEdits == null || additionalEdits.isEmpty()) {
                    applySingle(editor, startLine, startCol, endLine, endCol, replacement);
                } else {
                    List<EditChunk> allEdits = new ArrayList<>();
                    allEdits.add(new EditChunk(startLine, startCol, endLine, endCol, replacement));
                    allEdits.addAll(additionalEdits);
                    // Sort descending by position so later edits do not shift earlier coordinates
                    allEdits.sort((a, b) -> {
                        if (b.startLine != a.startLine) {
                            return Integer.compare(b.startLine, a.startLine);
                        }
                        return Integer.compare(b.startCol, a.startCol);
                    });
                    for (EditChunk chunk : allEdits) {
                        applySingle(editor, chunk.startLine, chunk.startCol, chunk.endLine, chunk.endCol, chunk.replacement);
                    }
                }
            } finally {
                editor.endAtomicGroup();
            }
        }

        private static void applySingle(CodeEditorTarget editor, int sLine, int sCol, int eLine, int eCol, String repl) {
            int lineCount = editor.getLineCount();
            int effEndLine = eLine;
            int effEndCol = eCol;
            if (effEndLine >= lineCount) {
                effEndLine = Math.max(0, lineCount - 1);
                String lastLineText = editor.getLineText(effEndLine);
                effEndCol = lastLineText != null ? lastLineText.length() : 0;
            }
            int effStartLine = Math.min(sLine, effEndLine);
            int effStartCol = sCol;
            if (effStartLine == effEndLine && effStartCol > effEndCol) {
                effStartCol = effEndCol;
            }
            editor.replaceRange(effStartLine, effStartCol, effEndLine, effEndCol, repl);
        }
    }
}

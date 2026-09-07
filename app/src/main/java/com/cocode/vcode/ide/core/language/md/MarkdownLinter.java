package com.cocode.vcode.ide.core.language.md;

import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real-time linter for Markdown files, checking headers, lists, links, and code blocks.
 */
public class MarkdownLinter {

    private static final Pattern EMPTY_LINK_TEXT = Pattern.compile("\\[\\s*\\]\\([^)]*\\)");
    private static final Pattern EMPTY_IMAGE_ALT = Pattern.compile("!\\[\\s*\\]\\([^)]*\\)");
    private static final Pattern USELESS_IMAGE_ALT = Pattern.compile("!\\[(image|picture|logo|photo|img|pic)\\]\\([^)]*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAW_URL = Pattern.compile("(?<![\\(\\[<])(http[s]?://[^\\s<>()]+)(?![\\)\\]>])");
    private static final Pattern TRAILING_WHITESPACE = Pattern.compile("[ \\t]+$", Pattern.MULTILINE);
    private static final Pattern HARD_TAB = Pattern.compile("\\t");

    public static List<Problem> analyze(File file, String text) {
        if (text == null || text.trim().isEmpty()) return new ArrayList<>();
        List<Problem> problems = new ArrayList<>();

        MdLineStream stream = MdLexer.lex(text);
        MdSyntaxTree tree = MdParser.parseBlocks(stream, text);

        // 1. AST Traversal
        int lastHeadingLevel = 0;
        char listMarker = '\0';

        for (int i = 1; i < tree.nodeCount; i++) {
            int type = tree.nodeType[i];
            int start = tree.nodeStart[i];
            int end = tree.nodeEnd[i];
            int len = Math.max(1, end - start);

            if (type == MdSyntaxTree.N_ERROR) {
                int line = LinterUtils.getLine(text, start);
                int col = LinterUtils.getColumn(text, start);
                String msg = tree.nodeName[i] != null ? tree.nodeName[i] : "Markdown syntax error";
                problems.add(new Problem(file, line, col, Math.min(len, 3), msg, Problem.Severity.ERROR));
            } else if (type == MdSyntaxTree.N_HEADER) {
                int level = tree.nodeExtra[i];
                if (lastHeadingLevel > 0 && level > lastHeadingLevel + 1) {
                    int line = LinterUtils.getLine(text, start);
                    int col = LinterUtils.getColumn(text, start);
                    problems.add(new Problem(file, line, col, len,
                            "Heading levels should only increment by one level at a time.",
                            Problem.Severity.WARNING));
                }
                lastHeadingLevel = level;
            } else if (type == MdSyntaxTree.N_BLOCKQUOTE) {
                if (start < end) {
                    String bqText = text.substring(start, end).trim();
                    if (bqText.equals(">") || bqText.isEmpty()) {
                        int line = LinterUtils.getLine(text, start);
                        int col = LinterUtils.getColumn(text, start);
                        problems.add(new Problem(file, line, col, len, "Empty blockquote", Problem.Severity.INFO));
                    }
                }
            } else if (type == MdSyntaxTree.N_LIST_ITEM) {
                if (start < text.length()) {
                    int j = start;
                    while (j < end && (text.charAt(j) == ' ' || text.charAt(j) == '>')) j++;
                    if (j < end) {
                        char mChar = text.charAt(j);
                        if (mChar == '-' || mChar == '*' || mChar == '+') {
                            if (listMarker == '\0') {
                                listMarker = mChar;
                            } else if (listMarker != mChar) {
                                int line = LinterUtils.getLine(text, j);
                                int col = LinterUtils.getColumn(text, j);
                                problems.add(new Problem(file, line, col, 1,
                                        "Inconsistent list marker. Expected '" + listMarker + "' but found '" + mChar + "'.",
                                        Problem.Severity.WARNING));
                            }
                        }
                    }
                }
            }
        }

        // 2. Pattern checks for links, images, URLs, tabs, and whitespace
        Matcher m = EMPTY_LINK_TEXT.matcher(text);
        while (m.find()) {
            if (m.start() > 0 && text.charAt(m.start() - 1) == '!') continue;
            problems.add(createProblem(file, text, m, "Link has no text", Problem.Severity.WARNING));
        }

        m = EMPTY_IMAGE_ALT.matcher(text);
        while (m.find()) {
            problems.add(createProblem(file, text, m, "Image is missing alt text: required for accessibility", Problem.Severity.WARNING));
        }

        m = USELESS_IMAGE_ALT.matcher(text);
        while (m.find()) {
            problems.add(createProblem(file, text, m, "Useless alt text. Avoid using words like 'image' or 'picture'.", Problem.Severity.WARNING));
        }

        m = RAW_URL.matcher(text);
        while (m.find()) {
            problems.add(createProblem(file, text, m, "Raw URL detected. Enclose in < > or use a standard link format.", Problem.Severity.WARNING));
        }

        m = TRAILING_WHITESPACE.matcher(text);
        while (m.find()) {
            problems.add(createProblem(file, text, m, "Trailing whitespace detected", Problem.Severity.WARNING));
        }

        m = HARD_TAB.matcher(text);
        while (m.find()) {
            problems.add(createProblem(file, text, m, "Hard tab detected. Use spaces instead.", Problem.Severity.WARNING));
        }

        return problems;
    }

    private static Problem createProblem(File file, String text, Matcher m, String message, Problem.Severity severity) {
        int line = LinterUtils.getLine(text, m.start());
        int col = LinterUtils.getColumn(text, m.start());
        return new Problem(file, line, col, m.end() - m.start(), message, severity);
    }
}

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
    private static final Pattern EMPTY_BLOCKQUOTE = Pattern.compile("^>\\s*$", Pattern.MULTILINE);
    private static final Pattern RAW_URL = Pattern.compile("(?<![\\(\\[<])(http[s]?://[^\\s<>()]+)(?![\\)\\]>])");
    private static final Pattern TRAILING_WHITESPACE = Pattern.compile("[ \\t]+$", Pattern.MULTILINE);
    private static final Pattern HARD_TAB = Pattern.compile("\\t");
    
    // For headings
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$", Pattern.MULTILINE);

    // For lists
    private static final Pattern UNORDERED_LIST = Pattern.compile("^\\s*([*\\-+])\\s+", Pattern.MULTILINE);

    public static List<Problem> analyze(File file, String text) {
        if (text == null || text.trim().isEmpty()) return new ArrayList<>();
        List<Problem> problems = new ArrayList<>();

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

        m = EMPTY_BLOCKQUOTE.matcher(text);
        while (m.find()) {
            problems.add(createProblem(file, text, m, "Empty blockquote", Problem.Severity.INFO));
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

        // Heading Sequence
        m = HEADING.matcher(text);
        int lastLevel = 0;
        while (m.find()) {
            int level = m.group(1).length();
            if (lastLevel > 0 && level > lastLevel + 1) {
                problems.add(createProblem(file, text, m, "Heading levels should only increment by one level at a time.", Problem.Severity.WARNING));
            }
            lastLevel = level;
        }

        // List Consistency
        m = UNORDERED_LIST.matcher(text);
        char listMarker = '\0';
        while (m.find()) {
            char marker = m.group(1).charAt(0);
            if (listMarker == '\0') {
                listMarker = marker;
            } else if (listMarker != marker) {
                problems.add(createProblem(file, text, m, "Inconsistent list marker. Expected '" + listMarker + "' but found '" + marker + "'.", Problem.Severity.WARNING));
            }
        }

        // Unclosed Code Blocks
        int codeBlockCount = 0;
        int lastCodeBlockIndex = -1;
        Pattern codeBlock = Pattern.compile("^```", Pattern.MULTILINE);
        m = codeBlock.matcher(text);
        while (m.find()) {
            codeBlockCount++;
            lastCodeBlockIndex = m.start();
        }
        if (codeBlockCount % 2 != 0 && lastCodeBlockIndex != -1) {
            int line = LinterUtils.getLine(text, lastCodeBlockIndex);
            int col = LinterUtils.getColumn(text, lastCodeBlockIndex);
            problems.add(new Problem(file, line, col, 3, "Unclosed code block", Problem.Severity.ERROR));
        }

        return problems;
    }

    private static Problem createProblem(File file, String text, Matcher m, String message, Problem.Severity severity) {
        int line = LinterUtils.getLine(text, m.start());
        int col = LinterUtils.getColumn(text, m.start());
        return new Problem(file, line, col, m.end() - m.start(), message, severity);
    }
}

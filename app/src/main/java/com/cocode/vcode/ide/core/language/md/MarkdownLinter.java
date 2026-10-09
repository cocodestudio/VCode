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
    private static final Pattern INLINE_LINK_PATTERN = Pattern.compile("(?<!!)\\[([^\\]]+)\\]\\(([^)]*)\\)");
    private static final Pattern INLINE_IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]*)\\)");

    public static List<Problem> analyze(File file, String text) {
        if (text == null || text.trim().isEmpty()) return new ArrayList<>();
        List<Problem> problems = new ArrayList<>();

        MdLineStream stream = MdLexer.lex(text);
        MdSyntaxTree tree = MdParser.parseBlocks(stream, text);

        // Traverse AST to validate structural rules
        int lastHeadingLevel = 0;
        char listMarker = '\0';

        for (int i = 1; i < tree.nodeCount; i++) {
            int type = tree.nodeType[i];
            int start = tree.nodeStart[i];
            int end = tree.nodeEnd[i];
            int len = Math.max(1, end - start);

            if (type == MdSyntaxTree.N_LIST) {
                listMarker = '\0';
            } else if (type == MdSyntaxTree.N_ERROR) {
                int line = LinterUtils.getLine(text, start);
                int col = LinterUtils.getColumn(text, start);
                String msg = tree.nodeName[i] != null ? tree.nodeName[i] : "Markdown syntax error";
                com.cocode.vcode.ide.core.diagnostic.ProblemCode pCode =
                        "Unclosed code block".equals(msg)
                                ? com.cocode.vcode.ide.core.diagnostic.ProblemCode.MD_UNCLOSED_CODE_BLOCK
                                : com.cocode.vcode.ide.core.diagnostic.ProblemCode.SYNTAX_ERROR;
                problems.add(new Problem(file, line, col, Math.min(len, 3), msg, Problem.Severity.ERROR, pCode));
            } else if (type == MdSyntaxTree.N_HEADER) {
                listMarker = '\0';
                int level = tree.nodeExtra[i];
                if (lastHeadingLevel > 0 && level > lastHeadingLevel + 1) {
                    int line = LinterUtils.getLine(text, start);
                    int col = LinterUtils.getColumn(text, start);
                    problems.add(new Problem(file, line, col, len,
                            "Heading levels should only increment by one level at a time.",
                            Problem.Severity.WARNING,
                            com.cocode.vcode.ide.core.diagnostic.ProblemCode.MD_HEADING_INCREMENT));
                }
                lastHeadingLevel = level;
            } else if (type == MdSyntaxTree.N_BLOCKQUOTE) {
                listMarker = '\0';
                if (start < end) {
                    String bqText = text.substring(start, end).trim();
                    if (bqText.equals(">") || bqText.isEmpty()) {
                        int line = LinterUtils.getLine(text, start);
                        int col = LinterUtils.getColumn(text, start);
                        problems.add(new Problem(file, line, col, len, "Empty blockquote", Problem.Severity.INFO,
                                com.cocode.vcode.ide.core.diagnostic.ProblemCode.MD_EMPTY_BLOCKQUOTE));
                    }
                }
            } else if (type == MdSyntaxTree.N_PARAGRAPH) {
                if (tree.nodeParent[i] <= 0 || tree.nodeType[tree.nodeParent[i]] != MdSyntaxTree.N_LIST_ITEM) {
                    listMarker = '\0';
                }
            } else if (type == MdSyntaxTree.N_CODE_BLOCK || type == MdSyntaxTree.N_THEMATIC_BREAK) {
                listMarker = '\0';
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
                                        Problem.Severity.WARNING,
                                        com.cocode.vcode.ide.core.diagnostic.ProblemCode.MD_INCONSISTENT_LIST_MARKER));
                            }
                        }
                    }
                }
            }
        }

        // Validate links, images, URLs, tabs, and trailing whitespace outside code blocks
        List<int[]> codeSpans = new ArrayList<>();
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == MdSyntaxTree.N_CODE_BLOCK) {
                codeSpans.add(new int[]{tree.nodeStart[i], tree.nodeEnd[i]});
            }
        }
        int bStart = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '`') {
                if (bStart == -1) {
                    bStart = i;
                } else {
                    codeSpans.add(new int[]{bStart, i + 1});
                    bStart = -1;
                }
            }
        }

        Matcher m = EMPTY_LINK_TEXT.matcher(text);
        while (m.find()) {
            if (isInsideCode(m.start(), codeSpans)) continue;
            if (m.start() > 0 && text.charAt(m.start() - 1) == '!') continue;
            problems.add(createProblem(file, text, m, "Link has no text", Problem.Severity.WARNING,
                    com.cocode.vcode.ide.core.diagnostic.ProblemCode.MD_EMPTY_LINK_TEXT));
        }

        m = EMPTY_IMAGE_ALT.matcher(text);
        while (m.find()) {
            if (isInsideCode(m.start(), codeSpans)) continue;
            problems.add(createProblem(file, text, m, "Image is missing alt text: required for accessibility", Problem.Severity.WARNING,
                    com.cocode.vcode.ide.core.diagnostic.ProblemCode.MD_MISSING_IMAGE_ALT));
        }

        m = USELESS_IMAGE_ALT.matcher(text);
        while (m.find()) {
            if (isInsideCode(m.start(), codeSpans)) continue;
            problems.add(createProblem(file, text, m, "Useless alt text. Avoid using words like 'image' or 'picture'.", Problem.Severity.WARNING,
                    com.cocode.vcode.ide.core.diagnostic.ProblemCode.MD_USELESS_IMAGE_ALT));
        }

        m = RAW_URL.matcher(text);
        while (m.find()) {
            if (isInsideCode(m.start(), codeSpans)) continue;
            problems.add(createProblem(file, text, m, "Raw URL detected. Enclose in < > or use a standard link format.", Problem.Severity.INFO,
                    com.cocode.vcode.ide.core.diagnostic.ProblemCode.MD_RAW_URL));
        }

        m = TRAILING_WHITESPACE.matcher(text);
        while (m.find()) {
            if (isInsideCode(m.start(), codeSpans)) continue;
            int len = m.end() - m.start();
            // Two trailing spaces followed by newline indicate an intentional hard line break in Markdown
            if (len == 2 && text.charAt(m.start()) == ' ' && text.charAt(m.start() + 1) == ' '
                    && m.end() < text.length() && (text.charAt(m.end()) == '\n' || text.charAt(m.end()) == '\r')) {
                continue;
            }
            problems.add(createProblem(file, text, m, "Trailing whitespace detected", Problem.Severity.INFO,
                    com.cocode.vcode.ide.core.diagnostic.ProblemCode.MD_TRAILING_WHITESPACE));
        }

        m = HARD_TAB.matcher(text);
        while (m.find()) {
            if (isInsideCode(m.start(), codeSpans)) continue;
            problems.add(createProblem(file, text, m, "Hard tab detected. Use spaces instead.", Problem.Severity.INFO,
                    com.cocode.vcode.ide.core.diagnostic.ProblemCode.MD_HARD_TAB));
        }

        // Heading slugs for local anchor validation
        java.util.Set<String> headingSlugs = new java.util.HashSet<>();
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == MdSyntaxTree.N_HEADER) {
                String title = tree.nodeName[i];
                if (title == null && tree.nodeStart[i] < tree.nodeEnd[i]) {
                    int s = tree.nodeStart[i];
                    while (s < tree.nodeEnd[i] && text.charAt(s) == '#') s++;
                    while (s < tree.nodeEnd[i] && Character.isWhitespace(text.charAt(s))) s++;
                    title = text.substring(s, tree.nodeEnd[i]).trim();
                }
                String slug = MdParser.toSlug(title);
                if (!slug.isEmpty()) {
                    headingSlugs.add(slug);
                }
            }
        }

        // Validate inline links [text](target)
        m = INLINE_LINK_PATTERN.matcher(text);
        while (m.find()) {
            if (isInsideCode(m.start(), codeSpans)) continue;
            String rawTarget = m.group(2).trim();
            if (rawTarget.isEmpty()) {
                problems.add(createProblem(file, text, m, "Link has no target URL or anchor", Problem.Severity.WARNING,
                        com.cocode.vcode.ide.core.diagnostic.ProblemCode.MD_EMPTY_LINK_TARGET));
            } else if (rawTarget.startsWith("#")) {
                String slug = rawTarget.substring(1).trim().toLowerCase();
                if (slug.isEmpty()) {
                    problems.add(createProblem(file, text, m, "Anchor link has no heading slug", Problem.Severity.WARNING,
                            com.cocode.vcode.ide.core.diagnostic.ProblemCode.MD_EMPTY_LINK_TARGET));
                } else if (!headingSlugs.contains(slug)) {
                    problems.add(createProblem(file, text, m, "Broken anchor: '#" + slug + "' does not match any heading in this document",
                            Problem.Severity.WARNING,
                            com.cocode.vcode.ide.core.diagnostic.ProblemCode.MD_BROKEN_ANCHOR));
                }
            } else if (!rawTarget.startsWith("http://") && !rawTarget.startsWith("https://")
                    && !rawTarget.startsWith("mailto:") && !rawTarget.startsWith("ftp://")
                    && !rawTarget.startsWith("data:") && !rawTarget.startsWith("tel:")) {
                String filePath = rawTarget.split("\\s+")[0];
                int hIdx = filePath.indexOf('#');
                if (hIdx != -1) {
                    filePath = filePath.substring(0, hIdx);
                }
                if (!filePath.isEmpty() && file != null && file.getParentFile() != null) {
                    File targetFile = new File(file.getParentFile(), filePath);
                    if (!targetFile.exists()) {
                        problems.add(createProblem(file, text, m, "Broken link: target file '" + filePath + "' does not exist",
                                Problem.Severity.WARNING,
                                com.cocode.vcode.ide.core.diagnostic.ProblemCode.MD_BROKEN_LINK));
                    }
                }
            }
        }

        // Validate inline images ![alt](target)
        m = INLINE_IMAGE_PATTERN.matcher(text);
        while (m.find()) {
            if (isInsideCode(m.start(), codeSpans)) continue;
            String rawTarget = m.group(2).trim();
            if (rawTarget.isEmpty()) {
                problems.add(createProblem(file, text, m, "Image has no target source", Problem.Severity.WARNING,
                        com.cocode.vcode.ide.core.diagnostic.ProblemCode.MD_EMPTY_LINK_TARGET));
            } else if (!rawTarget.startsWith("http://") && !rawTarget.startsWith("https://")
                    && !rawTarget.startsWith("data:") && !rawTarget.startsWith("#")) {
                String filePath = rawTarget.split("\\s+")[0];
                if (!filePath.isEmpty() && file != null && file.getParentFile() != null) {
                    File targetFile = new File(file.getParentFile(), filePath);
                    if (!targetFile.exists()) {
                        problems.add(createProblem(file, text, m, "Broken image link: target file '" + filePath + "' does not exist",
                                Problem.Severity.WARNING,
                                com.cocode.vcode.ide.core.diagnostic.ProblemCode.MD_BROKEN_LINK));
                    }
                }
            }
        }

        return problems;
    }

    private static boolean isInsideCode(int offset, List<int[]> codeSpans) {
        for (int[] span : codeSpans) {
            if (offset >= span[0] && offset < span[1]) return true;
        }
        return false;
    }

    private static Problem createProblem(File file, String text, Matcher m, String message, Problem.Severity severity,
                                         com.cocode.vcode.ide.core.diagnostic.ProblemCode code) {
        int line = LinterUtils.getLine(text, m.start());
        int col = LinterUtils.getColumn(text, m.start());
        return new Problem(file, line, col, m.end() - m.start(), message, severity, code);
    }
}

package com.cocode.vcode.ide.core.language.md;

import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownLinter {

    private static final Pattern EMPTY_LINK_TEXT = Pattern.compile("\\[\\s*\\]\\([^)]*\\)");
    private static final Pattern EMPTY_IMAGE_ALT = Pattern.compile("!\\[\\s*\\]\\([^)]*\\)");
    private static final Pattern EMPTY_BLOCKQUOTE = Pattern.compile("^>\\s*$", Pattern.MULTILINE);

    public static List<Problem> analyze(File file, String text) {
        if (text == null || text.trim().isEmpty()) return new ArrayList<>();
        List<Problem> problems = new ArrayList<>();

        Matcher m = EMPTY_LINK_TEXT.matcher(text);
        while (m.find()) {
            if (m.start() > 0 && text.charAt(m.start() - 1) == '!') {
                continue;
            }
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            problems.add(new Problem(file, line, col, m.end() - m.start(), "Link has no text", Problem.Severity.WARNING));
        }

        m = EMPTY_IMAGE_ALT.matcher(text);
        while (m.find()) {
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            problems.add(new Problem(file, line, col, m.end() - m.start(), "Image is missing alt text: required for accessibility", Problem.Severity.WARNING));
        }

        m = EMPTY_BLOCKQUOTE.matcher(text);
        while (m.find()) {
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            problems.add(new Problem(file, line, col, m.end() - m.start(), "Empty blockquote", Problem.Severity.INFO));
        }

        return problems;
    }
}

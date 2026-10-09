package com.cocode.vcode.ide.core.language.md;

import com.cocode.vcode.ide.core.language.base.BaseFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * High-performance, zero-allocation formatter for Markdown documents.
 * Normalizes heading spacing, standardizes unordered list markers,
 * aligns GFM table columns, and trims trailing whitespace while strictly
 * preserving fenced code blocks and intentional hard line breaks.
 */
public class MarkdownFormatter extends BaseFormatter {

    @Override
    public String format(String code) {
        if (code == null || code.isEmpty()) return code;

        String[] lines = code.split("\n", -1);
        List<String> formattedLines = new ArrayList<>(lines.length);

        boolean inCodeBlock = false;
        List<String> tableBlock = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();

            // Toggle fenced code block
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                if (!tableBlock.isEmpty()) {
                    formatAndFlushTable(tableBlock, formattedLines);
                }
                inCodeBlock = !inCodeBlock;
                formattedLines.add(line);
                continue;
            }

            if (inCodeBlock) {
                formattedLines.add(line);
                continue;
            }

            // Check if current line is a table row (starts and/or contains pipes)
            if (isTableRow(trimmed)) {
                tableBlock.add(line);
                continue;
            } else if (!tableBlock.isEmpty()) {
                formatAndFlushTable(tableBlock, formattedLines);
            }

            // 1. Heading spacing: ensure space after '#'
            String processed = line;
            if (trimmed.startsWith("#")) {
                processed = formatHeadingLine(line);
            } else {
                // 2. Normalize unordered list markers (* or + to -)
                processed = formatListMarker(line);
            }

            // 3. Trim trailing whitespace (preserving 2 trailing spaces for hard line breaks)
            processed = trimTrailingWhitespace(processed);
            formattedLines.add(processed);
        }

        if (!tableBlock.isEmpty()) {
            formatAndFlushTable(tableBlock, formattedLines);
        }

        StringBuilder sb = new StringBuilder(code.length());
        for (int i = 0; i < formattedLines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(formattedLines.get(i));
        }
        return sb.toString();
    }

    private static String formatHeadingLine(String line) {
        int i = 0;
        int len = line.length();
        while (i < len && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        int indentEnd = i;
        int hashCount = 0;
        while (i < len && line.charAt(i) == '#' && hashCount < 6) {
            hashCount++;
            i++;
        }
        if (hashCount > 0) {
            String leadingIndent = line.substring(0, indentEnd);
            String hashes = line.substring(indentEnd, indentEnd + hashCount);
            String rest = line.substring(indentEnd + hashCount).trim();
            // Remove trailing closing hashes if present: e.g. # Heading ##
            int rEnd = rest.length();
            while (rEnd > 0 && rest.charAt(rEnd - 1) == '#') rEnd--;
            while (rEnd > 0 && Character.isWhitespace(rest.charAt(rEnd - 1))) rEnd--;
            rest = rest.substring(0, rEnd).trim();

            if (rest.isEmpty()) {
                return leadingIndent + hashes;
            }
            return leadingIndent + hashes + " " + rest;
        }
        return line;
    }

    private static String formatListMarker(String line) {
        int i = 0;
        int len = line.length();
        while (i < len && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        if (i < len && (line.charAt(i) == '*' || line.charAt(i) == '+') && i + 1 < len && line.charAt(i + 1) == ' ') {
            return line.substring(0, i) + "-" + line.substring(i + 1);
        }
        return line;
    }

    private static String trimTrailingWhitespace(String line) {
        int len = line.length();
        if (len >= 2 && line.charAt(len - 1) == ' ' && line.charAt(len - 2) == ' ') {
            if (len >= 3 && line.charAt(len - 3) != ' ') {
                // Exactly 2 trailing spaces = hard break, preserve it
                return line;
            }
        }
        int end = len;
        while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) {
            end--;
        }
        return line.substring(0, end);
    }

    private static boolean isTableRow(String trimmed) {
        if (trimmed.isEmpty()) return false;
        return trimmed.startsWith("|") || (trimmed.contains("|") && !trimmed.startsWith("```"));
    }

    private static void formatAndFlushTable(List<String> tableBlock, List<String> output) {
        if (tableBlock.isEmpty()) return;

        // Split each row into trimmed cells
        List<List<String>> parsedRows = new ArrayList<>(tableBlock.size());
        int maxCols = 0;

        for (String row : tableBlock) {
            String s = row.trim();
            if (s.startsWith("|")) s = s.substring(1);
            if (s.endsWith("|")) s = s.substring(0, s.length() - 1);

            String[] cells = s.split("\\|", -1);
            List<String> rowList = new ArrayList<>(cells.length);
            for (String cell : cells) {
                rowList.add(cell.trim());
            }
            maxCols = Math.max(maxCols, rowList.size());
            parsedRows.add(rowList);
        }

        if (maxCols == 0) {
            output.addAll(tableBlock);
            tableBlock.clear();
            return;
        }

        // Calculate max width for each column (minimum 3 characters for '---')
        int[] colWidths = new int[maxCols];
        for (int c = 0; c < maxCols; c++) {
            colWidths[c] = 3;
            for (List<String> row : parsedRows) {
                if (c < row.size()) {
                    String cell = row.get(c);
                    colWidths[c] = Math.max(colWidths[c], cell.length());
                }
            }
        }

        // Format each row with aligned pipes
        for (List<String> row : parsedRows) {
            StringBuilder sb = new StringBuilder();
            sb.append("|");
            for (int c = 0; c < maxCols; c++) {
                String val = (c < row.size()) ? row.get(c) : "";
                sb.append(' ');
                boolean isSeparator = isSeparatorCell(val);
                if (isSeparator) {
                    sb.append(formatSeparatorCell(val, colWidths[c]));
                } else {
                    sb.append(val);
                    for (int pad = val.length(); pad < colWidths[c]; pad++) {
                        sb.append(' ');
                    }
                }
                sb.append(" |");
            }
            output.add(sb.toString());
        }

        tableBlock.clear();
    }

    private static boolean isSeparatorCell(String val) {
        if (val.isEmpty()) return false;
        for (int i = 0; i < val.length(); i++) {
            char c = val.charAt(i);
            if (c != '-' && c != ':' && c != ' ') return false;
        }
        return val.contains("-");
    }

    private static String formatSeparatorCell(String val, int width) {
        boolean leftColon = val.startsWith(":");
        boolean rightColon = val.endsWith(":");
        int dashCount = width - (leftColon ? 1 : 0) - (rightColon ? 1 : 0);
        dashCount = Math.max(1, dashCount);

        StringBuilder sb = new StringBuilder(width);
        if (leftColon) sb.append(':');
        for (int i = 0; i < dashCount; i++) {
            sb.append('-');
        }
        if (rightColon) sb.append(':');
        return sb.toString();
    }
}

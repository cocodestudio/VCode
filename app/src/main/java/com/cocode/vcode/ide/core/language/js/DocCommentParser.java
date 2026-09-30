package com.cocode.vcode.ide.core.language.js;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance parser extracting user-written documentation comments (JSDoc, block comments,
 * and single-line comment blocks) directly preceding functions, methods, classes, variables,
 * and identifiers for VS Code-grade signature help and hover documentation.
 */
public final class DocCommentParser {

    private static final Pattern PAT_PARAM = Pattern.compile(
            "^@param\\s*(?:\\{([^}]+)\\})?\\s*([a-zA-Z0-9_$.]+)\\s*(?:-\\s*)?(.*)$",
            Pattern.DOTALL
    );

    private static final Pattern PAT_RETURNS = Pattern.compile(
            "^@returns?\\s*(?:\\{([^}]+)\\})?\\s*(?:-\\s*)?(.*)$",
            Pattern.DOTALL
    );

    private DocCommentParser() {
    }

    /**
     * Immutable container holding structured documentation extracted from a source comment.
     */
    public static final class DocCommentInfo {
        public final String description;
        public final Map<String, String> paramDocs;
        public final Map<String, String> paramTypes;
        public final String returnDoc;
        public final String returnType;
        public final String rawComment;

        public DocCommentInfo(String description,
                              Map<String, String> paramDocs,
                              Map<String, String> paramTypes,
                              String returnDoc,
                              String returnType,
                              String rawComment) {
            this.description = description != null ? description.trim() : "";
            this.paramDocs = paramDocs != null ? Collections.unmodifiableMap(paramDocs) : Collections.emptyMap();
            this.paramTypes = paramTypes != null ? Collections.unmodifiableMap(paramTypes) : Collections.emptyMap();
            this.returnDoc = returnDoc != null ? returnDoc.trim() : "";
            this.returnType = returnType != null ? returnType.trim() : "";
            this.rawComment = rawComment != null ? rawComment : "";
        }

        public boolean isEmpty() {
            return description.isEmpty() && paramDocs.isEmpty() && returnDoc.isEmpty() && returnType.isEmpty();
        }

        /**
         * Formats this documentation into rich VS Code-style Markdown for hover documentation.
         *
         * @param signatureLine Code declaration (e.g. "function calculateTotal(subtotal, taxRate): number")
         * @param language      Language tag (e.g. "javascript" or "typescript")
         * @return Full Markdown string
         */
        public String toMarkdown(String signatureLine, String language) {
            StringBuilder sb = new StringBuilder();
            if (signatureLine != null && !signatureLine.trim().isEmpty()) {
                sb.append("```").append(language != null ? language : "javascript").append("\n");
                sb.append(signatureLine.trim()).append("\n");
                sb.append("```\n\n");
            }
            if (!description.isEmpty()) {
                sb.append(description).append("\n\n");
            }
            if (!paramDocs.isEmpty()) {
                sb.append("**Parameters:**\n");
                for (Map.Entry<String, String> entry : paramDocs.entrySet()) {
                    String pName = entry.getKey();
                    String pDoc = entry.getValue();
                    String pType = paramTypes.get(pName);
                    sb.append("• `").append(pName);
                    if (pType != null && !pType.isEmpty()) {
                        sb.append(": ").append(pType);
                    }
                    sb.append("`");
                    if (pDoc != null && !pDoc.isEmpty()) {
                        sb.append(" — ").append(pDoc);
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
            if (!returnDoc.isEmpty() || !returnType.isEmpty()) {
                sb.append("**Returns:**\n");
                if (!returnType.isEmpty()) {
                    sb.append("`").append(returnType).append("`");
                    if (!returnDoc.isEmpty()) {
                        sb.append(" — ");
                    }
                }
                if (!returnDoc.isEmpty()) {
                    sb.append(returnDoc);
                }
                sb.append("\n\n");
            }
            return sb.toString().trim();
        }
    }

    /**
     * Extracts user-written documentation (JSDoc, block comment, or single-line comment block)
     * immediately preceding the declaration at the given character offset in source text.
     *
     * @param text       Entire source code buffer
     * @param declOffset Character offset where the function, method, class, or variable begins
     * @return Extracted documentation info, or null if no preceding documentation comment exists
     */
    public static DocCommentInfo extractDoc(String text, int declOffset) {
        if (text == null || declOffset <= 0 || declOffset > text.length()) {
            return null;
        }

        // Scan backward from declOffset skipping whitespace and empty lines
        int pos = declOffset - 1;
        while (pos >= 0 && Character.isWhitespace(text.charAt(pos))) {
            pos--;
        }
        if (pos < 0) return null;

        // 1. Check for block comment ending with "*/"
        if (pos >= 1 && text.charAt(pos) == '/' && text.charAt(pos - 1) == '*') {
            int commentEnd = pos - 1; // position of '*' in "*/"
            int commentStart = text.lastIndexOf("/*", commentEnd);
            if (commentStart >= 0) {
                String between = text.substring(commentEnd + 2, declOffset);
                if (between.trim().isEmpty()) {
                    boolean isJsDoc = (commentStart + 2 < text.length() && text.charAt(commentStart + 2) == '*');
                    String raw = text.substring(commentStart, commentEnd + 2);
                    return parseBlockComment(raw, isJsDoc);
                }
            }
        }

        // 2. Check for consecutive single-line comments "// ..."
        return parsePrecedingLineComments(text, declOffset);
    }

    private static DocCommentInfo parseBlockComment(String raw, boolean isJsDoc) {
        if (raw == null || raw.length() < 4) return null;

        int start = isJsDoc ? 3 : 2;
        int end = raw.length() - 2;
        String content = (end > start) ? raw.substring(start, end) : "";

        String[] lines = content.split("\n", -1);
        StringBuilder descBuilder = new StringBuilder();
        Map<String, String> paramDocs = new HashMap<>();
        Map<String, String> paramTypes = new HashMap<>();
        String returnDoc = null;
        String returnType = null;

        String currentTag = null;
        String currentParamName = null;
        StringBuilder currentTagContent = new StringBuilder();

        for (String rawLine : lines) {
            String trimmed = rawLine.trim();
            if (trimmed.startsWith("*")) {
                trimmed = trimmed.substring(1).trim();
            }

            if (isJsDoc && trimmed.startsWith("@")) {
                flushTag(currentTag, currentParamName, currentTagContent.toString().trim(),
                        paramDocs, paramTypes);
                if ("returns".equals(currentTag)) {
                    returnDoc = currentTagContent.toString().trim();
                }
                currentTagContent.setLength(0);
                currentParamName = null;

                Matcher mParam = PAT_PARAM.matcher(trimmed);
                if (mParam.matches()) {
                    currentTag = "param";
                    String type = mParam.group(1);
                    String name = mParam.group(2);
                    String desc = mParam.group(3);
                    currentParamName = name;
                    if (type != null && !type.trim().isEmpty()) {
                        paramTypes.put(name, type.trim());
                    }
                    if (desc != null && !desc.trim().isEmpty()) {
                        currentTagContent.append(desc.trim());
                    }
                    continue;
                }

                Matcher mRet = PAT_RETURNS.matcher(trimmed);
                if (mRet.matches()) {
                    currentTag = "returns";
                    String type = mRet.group(1);
                    String desc = mRet.group(2);
                    if (type != null && !type.trim().isEmpty()) {
                        returnType = type.trim();
                    }
                    if (desc != null && !desc.trim().isEmpty()) {
                        currentTagContent.append(desc.trim());
                    }
                    continue;
                }

                currentTag = "other";
                currentTagContent.append(trimmed);
            } else if (currentTag != null) {
                if (!trimmed.isEmpty()) {
                    if (currentTagContent.length() > 0) currentTagContent.append(" ");
                    currentTagContent.append(trimmed);
                }
            } else {
                if (!trimmed.isEmpty() || descBuilder.length() > 0) {
                    if (descBuilder.length() > 0) descBuilder.append("\n");
                    descBuilder.append(trimmed);
                }
            }
        }

        flushTag(currentTag, currentParamName, currentTagContent.toString().trim(),
                paramDocs, paramTypes);
        if ("returns".equals(currentTag) && returnDoc == null) {
            returnDoc = currentTagContent.toString().trim();
        }

        return new DocCommentInfo(
                descBuilder.toString().trim(),
                paramDocs,
                paramTypes,
                returnDoc,
                returnType,
                raw
        );
    }

    private static void flushTag(String tag, String paramName, String content,
                                 Map<String, String> paramDocs, Map<String, String> paramTypes) {
        if ("param".equals(tag) && paramName != null) {
            paramDocs.put(paramName, content);
        }
    }

    private static DocCommentInfo parsePrecedingLineComments(String text, int declOffset) {
        int lineStart = text.lastIndexOf('\n', declOffset - 1);
        int curLineEnd = (lineStart >= 0) ? lineStart : 0;

        List<String> commentLines = new ArrayList<>();
        while (curLineEnd > 0) {
            int prevLineStart = text.lastIndexOf('\n', curLineEnd - 1);
            int start = (prevLineStart >= 0) ? prevLineStart + 1 : 0;
            String line = text.substring(start, curLineEnd).trim();

            if (line.startsWith("//")) {
                String c = line.substring(2).trim();
                commentLines.add(0, c);
            } else {
                break;
            }
            curLineEnd = prevLineStart;
        }

        if (commentLines.isEmpty()) {
            return null;
        }

        String desc = String.join("\n", commentLines).trim();
        return new DocCommentInfo(desc, null, null, null, null, desc);
    }
}

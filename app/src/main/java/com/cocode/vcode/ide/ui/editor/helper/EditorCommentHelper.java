package com.cocode.vcode.ide.ui.editor.helper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.cocode.vcode.ide.core.editor.text.Content;
import com.cocode.vcode.ide.core.editor.text.ContentPosition;
import com.cocode.vcode.ide.core.editor.text.UndoStack;
import com.cocode.vcode.ide.core.model.FileType;

/**
 * Context-aware line and block comment toggling across programming and markup languages.
 */
public final class EditorCommentHelper {

    private EditorCommentHelper() {}

    public static class CommentTokens {
        public final String linePrefix; // e.g. "// "
        public final String blockStart; // e.g. "/* "
        public final String blockEnd;   // e.g. " */"

        public CommentTokens(@Nullable String linePrefix, @Nullable String blockStart, @Nullable String blockEnd) {
            this.linePrefix = linePrefix;
            this.blockStart = blockStart;
            this.blockEnd = blockEnd;
        }

        public boolean isLineComment() {
            return linePrefix != null;
        }
    }

    public static class CommentResult {
        public final ContentPosition newCursor;
        public final ContentPosition newSelectionAnchor;

        public CommentResult(ContentPosition newCursor, ContentPosition newSelectionAnchor) {
            this.newCursor = newCursor;
            this.newSelectionAnchor = newSelectionAnchor;
        }
    }

    @NonNull
    public static CommentTokens getCommentTokens(@Nullable FileType fileType) {
        if (fileType == null) {
            return new CommentTokens("// ", null, null);
        }
        switch (fileType) {
            case HTML:
            case SVG:
            case MARKDOWN:
                return new CommentTokens(null, "<!-- ", " -->");
            case CSS:
            case SCSS:
                return new CommentTokens(null, "/* ", " */");
            case ENV:
            case GITIGNORE:
                return new CommentTokens("# ", null, null);
            case JAVASCRIPT:
            case TYPESCRIPT:
            case JSON:
            default:
                return new CommentTokens("// ", null, null);
        }
    }

    /**
     * Toggles comment on the specified line range [startLine, endLine].
     * If all lines in range are commented, uncomments them. Otherwise comments them.
     */
    @NonNull
    public static CommentResult toggleComment(
            @NonNull Content content,
            @NonNull UndoStack undoStack,
            int startLine,
            int endLine,
            @Nullable FileType fileType,
            @NonNull ContentPosition cursor,
            @Nullable ContentPosition selectionAnchor) {

        int totalLines = content.lineCount();
        if (totalLines == 0) {
            return new CommentResult(cursor, selectionAnchor);
        }

        startLine = Math.max(0, Math.min(startLine, totalLines - 1));
        endLine = Math.max(startLine, Math.min(endLine, totalLines - 1));

        CommentTokens tokens = getCommentTokens(fileType);

        undoStack.beginAtomicGroup();
        try {
            if (tokens.isLineComment()) {
                return toggleLineComments(content, undoStack, startLine, endLine, tokens.linePrefix, cursor, selectionAnchor);
            } else {
                return toggleBlockComments(content, undoStack, startLine, endLine, tokens.blockStart, tokens.blockEnd, cursor, selectionAnchor);
            }
        } finally {
            undoStack.endAtomicGroup();
            undoStack.commitPending();
        }
    }

    private static CommentResult toggleLineComments(
            Content content,
            UndoStack undoStack,
            int startLine,
            int endLine,
            String prefix,
            ContentPosition cursor,
            ContentPosition selectionAnchor) {

        String trimmedPrefix = prefix.trim();

        // 1. Check if all non-empty lines are already commented
        boolean allCommented = true;
        boolean hasNonEmpty = false;
        int minIndent = Integer.MAX_VALUE;

        for (int line = startLine; line <= endLine; line++) {
            String lineStr = content.getLineCopy(line);
            int indent = getLeadingWhitespaceLen(lineStr);
            if (indent < lineStr.length()) {
                hasNonEmpty = true;
                if (indent < minIndent) minIndent = indent;
                String textAfterIndent = lineStr.substring(indent);
                if (!textAfterIndent.startsWith(trimmedPrefix)) {
                    allCommented = false;
                }
            }
        }

        if (!hasNonEmpty) {
            allCommented = false;
            minIndent = 0;
        }

        UndoStack.EditorSnapshot beforeSnap = new UndoStack.EditorSnapshot(cursor, selectionAnchor, 0, 0);

        int cursorLine = cursor.line;
        int cursorCol = cursor.column;
        int anchorLine = selectionAnchor != null ? selectionAnchor.line : -1;
        int anchorCol = selectionAnchor != null ? selectionAnchor.column : -1;

        int newCursorCol = cursorCol;
        int newAnchorCol = anchorCol;

        if (allCommented) {
            if (cursorLine >= startLine && cursorLine <= endLine) {
                String lineStr = content.getLineCopy(cursorLine);
                int indent = getLeadingWhitespaceLen(lineStr);
                if (indent < lineStr.length() && cursorCol > indent) {
                    int delLen = getUncommentDeleteLen(lineStr, indent, prefix, trimmedPrefix);
                    newCursorCol = Math.max(indent, cursorCol - delLen);
                }
            }
            if (anchorLine >= startLine && anchorLine <= endLine) {
                String lineStr = content.getLineCopy(anchorLine);
                int indent = getLeadingWhitespaceLen(lineStr);
                if (indent < lineStr.length() && anchorCol > indent) {
                    int delLen = getUncommentDeleteLen(lineStr, indent, prefix, trimmedPrefix);
                    newAnchorCol = Math.max(indent, anchorCol - delLen);
                }
            }
        } else {
            if (cursorLine >= startLine && cursorLine <= endLine) {
                int lineLen = content.lineLength(cursorLine);
                int insertCol = Math.min(minIndent, lineLen);
                if (cursorCol >= insertCol) {
                    newCursorCol += prefix.length();
                }
            }
            if (anchorLine >= startLine && anchorLine <= endLine) {
                int lineLen = content.lineLength(anchorLine);
                int insertCol = Math.min(minIndent, lineLen);
                if (anchorCol >= insertCol) {
                    newAnchorCol += prefix.length();
                }
            }
        }

        ContentPosition newCursor = new ContentPosition(cursorLine, newCursorCol);
        ContentPosition newAnchor = selectionAnchor != null ? new ContentPosition(anchorLine, newAnchorCol) : null;
        UndoStack.EditorSnapshot afterSnap = new UndoStack.EditorSnapshot(newCursor, newAnchor, 0, 0);

        if (allCommented) {
            // Uncomment each line
            for (int line = startLine; line <= endLine; line++) {
                String lineStr = content.getLineCopy(line);
                int indent = getLeadingWhitespaceLen(lineStr);
                if (indent < lineStr.length()) {
                    int deleteLen = getUncommentDeleteLen(lineStr, indent, prefix, trimmedPrefix);
                    if (deleteLen > 0) {
                        String deleted = lineStr.substring(indent, indent + deleteLen);
                        content.delete(line, indent, line, indent + deleteLen);
                        undoStack.recordDelete(line, indent, line, indent + deleteLen, deleted, beforeSnap, afterSnap);
                    }
                }
            }
        } else {
            // Comment each line at minIndent
            for (int line = startLine; line <= endLine; line++) {
                String lineStr = content.getLineCopy(line);
                int insertCol = Math.min(minIndent, lineStr.length());
                content.insert(line, insertCol, prefix);
                undoStack.recordInsert(line, insertCol, prefix, beforeSnap, afterSnap);
            }
        }

        return new CommentResult(newCursor, newAnchor);
    }

    private static int getUncommentDeleteLen(String lineStr, int indent, String prefix, String trimmedPrefix) {
        String after = lineStr.substring(indent);
        if (after.startsWith(prefix)) {
            return prefix.length();
        } else if (after.startsWith(trimmedPrefix)) {
            int deleteLen = trimmedPrefix.length();
            if (after.length() > deleteLen && after.charAt(deleteLen) == ' ') {
                deleteLen++;
            }
            return deleteLen;
        }
        return 0;
    }

    private static CommentResult toggleBlockComments(
            Content content,
            UndoStack undoStack,
            int startLine,
            int endLine,
            String blockStart,
            String blockEnd,
            ContentPosition cursor,
            ContentPosition selectionAnchor) {

        String trimmedStart = blockStart.trim();
        String trimmedEnd = blockEnd.trim();

        boolean allCommented = true;
        boolean hasNonEmpty = false;

        for (int line = startLine; line <= endLine; line++) {
            String lineStr = content.getLineCopy(line).trim();
            if (!lineStr.isEmpty()) {
                hasNonEmpty = true;
                if (!lineStr.startsWith(trimmedStart) || !lineStr.endsWith(trimmedEnd)) {
                    allCommented = false;
                }
            }
        }

        if (!hasNonEmpty) {
            allCommented = false;
        }

        UndoStack.EditorSnapshot beforeSnap = new UndoStack.EditorSnapshot(cursor, selectionAnchor, 0, 0);

        int cursorLine = cursor.line;
        int cursorCol = cursor.column;
        int anchorLine = selectionAnchor != null ? selectionAnchor.line : -1;
        int anchorCol = selectionAnchor != null ? selectionAnchor.column : -1;

        int newCursorCol = cursorCol;
        int newAnchorCol = anchorCol;

        if (allCommented) {
            if (cursorLine >= startLine && cursorLine <= endLine) {
                String lineStr = content.getLineCopy(cursorLine);
                int firstChar = getLeadingWhitespaceLen(lineStr);
                if (firstChar < lineStr.length()) {
                    int startLen = lineStr.substring(firstChar).startsWith(blockStart) ? blockStart.length() : trimmedStart.length();
                    if (cursorCol > firstChar) {
                        newCursorCol = Math.max(firstChar, cursorCol - startLen);
                    }
                }
            }
            if (anchorLine >= startLine && anchorLine <= endLine) {
                String lineStr = content.getLineCopy(anchorLine);
                int firstChar = getLeadingWhitespaceLen(lineStr);
                if (firstChar < lineStr.length()) {
                    int startLen = lineStr.substring(firstChar).startsWith(blockStart) ? blockStart.length() : trimmedStart.length();
                    if (anchorCol > firstChar) {
                        newAnchorCol = Math.max(firstChar, anchorCol - startLen);
                    }
                }
            }
        } else {
            if (cursorLine >= startLine && cursorLine <= endLine) {
                String lineStr = content.getLineCopy(cursorLine);
                int indent = getLeadingWhitespaceLen(lineStr);
                if (indent < lineStr.length() && cursorCol >= indent) {
                    newCursorCol += blockStart.length();
                }
            }
            if (anchorLine >= startLine && anchorLine <= endLine) {
                String lineStr = content.getLineCopy(anchorLine);
                int indent = getLeadingWhitespaceLen(lineStr);
                if (indent < lineStr.length() && anchorCol >= indent) {
                    newAnchorCol += blockStart.length();
                }
            }
        }

        ContentPosition newCursor = new ContentPosition(cursorLine, newCursorCol);
        ContentPosition newAnchor = selectionAnchor != null ? new ContentPosition(anchorLine, newAnchorCol) : null;
        UndoStack.EditorSnapshot afterSnap = new UndoStack.EditorSnapshot(newCursor, newAnchor, 0, 0);

        if (allCommented) {
            // Remove block comment from each line
            for (int line = startLine; line <= endLine; line++) {
                String lineStr = content.getLineCopy(line);
                int firstChar = getLeadingWhitespaceLen(lineStr);
                if (firstChar < lineStr.length()) {
                    int startLen = lineStr.substring(firstChar).startsWith(blockStart) ? blockStart.length() : trimmedStart.length();
                    int lastChar = getTrailingWhitespaceIndex(lineStr);
                    int endLen = lineStr.substring(0, lastChar).endsWith(blockEnd) ? blockEnd.length() : trimmedEnd.length();
                    int endCol = lastChar - endLen;

                    // Delete suffix first (so prefix offset isn't disturbed)
                    String delSuffix = lineStr.substring(endCol, lastChar);
                    content.delete(line, endCol, line, lastChar);
                    undoStack.recordDelete(line, endCol, line, lastChar, delSuffix, beforeSnap, afterSnap);

                    // Delete prefix
                    String delPrefix = lineStr.substring(firstChar, firstChar + startLen);
                    content.delete(line, firstChar, line, firstChar + startLen);
                    undoStack.recordDelete(line, firstChar, line, firstChar + startLen, delPrefix, beforeSnap, afterSnap);
                }
            }
        } else {
            // Add block comments to each line
            for (int line = startLine; line <= endLine; line++) {
                String lineStr = content.getLineCopy(line);
                int indent = getLeadingWhitespaceLen(lineStr);
                if (indent < lineStr.length()) {
                    content.insert(line, lineStr.length(), blockEnd);
                    undoStack.recordInsert(line, lineStr.length(), blockEnd, beforeSnap, afterSnap);

                    content.insert(line, indent, blockStart);
                    undoStack.recordInsert(line, indent, blockStart, beforeSnap, afterSnap);
                }
            }
        }

        return new CommentResult(newCursor, newAnchor);
    }

    private static int getLeadingWhitespaceLen(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }

    private static int getTrailingWhitespaceIndex(String s) {
        int i = s.length();
        while (i > 0 && (s.charAt(i - 1) == ' ' || s.charAt(i - 1) == '\t')) {
            i--;
        }
        return i;
    }
}

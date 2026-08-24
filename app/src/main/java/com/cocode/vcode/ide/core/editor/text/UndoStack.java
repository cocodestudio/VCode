package com.cocode.vcode.ide.core.editor.text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

/**
 * Manages the undo/redo history for editor operations, supporting action grouping and atomic edits.
 */
public final class UndoStack {

    private static final int MAX_HISTORY = 200;
    private static final long GROUP_TIMEOUT_MS = 800L;
    private final ArrayList<UndoRecord> pendingGroup = new ArrayList<>();
    private final Deque<UndoUnit> undoStack = new ArrayDeque<>();
    private final Deque<UndoUnit> redoStack = new ArrayDeque<>();
    private RecordType pendingType = null;
    private long lastEditTimeMs = 0L;
    private EditorSnapshot lastAfter = null;
    private boolean lastCharWasWordChar = false;
    private boolean lastWasBackspace = false;
    // When > 0, all recordInsert/recordDelete calls are funnelled into the same
    // pending group regardless of character class or timeout. Used to atomically
    // bundle auto-indent and auto-close side-effects with the triggering keystroke.
    private int atomicDepth = 0;

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public static ContentPosition advancePosition(int startLine, int startColumn, String text) {
        int line = startLine;
        int col = startColumn;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 0;
            } else {
                col++;
            }
        }
        return new ContentPosition(line, col);
    }

    public void recordInsert(int startLine, int startColumn,
                             String insertedText,
                             EditorSnapshot before,
                             EditorSnapshot after) {
        if (insertedText == null || insertedText.isEmpty()) return;

        long now = System.currentTimeMillis();
        boolean isSingleChar = insertedText.length() == 1 && insertedText.charAt(0) != '\n';
        boolean isWordChar = isSingleChar && isWordChar(insertedText.charAt(0));

        if (atomicDepth > 0) {
            // Inside an atomic group — always append to the current pending batch.
            // If there is no pending batch yet, just start one without committing.
            if (pendingType != null && pendingType != RecordType.INSERT) {
                commitPending();
            }
            ContentPosition insertEnd = advancePosition(startLine, startColumn, insertedText);
            UndoRecord record = new UndoRecord(
                    RecordType.INSERT,
                    startLine, startColumn,
                    insertEnd.line, insertEnd.column,
                    insertedText, before, after);
            pendingGroup.add(record);
            pendingType = RecordType.INSERT;
            lastEditTimeMs = now;
            lastAfter = after;
            lastCharWasWordChar = isWordChar;
            redoStack.clear();
            return;
        }

        boolean sameGroupType = pendingType == RecordType.INSERT && !pendingGroup.isEmpty();
        boolean adjacent = sameGroupType && lastAfter != null
                && lastAfter.cursor.isSameAs(new ContentPosition(startLine, startColumn));
        boolean withinTimeout = (now - lastEditTimeMs) < GROUP_TIMEOUT_MS;
        boolean sameCharClass = sameGroupType && lastCharWasWordChar == isWordChar;

        if (!isSingleChar || !sameGroupType || !adjacent || !withinTimeout || !sameCharClass) {
            commitPending();
        }

        ContentPosition insertEnd = advancePosition(startLine, startColumn, insertedText);

        UndoRecord record = new UndoRecord(
                RecordType.INSERT,
                startLine, startColumn,
                insertEnd.line, insertEnd.column,
                insertedText,
                before, after);

        pendingGroup.add(record);
        pendingType = RecordType.INSERT;
        lastEditTimeMs = now;
        lastAfter = after;
        lastCharWasWordChar = isWordChar;

        if (!isSingleChar) commitPending();

        redoStack.clear();
    }

    public void recordDelete(int startLine, int startColumn,
                             int endLine, int endColumn,
                             String deletedText,
                             EditorSnapshot before,
                             EditorSnapshot after) {
        if (deletedText == null || deletedText.isEmpty()) return;

        long now = System.currentTimeMillis();
        boolean isSingleChar = deletedText.length() == 1 && deletedText.charAt(0) != '\n';
        ContentPosition rangeStart = new ContentPosition(startLine, startColumn);
        ContentPosition rangeEnd = new ContentPosition(endLine, endColumn);
        boolean isBackspace = isSingleChar && after.cursor.isSameAs(rangeStart) && before.cursor.isSameAs(rangeEnd);

        if (atomicDepth > 0) {
            // Inside an atomic group — always append to the current pending batch,
            // regardless of character class, direction, adjacency, or timeout.
            // Mirrors recordInsert's atomic branch; previously recordDelete ignored
            // atomicDepth entirely, so deletes could never be bundled atomically.
            if (pendingType != null && pendingType != RecordType.DELETE) {
                commitPending();
            }
            UndoRecord record = new UndoRecord(
                    RecordType.DELETE,
                    startLine, startColumn,
                    endLine, endColumn,
                    deletedText, before, after);
            pendingGroup.add(record);
            pendingType = RecordType.DELETE;
            lastEditTimeMs = now;
            lastAfter = after;
            lastWasBackspace = isBackspace;
            redoStack.clear();
            return;
        }

        boolean isForwardDelete = isSingleChar && before.cursor.isSameAs(rangeStart) && after.cursor.isSameAs(rangeStart);

        boolean sameGroupType = pendingType == RecordType.DELETE && !pendingGroup.isEmpty();
        boolean sameDirection = sameGroupType && lastWasBackspace == isBackspace;
        boolean adjacent = sameGroupType && sameDirection && lastAfter != null
                && lastAfter.cursor.isSameAs(isBackspace ? rangeEnd : rangeStart);
        boolean withinTimeout = (now - lastEditTimeMs) < GROUP_TIMEOUT_MS;
        boolean directional = isBackspace || isForwardDelete;

        if (!isSingleChar || !directional || !sameGroupType || !adjacent || !withinTimeout) {
            commitPending();
        }

        UndoRecord record = new UndoRecord(
                RecordType.DELETE,
                startLine, startColumn,
                endLine, endColumn,
                deletedText,
                before, after);

        pendingGroup.add(record);
        pendingType = RecordType.DELETE;
        lastEditTimeMs = now;
        lastAfter = after;
        lastWasBackspace = isBackspace;

        if (!isSingleChar) commitPending();

        redoStack.clear();
    }

    public void recordReplace(int startLine, int startColumn,
                              int endLine, int endColumn,
                              String deletedText, String insertedText,
                              EditorSnapshot before, EditorSnapshot after) {
        ArrayList<UndoRecord> records = new ArrayList<>(2);
        ContentPosition afterDelete = new ContentPosition(startLine, startColumn);

        if (deletedText != null && !deletedText.isEmpty()) {
            EditorSnapshot deleteAfter = new EditorSnapshot(afterDelete, null, before.scrollX, before.scrollY);
            records.add(new UndoRecord(RecordType.DELETE, startLine, startColumn, endLine, endColumn,
                    deletedText, before, deleteAfter));
        }

        if (insertedText != null && !insertedText.isEmpty()) {
            ContentPosition insertEnd = advancePosition(startLine, startColumn, insertedText);
            EditorSnapshot insertBefore = new EditorSnapshot(afterDelete, null, after.scrollX, after.scrollY);
            records.add(new UndoRecord(RecordType.INSERT, startLine, startColumn, insertEnd.line, insertEnd.column,
                    insertedText, insertBefore, after));
        }

        if (records.isEmpty()) return;

        if (atomicDepth > 0) {
            // Bundle the replace's delete+insert pair into the currently-open atomic
            // group instead of pushing it as its own standalone unit — otherwise a
            // selection-replacing keystroke that also triggers an auto-close/auto-indent
            // side effect would still end up split across two undo units.
            if (pendingType != null && pendingType != RecordType.INSERT) {
                commitPending();
            }
            pendingGroup.addAll(records);
            pendingType = RecordType.INSERT;
            lastEditTimeMs = System.currentTimeMillis();
            lastAfter = after;
            redoStack.clear();
            return;
        }

        commitPending();
        pushUndo(new UndoUnit(records.toArray(new UndoRecord[0])));
        redoStack.clear();
    }

    public void commitPending() {
        if (pendingGroup.isEmpty()) {
            pendingType = null;
            lastAfter = null;
            return;
        }
        UndoRecord[] records = pendingGroup.toArray(new UndoRecord[0]);
        pendingGroup.clear();
        pendingType = null;
        lastAfter = null;
        pushUndo(new UndoUnit(records));
    }

    /**
     * Begins an atomic group: all subsequent {@link #recordInsert} / {@link #recordDelete}
     * calls will be bundled into the current pending group (or a new one) regardless of
     * character class, timeout, or whether the text is multi-character.
     * Must be balanced with {@link #endAtomicGroup()}.
     * <p>
     * Use this to atomically bundle auto-indent, auto-close-bracket, and auto-close-tag
     * side-effects with the keystroke that triggered them.
     */
    public void beginAtomicGroup() {
        atomicDepth++;
    }

    /**
     * Ends an atomic group started by {@link #beginAtomicGroup()}.
     * When the depth reaches zero the group is left open (it will be committed on the
     * next unrelated edit or timeout), so it still merges with further typing.
     */
    public void endAtomicGroup() {
        if (atomicDepth > 0) atomicDepth--;
    }

    public EditorSnapshot undo(Content content) {
        commitPending();
        if (undoStack.isEmpty()) return null;

        UndoUnit unit = undoStack.pop();
        redoStack.push(unit);

        EditorSnapshot snapshot = null;
        for (int i = unit.records.length - 1; i >= 0; i--) {
            snapshot = reverseRecord(unit.records[i], content);
        }
        return snapshot;
    }

    public EditorSnapshot redo(Content content) {
        if (redoStack.isEmpty()) return null;

        UndoUnit unit = redoStack.pop();
        undoStack.push(unit);

        EditorSnapshot snapshot = null;
        for (UndoRecord record : unit.records) {
            snapshot = applyRecord(record, content);
        }
        return snapshot;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty() || !pendingGroup.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void reset() {
        undoStack.clear();
        redoStack.clear();
        pendingGroup.clear();
        pendingType = null;
        lastAfter = null;
        lastEditTimeMs = 0L;
    }

    private void pushUndo(UndoUnit unit) {
        undoStack.push(unit);
        while (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast();
        }
    }

    private EditorSnapshot reverseRecord(UndoRecord record, Content content) {
        if (record.type == RecordType.INSERT) {
            content.delete(record.startLine, record.startColumn, record.endLine, record.endColumn);
        } else {
            content.insert(record.startLine, record.startColumn, record.text);
        }
        return record.before;
    }

    private EditorSnapshot applyRecord(UndoRecord record, Content content) {
        if (record.type == RecordType.INSERT) {
            content.insert(record.startLine, record.startColumn, record.text);
        } else {
            content.delete(record.startLine, record.startColumn, record.endLine, record.endColumn);
        }
        return record.after;
    }

    private enum RecordType {INSERT, DELETE}

    public static final class EditorSnapshot {
        public final ContentPosition cursor;
        public final ContentPosition selectionAnchor;
        public final int scrollX;
        public final int scrollY;

        public EditorSnapshot(ContentPosition cursor, ContentPosition selectionAnchor, int scrollX, int scrollY) {
            this.cursor = cursor;
            this.selectionAnchor = selectionAnchor;
            this.scrollX = scrollX;
            this.scrollY = scrollY;
        }
    }

    private static final class UndoRecord {
        final RecordType type;
        final int startLine;
        final int startColumn;
        final int endLine;
        final int endColumn;
        final String text;
        final EditorSnapshot before;
        final EditorSnapshot after;

        UndoRecord(RecordType type,
                   int startLine, int startColumn,
                   int endLine, int endColumn,
                   String text,
                   EditorSnapshot before,
                   EditorSnapshot after) {
            this.type = type;
            this.startLine = startLine;
            this.startColumn = startColumn;
            this.endLine = endLine;
            this.endColumn = endColumn;
            this.text = text;
            this.before = before;
            this.after = after;
        }
    }

    private static final class UndoUnit {
        final UndoRecord[] records;

        UndoUnit(UndoRecord[] records) {
            this.records = records;
        }
    }
}

package com.cocode.vcode.ide.core.editor.text;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The authoritative text model for the VCode code editor.
 *
 * <p>This is a <strong>line-based</strong> representation: text is stored as an ordered list of
 * {@link ContentLine} objects rather than as one giant {@code char[]} or {@code Spannable}.
 * Editing a single line on a 50,000-line file only touches that one line's data — no full-document
 * array shift is required.
 *
 * <p><strong>Key design properties:</strong>
 * <ul>
 *   <li><b>Cumulative length index</b> — a {@code long[]} of running character totals (including
 *       the newline terminator for each line) lets {@link #flatOffset(ContentPosition)} and
 *       {@link #positionAt(int)} run in O(log n) (binary search) rather than O(n) document scan.
 *       The index is rebuilt incrementally from the edit point, not from scratch.</li>
 *   <li><b>Atomic version counter</b> — every mutation bumps {@link #getVersion()}, giving
 *       background threads a cheap "has anything changed since I last checked" mechanism.</li>
 *   <li><b>Read-write lock</b> — the main thread holds the write lock during mutations.
 *       Background analysers take the read lock only long enough to copy the specific line range
 *       they need, then release — they never hold a reference into internal arrays across a
 *       thread-hop.</li>
 *   <li><b>Listener notification</b> — {@link ContentChangeListener}s are notified synchronously
 *       on the mutating thread (always the main thread). Listeners must not perform I/O inline.</li>
 * </ul>
 *
 * <p>This class replaces the standalone {@code OffsetIndexCache} utility and the bolt-on span
 * approach of the prior {@code AppCompatEditText}-based architecture. It is the single source of
 * truth for all text content in the editor.
 *
 * <p><b>Thread safety:</b> All public mutation methods acquire the write lock internally.
 * All public query methods that read multiple lines acquire the read lock internally.
 * Single-line atomic reads ({@link #lineLength}, {@link #charAt}) are lock-free when called
 * from the main thread since no concurrent writer can exist at that point.
 */
public final class Content {

    // ── Internal state ────────────────────────────────────────────────────────

    /**
     * The ordered line store. Element {@code i} is the {@code ContentLine} for line {@code i}.
     */
    private final ArrayList<ContentLine> lines = new ArrayList<>();
    /**
     * Incremented on every mutation. Background threads compare against this to detect staleness.
     */
    private final AtomicLong version = new AtomicLong(0L);
    /**
     * Guards all structural mutations and multi-line reads.
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    /**
     * Registered change listeners. {@link CopyOnWriteArrayList} allows listeners to remove
     * themselves inside a callback without a {@link java.util.ConcurrentModificationException}.
     */
    private final CopyOnWriteArrayList<ContentChangeListener> listeners = new CopyOnWriteArrayList<>();
    /**
     * Binary Indexed Tree (Fenwick Tree) storing line lengths including the newline character.
     * bit[i] represents a segment sum of line lengths.
     * 1-indexed for Fenwick operations, but size is up to lines.size().
     */
    private long[] bit;
    private int bitCapacity = 0;

    // ── Construction ──────────────────────────────────────────────────────────

    public Content() {
        lines.add(new ContentLine());
        rebuildBit();
    }

    /**
     * Creates a {@code Content} document pre-populated with the given text.
     */
    public Content(String text) {
        loadText(text);
    }

    // ── Listener management ───────────────────────────────────────────────────

    /**
     * Off-thread-safe: builds a complete replacement line/index structure without touching
     * this instance's live state. Safe to call from a background thread.
     */
    public static LoadedLines prepareLoad(String text) {
        ArrayList<ContentLine> newLines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            newLines.add(new ContentLine());
        } else {
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    newLines.add(new ContentLine(text.substring(start, i)));
                    start = i + 1;
                }
            }
            newLines.add(new ContentLine(text.substring(start)));
        }

        int n = newLines.size();
        int capacity = Math.max(n + 1, 64);
        long[] newBit = new long[capacity];
        int longest = 0;
        for (int i = 0; i < n; i++) {
            int len = newLines.get(i).length();
            if (len > longest) longest = len;
            newBit[i + 1] += len + 1;
            int j = (i + 1) + ((i + 1) & -(i + 1));
            if (j <= n) {
                newBit[j] += newBit[i + 1];
            }
        }
        return new LoadedLines(newLines, newBit, capacity, longest);
    }

    private static int indexOfNewline(CharSequence s, int fromIndex) {
        for (int i = fromIndex; i < s.length(); i++) {
            if (s.charAt(i) == '\n') return i;
        }
        return -1;
    }

    // ── Core mutations ────────────────────────────────────────────────────────

    private static char[] toCharArray(CharSequence s) {
        char[] arr = new char[s.length()];
        for (int i = 0; i < arr.length; i++) arr[i] = s.charAt(i);
        return arr;
    }

    public void addChangeListener(ContentChangeListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeChangeListener(ContentChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Inserts {@code text} at position {@code (line, column)}.
     *
     * <p>If {@code text} contains newline characters, lines will be split accordingly.
     * The cumulative index is updated incrementally from {@code line} onwards.
     *
     * @param line   Zero-indexed line index; must be in {@code [0, lineCount())}.
     * @param column Zero-indexed column; must be in {@code [0, lineLength(line)]}.
     * @param text   Text to insert. May contain {@code '\n'}. Must not be {@code null}.
     */
    public void insert(int line, int column, CharSequence text) {
        if (text == null || text.length() == 0) return;
        lock.writeLock().lock();
        try {
            insertInternal(line, column, text);
        } finally {
            lock.writeLock().unlock();
        }
        version.incrementAndGet();
        notifyInsert(line, column, text);
    }

    /**
     * Deletes the text in {@code [startLine, startColumn) .. [endLine, endColumn)}.
     *
     * <p>If {@code startLine == endLine}, only characters in {@code [startColumn, endColumn)}
     * on that line are removed. If the range spans multiple lines, those lines are merged.
     */
    public void delete(int startLine, int startColumn, int endLine, int endColumn) {
        if (startLine == endLine && startColumn == endColumn) return;
        lock.writeLock().lock();
        try {
            deleteInternal(startLine, startColumn, endLine, endColumn);
        } finally {
            lock.writeLock().unlock();
        }
        version.incrementAndGet();
        notifyDelete(startLine, startColumn, endLine, endColumn);
    }

    /**
     * Replaces the text in {@code [startLine, startColumn) .. [endLine, endColumn)} with
     * {@code newText}. Equivalent to {@code delete(...)} followed by {@code insert(...)}.
     */
    public void replace(int startLine, int startColumn, int endLine, int endColumn,
                        CharSequence newText) {
        lock.writeLock().lock();
        try {
            deleteInternal(startLine, startColumn, endLine, endColumn);
            insertInternal(startLine, startColumn, newText != null ? newText : "");
        } finally {
            lock.writeLock().unlock();
        }
        version.incrementAndGet();
        // Notify as a delete followed by insert (simplest contract for listeners)
        notifyDelete(startLine, startColumn, endLine, endColumn);
        if (newText != null && newText.length() > 0) {
            notifyInsert(startLine, startColumn, newText);
        }
    }

    /**
     * Replaces the entire document content with {@code text} without notifying listeners per-edit.
     * Used when loading a new file. Resets the version counter.
     */
    public void loadText(String text) {
        lock.writeLock().lock();
        try {
            lines.clear();
            if (text == null || text.isEmpty()) {
                lines.add(new ContentLine());
            } else {
                // Split on '\n', keeping each segment as a ContentLine
                int start = 0;
                for (int i = 0; i < text.length(); i++) {
                    if (text.charAt(i) == '\n') {
                        lines.add(new ContentLine(text.substring(start, i)));
                        start = i + 1;
                    }
                }
                // Last line (no trailing newline)
                lines.add(new ContentLine(text.substring(start)));
            }
            rebuildBit();
        } finally {
            lock.writeLock().unlock();
        }
        version.incrementAndGet();
    }

    // ── Queries (main-thread safe without lock for single values) ─────────────

    /**
     * Applies a {@link LoadedLines} built via {@link #prepareLoad}, in place, preserving this
     * Content instance's identity (and therefore any listeners already registered on it).
     */
    public void applyLoaded(LoadedLines loaded) {
        lock.writeLock().lock();
        try {
            lines.clear();
            lines.addAll(loaded.lines);
            bit = loaded.bit;
            bitCapacity = loaded.bitCapacity;
        } finally {
            lock.writeLock().unlock();
        }
        version.incrementAndGet();
    }

    /**
     * Returns the total number of lines (always at least 1).
     */
    public int lineCount() {
        return lines.size();
    }

    /**
     * Returns the length of the specified line in characters (excluding the newline terminator).
     * Must be called with the read lock held if on a background thread.
     */
    public int lineLength(int line) {
        return lines.get(line).length();
    }

    /**
     * Returns the character at {@code (line, column)}.
     * Must be called with the read lock held if on a background thread.
     */
    public char charAt(int line, int column) {
        return lines.get(line).charAt(column);
    }

    /**
     * Copies the characters of the specified line into {@code dest}.
     * {@code dest} must have capacity at least {@code lineLength(line)}.
     *
     * @return the number of characters copied.
     */

    public int getLineChars(int line, int start, int end, char[] dest) {
        lock.readLock().lock();
        try {
            ContentLine cl = lines.get(line);
            int len = cl.length();
            int actualStart = Math.max(0, Math.min(len, start));
            int actualEnd = Math.max(0, Math.min(len, end));
            int count = actualEnd - actualStart;
            if (count > 0 && dest.length >= count) {
                cl.getChars(actualStart, actualEnd, dest, 0);
            }
            return count;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getLineChars(int line, char[] dest) {
        lock.readLock().lock();
        try {
            ContentLine cl = lines.get(line);
            int len = cl.length();
            if (dest.length >= len) {
                cl.getChars(0, len, dest, 0);
                return len;
            }
            return 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the {@link ContentLine} for the given line index.
     *
     * <p><strong>Warning:</strong> the returned object is a live reference into the internal
     * line store. The caller must not retain this reference across a thread-hop or beyond the
     * enclosing read-lock section. Background threads should use {@link #getLineCopy(int)}
     * instead.
     */
    public ContentLine getLine(int line) {
        return lines.get(line);
    }

    /**
     * Returns an independent {@link String} copy of the specified line.
     * Safe to use on background threads without holding the read lock after this call returns
     * (the returned {@code String} is immutable).
     */
    public String getLineCopy(int line) {
        lock.readLock().lock();
        try {
            return lines.get(line).toLineString();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Converts a {@link ContentPosition} to a flat (absolute) character offset within the
     * entire document, counting newlines as single characters.
     *
     * <p>O(1) via the cumulative length index.
     */
    public int flatOffset(ContentPosition pos) {
        lock.readLock().lock();
        try {
            int lineIdx = Math.max(0, Math.min(pos.line, lines.size() - 1));
            int col = Math.max(0, Math.min(pos.column, lines.get(lineIdx).length()));
            return (int) bitQuery(lineIdx) + col;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Converts a flat character offset into a {@link ContentPosition}.
     *
     * <p>O(log n) via binary search on the cumulative length array.
     *
     * @param offset Flat offset in {@code [0, totalLength()]}.
     */
    public ContentPosition positionAt(int offset) {
        lock.readLock().lock();
        try {
            if (offset <= 0) return ContentPosition.ZERO;
            int lineCount = lines.size();
            long total = bitQuery(lineCount) - 1;
            if (offset >= total) {
                int lastLine = lineCount - 1;
                return new ContentPosition(lastLine, lines.get(lastLine).length());
            }

            int index = 0;
            long currentSum = 0;
            int highestOneBit = Integer.highestOneBit(lineCount);

            for (int i = highestOneBit; i > 0; i >>= 1) {
                int next = index + i;
                if (next <= lineCount && currentSum + bit[next] <= offset) {
                    index = next;
                    currentSum += bit[index];
                }
            }

            int line = index;
            int col = offset - (int) currentSum;
            if (line >= lineCount) {
                line = lineCount - 1;
                col = lines.get(line).length();
            } else {
                col = Math.min(col, lines.get(line).length());
            }
            return new ContentPosition(line, col);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the total character count of the document, counting every newline as 1 character.
     * This is the flat offset of the position just past the last character.
     */
    public int totalLength() {
        lock.readLock().lock();
        try {
            return (int) (bitQuery(lines.size()) - 1);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Materialises the entire document as a {@link String}.
     *
     * <p>Avoid calling this in hot paths (per-keystroke) — it allocates a full copy of the
     * document. Prefer line-by-line reads via {@link #getLineCopy(int)} or {@link #getLineChars}.
     * This method is intentionally only called for file-save operations and full-document
     * search operations, neither of which is per-keystroke.
     */
    public String getText() {
        lock.readLock().lock();
        try {
            int total = (int) (bitQuery(lines.size()) - 1);
            StringBuilder sb = new StringBuilder(Math.max(0, total));
            for (int i = 0; i < lines.size(); i++) {
                ContentLine cl = lines.get(i);
                sb.append(cl.toLineString());
                if (i < lines.size() - 1) sb.append('\n');
            }
            return sb.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Efficiently extracts a sub-region of the document without materializing the whole text.
     * Used heavily by the IME InputConnection to retrieve text context.
     */
    public String getSubstring(int startFlat, int endFlat) {
        if (startFlat >= endFlat) return "";
        lock.readLock().lock();
        try {
            int total = (int) (bitQuery(lines.size()) - 1);
            if (startFlat < 0) startFlat = 0;
            if (endFlat > total) endFlat = total;
            if (startFlat >= endFlat) return "";

            ContentPosition startPos = positionAt(startFlat);
            ContentPosition endPos = positionAt(endFlat);

            int len = endFlat - startFlat;
            StringBuilder sb = new StringBuilder(len);

            if (startPos.line == endPos.line) {
                ContentLine cl = lines.get(startPos.line);
                sb.append(cl.buffer, startPos.column, endPos.column - startPos.column);
            } else {
                ContentLine cl = lines.get(startPos.line);
                char[] tmp = new char[cl.length() - startPos.column];
                cl.getChars(startPos.column, cl.length(), tmp, 0);
                sb.append(tmp);
                sb.append('\n');
                for (int i = startPos.line + 1; i < endPos.line; i++) {
                    ContentLine line = lines.get(i);
                    sb.append(line.toLineString());
                    sb.append('\n');
                }
                ContentLine endCl = lines.get(endPos.line);
                if (endPos.column > 0) {
                    sb.append(endCl.buffer, 0, endPos.column);
                }
            }
            return sb.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the longest line length (in characters) across the entire document.
     * Used by the renderer to compute the horizontal scroll extent.
     *
     * <p>This is computed lazily on demand; the result is O(n) in the number of lines.
     * The renderer caches this and invalidates it via the {@link ContentChangeListener}.
     */
    public int longestLineLength() {
        lock.readLock().lock();
        try {
            int max = 0;
            for (int i = 0; i < lines.size(); i++) {
                int len = lines.get(i).length();
                if (len > max) max = len;
            }
            return max;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the current version stamp. Incremented on every mutation.
     * Background threads compare against this to detect staleness without acquiring any lock.
     */
    public long getVersion() {
        return version.get();
    }

    /**
     * Acquires the read lock. Background threads must call this before accessing internal state
     * and must call {@link #releaseReadLock()} in a {@code finally} block immediately after
     * copying the data they need. <em>Never</em> hold the read lock across a thread-hop.
     */
    public void acquireReadLock() {
        lock.readLock().lock();
    }

    // ── Internal mutation helpers (called under the write lock) ───────────────

    /**
     * Releases a previously acquired read lock.
     */
    public void releaseReadLock() {
        lock.readLock().unlock();
    }

    private void insertInternal(int line, int column, CharSequence text) {
        // Find the first '\n' in the inserted text
        int firstNewline = indexOfNewline(text, 0);

        if (firstNewline < 0) {
            // No newline — simple single-line insert
            char[] src = toCharArray(text);
            lines.get(line).insert(column, src, 0, src.length);
            bitAdd(line, src.length);
        } else {
            // There is at least one newline — we need to split the current line and insert new lines
            ContentLine currentLine = lines.get(line);

            // Capture the tail of the current line (what comes after `column`)
            int tailLen = currentLine.length() - column;
            char[] tail = new char[tailLen];
            if (tailLen > 0) currentLine.getChars(column, column + tailLen, tail, 0);

            // Truncate current line to `column`
            currentLine.delete(column, currentLine.length());
            currentLine.tokenizerEndState = 0;

            // Insert the part of text before the first '\n' into the current line
            char[] firstPart = toCharArray(text.subSequence(0, firstNewline));
            currentLine.insert(column, firstPart, 0, firstPart.length);

            // Process each subsequent segment between newlines
            int insertPos = line + 1;
            int segStart = firstNewline + 1;
            int nextNewline;
            while ((nextNewline = indexOfNewline(text, segStart)) >= 0) {
                ContentLine newLine = new ContentLine(text.subSequence(segStart, nextNewline));
                lines.add(insertPos, newLine);
                insertPos++;
                segStart = nextNewline + 1;
            }

            // Last segment + the original tail
            ContentLine lastNew = new ContentLine(text.subSequence(segStart, text.length()));
            if (tailLen > 0) lastNew.appendFrom(new ContentLine(tail, 0, tailLen), 0);
            lines.add(insertPos, lastNew);

            rebuildBit();
        }
    }

    private void deleteInternal(int startLine, int startColumn, int endLine, int endColumn) {
        if (startLine == endLine) {
            lines.get(startLine).delete(startColumn, endColumn);
            bitAdd(startLine, -(endColumn - startColumn));
        } else {
            // Merge startLine and endLine: keep [0, startColumn) from startLine and
            // [endColumn, ...) from endLine.
            ContentLine first = lines.get(startLine);
            ContentLine last = lines.get(endLine);

            // Truncate first line
            first.delete(startColumn, first.length());
            first.tokenizerEndState = 0;

            // Append the tail of endLine to first
            if (endColumn < last.length()) {
                first.appendFrom(last, endColumn);
            }

            // Remove all intermediate lines and the end line
            lines.subList(startLine + 1, endLine + 1).clear();
            rebuildBit();
        }
    }

    private void bitAdd(int index, long delta) {
        for (int i = index + 1; i < bit.length; i += i & -i) {
            bit[i] += delta;
        }
    }

    private long bitQuery(int count) {
        long sum = 0;
        for (int i = count; i > 0; i -= i & -i) {
            sum += bit[i];
        }
        return sum;
    }

    // ── Listener notification ─────────────────────────────────────────────────

    private void rebuildBit() {
        int n = lines.size();
        if (bit == null || bitCapacity < n + 1) {
            bitCapacity = Math.max(n + 1, (bit == null ? 64 : bit.length) * 2);
            bit = new long[bitCapacity];
        } else {
            java.util.Arrays.fill(bit, 0, n + 1, 0L);
        }
        for (int i = 0; i < n; i++) {
            bit[i + 1] += lines.get(i).length() + 1;
            int j = (i + 1) + ((i + 1) & -(i + 1));
            if (j <= n) {
                bit[j] += bit[i + 1];
            }
        }
    }

    private void notifyInsert(int line, int col, CharSequence text) {
        for (ContentChangeListener l : listeners) l.onInsert(line, col, text);
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    private void notifyDelete(int startLine, int startCol, int endLine, int endCol) {
        for (ContentChangeListener l : listeners) l.onDelete(startLine, startCol, endLine, endCol);
    }

    public static final class LoadedLines {
        public final int longestLineLength;
        private final ArrayList<ContentLine> lines;
        private final long[] bit;
        private final int bitCapacity;

        private LoadedLines(ArrayList<ContentLine> lines, long[] bit, int bitCapacity, int longestLineLength) {
            this.lines = lines;
            this.bit = bit;
            this.bitCapacity = bitCapacity;
            this.longestLineLength = longestLineLength;
        }
    }
}
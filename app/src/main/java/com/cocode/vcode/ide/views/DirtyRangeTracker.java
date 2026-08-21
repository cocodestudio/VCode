package com.cocode.vcode.ide.views;

/**
 * Tracks the bounding character offsets of modified text regions between incremental highlighting passes.
 * Automatically expands and shifts range boundaries as successive edits occur.
 */
public class DirtyRangeTracker {
    public int start = -1;
    public int end = -1;

    /**
     * Records a text modification and updates the tracked dirty span.
     *
     * @param editStart    The starting offset of the edit.
     * @param beforeLength The character length of the replaced text.
     * @param afterLength  The character length of the newly inserted text.
     */
    public void addEdit(int editStart, int beforeLength, int afterLength) {
        if (start == -1) {
            start = editStart;
            end = editStart + afterLength;
        } else {
            int diff = afterLength - beforeLength;
            if (editStart <= end) {
                end += diff;
            }
            if (editStart < start) {
                start = editStart;
            }
            if (editStart + afterLength > end) {
                end = editStart + afterLength;
            }
        }
    }

    public void reset() {
        start = -1;
        end = -1;
    }

    public boolean isDirty() {
        return start != -1;
    }
}

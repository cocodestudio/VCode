package com.cocode.vcode.ide.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.git.model.FileStatus;

/**
 * Small circular status dot indicating the Git state of a file in the file tree or tab bar.
 * Renders distinct colors for modified, untracked, staged, deleted, and conflicted states.
 */
public class GitStatusBadge extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private State state = State.NONE;
    private int dotColor = 0;

    public GitStatusBadge(Context context) {
        super(context);
        init();
    }

    public GitStatusBadge(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (state == State.NONE || dotColor == 0) return;

        paint.setColor(dotColor);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy);

        canvas.drawCircle(cx, cy, r, paint);
    }

    /**
     * Updates the badge color and visibility based on the provided Git status state.
     */
    public void setState(State state) {
        this.state = state;
        switch (state) {
            case CLEAN:
            case STAGED:
                dotColor = ContextCompat.getColor(getContext(), R.color.vcode_git_staged_color);
                break;
            case MODIFIED:
                dotColor = ContextCompat.getColor(getContext(), R.color.vcode_git_modified_color);
                break;
            case UNTRACKED:
                dotColor = ContextCompat.getColor(getContext(), R.color.vcode_git_untracked_color);
                break;
            case DELETED:
                dotColor = ContextCompat.getColor(getContext(), R.color.vcode_git_deleted_color);
                break;
            case CONFLICTED:
                dotColor = ContextCompat.getColor(getContext(), R.color.vcode_git_conflicted_color);
                break;
            default:
                dotColor = 0;
                break;
        }
        setVisibility(state == State.NONE ? GONE : VISIBLE);
        invalidate();
    }

    public void setStatus(FileStatus.Type type) {
        setFromFileStatusType(type);
    }

    /**
     * Maps a FileStatus.Type to the corresponding badge state.
     */
    public void setFromFileStatusType(FileStatus.Type type) {
        if (type == null) {
            setState(State.NONE);
            return;
        }

        switch (type) {
            case STAGED_ADDED:
            case STAGED_MODIFIED:
            case STAGED_DELETED:
                setState(State.STAGED);
                break;
            case UNSTAGED_MODIFIED:
                setState(State.MODIFIED);
                break;
            case UNSTAGED_DELETED:
                setState(State.DELETED);
                break;
            case UNTRACKED:
                setState(State.UNTRACKED);
                break;
            case CONFLICTED:
                setState(State.CONFLICTED);
                break;
            default:
                setState(State.NONE);
                break;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = (int) (8 * getContext().getResources().getDisplayMetrics().density);
        setMeasuredDimension(size, size);
    }

    public enum State {NONE, CLEAN, MODIFIED, UNTRACKED, STAGED, DELETED, CONFLICTED}
}
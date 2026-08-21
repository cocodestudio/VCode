package com.cocode.vcode.ide.utils;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * {@link RecyclerView.ItemDecoration} that applies explicit top, bottom, and inter-item margins to list items.
 */
public class MarginItemDecorator extends RecyclerView.ItemDecoration {
    private final int topMargin;
    private final int bottomMargin;
    private final int betweenMargin;

    public MarginItemDecorator(int topMargin, int bottomMargin, int betweenMargin) {
        this.topMargin = topMargin;
        this.bottomMargin = bottomMargin;
        this.betweenMargin = betweenMargin;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION) {
            return;
        }

        int itemCount = state.getItemCount();

        if (position == 0) {
            outRect.set(0, topMargin, 0, betweenMargin);
        } else if (position == itemCount - 1) {
            outRect.set(0, 0, 0, bottomMargin);
        } else {
            outRect.set(0, 0, 0, betweenMargin);
        }
    }
}
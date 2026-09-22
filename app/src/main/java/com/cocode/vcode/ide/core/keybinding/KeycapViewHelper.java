package com.cocode.vcode.ide.core.keybinding;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.utils.FontManager;

import java.util.List;

/**
 * Reusable UI helper that populates ViewGroup containers with styled keycap badges.
 */
public final class KeycapViewHelper {

    private KeycapViewHelper() {}

    /**
     * Populates a ViewGroup with styled keycap badges for the given KeyStroke.
     * Reuses existing child views when available to avoid view creation churn.
     */
    public static void populateKeycaps(@NonNull ViewGroup container, @Nullable KeyStroke stroke) {
        Context context = container.getContext();
        if (stroke == null) {
            TextView unassignedView = getOrCreateChild(container, 0, context);
            unassignedView.setText(R.string.vcode_key_unassigned);
            unassignedView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            unassignedView.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_hint));
            unassignedView.setTypeface(FontManager.getInstance().getUiFont(context), Typeface.ITALIC);
            unassignedView.setBackground(null);
            unassignedView.setPadding(0, 0, 0, 0);
            unassignedView.setGravity(Gravity.CENTER_VERTICAL);
            unassignedView.setVisibility(View.VISIBLE);
            trimExtraChildren(container, 1);
            return;
        }

        List<String> parts = stroke.toDisplayParts();
        int viewIndex = 0;
        for (int i = 0; i < parts.size(); i++) {
            TextView keycap = getOrCreateChild(container, viewIndex++, context);
            keycap.setText(parts.get(i));
            keycap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            keycap.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_primary));
            keycap.setTypeface(FontManager.getInstance().getUiSemiBold(context));
            keycap.setBackgroundResource(R.drawable.vcode_bg_keycap);
            keycap.setGravity(Gravity.CENTER);
            keycap.setVisibility(View.VISIBLE);

            if (i < parts.size() - 1) {
                TextView separator = getOrCreateChild(container, viewIndex++, context);
                separator.setText("+");
                separator.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                separator.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_hint));
                separator.setTypeface(FontManager.getInstance().getUiFont(context));
                separator.setBackground(null);
                separator.setPadding(dpToPx(context, 4), 0, dpToPx(context, 4), 0);
                separator.setGravity(Gravity.CENTER);
                separator.setVisibility(View.VISIBLE);
            }
        }
        trimExtraChildren(container, viewIndex);
    }

    private static TextView getOrCreateChild(@NonNull ViewGroup container, int index, @NonNull Context context) {
        if (index < container.getChildCount()) {
            View child = container.getChildAt(index);
            if (child instanceof TextView) {
                return (TextView) child;
            }
        }
        TextView tv = new TextView(context);
        container.addView(tv);
        return tv;
    }

    private static void trimExtraChildren(@NonNull ViewGroup container, int keepCount) {
        int count = container.getChildCount();
        if (count > keepCount) {
            container.removeViews(keepCount, count - keepCount);
        }
    }

    @NonNull
    public static TextView createKeycapBadge(@NonNull Context context, @NonNull String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_primary));
        tv.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        tv.setBackgroundResource(R.drawable.vcode_bg_keycap);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    private static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}

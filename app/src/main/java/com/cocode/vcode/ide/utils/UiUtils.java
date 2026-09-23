package com.cocode.vcode.ide.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.views.VCodeSnackbar;

/**
 * UI utility methods for density conversions, soft keyboard management, Snackbars, window insets, and view styling.
 */
public class UiUtils {

    private UiUtils() {
    }

    /**
     * Converts density-independent pixels (dp) to device pixels (px).
     */
    public static int dpToPx(Context ctx, float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                ctx.getResources().getDisplayMetrics()));
    }

    /**
     * Hides the soft input keyboard for the given activity.
     */
    public static void hideKeyboard(Activity activity) {
        if (activity == null) return;
        View view = activity.getCurrentFocus();
        if (view == null) view = new View(activity);
        InputMethodManager imm = (InputMethodManager)
                activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Shows the soft input keyboard for the given focused view.
     */
    public static void showKeyboard(View view) {
        if (view == null) return;
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager)
                view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /**
     * Displays a standard custom VCodeSnackbar message.
     */
    public static void showSnackbar(View anchor, String message, int duration) {
        if (anchor == null || message == null) return;
        com.cocode.vcode.ide.views.VCodeSnackbar.make(anchor, message, duration).show();
    }

    /**
     * Displays an error custom VCodeSnackbar with error styling.
     */
    public static void showErrorSnackbar(View anchor, String message) {
        if (anchor == null || message == null) return;
        com.cocode.vcode.ide.views.VCodeSnackbar.error(anchor, message).show();
    }

    /**
     * Displays a success custom VCodeSnackbar with success styling.
     */
    public static void showSuccessSnackbar(View anchor, String message) {
        if (anchor == null || message == null) return;
        com.cocode.vcode.ide.views.VCodeSnackbar.success(anchor, message).show();
    }

    /**
     * Displays a warning custom VCodeSnackbar with warning styling.
     */
    public static void showWarningSnackbar(View anchor, String message) {
        if (anchor == null || message == null) return;
        com.cocode.vcode.ide.views.VCodeSnackbar.warning(anchor, message).show();
    }

    /**
     * Applies system bar insets (status bar and navigation bar) as padding to the given view.
     */
    public static void applySystemBarInsets(View view) {
        if (view == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    /**
     * Applies system and IME bar insets as padding to main content and drawer views.
     */
    public static void applySystemBarInsets(View drawerLayout, View mainContent, View drawerContainer) {
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (v, insets) -> {
            Insets systemAndImeBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()
            );

            mainContent.setPadding(
                    systemAndImeBars.left,
                    systemAndImeBars.top,
                    systemAndImeBars.right,
                    systemAndImeBars.bottom
            );
            drawerContainer.setPadding(
                    systemAndImeBars.left,
                    systemAndImeBars.top,
                    systemAndImeBars.right,
                    systemAndImeBars.bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /**
     * Applies a rounded rectangle background shape with the specified corner radius and color to a view.
     *
     * @param view   the target view
     * @param radius corner radius in pixels
     * @param color  fill color
     */
    public static void setViewRounded(View view, float radius, int color) {
        setViewRounded(view, radius, color, 0, 0);
    }

    /**
     * Applies a rounded rectangle background shape with the specified corner radius, fill color,
     * and optional outline stroke to a view, preserving existing padding.
     *
     * @param view        the target view
     * @param radius      corner radius in pixels
     * @param color       fill color
     * @param strokeWidth stroke width in pixels (0 for no stroke)
     * @param strokeColor stroke color
     */
    public static void setViewRounded(View view, float radius, int color, int strokeWidth, int strokeColor) {
        if (view == null) return;
        int pl = view.getPaddingLeft();
        int pt = view.getPaddingTop();
        int pr = view.getPaddingRight();
        int pb = view.getPaddingBottom();

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(radius);
        shape.setColor(color);
        if (strokeWidth > 0) {
            shape.setStroke(strokeWidth, strokeColor);
        }
        view.setBackground(shape);
        view.setClipToOutline(true);
        view.setPadding(pl, pt, pr, pb);
    }

    /**
     * Applies a rounded input field background with distinct resting and focused states,
     * highlighting the outline stroke with the active accent color upon focus.
     *
     * @param view               the target view (EditText)
     * @param radius             corner radius in pixels
     * @param bgColor            fill color
     * @param strokeWidth        stroke width in pixels
     * @param normalStrokeColor  resting stroke color
     * @param focusedStrokeColor focused stroke color
     */
    public static void setInputRounded(View view, float radius, int bgColor, int strokeWidth, int normalStrokeColor, int focusedStrokeColor) {
        if (view == null) return;
        int pl = view.getPaddingLeft();
        int pt = view.getPaddingTop();
        int pr = view.getPaddingRight();
        int pb = view.getPaddingBottom();

        GradientDrawable normalShape = new GradientDrawable();
        normalShape.setShape(GradientDrawable.RECTANGLE);
        normalShape.setCornerRadius(radius);
        normalShape.setColor(bgColor);
        if (strokeWidth > 0) {
            normalShape.setStroke(strokeWidth, normalStrokeColor);
        }

        GradientDrawable focusedShape = new GradientDrawable();
        focusedShape.setShape(GradientDrawable.RECTANGLE);
        focusedShape.setCornerRadius(radius);
        focusedShape.setColor(bgColor);
        if (strokeWidth > 0) {
            int focusedWidth = Math.max(strokeWidth, Math.round(strokeWidth * 1.5f));
            focusedShape.setStroke(focusedWidth, focusedStrokeColor);
        }

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focusedShape);
        states.addState(new int[]{}, normalShape);

        view.setBackground(states);
        view.setClipToOutline(true);
        view.setPadding(pl, pt, pr, pb);
    }

    /**
     * Configures an EditText to use the application's primary accent color for its
     * cursor, selection handles, and highlight color.
     *
     * @param editText the target EditText
     */
    public static void applyAccentToEditText(EditText editText) {
        if (editText == null) return;
        Context context = editText.getContext();
        int selectionColor = ContextCompat.getColor(context, R.color.vcode_selection_color);
        editText.setHighlightColor(selectionColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Drawable cursor = ContextCompat.getDrawable(context, R.drawable.vcode_cursor_drawable);
            if (cursor != null) editText.setTextCursorDrawable(cursor);

            Drawable handleMiddle = ContextCompat.getDrawable(context, R.drawable.vcode_text_select_handle_middle);
            if (handleMiddle != null) editText.setTextSelectHandle(handleMiddle);

            Drawable handleLeft = ContextCompat.getDrawable(context, R.drawable.vcode_text_select_handle_left);
            if (handleLeft != null) editText.setTextSelectHandleLeft(handleLeft);

            Drawable handleRight = ContextCompat.getDrawable(context, R.drawable.vcode_text_select_handle_right);
            if (handleRight != null) editText.setTextSelectHandleRight(handleRight);
        }
    }
}
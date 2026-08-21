package com.cocode.vcode.ide.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.cocode.vcode.ide.R;
import com.google.android.material.snackbar.Snackbar;

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
     * Displays a standard Snackbar message.
     */
    public static void showSnackbar(View anchor, String message, int duration) {
        if (anchor == null || message == null) return;
        Snackbar.make(anchor, message, duration).show();
    }

    /**
     * Displays an error Snackbar with the error accent color.
     */
    public static void showErrorSnackbar(View anchor, String message) {
        if (anchor == null || message == null) return;
        Snackbar snackbar = Snackbar.make(anchor, message, Snackbar.LENGTH_LONG);
        snackbar.getView().setBackgroundColor(
            ContextCompat.getColor(anchor.getContext(), R.color.vcode_accent_error));
        snackbar.show();
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
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(radius);
        shape.setColor(color);
        view.setBackground(shape);
        view.setClipToOutline(true);
    }
}
package com.cocode.vcode.ide.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.LayoutSnackbarBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

/**
 * Custom Material 3 snackbar designed specifically for the VCode IDE.
 *
 * <p>Features:
 * <ul>
 *   <li>Native ViewBinding with {@link LayoutSnackbarBinding}.</li>
 *   <li>Integrated {@link FontManager} typography (Sora-Medium for message, Sora-SemiBold for action).</li>
 *   <li>Semantic color and iconography badges for {@link Type#SUCCESS}, {@link Type#ERROR},
 *       {@link Type#WARNING}, {@link Type#INFO}, and neutral {@link Type#NORMAL}.</li>
 *   <li>Modern floating card design: soft pastel tinted background for error/warning, 12dp rounded corners,
 *       subtle 2dp elevation shadow, and crisp 1dp outline border.</li>
 *   <li>Spacious 16dp horizontal and 14dp vertical padding with 20dp status icons.</li>
 *   <li>Fluent API with support for actions, anchors, dismiss triggers, and callbacks.</li>
 *   <li>Retains 100% CoordinatorLayout swipe-to-dismiss and queuing behavior via underlying {@link Snackbar}.</li>
 * </ul>
 */
public final class VCodeSnackbar {

    public static final int LENGTH_SHORT = Snackbar.LENGTH_SHORT;
    public static final int LENGTH_LONG = Snackbar.LENGTH_LONG;
    public static final int LENGTH_INDEFINITE = Snackbar.LENGTH_INDEFINITE;

    public enum Type {
        NORMAL,
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }

    private final Snackbar baseSnackbar;
    private final LayoutSnackbarBinding binding;
    private final Context context;
    private Type type = Type.NORMAL;
    private boolean hasCustomActionColor = false;

    private VCodeSnackbar(@NonNull View view, @NonNull CharSequence message, int duration) {
        this.context = view.getContext();
        this.baseSnackbar = Snackbar.make(view, "", duration);

        Snackbar.SnackbarLayout layout = (Snackbar.SnackbarLayout) baseSnackbar.getView();
        layout.setBackgroundColor(Color.TRANSPARENT);
        layout.setPadding(0, 0, 0, 0);
        layout.setElevation(0f);
        ViewCompat.setElevation(layout, 0f);

        ViewGroup.LayoutParams params = layout.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
            marginParams.leftMargin = 0;
            marginParams.rightMargin = 0;
            marginParams.bottomMargin = 0;
            layout.setLayoutParams(marginParams);
        }

        View defaultText = layout.findViewById(com.google.android.material.R.id.snackbar_text);
        if (defaultText != null) defaultText.setVisibility(View.GONE);
        View defaultAction = layout.findViewById(com.google.android.material.R.id.snackbar_action);
        if (defaultAction != null) defaultAction.setVisibility(View.GONE);

        this.binding = LayoutSnackbarBinding.inflate(LayoutInflater.from(context), layout, false);
        layout.removeAllViews();
        layout.addView(binding.getRoot(), 0);

        // Apply FontManager typography
        FontManager fm = FontManager.getInstance();
        binding.tvSnackbarMessage.setTypeface(fm.getUiMedium(context));
        binding.btnSnackbarAction.setTypeface(fm.getUiSemiBold(context));

        binding.tvSnackbarMessage.setText(message);
        setType(Type.NORMAL);

        binding.btnSnackbarDismiss.setOnClickListener(v -> dismiss());
    }

    /**
     * Creates a custom {@link VCodeSnackbar} with the specified message and duration.
     */
    public static VCodeSnackbar make(@NonNull View view, @NonNull CharSequence message, int duration) {
        return new VCodeSnackbar(view, message, duration);
    }

    /**
     * Creates a custom {@link VCodeSnackbar} with a string resource ID and duration.
     */
    public static VCodeSnackbar make(@NonNull View view, @StringRes int messageResId, int duration) {
        return make(view, view.getContext().getString(messageResId), duration);
    }

    /**
     * Creates an informational {@link VCodeSnackbar} with primary accent iconography.
     */
    public static VCodeSnackbar info(@NonNull View view, @NonNull CharSequence message) {
        return make(view, message, LENGTH_LONG).setType(Type.INFO);
    }

    public static VCodeSnackbar info(@NonNull View view, @StringRes int messageResId) {
        return make(view, messageResId, LENGTH_LONG).setType(Type.INFO);
    }

    /**
     * Creates a success {@link VCodeSnackbar} with green checkmark iconography.
     */
    public static VCodeSnackbar success(@NonNull View view, @NonNull CharSequence message) {
        return make(view, message, LENGTH_LONG).setType(Type.SUCCESS);
    }

    public static VCodeSnackbar success(@NonNull View view, @StringRes int messageResId) {
        return make(view, messageResId, LENGTH_LONG).setType(Type.SUCCESS);
    }

    /**
     * Creates a warning {@link VCodeSnackbar} with amber warning iconography.
     */
    public static VCodeSnackbar warning(@NonNull View view, @NonNull CharSequence message) {
        return make(view, message, LENGTH_LONG).setType(Type.WARNING);
    }

    public static VCodeSnackbar warning(@NonNull View view, @StringRes int messageResId) {
        return make(view, messageResId, LENGTH_LONG).setType(Type.WARNING);
    }

    /**
     * Creates an error {@link VCodeSnackbar} with red alert iconography and soft tinted background.
     */
    public static VCodeSnackbar error(@NonNull View view, @NonNull CharSequence message) {
        return make(view, message, LENGTH_LONG).setType(Type.ERROR);
    }

    public static VCodeSnackbar error(@NonNull View view, @StringRes int messageResId) {
        return make(view, messageResId, LENGTH_LONG).setType(Type.ERROR);
    }

    /**
     * Configures the semantic type, adjusting card background, stroke, icon badge, and status colors.
     */
    public VCodeSnackbar setType(Type type) {
        this.type = type != null ? type : Type.NORMAL;
        int bgColor;
        int fgColor;
        int iconColor;
        int strokeColor;
        int iconRes = R.drawable.ic_circle_check;
        boolean showIcon = true;

        switch (this.type) {
            case SUCCESS:
                bgColor = ContextCompat.getColor(context, R.color.vcode_bg_surface);
                fgColor = ContextCompat.getColor(context, R.color.vcode_text_primary);
                iconColor = ContextCompat.getColor(context, R.color.vcode_accent_success);
                strokeColor = ContextCompat.getColor(context, R.color.vcode_outline_variant);
                iconRes = R.drawable.ic_circle_check;
                break;
            case ERROR:
                bgColor = ContextCompat.getColor(context, R.color.vcode_diff_removed_bg);
                fgColor = ContextCompat.getColor(context, R.color.vcode_accent_error);
                iconColor = fgColor;
                strokeColor = ColorUtils.setAlphaComponent(fgColor, 51); // 20% alpha border
                iconRes = R.drawable.ic_error;
                break;
            case WARNING:
                bgColor = ContextCompat.getColor(context, R.color.vcode_warning_tint);
                fgColor = ContextCompat.getColor(context, R.color.vcode_accent_warning);
                iconColor = fgColor;
                strokeColor = ColorUtils.setAlphaComponent(fgColor, 51); // 20% alpha border
                iconRes = R.drawable.ic_triangle_exclamation;
                break;
            case INFO:
                bgColor = ContextCompat.getColor(context, R.color.vcode_bg_surface);
                fgColor = ContextCompat.getColor(context, R.color.vcode_text_primary);
                iconColor = ContextCompat.getColor(context, R.color.vcode_text_secondary);
                strokeColor = ContextCompat.getColor(context, R.color.vcode_outline_variant);
                iconRes = R.drawable.ic_info;
                break;
            case NORMAL:
            default:
                bgColor = ContextCompat.getColor(context, R.color.vcode_bg_surface);
                fgColor = ContextCompat.getColor(context, R.color.vcode_text_primary);
                iconColor = ContextCompat.getColor(context, R.color.vcode_text_secondary);
                strokeColor = ContextCompat.getColor(context, R.color.vcode_outline_variant);
                showIcon = false;
                break;
        }

        binding.cardSnackbar.setCardBackgroundColor(bgColor);
        binding.cardSnackbar.setStrokeColor(strokeColor);
        binding.cardSnackbar.setCardElevation(UiUtils.dpToPx(context, 2));
        binding.cardSnackbar.setMaxCardElevation(UiUtils.dpToPx(context, 2));

        binding.tvSnackbarMessage.setTextColor(fgColor);

        if (showIcon) {
            binding.ivSnackbarIcon.setVisibility(View.VISIBLE);
            binding.ivSnackbarIcon.setImageResource(iconRes);
            binding.ivSnackbarIcon.setImageTintList(ColorStateList.valueOf(iconColor));
        } else {
            binding.ivSnackbarIcon.setVisibility(View.GONE);
        }

        if (this.type == Type.ERROR || this.type == Type.WARNING) {
            binding.btnSnackbarDismiss.setImageTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(fgColor, 180)));
        } else {
            binding.btnSnackbarDismiss.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_text_secondary)));
        }

        if (!hasCustomActionColor && binding.btnSnackbarAction.getVisibility() == View.VISIBLE) {
            int actionColor = (this.type == Type.ERROR || this.type == Type.WARNING)
                    ? fgColor
                    : ContextCompat.getColor(context, R.color.vcode_accent_primary);
            binding.btnSnackbarAction.setTextColor(actionColor);
        }

        return this;
    }

    /**
     * Configures an interactive action button with callback listener.
     */
    public VCodeSnackbar setAction(@NonNull CharSequence text, @Nullable View.OnClickListener listener) {
        binding.btnSnackbarAction.setText(text);
        binding.btnSnackbarAction.setVisibility(View.VISIBLE);
        binding.btnSnackbarAction.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(v);
            }
            dismiss();
        });
        if (!hasCustomActionColor) {
            int actionColor = (type == Type.ERROR || type == Type.WARNING)
                    ? binding.tvSnackbarMessage.getCurrentTextColor()
                    : ContextCompat.getColor(context, R.color.vcode_accent_primary);
            binding.btnSnackbarAction.setTextColor(actionColor);
        }
        return this;
    }

    public VCodeSnackbar setAction(@StringRes int textResId, @Nullable View.OnClickListener listener) {
        return setAction(context.getString(textResId), listener);
    }

    /**
     * Sets custom text color on the action button.
     */
    public VCodeSnackbar setActionTextColor(@ColorInt int color) {
        this.hasCustomActionColor = true;
        binding.btnSnackbarAction.setTextColor(color);
        binding.btnSnackbarAction.setStrokeColor(ColorStateList.valueOf(color));
        return this;
    }

    public VCodeSnackbar setActionTextColor(ColorStateList colors) {
        this.hasCustomActionColor = true;
        binding.btnSnackbarAction.setTextColor(colors);
        binding.btnSnackbarAction.setStrokeColor(colors);
        return this;
    }

    /**
     * Sets a custom stroke color on the snackbar card.
     */
    public VCodeSnackbar setStrokeColor(@ColorInt int color) {
        binding.cardSnackbar.setStrokeColor(color);
        return this;
    }

    /**
     * Sets custom stroke width in pixels on the snackbar card.
     */
    public VCodeSnackbar setStrokeWidth(int widthPx) {
        binding.cardSnackbar.setStrokeWidth(widthPx);
        return this;
    }

    /**
     * Overrides the leading icon drawable.
     */
    public VCodeSnackbar setIcon(@DrawableRes int iconRes) {
        binding.ivSnackbarIcon.setImageResource(iconRes);
        binding.ivSnackbarIcon.setVisibility(View.VISIBLE);
        return this;
    }

    /**
     * Overrides the leading icon color tint.
     */
    public VCodeSnackbar setIconTint(@ColorInt int color) {
        binding.ivSnackbarIcon.setImageTintList(ColorStateList.valueOf(color));
        return this;
    }

    /**
     * Enables or disables a dedicated close/dismiss button on the trailing end.
     */
    public VCodeSnackbar setDismissible(boolean dismissible) {
        binding.btnSnackbarDismiss.setVisibility(dismissible ? View.VISIBLE : View.GONE);
        return this;
    }

    /**
     * Sets the anchor view above which the snackbar will float.
     */
    public VCodeSnackbar setAnchorView(@Nullable View anchorView) {
        baseSnackbar.setAnchorView(anchorView);
        return this;
    }

    public VCodeSnackbar setAnchorView(@IdRes int anchorViewId) {
        baseSnackbar.setAnchorView(anchorViewId);
        return this;
    }

    /**
     * Adds a callback listener for show and dismiss lifecycle events.
     */
    public VCodeSnackbar addCallback(@Nullable BaseTransientBottomBar.BaseCallback<Snackbar> callback) {
        if (callback != null) {
            baseSnackbar.addCallback(callback);
        }
        return this;
    }

    /**
     * Displays the custom snackbar.
     */
    public void show() {
        baseSnackbar.show();
    }

    /**
     * Dismisses the snackbar.
     */
    public void dismiss() {
        baseSnackbar.dismiss();
    }

    /**
     * Returns whether the snackbar is currently showing.
     */
    public boolean isShown() {
        return baseSnackbar.isShown();
    }

    /**
     * Returns the underlying Material {@link Snackbar} instance.
     */
    public Snackbar getSnackbar() {
        return baseSnackbar;
    }

    /**
     * Returns the ViewBinding instance for the snackbar layout.
     */
    public LayoutSnackbarBinding getBinding() {
        return binding;
    }

    /**
     * Returns the current {@link Type}.
     */
    public Type getType() {
        return type;
    }
}

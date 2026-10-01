package com.cocode.vcode.ide.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.ItemCustomPopupBinding;
import com.cocode.vcode.ide.databinding.LayoutCustomPopupBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

/**
 * Custom popup menu with Material 3 styling, icon support,
 * and boundary-safe positioning relative to the anchor view.
 */
public class CustomPopupMenu {

    private final Context context;
    private final View anchor;
    private final LayoutCustomPopupBinding binding;
    private final PopupWindow popupWindow;
    private int customWidth = -1;
    private boolean alignRight = false;

    public CustomPopupMenu(@NonNull Context context, @NonNull View anchor) {
        this.context = context;
        this.anchor = anchor;
        this.binding = LayoutCustomPopupBinding.inflate(LayoutInflater.from(context));

        this.binding.getRoot().setCardBackgroundColor(
                ContextCompat.getColor(context, R.color.vcode_bg_surface));

        this.popupWindow = new PopupWindow(
                binding.getRoot(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true /* focusable so outside touch dismisses */
        );
        this.popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        this.popupWindow.setElevation(UiUtils.dpToPx(context, 8));
        this.popupWindow.setAnimationStyle(R.style.VCodePopupMenuAnimation);
    }

    public CustomPopupMenu setCardBackgroundColor(@Nullable ColorStateList colorStateList) {
        if (colorStateList != null) {
            binding.getRoot().setCardBackgroundColor(colorStateList);
        }
        return this;
    }

    public CustomPopupMenu setCardBackgroundColor(@ColorInt int colorInt) {
        binding.getRoot().setCardBackgroundColor(colorInt);
        return this;
    }

    public CustomPopupMenu setWidth(int widthPx) {
        this.customWidth = widthPx;
        return this;
    }

    public CustomPopupMenu setAlignRight(boolean alignRight) {
        this.alignRight = alignRight;
        return this;
    }

    public CustomPopupMenu setOnDismissListener(@Nullable PopupWindow.OnDismissListener listener) {
        popupWindow.setOnDismissListener(listener);
        return this;
    }

    public CustomPopupMenu addItem(@DrawableRes int iconRes, CharSequence title, Runnable action) {
        return addItem(iconRes, (Integer) null, 0f, title, action);
    }

    public CustomPopupMenu addItem(@DrawableRes int iconRes, @ColorRes int iconTintRes, CharSequence title, Runnable action) {
        Integer tint = iconTintRes != 0 ? ContextCompat.getColor(context, iconTintRes) : null;
        return addItem(iconRes, tint, 0f, title, action);
    }

    public CustomPopupMenu addItem(@DrawableRes int iconRes, @Nullable Integer iconTintColor, float rotationDegrees,
                                   CharSequence title, Runnable action) {
        ItemCustomPopupBinding itemBinding = ItemCustomPopupBinding.inflate(
                LayoutInflater.from(context), binding.popupContainer, false);

        if (iconRes != 0) {
            itemBinding.ivIcon.setImageResource(iconRes);
            itemBinding.ivIcon.setVisibility(View.VISIBLE);
            if (iconTintColor != null) {
                itemBinding.ivIcon.setColorFilter(iconTintColor);
            }
            if (rotationDegrees != 0f) {
                itemBinding.ivIcon.setRotation(rotationDegrees);
            }
        } else {
            itemBinding.ivIcon.setVisibility(View.GONE);
        }

        itemBinding.tvTitle.setText(title);
        itemBinding.tvTitle.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall);
        itemBinding.tvTitle.setTypeface(FontManager.getInstance().getUiMedium(context));

        itemBinding.getRoot().setOnClickListener(v -> {
            popupWindow.dismiss();
            if (action != null) {
                action.run();
            }
        });

        binding.popupContainer.addView(itemBinding.getRoot());
        return this;
    }

    public CustomPopupMenu addDivider() {
        View divider = new View(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiUtils.dpToPx(context, 1)
        );
        params.setMargins(0, UiUtils.dpToPx(context, 4), 0, UiUtils.dpToPx(context, 4));
        divider.setLayoutParams(params);

        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, typedValue, true)) {
            divider.setBackgroundColor(typedValue.data);
        } else {
            divider.setBackgroundColor(ContextCompat.getColor(context, R.color.vcode_divider));
        }

        binding.popupContainer.addView(divider);
        return this;
    }

    public void show() {
        show(anchor);
    }

    public void show(@NonNull View targetAnchor) {
        if (targetAnchor.getWindowToken() == null) return;

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int maxWidth = context.getResources().getDimensionPixelSize(R.dimen.dialog_max_width);
        int maxAllowedWidth = Math.min((int) (screenWidth * 0.92f), maxWidth);

        int width;
        if (customWidth > 0) {
            width = Math.min(customWidth, maxAllowedWidth);
        } else {
            int preferredWidth = UiUtils.dpToPx(context, 220);
            binding.getRoot().measure(
                    View.MeasureSpec.makeMeasureSpec(maxAllowedWidth, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int measuredW = binding.getRoot().getMeasuredWidth();
            width = Math.max(preferredWidth, Math.min(measuredW, maxAllowedWidth));
        }

        popupWindow.setWidth(width);

        binding.getRoot().measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupHeight = binding.getRoot().getMeasuredHeight();

        int[] anchorLoc = new int[2];
        targetAnchor.getLocationOnScreen(anchorLoc);

        int margin = UiUtils.dpToPx(context, 4);
        int spaceBelow = screenHeight - anchorLoc[1] - targetAnchor.getHeight();
        int spaceAbove = anchorLoc[1];

        // Horizontal positioning: align right or center, clamped to screen edges
        int xOffset = alignRight ? (targetAnchor.getWidth() - width) : (targetAnchor.getWidth() - width) / 2;
        int screenX = anchorLoc[0] + xOffset;
        if (screenX < margin) {
            xOffset += (margin - screenX);
        } else if (screenX + width > screenWidth - margin) {
            xOffset -= (screenX + width - (screenWidth - margin));
        }

        // Vertical positioning: flip above if insufficient space below
        int yOffset;
        if (spaceBelow >= popupHeight || spaceBelow >= spaceAbove) {
            yOffset = margin;
        } else {
            yOffset = -targetAnchor.getHeight() - popupHeight - margin;
        }

        popupWindow.showAsDropDown(targetAnchor, xOffset, yOffset);
    }

    public void dismiss() {
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    public boolean isShowing() {
        return popupWindow.isShowing();
    }

    public PopupWindow getPopupWindow() {
        return popupWindow;
    }
}

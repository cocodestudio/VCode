package com.cocode.vcode.ide.views;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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
    private View tokenView;
    private Integer customTextColor = null;
    private int customWidth = -1;
    private boolean alignRight = false;

    public CustomPopupMenu(@NonNull Context context, @NonNull View anchor) {
        this.context = context;
        this.anchor = anchor;
        this.binding = LayoutCustomPopupBinding.inflate(LayoutInflater.from(context));

        this.binding.getRoot().setCardElevation(0f);
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
        this.popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
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

    @NonNull
    public CustomPopupMenu setTokenView(@Nullable View tokenView) {
        this.tokenView = tokenView;
        return this;
    }

    @NonNull
    public CustomPopupMenu setTextColor(@ColorInt int colorInt) {
        this.customTextColor = colorInt;
        return this;
    }

    @NonNull
    public CustomPopupMenu setTextColorRes(@ColorRes int colorRes) {
        this.customTextColor = ContextCompat.getColor(context, colorRes);
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
        int textColor = customTextColor != null
                ? customTextColor
                : ContextCompat.getColor(context, R.color.vcode_text_primary);
        itemBinding.tvTitle.setTextColor(textColor);
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

        View parentView = tokenView;
        if (parentView == null || parentView.getWindowToken() == null) {
            Activity activity = findActivity(targetAnchor.getContext());
            if (activity == null) {
                activity = findActivity(context);
            }
            if (activity != null) {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                if (activity.getWindow() != null && activity.getWindow().getDecorView() != null
                        && activity.getWindow().getDecorView().getWindowToken() != null) {
                    parentView = activity.getWindow().getDecorView();
                }
            }
        }
        if (parentView == null || parentView.getWindowToken() == null) {
            parentView = targetAnchor.getRootView() != null ? targetAnchor.getRootView() : targetAnchor;
        }
        if (parentView == null || parentView.getWindowToken() == null) {
            return;
        }

        Rect visibleFrame = new Rect();
        if (parentView != null) {
            parentView.getWindowVisibleDisplayFrame(visibleFrame);
        }
        if (visibleFrame.isEmpty() || visibleFrame.width() <= 0 || visibleFrame.height() <= 0) {
            targetAnchor.getWindowVisibleDisplayFrame(visibleFrame);
        }
        if (visibleFrame.isEmpty() || visibleFrame.width() <= 0 || visibleFrame.height() <= 0) {
            visibleFrame.set(0, 0,
                    context.getResources().getDisplayMetrics().widthPixels,
                    context.getResources().getDisplayMetrics().heightPixels);
        }

        int availableWidth = visibleFrame.width();
        int maxWidth = context.getResources().getDimensionPixelSize(R.dimen.dialog_max_width);
        int maxAllowedWidth = Math.min((int) (availableWidth * 0.92f), maxWidth);

        int width;
        if (customWidth > 0) {
            width = Math.min(customWidth, maxAllowedWidth);
        } else {
            int minWidth = UiUtils.dpToPx(context, 160);
            int contentWidth = measureContentWidth(maxAllowedWidth);
            width = Math.min(Math.max(contentWidth, minWidth), maxAllowedWidth);
        }

        popupWindow.setWidth(width);

        binding.getRoot().measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupHeight = binding.getRoot().getMeasuredHeight();
        popupWindow.setHeight(popupHeight);

        int[] anchorLoc = new int[2];
        targetAnchor.getLocationOnScreen(anchorLoc);
        int anchorX = anchorLoc[0];
        int anchorY = anchorLoc[1];
        int anchorWidth = targetAnchor.getWidth();
        int anchorHeight = targetAnchor.getHeight();

        int margin = UiUtils.dpToPx(context, 4);

        // Horizontal positioning: align right edge with anchor right edge if alignRight;
        // otherwise align left edge with anchor left edge.
        int x = alignRight ? (anchorX + anchorWidth - width) : anchorX;
        int minX = visibleFrame.left + margin;
        int maxX = visibleFrame.right - width - margin;
        if (maxX >= minX) {
            x = Math.max(minX, Math.min(x, maxX));
        } else {
            x = minX;
        }

        // Vertical positioning: check space below and space above within visible frame
        int spaceBelow = visibleFrame.bottom - (anchorY + anchorHeight);
        int spaceAbove = anchorY - visibleFrame.top;

        int y;
        if (spaceBelow >= popupHeight + margin) {
            y = anchorY + anchorHeight + margin;
        } else if (spaceAbove >= popupHeight + margin) {
            y = anchorY - popupHeight - margin;
        } else if (spaceAbove >= spaceBelow) {
            y = Math.max(visibleFrame.top + margin, anchorY - popupHeight - margin);
        } else {
            y = anchorY + anchorHeight + margin;
        }

        int minY = visibleFrame.top + margin;
        int maxY = visibleFrame.bottom - popupHeight - margin;
        if (maxY >= minY) {
            y = Math.max(minY, Math.min(y, maxY));
        } else {
            y = minY;
        }

        if (popupWindow.isShowing()) {
            popupWindow.update(x, y, width, popupHeight);
        } else {
            try {
                popupWindow.showAtLocation(parentView, Gravity.NO_GRAVITY, x, y);
            } catch (WindowManager.BadTokenException e) {
                Log.e("CustomPopupMenu", "Failed to show popup window: invalid window token", e);
            }
        }
    }

    @Nullable
    private static Activity findActivity(@Nullable Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private int measureContentWidth(int maxAllowedWidth) {
        int maxWidth = 0;
        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(maxAllowedWidth, View.MeasureSpec.AT_MOST);
        int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

        int count = binding.popupContainer.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = binding.popupContainer.getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            // Only measure item rows, skipping dividers (which are plain Views)
            if (child instanceof ViewGroup) {
                child.measure(widthMeasureSpec, heightMeasureSpec);
                maxWidth = Math.max(maxWidth, child.getMeasuredWidth());
            }
        }

        int padding = binding.popupContainer.getPaddingLeft() + binding.popupContainer.getPaddingRight()
                + binding.getRoot().getContentPaddingLeft() + binding.getRoot().getContentPaddingRight();
        return Math.min(maxWidth + padding, maxAllowedWidth);
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

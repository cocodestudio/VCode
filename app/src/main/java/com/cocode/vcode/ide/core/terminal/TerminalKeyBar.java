package com.cocode.vcode.ide.core.terminal;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

/**
 * Accessory keyboard bar providing mobile-first virtual keys for terminal interactions:
 * ESC, TAB, CTRL, ALT, Arrow keys, and common programming symbols.
 */
public class TerminalKeyBar extends HorizontalScrollView {

    public interface OnKeyActionListener {
        void onSpecialKey(int specialKey);
        void onInsertText(String text);
    }

    private LinearLayout container;
    private OnKeyActionListener actionListener;

    private boolean isCtrlActive = false;
    private boolean isAltActive = false;

    private TextView btnCtrl;
    private TextView btnAlt;

    private int keyBgColor;
    private int keyActiveColor;
    private int keyTextColor;
    private int keyActiveTextColor;
    private int cornerRadius;

    public TerminalKeyBar(Context context) {
        super(context);
        init(context);
    }

    public TerminalKeyBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TerminalKeyBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setBackgroundColor(ContextCompat.getColor(context, R.color.vcode_bg_surface));

        keyBgColor = ContextCompat.getColor(context, R.color.vcode_surface_variant);
        keyActiveColor = ContextCompat.getColor(context, R.color.vcode_accent_primary);
        keyTextColor = ContextCompat.getColor(context, R.color.vcode_text_primary);
        keyActiveTextColor = ContextCompat.getColor(context, R.color.vcode_on_primary);
        cornerRadius = UiUtils.dpToPx(context, 6);

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        int pad = UiUtils.dpToPx(context, 4);
        container.setPadding(pad * 2, pad, pad * 2, pad);

        addView(container, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        buildKeys(context);
    }

    public void updateTheme() {
        Context context = getContext();
        setBackgroundColor(ContextCompat.getColor(context, R.color.vcode_bg_surface));
        keyBgColor = ContextCompat.getColor(context, R.color.vcode_surface_variant);
        keyActiveColor = ContextCompat.getColor(context, R.color.vcode_accent_primary);
        keyTextColor = ContextCompat.getColor(context, R.color.vcode_text_primary);
        keyActiveTextColor = ContextCompat.getColor(context, R.color.vcode_on_primary);

        if (container != null) {
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    boolean isActive = (tv == btnCtrl && isCtrlActive) || (tv == btnAlt && isAltActive);
                    applyBackground(tv, isActive ? keyActiveColor : keyBgColor);
                    tv.setTextColor(isActive ? keyActiveTextColor : keyTextColor);
                }
            }
        }
    }

    public void setOnKeyActionListener(@Nullable OnKeyActionListener listener) {
        this.actionListener = listener;
    }

    public boolean isCtrlActive() {
        return isCtrlActive;
    }

    public void consumeCtrl() {
        if (isCtrlActive) {
            isCtrlActive = false;
            updateModifierButton(btnCtrl, false);
        }
    }

    private void buildKeys(Context context) {
        btnCtrl = createKeyButton("CTRL", v -> {
            isCtrlActive = !isCtrlActive;
            updateModifierButton(btnCtrl, isCtrlActive);
        });
        container.addView(btnCtrl);

        btnAlt = createKeyButton("ALT", v -> {
            isAltActive = !isAltActive;
            updateModifierButton(btnAlt, isAltActive);
        });
        container.addView(btnAlt);

        container.addView(createKeyButton("ESC", v -> sendSpecial(TerminalSession.SPECIAL_KEY_ESC)));
        container.addView(createKeyButton("TAB", v -> sendSpecial(TerminalSession.SPECIAL_KEY_TAB)));

        container.addView(createKeyButton("↑", v -> sendSpecial(TerminalSession.SPECIAL_KEY_ARROW_UP)));
        container.addView(createKeyButton("↓", v -> sendSpecial(TerminalSession.SPECIAL_KEY_ARROW_DOWN)));
        container.addView(createKeyButton("←", v -> sendSpecial(TerminalSession.SPECIAL_KEY_ARROW_LEFT)));
        container.addView(createKeyButton("→", v -> sendSpecial(TerminalSession.SPECIAL_KEY_ARROW_RIGHT)));

        container.addView(createKeyButton("|", v -> sendText("|")));
        container.addView(createKeyButton("~", v -> sendText("~")));
        container.addView(createKeyButton("/", v -> sendText("/")));
        container.addView(createKeyButton("-", v -> sendText("-")));
        container.addView(createKeyButton("C-c", v -> sendSpecial(TerminalSession.SPECIAL_KEY_CTRL_C)));
        container.addView(createKeyButton("C-d", v -> sendSpecial(TerminalSession.SPECIAL_KEY_CTRL_D)));
        container.addView(createKeyButton("C-l", v -> sendSpecial(TerminalSession.SPECIAL_KEY_CTRL_L)));
    }

    private TextView createKeyButton(String label, OnClickListener listener) {
        TextView tv = new TextView(getContext());
        tv.setText(label);
        tv.setTextSize(12);
        tv.setTextColor(keyTextColor);
        tv.setTypeface(FontManager.getInstance().getUiMedium(getContext()));
        tv.setGravity(Gravity.CENTER);
        tv.setFocusable(false);
        tv.setFocusableInTouchMode(false);

        int padH = UiUtils.dpToPx(getContext(), 10);
        int padV = UiUtils.dpToPx(getContext(), 6);
        tv.setPadding(padH, padV, padH, padV);

        applyBackground(tv, keyBgColor);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int margin = UiUtils.dpToPx(getContext(), 3);
        lp.setMargins(margin, 0, margin, 0);
        tv.setLayoutParams(lp);

        tv.setOnClickListener(listener);
        return tv;
    }

    private void updateModifierButton(TextView btn, boolean active) {
        if (btn == null) return;
        applyBackground(btn, active ? keyActiveColor : keyBgColor);
        btn.setTextColor(active ? keyActiveTextColor : keyTextColor);
    }

    private void applyBackground(View view, int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(cornerRadius);
        view.setBackground(gd);
    }

    private void sendSpecial(int specialKey) {
        consumeCtrl();
        if (actionListener != null) {
            actionListener.onSpecialKey(specialKey);
        }
    }

    private void sendText(String text) {
        consumeCtrl();
        if (actionListener != null) {
            actionListener.onInsertText(text);
        }
    }
}

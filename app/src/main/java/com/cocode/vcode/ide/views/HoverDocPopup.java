package com.cocode.vcode.ide.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Html;
import android.text.Spanned;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.lsp.HoverInfo;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

/**
 * Floating Material 3 hover documentation card rendering markdown docs, MDN descriptions,
 * and symbol signatures above or below the tapped cursor location.
 */
public class HoverDocPopup {

    private final Context context;
    private final PopupWindow popupWindow;
    private final TextView tvContent;

    public HoverDocPopup(Context context) {
        this.context = context;

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackground(ContextCompat.getDrawable(context, R.drawable.vcode_bg_autocomplete_popup));
        int padding = UiUtils.dpToPx(context, 10);
        container.setPadding(padding, padding, padding, padding);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                UiUtils.dpToPx(context, 280),
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        tvContent = new TextView(context);
        tvContent.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_primary));
        tvContent.setTypeface(FontManager.getInstance().getUiFont(context));
        tvContent.setTextSize(12f);
        tvContent.setLineSpacing(UiUtils.dpToPx(context, 2), 1f);

        scrollView.addView(tvContent);
        container.addView(scrollView);

        popupWindow = new PopupWindow(container,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(false);
        popupWindow.setElevation(10f);
        popupWindow.setAnimationStyle(android.R.style.Animation_Toast);
    }

    public void show(HoverInfo info, View anchor, int screenX, int screenY) {
        if (info == null || info.getMarkdownContent() == null || info.getMarkdownContent().isEmpty()) {
            dismiss();
            return;
        }

        Spanned spanned = parseMarkdownToSpanned(info.getMarkdownContent());
        tvContent.setText(spanned);

        int posX = screenX;
        int posY = screenY - UiUtils.dpToPx(context, 70);

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int popupWidth = UiUtils.dpToPx(context, 290);
        if (posX + popupWidth > screenWidth) {
            posX = Math.max(UiUtils.dpToPx(context, 10), screenWidth - popupWidth - UiUtils.dpToPx(context, 10));
        }

        if (posY < UiUtils.dpToPx(context, 60)) {
            posY = screenY + UiUtils.dpToPx(context, 30);
        }

        if (popupWindow.isShowing()) {
            popupWindow.update(posX, posY, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, posX, posY);
        }
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }

    private Spanned parseMarkdownToSpanned(String markdown) {
        if (markdown == null) return Html.fromHtml("", Html.FROM_HTML_MODE_LEGACY);
        String html = markdown
                .replaceAll("### `([^`]+)`", "<b><font color=\"#2B6EE8\">$1</font></b><br/>")
                .replaceAll("### ([^\n]+)", "<b><font color=\"#2B6EE8\">$1</font></b><br/>")
                .replaceAll("```[a-z]*\n([\\s\\S]*?)\n```", "<tt>$1</tt><br/>")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>")
                .replaceAll("`([^`]+)`", "<tt><font color=\"#6340E0\">$1</font></tt>")
                .replace("\n\n", "<br/><br/>")
                .replace("\n", "<br/>");
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
    }
}

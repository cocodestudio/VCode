package com.cocode.vcode.ide.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

public class SignatureHintPopup {

    private final Context context;
    private final PopupWindow popupWindow;
    private final TextView tvSignature;

    public SignatureHintPopup(Context context) {
        this.context = context;

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackground(ContextCompat.getDrawable(context, R.drawable.vcode_bg_autocomplete_popup));
        int padding = UiUtils.dpToPx(context, 8);
        container.setPadding(padding, padding, padding, padding);

        tvSignature = new TextView(context);
        tvSignature.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_primary));
        tvSignature.setTypeface(FontManager.getInstance().getCodeFont(context));
        tvSignature.setTextSize(14f);
        
        container.addView(tvSignature, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        popupWindow = new PopupWindow(container,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(false);
        popupWindow.setElevation(8f);
        popupWindow.setAnimationStyle(android.R.style.Animation_Toast);
    }

    public void show(LspSignatureHelp help, View editorView, int cursorOffset) {
        if (help == null || help.signatures == null || help.signatures.isEmpty()) {
            dismiss();
            return;
        }

        LspSignatureHelp.LspSignatureInformation activeSig = help.signatures.get(
                Math.max(0, Math.min(help.activeSignature, help.signatures.size() - 1))
        );

        SpannableStringBuilder sb = new SpannableStringBuilder();
        String label = activeSig.label;
        
        if (activeSig.parameters == null || activeSig.parameters.isEmpty()) {
            sb.append(label);
        } else {
            // Find function name and open paren
            int openParen = label.indexOf('(');
            if (openParen >= 0) {
                sb.append(label.substring(0, openParen + 1));
                
                for (int i = 0; i < activeSig.parameters.size(); i++) {
                    LspSignatureHelp.LspParameterInformation param = activeSig.parameters.get(i);
                    int start = sb.length();
                    sb.append(param.label);
                    
                    if (i == help.activeParameter) {
                        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.vcode_accent_primary)), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    
                    if (i < activeSig.parameters.size() - 1) {
                        sb.append(", ");
                    }
                }
                
                int closeParen = label.lastIndexOf(')');
                if (closeParen > openParen) {
                    sb.append(label.substring(closeParen));
                } else {
                    sb.append(")");
                }
            } else {
                sb.append(label);
            }
        }
        
        tvSignature.setText(sb);
        
        // Position logic similar to AutoCompletePopup
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int maxPopupWidth = (int) (screenWidth * 0.9f);
        popupWindow.setWidth(Math.min(maxPopupWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        popupWindow.getContentView().measure(
                View.MeasureSpec.makeMeasureSpec(maxPopupWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int popupWidth = popupWindow.getContentView().getMeasuredWidth();
        int popupHeight = popupWindow.getContentView().getMeasuredHeight();

        int windowX = 0;
        int windowYTop = 0;
        int windowYBottom = 0;

        if (editorView instanceof CodeEditText) {
            CodeEditText codeEditor = (CodeEditText) editorView;
            int[] coords = codeEditor.getCursorScreenCoords(cursorOffset);
            windowX = coords[0];
            windowYTop = coords[1];
            windowYBottom = coords[2];
        }

        int x = windowX;

        android.graphics.Rect visibleFrame = new android.graphics.Rect();
        editorView.getWindowVisibleDisplayFrame(visibleFrame);

        int yBelow = windowYBottom + UiUtils.dpToPx(context, 4);
        int yAbove = windowYTop - popupHeight - UiUtils.dpToPx(context, 4);

        int y;
        if (yAbove < visibleFrame.top) {
            y = Math.max(visibleFrame.top, yBelow);
        } else {
            y = yAbove;
        }

        if (x + popupWidth > screenWidth) {
            x = screenWidth - popupWidth - UiUtils.dpToPx(context, 8);
        }
        x = Math.max(0, x);

        if (popupWindow.isShowing()) {
            popupWindow.update(x, y, popupWidth, popupHeight);
        } else {
            popupWindow.showAtLocation(editorView, Gravity.NO_GRAVITY, x, y);
        }
    }

    public void dismiss() {
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    public boolean isShowing() {
        return popupWindow.isShowing();
    }
}

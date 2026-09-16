package com.cocode.vcode.ide.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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

/**
 * Lightweight floating popup window that displays method parameter signatures and documentation hints.
 * Positions above or below the cursor caret in {@link CodeEditText}, highlighting the currently active
 * parameter in bold accent styling and rendering documentation snippets for standard library and user functions.
 */
public class SignatureHintPopup {

    private final Context context;
    private final PopupWindow popupWindow;
    private final TextView tvSignature;
    private final TextView tvDoc;

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

        tvDoc = new TextView(context);
        tvDoc.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_secondary));
        tvDoc.setTypeface(FontManager.getInstance().getUiFont(context));
        tvDoc.setTextSize(11f);
        tvDoc.setMaxLines(3);
        tvDoc.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams docParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        docParams.topMargin = UiUtils.dpToPx(context, 4);
        container.addView(tvDoc, docParams);
        tvDoc.setVisibility(View.GONE);

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

    /**
     * Extracts only the simple method or function name from a signature prefix string,
     * stripping any receiver objects, chaining dots (e.g. "a.b.c.foo"), "new" keywords,
     * generic type arguments, and leading dots.
     *
     * @param prefix the text preceding '(' in a signature label, or the full label if no '(' exists
     * @return the isolated method name
     */
    public static String extractMethodName(String prefix) {
        if (prefix == null) return "";
        prefix = prefix.trim();
        if (prefix.startsWith("new ")) {
            prefix = prefix.substring(4).trim();
        }
        int genericStart = prefix.indexOf('<');
        if (genericStart >= 0 && prefix.endsWith(">")) {
            prefix = prefix.substring(0, genericStart).trim();
        }
        int lastDot = prefix.lastIndexOf('.');
        if (lastDot >= 0) {
            prefix = prefix.substring(lastDot + 1).trim();
        }
        while (prefix.startsWith(".")) {
            prefix = prefix.substring(1).trim();
        }
        return prefix;
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
        if (label == null) {
            tvSignature.setText("");
            return;
        }

        int openParen = label.indexOf('(');
        String methodName = "";
        String suffix = "";

        if (openParen >= 0) {
            methodName = extractMethodName(label.substring(0, openParen));
            int closeParen = label.lastIndexOf(')');
            if (closeParen > openParen) {
                suffix = label.substring(closeParen);
            } else {
                suffix = ")";
            }
        } else {
            methodName = extractMethodName(label);
        }

        if (activeSig.parameters == null || activeSig.parameters.isEmpty()) {
            if (openParen >= 0) {
                sb.append(methodName).append(label.substring(openParen));
            } else {
                sb.append(methodName);
            }
        } else {
            sb.append(methodName).append("(");

            for (int i = 0; i < activeSig.parameters.size(); i++) {
                LspSignatureHelp.LspParameterInformation param = activeSig.parameters.get(i);
                int start = sb.length();
                sb.append(param != null && param.label != null ? param.label : "");

                boolean isLast = (i == activeSig.parameters.size() - 1);
                boolean isRest = param != null && param.label != null && param.label.trim().startsWith("...");
                boolean isActive = (i == help.activeParameter) || (isLast && isRest && help.activeParameter >= i);

                if (isActive) {
                    sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.vcode_accent_primary)), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                if (i < activeSig.parameters.size() - 1) {
                    sb.append(", ");
                }
            }

            sb.append(suffix);
        }

        tvSignature.setText(sb);

        // Documentation display
        String docText = null;
        if (activeSig.parameters != null && help.activeParameter >= 0 && help.activeParameter < activeSig.parameters.size()) {
            LspSignatureHelp.LspParameterInformation activeParam = activeSig.parameters.get(help.activeParameter);
            if (activeParam.documentation != null && !activeParam.documentation.trim().isEmpty()) {
                docText = activeParam.documentation.trim();
            }
        }
        if (docText == null && activeSig.documentation != null && !activeSig.documentation.trim().isEmpty()) {
            docText = activeSig.documentation.trim();
        }

        if (docText != null && !docText.isEmpty()) {
            tvDoc.setText(docText);
            tvDoc.setVisibility(View.VISIBLE);
        } else {
            tvDoc.setVisibility(View.GONE);
        }

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
        if (yAbove >= visibleFrame.top) {
            y = yAbove;
        } else if (yBelow + popupHeight <= visibleFrame.bottom) {
            y = yBelow;
        } else {
            y = Math.max(visibleFrame.top, yAbove);
        }

        if (y + popupHeight > visibleFrame.bottom) {
            y = Math.max(visibleFrame.top, visibleFrame.bottom - popupHeight);
        }
        if (y < visibleFrame.top) {
            y = visibleFrame.top;
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

package com.cocode.vcode.ide.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
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
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

/**
 * Floating popup window for documentation and signature hints.
 * Displays method signatures with active parameter highlighting,
 * symbol descriptions, and hover documentation.
 */
public class DocumentationPopup {

    public enum PopupMode {
        NONE,
        SIGNATURE,
        HOVER
    }

    private final Context context;
    private final PopupWindow popupWindow;
    private final LinearLayout container;
    private final TextView tvHeader;
    private final View vDivider;
    private final MaxHeightScrollView scrollView;
    private final TextView tvParamDoc;
    private final TextView tvContent;

    private PopupMode currentMode = PopupMode.NONE;

    public DocumentationPopup(Context context) {
        this.context = context;

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setClickable(true);
        container.setBackground(ContextCompat.getDrawable(context, R.drawable.vcode_bg_autocomplete_popup));
        int padding = context.getResources().getDimensionPixelSize(R.dimen.vcode_padding_small);
        container.setPadding(padding, padding, padding, padding);

        // Header TextView for function signatures or symbol declaration banners
        tvHeader = new TextView(context);
        tvHeader.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_primary));
        tvHeader.setTypeface(FontManager.getInstance().getCodeFont(context), Typeface.BOLD);
        tvHeader.setTextSize(13f);
        container.addView(tvHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Hairline separator between declaration header and documentation body
        // Custom onMeasure ensures divider does not expand container in AT_MOST measurement
        vDivider = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthMode = MeasureSpec.getMode(widthMeasureSpec);
                int width = (widthMode == MeasureSpec.EXACTLY) ? MeasureSpec.getSize(widthMeasureSpec) : 0;
                setMeasuredDimension(width, UiUtils.dpToPx(context, 1));
            }
        };
        vDivider.setBackgroundColor(ContextCompat.getColor(context, R.color.vcode_divider));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiUtils.dpToPx(context, 1)
        );
        int marginVertical = UiUtils.dpToPx(context, 6);
        dividerParams.topMargin = marginVertical;
        dividerParams.bottomMargin = marginVertical;
        container.addView(vDivider, dividerParams);

        // Scrollable content wrapper with capped maximum height
        scrollView = new MaxHeightScrollView(context);
        int maxHeight = context.getResources().getDimensionPixelSize(R.dimen.vcode_doc_popup_scroll_max_height);
        scrollView.setMaxHeight(maxHeight);
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.setScrollbarFadingEnabled(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout contentContainer = new LinearLayout(context);
        contentContainer.setOrientation(LinearLayout.VERTICAL);

        // Parameter documentation (active parameter snippet in signature mode)
        tvParamDoc = new TextView(context);
        tvParamDoc.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_secondary));
        tvParamDoc.setTypeface(FontManager.getInstance().getUiFont(context));
        tvParamDoc.setTextSize(12f);
        LinearLayout.LayoutParams paramParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramParams.bottomMargin = UiUtils.dpToPx(context, 4);
        contentContainer.addView(tvParamDoc, paramParams);

        // Main documentation body (JSDoc, MDN, markdown text)
        tvContent = new TextView(context);
        tvContent.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_primary));
        tvContent.setTypeface(FontManager.getInstance().getUiFont(context));
        tvContent.setTextSize(12f);
        tvContent.setLineSpacing(UiUtils.dpToPx(context, 2), 1.1f);
        tvContent.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        contentContainer.addView(tvContent, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        scrollView.addView(contentContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        container.addView(scrollView, new LinearLayout.LayoutParams(
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
        popupWindow.setElevation(10f);
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

    /**
     * Shows signature help hints with the active parameter highlighted in bold accent color
     * and parameter / method documentation rendered below.
     *
     * @param help LSP signature help result
     * @param editorView the code editor view hosting the popup
     * @param cursorOffset character flat offset where the cursor is currently placed
     */
    public void showSignature(LspSignatureHelp help, View editorView, int cursorOffset) {
        if (help == null || help.signatures == null || help.signatures.isEmpty()) {
            dismiss();
            return;
        }

        if (editorView instanceof CodeEditText) {
            CodeEditText codeEditor = (CodeEditText) editorView;
            if (codeEditor.isAutoCompleteVisible()) {
                dismiss();
                return;
            }
        }

        currentMode = PopupMode.SIGNATURE;

        LspSignatureHelp.LspSignatureInformation activeSig = help.signatures.get(
                Math.max(0, Math.min(help.activeSignature, help.signatures.size() - 1))
        );

        SpannableStringBuilder sb = new SpannableStringBuilder();
        String label = activeSig.label;
        if (label == null) {
            tvHeader.setText("");
            dismiss();
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

        tvHeader.setText(sb);
        tvHeader.setVisibility(View.VISIBLE);

        // Active parameter documentation
        boolean hasParamDoc = false;
        if (activeSig.parameters != null && help.activeParameter >= 0 && help.activeParameter < activeSig.parameters.size()) {
            LspSignatureHelp.LspParameterInformation activeParam = activeSig.parameters.get(help.activeParameter);
            if (activeParam != null && activeParam.documentation != null && !activeParam.documentation.trim().isEmpty()) {
                String paramName = activeParam.label != null ? activeParam.label.trim() : "";
                int colonIdx = paramName.indexOf(':');
                if (colonIdx >= 0) paramName = paramName.substring(0, colonIdx).trim();

                SpannableStringBuilder psb = new SpannableStringBuilder();
                if (!paramName.isEmpty()) {
                    psb.append(paramName).append(": ");
                    psb.setSpan(new StyleSpan(Typeface.BOLD), 0, paramName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    psb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.vcode_accent_primary)), 0, paramName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                psb.append(activeParam.documentation.trim());
                tvParamDoc.setText(psb);
                tvParamDoc.setVisibility(View.VISIBLE);
                hasParamDoc = true;
            }
        }
        if (!hasParamDoc) {
            tvParamDoc.setVisibility(View.GONE);
        }

        // Method / function overview documentation
        boolean hasFuncDoc = false;
        if (activeSig.documentation != null && !activeSig.documentation.trim().isEmpty()) {
            tvContent.setText(parseMarkdownToSpanned(activeSig.documentation.trim()));
            tvContent.setVisibility(View.VISIBLE);
            hasFuncDoc = true;
        } else {
            tvContent.setVisibility(View.GONE);
        }

        boolean hasAnyDoc = hasParamDoc || hasFuncDoc;
        vDivider.setVisibility(hasAnyDoc ? View.VISIBLE : View.GONE);
        scrollView.setVisibility(hasAnyDoc ? View.VISIBLE : View.GONE);

        updatePositionForEditor(editorView, cursorOffset);
    }

    /**
     * Shows hover documentation card for an identifier, function, or keyword at given screen coordinates.
     *
     * @param info LSP hover information with rich markdown
     * @param anchor the anchor view for window token resolution
     * @param screenX horizontal screen coordinate
     * @param screenY vertical screen coordinate
     */
    public void showHover(HoverInfo info, View anchor, int screenX, int screenY) {
        if (info == null || info.getMarkdownContent() == null || info.getMarkdownContent().trim().isEmpty()) {
            dismiss();
            return;
        }

        currentMode = PopupMode.HOVER;

        String raw = info.getMarkdownContent().trim();
        String headerText = null;
        String docMarkdown = raw;

        // Extract code block or header for top banner if present
        if (raw.startsWith("```")) {
            int firstNewline = raw.indexOf('\n');
            int closingBackticks = raw.indexOf("```", 3);
            if (firstNewline > 0 && closingBackticks > firstNewline) {
                headerText = raw.substring(firstNewline + 1, closingBackticks).trim();
                docMarkdown = raw.substring(closingBackticks + 3).trim();
            }
        } else if (raw.startsWith("### ")) {
            int newline = raw.indexOf('\n');
            if (newline > 0) {
                String title = raw.substring(4, newline).trim();
                if (title.startsWith("`") && title.endsWith("`")) {
                    title = title.substring(1, title.length() - 1);
                }
                headerText = title;
                docMarkdown = raw.substring(newline + 1).trim();
                if (docMarkdown.startsWith("```")) {
                    int fn = docMarkdown.indexOf('\n');
                    int cb = docMarkdown.indexOf("```", 3);
                    if (fn > 0 && cb > fn) {
                        headerText = docMarkdown.substring(fn + 1, cb).trim();
                        docMarkdown = docMarkdown.substring(cb + 3).trim();
                    }
                }
            }
        }

        if (headerText != null && !headerText.isEmpty()) {
            tvHeader.setText(headerText);
            tvHeader.setVisibility(View.VISIBLE);
        } else {
            tvHeader.setVisibility(View.GONE);
        }

        tvParamDoc.setVisibility(View.GONE);

        if (docMarkdown != null && !docMarkdown.isEmpty()) {
            tvContent.setText(parseMarkdownToSpanned(docMarkdown));
            tvContent.setVisibility(View.VISIBLE);
            vDivider.setVisibility(tvHeader.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
            scrollView.setVisibility(View.VISIBLE);
        } else {
            tvContent.setVisibility(View.GONE);
            vDivider.setVisibility(View.GONE);
            scrollView.setVisibility(View.GONE);
        }

        updatePositionForCoordinates(anchor, screenX, screenY);
    }

    /**
     * Shows hover documentation card positioned relative to the cursor caret offset in the code editor.
     *
     * @param info LSP hover information with rich markdown
     * @param editorView the code editor view hosting the popup
     * @param cursorOffset character flat offset where the cursor is placed
     */
    public void showHover(HoverInfo info, View editorView, int cursorOffset) {
        if (editorView instanceof CodeEditText) {
            CodeEditText codeEditor = (CodeEditText) editorView;
            int[] coords = codeEditor.getCursorScreenCoords(cursorOffset);
            showHover(info, editorView, coords[0], coords[1]);
        } else {
            showHover(info, editorView, 0, 0);
        }
    }

    private void updatePositionForEditor(View editorView, int cursorOffset) {
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

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int minPopupWidth = context.getResources().getDimensionPixelSize(R.dimen.vcode_doc_popup_min_width);
        int maxPopupWidth = Math.min(context.getResources().getDimensionPixelSize(R.dimen.vcode_doc_popup_max_width), (int) (screenWidth * 0.90f));
        if (minPopupWidth > maxPopupWidth) {
            minPopupWidth = maxPopupWidth;
        }
        container.setMinimumWidth(minPopupWidth);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(maxPopupWidth, View.MeasureSpec.AT_MOST);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        container.measure(widthSpec, heightSpec);

        int popupWidth = Math.max(minPopupWidth, Math.min(container.getMeasuredWidth(), maxPopupWidth));
        // Remeasure container with resolved exact width so layout and multi-line wrapping are accurate
        container.measure(View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY), heightSpec);
        int popupHeight = container.getMeasuredHeight();

        Rect visibleFrame = new Rect();
        editorView.getWindowVisibleDisplayFrame(visibleFrame);

        int spacing = UiUtils.dpToPx(context, 4);
        int yBelow = windowYBottom + spacing;
        int yAbove = windowYTop - popupHeight - spacing;

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

        int x = windowX;
        int margin = UiUtils.dpToPx(context, 8);
        if (x + popupWidth > screenWidth - margin) {
            x = screenWidth - popupWidth - margin;
        }
        if (x < margin) {
            x = margin;
        }

        popupWindow.setWidth(popupWidth);
        popupWindow.setHeight(popupHeight);

        if (popupWindow.isShowing()) {
            popupWindow.update(x, y, popupWidth, popupHeight);
        } else {
            popupWindow.showAtLocation(editorView, Gravity.NO_GRAVITY, x, y);
        }
    }

    private void updatePositionForCoordinates(View anchor, int screenX, int screenY) {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int minPopupWidth = context.getResources().getDimensionPixelSize(R.dimen.vcode_doc_popup_min_width);
        int maxPopupWidth = Math.min(context.getResources().getDimensionPixelSize(R.dimen.vcode_doc_popup_max_width), (int) (screenWidth * 0.90f));
        if (minPopupWidth > maxPopupWidth) {
            minPopupWidth = maxPopupWidth;
        }
        container.setMinimumWidth(minPopupWidth);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(maxPopupWidth, View.MeasureSpec.AT_MOST);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        container.measure(widthSpec, heightSpec);

        int popupWidth = Math.max(minPopupWidth, Math.min(container.getMeasuredWidth(), maxPopupWidth));
        // Remeasure container with resolved exact width so layout and multi-line wrapping are accurate
        container.measure(View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY), heightSpec);
        int popupHeight = container.getMeasuredHeight();

        Rect visibleFrame = new Rect();
        anchor.getWindowVisibleDisplayFrame(visibleFrame);

        int spacing = UiUtils.dpToPx(context, 8);
        int yAbove = screenY - popupHeight - spacing;
        int yBelow = screenY + UiUtils.dpToPx(context, 24);

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

        int x = screenX;
        int margin = UiUtils.dpToPx(context, 8);
        if (x + popupWidth > screenWidth - margin) {
            x = screenWidth - popupWidth - margin;
        }
        if (x < margin) {
            x = margin;
        }

        popupWindow.setWidth(popupWidth);
        popupWindow.setHeight(popupHeight);

        if (popupWindow.isShowing()) {
            popupWindow.update(x, y, popupWidth, popupHeight);
        } else {
            popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
        }
    }

    /**
     * Converts markdown text into formatted {@link Spanned} text for display.
     * Escapes standard XML characters first to protect generics (e.g. Map&lt;K,V&gt;) from
     * being stripped as HTML tags.
     */
    private Spanned parseMarkdownToSpanned(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return Html.fromHtml("", Html.FROM_HTML_MODE_LEGACY);
        }
        String accentHex = String.format("#%06X", (0xFFFFFF & ContextCompat.getColor(context, R.color.vcode_accent_primary)));
        String secondaryHex = String.format("#%06X", (0xFFFFFF & ContextCompat.getColor(context, R.color.vcode_accent_secondary)));
        String typeHex = String.format("#%06X", (0xFFFFFF & ContextCompat.getColor(context, R.color.vcode_lang_md)));
        String errorHex = String.format("#%06X", (0xFFFFFF & ContextCompat.getColor(context, R.color.vcode_accent_error)));

        String escaped = markdown
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");

        String html = escaped
                .replaceAll("### `([^`]+)`", "<b><font color=\"" + accentHex + "\">$1</font></b><br/>")
                .replaceAll("### ([^\n]+)", "<b><font color=\"" + accentHex + "\">$1</font></b><br/>")
                .replaceAll("```[a-z]*\n([\\s\\S]*?)\n```", "<tt><font color=\"" + accentHex + "\">$1</font></tt><br/>")
                .replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\"><font color=\"" + accentHex + "\">$1</font></a>")
                .replaceAll("@param `([a-zA-Z0-9_$.]+):\\s*([^`]+)`", "<font color=\"" + secondaryHex + "\"><b>@param</b></font> <b><font color=\"" + accentHex + "\">$1</font></b><font color=\"" + typeHex + "\">: $2</font>")
                .replaceAll("@param `([a-zA-Z0-9_$.]+)`", "<font color=\"" + secondaryHex + "\"><b>@param</b></font> <b><font color=\"" + accentHex + "\">$1</font></b>")
                .replaceAll("@param\\s*\\{([^}]+)\\}\\s*(\\[?[a-zA-Z0-9_$.=]+\\]?)", "<font color=\"" + secondaryHex + "\"><b>@param</b></font> <b><font color=\"" + accentHex + "\">$2</font></b> <font color=\"" + typeHex + "\">{$1}</font>")
                .replaceAll("@returns `([^`]+)`", "<font color=\"" + secondaryHex + "\"><b>@returns</b></font> <font color=\"" + typeHex + "\"><b>$1</b></font>")
                .replaceAll("@returns\\s*\\{([^}]+)\\}", "<font color=\"" + secondaryHex + "\"><b>@returns</b></font> <font color=\"" + typeHex + "\">{$1}</font>")
                .replaceAll("@throws `([^`]+)`", "<font color=\"" + secondaryHex + "\"><b>@throws</b></font> <font color=\"" + errorHex + "\"><b>$1</b></font>")
                .replaceAll("@throws\\s*\\{([^}]+)\\}", "<font color=\"" + secondaryHex + "\"><b>@throws</b></font> <font color=\"" + errorHex + "\">{$1}</font>")
                .replaceAll("(@(?:param|returns?|throws?|exception|typedef|property|see|example|deprecated|template|type|default))\\b", "<font color=\"" + secondaryHex + "\"><b>$1</b></font>")
                .replaceAll("(?<![\\w`])\\{([a-zA-Z0-9_$|\\[\\].?]+)\\}", "<font color=\"" + typeHex + "\">{$1}</font>")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>")
                .replaceAll("`([^`]+)`", "<tt><font color=\"" + secondaryHex + "\">$1</font></tt>")
                .replace("\n\n", "<br/><br/>")
                .replace("\n", "<br/>");

        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
    }

    public void dismiss() {
        currentMode = PopupMode.NONE;
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    public boolean isShowing() {
        return popupWindow.isShowing();
    }

    public boolean isSignatureMode() {
        return isShowing() && currentMode == PopupMode.SIGNATURE;
    }

    public boolean isHoverMode() {
        return isShowing() && currentMode == PopupMode.HOVER;
    }

    public PopupMode getCurrentMode() {
        return currentMode;
    }

    /**
     * ScrollView enforcing a maximum vertical height constraint.
     */
    public static class MaxHeightScrollView extends ScrollView {
        private int maxHeightPx;

        public MaxHeightScrollView(Context context) {
            super(context);
        }

        public void setMaxHeight(int maxHeightPx) {
            this.maxHeightPx = maxHeightPx;
            requestLayout();
        }

        public int getMaxHeight() {
            return maxHeightPx;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (maxHeightPx > 0) {
                int heightMode = MeasureSpec.getMode(heightMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);
                if (heightMode == MeasureSpec.EXACTLY) {
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.min(heightSize, maxHeightPx), MeasureSpec.AT_MOST);
                } else if (heightMode == MeasureSpec.AT_MOST) {
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.min(heightSize, maxHeightPx), MeasureSpec.AT_MOST);
                } else {
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST);
                }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}

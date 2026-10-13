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

    private final int colorFunction;
    private final int colorParam;
    private final int colorActiveParam;
    private final int colorKeyword;
    private final int colorType;
    private final int colorString;
    private final int colorNumber;
    private final int colorOperator;
    private final int colorPunctuation;
    private final int colorComment;

    private PopupMode currentMode = PopupMode.NONE;

    public DocumentationPopup(Context context) {
        this.context = context;

        colorFunction = ContextCompat.getColor(context, R.color.vcode_color_js_function);
        colorParam = ContextCompat.getColor(context, R.color.vcode_color_html_attribute);
        colorActiveParam = ContextCompat.getColor(context, R.color.vcode_accent_primary);
        colorKeyword = ContextCompat.getColor(context, R.color.vcode_color_js_keyword);
        colorType = ContextCompat.getColor(context, R.color.vcode_color_css_property);
        colorString = ContextCompat.getColor(context, R.color.vcode_color_js_string);
        colorNumber = ContextCompat.getColor(context, R.color.vcode_color_js_number);
        colorOperator = ContextCompat.getColor(context, R.color.vcode_color_js_operator);
        colorPunctuation = ContextCompat.getColor(context, R.color.vcode_color_json_colon);
        colorComment = ContextCompat.getColor(context, R.color.vcode_color_js_comment);

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
     * Shows signature help hints with the active parameter highlighted in bold accent color,
     * inactive parameters and types highlighted with syntax colors, and documentation rendered below.
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

        // Highlight methodName
        int mStart = sb.length();
        sb.append(methodName);
        sb.setSpan(new ForegroundColorSpan(colorFunction), mStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), mStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (openParen >= 0 || (activeSig.parameters != null && !activeSig.parameters.isEmpty())) {
            int openStart = sb.length();
            sb.append("(");
            sb.setSpan(new ForegroundColorSpan(colorOperator), openStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            if (activeSig.parameters != null && !activeSig.parameters.isEmpty()) {
                for (int i = 0; i < activeSig.parameters.size(); i++) {
                    LspSignatureHelp.LspParameterInformation param = activeSig.parameters.get(i);
                    String paramLabel = param != null && param.label != null ? param.label : "";

                    boolean isLast = (i == activeSig.parameters.size() - 1);
                    boolean isRest = paramLabel.trim().startsWith("...");
                    boolean isActive = (i == help.activeParameter) || (isLast && isRest && help.activeParameter >= i);

                    if (isActive) {
                        int pStart = sb.length();
                        sb.append(paramLabel);
                        sb.setSpan(new StyleSpan(Typeface.BOLD), pStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        sb.setSpan(new ForegroundColorSpan(colorActiveParam), pStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else {
                        formatInactiveParameter(sb, paramLabel);
                    }

                    if (i < activeSig.parameters.size() - 1) {
                        int commaStart = sb.length();
                        sb.append(", ");
                        sb.setSpan(new ForegroundColorSpan(colorPunctuation), commaStart, commaStart + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }

            int closeStart = sb.length();
            sb.append(")");
            sb.setSpan(new ForegroundColorSpan(colorOperator), closeStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            if (suffix.startsWith(")")) {
                String returnTypePart = suffix.substring(1).trim();
                if (!returnTypePart.isEmpty()) {
                    if (returnTypePart.startsWith(":")) {
                        sb.append(" ");
                    }
                    formatTypeSnippet(sb, returnTypePart);
                }
            }
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
                    psb.setSpan(new ForegroundColorSpan(colorActiveParam), 0, paramName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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

    private void formatInactiveParameter(SpannableStringBuilder sb, String paramLabel) {
        if (paramLabel == null || paramLabel.isEmpty()) return;

        int colonIdx = -1;
        int eqIdx = -1;
        int depth = 0;
        for (int i = 0; i < paramLabel.length(); i++) {
            char c = paramLabel.charAt(i);
            if (c == '(' || c == '{' || c == '[' || c == '<') {
                depth++;
            } else if (c == ')' || c == '}' || c == ']' || c == '>') {
                if (depth > 0) depth--;
            } else if (depth == 0) {
                if (c == ':' && colonIdx == -1) {
                    colonIdx = i;
                    break;
                } else if (c == '=' && eqIdx == -1) {
                    eqIdx = i;
                }
            }
        }

        if (colonIdx >= 0) {
            String identifier = paramLabel.substring(0, colonIdx);
            appendParameterIdentifier(sb, identifier);
            formatTypeSnippet(sb, paramLabel.substring(colonIdx));
        } else if (eqIdx >= 0) {
            String identifier = paramLabel.substring(0, eqIdx);
            appendParameterIdentifier(sb, identifier);
            formatTypeSnippet(sb, paramLabel.substring(eqIdx));
        } else {
            appendParameterIdentifier(sb, paramLabel);
        }
    }

    private void appendParameterIdentifier(SpannableStringBuilder sb, String identifier) {
        if (identifier == null || identifier.isEmpty()) return;
        if (identifier.startsWith("...")) {
            int sStart = sb.length();
            sb.append("...");
            sb.setSpan(new ForegroundColorSpan(colorOperator), sStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            identifier = identifier.substring(3);
        }
        int pStart = sb.length();
        if (identifier.endsWith("?")) {
            String nameOnly = identifier.substring(0, identifier.length() - 1);
            sb.append(nameOnly);
            sb.setSpan(new ForegroundColorSpan(colorParam), pStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int qStart = sb.length();
            sb.append("?");
            sb.setSpan(new ForegroundColorSpan(colorOperator), qStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            sb.append(identifier);
            sb.setSpan(new ForegroundColorSpan(colorParam), pStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /**
     * Applies syntax colors to primitive types, class/interface types, string literals,
     * numeric literals, and union/intersection operators in type annotation snippets.
     */
    public void formatTypeSnippet(SpannableStringBuilder sb, String typeStr) {
        if (sb == null || typeStr == null || typeStr.isEmpty()) return;
        int len = typeStr.length();
        int i = 0;
        while (i < len) {
            char c = typeStr.charAt(i);
            if (Character.isWhitespace(c)) {
                sb.append(c);
                i++;
            } else if (c == ':' || c == ',' || c == ';') {
                int start = sb.length();
                sb.append(c);
                sb.setSpan(new ForegroundColorSpan(colorPunctuation), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i++;
            } else if (c == '=' && i + 1 < len && typeStr.charAt(i + 1) == '>') {
                int start = sb.length();
                sb.append("=>");
                sb.setSpan(new ForegroundColorSpan(colorOperator), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i += 2;
            } else if (c == '.' && i + 2 < len && typeStr.charAt(i + 1) == '.' && typeStr.charAt(i + 2) == '.') {
                int start = sb.length();
                sb.append("...");
                sb.setSpan(new ForegroundColorSpan(colorOperator), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i += 3;
            } else if (c == '|' || c == '&' || c == '?' || c == '=' || c == '<' || c == '>' || c == '[' || c == ']' || c == '(' || c == ')') {
                int start = sb.length();
                sb.append(c);
                sb.setSpan(new ForegroundColorSpan(colorOperator), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i++;
            } else if (c == '\'' || c == '"' || c == '`') {
                char quote = c;
                int start = sb.length();
                sb.append(c);
                i++;
                while (i < len) {
                    char qc = typeStr.charAt(i);
                    sb.append(qc);
                    i++;
                    if (qc == '\\' && i < len) {
                        sb.append(typeStr.charAt(i));
                        i++;
                    } else if (qc == quote) {
                        break;
                    }
                }
                sb.setSpan(new ForegroundColorSpan(colorString), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (Character.isDigit(c)) {
                int start = sb.length();
                while (i < len && (Character.isDigit(typeStr.charAt(i)) || typeStr.charAt(i) == '.')) {
                    sb.append(typeStr.charAt(i));
                    i++;
                }
                sb.setSpan(new ForegroundColorSpan(colorNumber), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (Character.isJavaIdentifierStart(c)) {
                int start = sb.length();
                int wordStart = i;
                while (i < len && Character.isJavaIdentifierPart(typeStr.charAt(i))) {
                    i++;
                }
                String word = typeStr.substring(wordStart, i);
                sb.append(word);
                if (isPrimitiveType(word)) {
                    sb.setSpan(new ForegroundColorSpan(colorKeyword), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    sb.setSpan(new ForegroundColorSpan(colorType), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                sb.append(c);
                i++;
            }
        }
    }

    private static boolean isPrimitiveType(String word) {
        if (word == null) return false;
        switch (word) {
            case "number":
            case "string":
            case "boolean":
            case "any":
            case "void":
            case "null":
            case "undefined":
            case "symbol":
            case "bigint":
            case "never":
            case "unknown":
            case "true":
            case "false":
                return true;
            default:
                return false;
        }
    }

    /**
     * Highlights declaration code snippets (functions, methods, variables, constants,
     * classes, interfaces, types, etc.) with rich syntax colors.
     */
    public SpannableStringBuilder highlightDeclarationCode(String text) {
        if (text == null || text.isEmpty()) {
            return new SpannableStringBuilder();
        }

        SpannableStringBuilder sb = new SpannableStringBuilder();
        int len = text.length();
        int i = 0;
        int parenDepth = 0;
        String lastKeyword = null;
        boolean afterKindTag = false;

        while (i < len) {
            char c = text.charAt(i);

            // 1. Whitespace
            if (Character.isWhitespace(c)) {
                sb.append(c);
                i++;
                continue;
            }

            // 2. Kind tags in parentheses at start or after whitespace: (method), (property), (variable), etc.
            if (c == '(' && (sb.length() == 0 || Character.isWhitespace(sb.charAt(sb.length() - 1)))) {
                int closeParen = text.indexOf(')', i);
                if (closeParen > i) {
                    String inside = text.substring(i + 1, closeParen).trim();
                    if (!inside.isEmpty() && inside.indexOf(':') < 0 && inside.indexOf(';') < 0
                            && inside.indexOf(',') < 0 && !inside.contains("=>")) {
                        int pStart = sb.length();
                        sb.append('(');
                        sb.setSpan(new ForegroundColorSpan(colorOperator), pStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                        int tStart = sb.length();
                        sb.append(inside);
                        sb.setSpan(new ForegroundColorSpan(colorKeyword), tStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                        int pEnd = sb.length();
                        sb.append(')');
                        sb.setSpan(new ForegroundColorSpan(colorOperator), pEnd, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                        afterKindTag = true;
                        i = closeParen + 1;
                        continue;
                    }
                }
            }

            // 3. Comments
            if (c == '/' && i + 1 < len) {
                char nextC = text.charAt(i + 1);
                if (nextC == '/') {
                    int endLine = text.indexOf('\n', i);
                    if (endLine < 0) endLine = len;
                    int cStart = sb.length();
                    sb.append(text, i, endLine);
                    sb.setSpan(new ForegroundColorSpan(colorComment), cStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = endLine;
                    continue;
                } else if (nextC == '*') {
                    int endBlock = text.indexOf("*/", i + 2);
                    if (endBlock < 0) endBlock = len;
                    else endBlock += 2;
                    int cStart = sb.length();
                    sb.append(text, i, endBlock);
                    sb.setSpan(new ForegroundColorSpan(colorComment), cStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = endBlock;
                    continue;
                }
            }

            // 4. String Literals
            if (c == '\'' || c == '"' || c == '`') {
                char quote = c;
                int sStart = sb.length();
                sb.append(c);
                i++;
                while (i < len) {
                    char sc = text.charAt(i);
                    sb.append(sc);
                    i++;
                    if (sc == '\\' && i < len) {
                        sb.append(text.charAt(i));
                        i++;
                    } else if (sc == quote) {
                        break;
                    }
                }
                sb.setSpan(new ForegroundColorSpan(colorString), sStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }

            // 5. Numeric Literals
            if (Character.isDigit(c)) {
                int nStart = sb.length();
                while (i < len && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                    sb.append(text.charAt(i));
                    i++;
                }
                sb.setSpan(new ForegroundColorSpan(colorNumber), nStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }

            // 6. Multi-character Operators
            if (c == '=' && i + 1 < len && text.charAt(i + 1) == '>') {
                int oStart = sb.length();
                sb.append("=>");
                sb.setSpan(new ForegroundColorSpan(colorOperator), oStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i += 2;
                continue;
            }
            if (c == '.' && i + 2 < len && text.charAt(i + 1) == '.' && text.charAt(i + 2) == '.') {
                int oStart = sb.length();
                sb.append("...");
                sb.setSpan(new ForegroundColorSpan(colorOperator), oStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i += 3;
                continue;
            }
            if ((c == '=' || c == '!') && i + 1 < len && text.charAt(i + 1) == '=') {
                int oStart = sb.length();
                if (i + 2 < len && text.charAt(i + 2) == '=') {
                    sb.append(text, i, i + 3);
                    i += 3;
                } else {
                    sb.append(text, i, i + 2);
                    i += 2;
                }
                sb.setSpan(new ForegroundColorSpan(colorOperator), oStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }

            // 7. Single-character Operators & Punctuation
            if (c == '(') {
                int oStart = sb.length();
                sb.append('(');
                sb.setSpan(new ForegroundColorSpan(colorOperator), oStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                parenDepth++;
                i++;
                continue;
            }
            if (c == ')') {
                int oStart = sb.length();
                sb.append(')');
                sb.setSpan(new ForegroundColorSpan(colorOperator), oStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (parenDepth > 0) parenDepth--;
                i++;
                continue;
            }
            if (c == ':' || c == ',' || c == ';') {
                int pStart = sb.length();
                sb.append(c);
                sb.setSpan(new ForegroundColorSpan(colorPunctuation), pStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i++;
                continue;
            }
            if (c == '|' || c == '&' || c == '?' || c == '=' || c == '<' || c == '>'
                    || c == '[' || c == ']' || c == '{' || c == '}' || c == '.'
                    || c == '+' || c == '-' || c == '*' || c == '/' || c == '!'
                    || c == '~' || c == '^' || c == '%') {
                int oStart = sb.length();
                sb.append(c);
                sb.setSpan(new ForegroundColorSpan(colorOperator), oStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i++;
                continue;
            }

            // 8. Identifiers & Keywords
            if (Character.isJavaIdentifierStart(c)) {
                int wordStart = i;
                while (i < len && Character.isJavaIdentifierPart(text.charAt(i))) {
                    i++;
                }
                String word = text.substring(wordStart, i);
                int wStart = sb.length();
                sb.append(word);

                if (isDeclarationKeyword(word)) {
                    sb.setSpan(new ForegroundColorSpan(colorKeyword), wStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    lastKeyword = word;
                } else if (isPrimitiveType(word)) {
                    sb.setSpan(new ForegroundColorSpan(colorKeyword), wStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    boolean isFunc = isFollowedByParen(text, i);
                    boolean isReceiver = (i < len && text.charAt(i) == '.');

                    if (isFunc && !"class".equals(lastKeyword) && !"interface".equals(lastKeyword) && !"type".equals(lastKeyword)) {
                        sb.setSpan(new ForegroundColorSpan(colorFunction), wStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        sb.setSpan(new StyleSpan(Typeface.BOLD), wStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else if ("class".equals(lastKeyword) || "interface".equals(lastKeyword) || "type".equals(lastKeyword) || "enum".equals(lastKeyword)) {
                        sb.setSpan(new ForegroundColorSpan(colorType), wStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        sb.setSpan(new StyleSpan(Typeface.BOLD), wStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        lastKeyword = null;
                    } else if ("extends".equals(lastKeyword) || "implements".equals(lastKeyword) || "new".equals(lastKeyword)) {
                        sb.setSpan(new ForegroundColorSpan(colorType), wStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        lastKeyword = null;
                    } else if ("const".equals(lastKeyword) || "let".equals(lastKeyword) || "var".equals(lastKeyword)) {
                        sb.setSpan(new ForegroundColorSpan(colorParam), wStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        sb.setSpan(new StyleSpan(Typeface.BOLD), wStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        lastKeyword = null;
                    } else if (isReceiver) {
                        sb.setSpan(new ForegroundColorSpan(colorType), wStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        afterKindTag = false;
                    } else if (afterKindTag) {
                        afterKindTag = false;
                        sb.setSpan(new ForegroundColorSpan(colorParam), wStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        sb.setSpan(new StyleSpan(Typeface.BOLD), wStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else if (parenDepth > 0 && isParameterName(text, i)) {
                        sb.setSpan(new ForegroundColorSpan(colorParam), wStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else if (Character.isUpperCase(word.charAt(0))) {
                        sb.setSpan(new ForegroundColorSpan(colorType), wStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else {
                        sb.setSpan(new ForegroundColorSpan(colorParam), wStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
                continue;
            }

            // Fallback for any other characters
            sb.append(c);
            i++;
        }

        return sb;
    }

    private static boolean isDeclarationKeyword(String word) {
        if (word == null) return false;
        switch (word) {
            case "function":
            case "class":
            case "interface":
            case "type":
            case "enum":
            case "extends":
            case "implements":
            case "const":
            case "let":
            case "var":
            case "readonly":
            case "static":
            case "async":
            case "await":
            case "new":
            case "import":
            case "export":
            case "default":
            case "as":
            case "from":
            case "get":
            case "set":
            case "public":
            case "private":
            case "protected":
            case "namespace":
            case "declare":
            case "return":
            case "throw":
            case "typeof":
            case "instanceof":
            case "keyof":
            case "in":
            case "of":
                return true;
            default:
                return false;
        }
    }

    private static boolean isFollowedByParen(String text, int offset) {
        if (text == null) return false;
        int len = text.length();
        int k = offset;
        while (k < len && Character.isWhitespace(text.charAt(k))) {
            k++;
        }
        if (k >= len) return false;
        char c = text.charAt(k);
        if (c == '(') return true;
        if (c == '<') {
            int depth = 1;
            k++;
            while (k < len && depth > 0) {
                char ch = text.charAt(k);
                if (ch == '<') depth++;
                else if (ch == '>') depth--;
                else if (ch == '(' || ch == ';' || ch == '\n') break;
                k++;
            }
            if (depth == 0) {
                while (k < len && Character.isWhitespace(text.charAt(k))) {
                    k++;
                }
                return k < len && text.charAt(k) == '(';
            }
        }
        return false;
    }

    private static boolean isParameterName(String text, int offset) {
        if (text == null) return false;
        int len = text.length();
        int k = offset;
        while (k < len && Character.isWhitespace(text.charAt(k))) {
            k++;
        }
        if (k >= len) return false;
        char c = text.charAt(k);
        return c == ':' || c == '?' || c == '=' || c == ',' || c == ')';
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

        if (headerText == null) {
            int codeBlockStart = raw.indexOf("```");
            if (codeBlockStart >= 0) {
                int firstNewline = raw.indexOf('\n', codeBlockStart);
                int closingBackticks = raw.indexOf("```", codeBlockStart + 3);
                if (firstNewline > codeBlockStart && closingBackticks > firstNewline) {
                    headerText = raw.substring(firstNewline + 1, closingBackticks).trim();
                    docMarkdown = (raw.substring(0, codeBlockStart).trim() + "\n\n" + raw.substring(closingBackticks + 3).trim()).trim();
                }
            }
        }

        if (headerText != null && !headerText.isEmpty()) {
            tvHeader.setText(highlightDeclarationCode(headerText));
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

    public CharSequence getHeaderText() {
        return tvHeader.getText();
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

package com.cocode.vcode.ide.views;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Horizontal navigation path displaying folder nesting and AST symbol hierarchy for the active file.
 * Automatically scrolls to the rightmost leaf and offers one-tap navigation to symbols.
 */
public class BreadcrumbView extends HorizontalScrollView {

    public static class BreadcrumbSymbol {
        private final String name;
        private final int kind;
        private final int startOffset;
        private final int endOffset;

        public BreadcrumbSymbol(String name, int kind, int startOffset, int endOffset) {
            this.name = name != null ? name : "";
            this.kind = kind;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        public String getName() {
            return name;
        }

        public int getKind() {
            return kind;
        }

        public int getStartOffset() {
            return startOffset;
        }

        public int getEndOffset() {
            return endOffset;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BreadcrumbSymbol)) return false;
            BreadcrumbSymbol that = (BreadcrumbSymbol) o;
            return kind == that.kind && startOffset == that.startOffset && endOffset == that.endOffset && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, kind, startOffset, endOffset);
        }
    }

    public interface OnSymbolClickListener {
        void onSymbolClick(BreadcrumbSymbol symbol);
    }

    private LinearLayout container;
    private String cachedProjectName;
    private String cachedRelativePath;
    private int cachedBasePathViewCount;
    private List<BreadcrumbSymbol> cachedSymbols = Collections.emptyList();
    private OnSymbolClickListener symbolClickListener;

    public BreadcrumbView(Context context) {
        super(context);
        init();
    }

    public BreadcrumbView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BreadcrumbView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);

        if (getBackground() == null) {
            setBackgroundColor(ContextCompat.getColor(getContext(), R.color.vcode_bg_surface));
        }

        container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);

        int padPx = UiUtils.dpToPx(getContext(), 16);
        container.setPaddingRelative(padPx, 0, padPx, 0);
        container.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        addView(container);
    }

    /**
     * Convenience delegation for callers that only specify file path without symbols.
     */
    public void setPath(String projectName, String relativePath) {
        setPathAndSymbols(projectName, relativePath, Collections.emptyList(), null);
    }

    /**
     * Updates the base project and relative path while preserving currently displayed symbols
     * until the background resolver reports updated symbols.
     */
    public void updateBasePath(String projectName, String relativePath) {
        if (Objects.equals(cachedProjectName, projectName) && Objects.equals(cachedRelativePath, relativePath)) {
            return;
        }
        setPathAndSymbols(projectName, relativePath, Collections.emptyList(), null);
    }

    /**
     * Updates the breadcrumb trail with the project name, relative path, and active AST symbols.
     * Skips view reconstruction if the path and symbol chain are identical to the cached state.
     */
    public void setPathAndSymbols(String projectName, String relativePath, List<BreadcrumbSymbol> symbols, OnSymbolClickListener listener) {
        List<BreadcrumbSymbol> safeSymbols = (symbols != null && !symbols.isEmpty()) ? symbols : Collections.emptyList();

        if (Objects.equals(cachedProjectName, projectName)
                && Objects.equals(cachedRelativePath, relativePath)
                && Objects.equals(cachedSymbols, safeSymbols)) {
            this.symbolClickListener = listener;
            return;
        }

        this.symbolClickListener = listener;

        boolean samePath = Objects.equals(cachedProjectName, projectName)
                && Objects.equals(cachedRelativePath, relativePath)
                && cachedBasePathViewCount > 0
                && container.getChildCount() >= cachedBasePathViewCount;

        if (samePath) {
            int extraViews = container.getChildCount() - cachedBasePathViewCount;
            if (extraViews > 0) {
                container.removeViews(cachedBasePathViewCount, extraViews);
            }

            android.view.View lastPathView = container.getChildAt(cachedBasePathViewCount - 1);
            if (lastPathView instanceof TextView) {
                ((TextView) lastPathView).setTextColor(ContextCompat.getColor(getContext(),
                        safeSymbols.isEmpty() ? R.color.vcode_text_primary : R.color.vcode_text_secondary));
            }

            for (int i = 0; i < safeSymbols.size(); i++) {
                BreadcrumbSymbol symbol = safeSymbols.get(i);
                addDivider();
                boolean isLastSymbol = (i == safeSymbols.size() - 1);
                addSymbolSegment(symbol, isLastSymbol);
            }

            this.cachedSymbols = new ArrayList<>(safeSymbols);
            post(() -> fullScroll(FOCUS_RIGHT));
            return;
        }

        this.cachedProjectName = projectName;
        this.cachedRelativePath = relativePath;
        this.cachedSymbols = new ArrayList<>(safeSymbols);

        container.removeAllViews();

        boolean hasSymbols = !safeSymbols.isEmpty();
        boolean hasPath = relativePath != null && !relativePath.isEmpty();

        addSegment(projectName != null ? projectName : "Project", !hasPath && !hasSymbols);

        if (hasPath) {
            String normalized = relativePath.replace('\\', '/');
            String[] parts = normalized.split("/");
            List<String> validParts = new ArrayList<>();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    validParts.add(part);
                }
            }

            for (int i = 0; i < validParts.size(); i++) {
                addDivider();
                boolean isLastPart = (i == validParts.size() - 1);
                addSegment(validParts.get(i), isLastPart && !hasSymbols);
            }
        }

        this.cachedBasePathViewCount = container.getChildCount();

        if (hasSymbols) {
            for (int i = 0; i < safeSymbols.size(); i++) {
                BreadcrumbSymbol symbol = safeSymbols.get(i);
                addDivider();
                boolean isLastSymbol = (i == safeSymbols.size() - 1);
                addSymbolSegment(symbol, isLastSymbol);
            }
        }

        post(() -> fullScroll(FOCUS_RIGHT));
    }

    private void addSegment(String text, boolean isActive) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setMaxLines(1);
        tv.setTextSize(12);
        tv.setTypeface(FontManager.getInstance().getUiFont(getContext()));
        tv.setTextColor(ContextCompat.getColor(getContext(),
                isActive ? R.color.vcode_text_primary : R.color.vcode_text_secondary));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(params);
        container.addView(tv);
    }

    private void addSymbolSegment(BreadcrumbSymbol symbol, boolean isActive) {
        TextView tv = new TextView(getContext());
        tv.setText(symbol.getName());
        tv.setMaxLines(1);
        tv.setTextSize(12);
        tv.setTypeface(FontManager.getInstance().getCodeFont(getContext()),
                isActive ? Typeface.BOLD : Typeface.NORMAL);
        tv.setTextColor(ContextCompat.getColor(getContext(), getSymbolColor(symbol, isActive)));

        int padH = UiUtils.dpToPx(getContext(), 4);
        int padV = UiUtils.dpToPx(getContext(), 2);
        tv.setPadding(padH, padV, padH, padV);

        TypedValue outValue = new TypedValue();
        if (getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
            tv.setBackgroundResource(outValue.resourceId);
        }

        tv.setOnClickListener(v -> {
            if (symbolClickListener != null) {
                symbolClickListener.onSymbolClick(symbol);
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(params);
        container.addView(tv);
    }

    private int getSymbolColor(BreadcrumbSymbol symbol, boolean isActive) {
        if (!isActive) {
            return R.color.vcode_text_secondary;
        }
        String name = symbol.getName();
        if (name == null || name.isEmpty()) {
            return R.color.vcode_text_primary;
        }

        if (name.startsWith("<") && name.endsWith(">")) {
            return R.color.vcode_lang_html;
        }
        if (name.startsWith("#") || name.startsWith("```")) {
            return R.color.vcode_lang_md;
        }
        if (name.startsWith("class ") || name.startsWith("interface ") || name.startsWith("enum ") || name.startsWith("type ")) {
            return R.color.vcode_accent_secondary;
        }
        if (name.endsWith("()") || "constructor".equals(name) || name.startsWith("get ") || name.startsWith("set ")) {
            return R.color.vcode_accent_primary;
        }
        if (name.startsWith("const ") || name.startsWith("let ") || name.startsWith("var ")) {
            return R.color.vcode_lang_ts;
        }
        if (name.startsWith("for") || name.startsWith("while") || name.startsWith("do") || name.startsWith("if")
                || name.startsWith("switch") || name.startsWith("case") || "default".equals(name)
                || name.startsWith("try") || name.startsWith("catch") || "finally".equals(name)) {
            return R.color.vcode_accent_secondary;
        }
        if (name.startsWith(".") || name.startsWith("@")) {
            return R.color.vcode_lang_css;
        }
        return R.color.vcode_text_primary;
    }

    private void addDivider() {
        ImageView iv = new ImageView(getContext());
        iv.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_chevron_right));
        iv.setColorFilter(ContextCompat.getColor(getContext(), R.color.vcode_text_hint));

        int size = UiUtils.dpToPx(getContext(), 14);
        int margin = UiUtils.dpToPx(getContext(), 4);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMarginStart(margin);
        params.setMarginEnd(margin);
        params.gravity = Gravity.CENTER_VERTICAL;
        iv.setLayoutParams(params);

        container.addView(iv);
    }
}
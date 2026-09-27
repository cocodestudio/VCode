package com.cocode.vcode.ide.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.core.quickfix.QuickFix;
import com.cocode.vcode.ide.core.quickfix.QuickFixRegistry;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Mobile virtual keyboard accessory bar providing rapid 1-tap insertion of programming symbols
 * and contextual Quick Fix (💡) intentions.
 */
public class QuickSymbolBar extends LinearLayout {

    private static final String[] DEFAULT_SYMBOLS = {
            "Tab", "{", "}", "(", ")", "[", "]", ";", ":", "\"", "'", "=", ">", "<", "!", "/", "->", "=>"
    };

    private ImageView btnQuickFix;
    private View dividerQuickFix;
    private LinearLayout layoutKeysContainer;
    private WeakReference<CodeEditText> activeEditorRef;
    private final Runnable cursorChangeListener = this::checkQuickFixAvailable;

    public QuickSymbolBar(@NonNull Context context) {
        this(context, null);
    }

    public QuickSymbolBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QuickSymbolBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        LayoutInflater.from(context).inflate(R.layout.view_quick_symbol_bar, this, true);

        btnQuickFix = findViewById(R.id.btn_quick_fix);
        dividerQuickFix = findViewById(R.id.divider_quick_fix);
        layoutKeysContainer = findViewById(R.id.layout_keys_container);

        btnQuickFix.setOnClickListener(v -> onQuickFixClicked());

        populateSymbolKeys(context);
    }

    private void populateSymbolKeys(Context context) {
        layoutKeysContainer.removeAllViews();
        FontManager fm = FontManager.getInstance();

        int heightPx = UiUtils.dpToPx(context, 28);
        int marginPx = UiUtils.dpToPx(context, 2);
        int textColor = ContextCompat.getColor(context, R.color.vcode_text_primary);

        for (String sym : DEFAULT_SYMBOLS) {
            TextView keyView = new TextView(context);
            keyView.setText(sym);
            keyView.setGravity(Gravity.CENTER);
            keyView.setTextColor(textColor);
            boolean isTextLabel = sym.equals("Tab");
            keyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, isTextLabel ? 11 : 13);
            keyView.setTypeface(isTextLabel ? fm.getUiMedium(context) : fm.getCodeFont(context));
            keyView.setBackgroundResource(R.drawable.vcode_bg_symbol_key_interactive);
            keyView.setClickable(true);
            keyView.setFocusable(true);

            int minWidth = isTextLabel ? UiUtils.dpToPx(context, 42) : UiUtils.dpToPx(context, 32);
            keyView.setMinWidth(minWidth);
            keyView.setPadding(UiUtils.dpToPx(context, 4), 0, UiUtils.dpToPx(context, 4), 0);

            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, heightPx);
            lp.setMargins(marginPx, 0, marginPx, 0);
            keyView.setLayoutParams(lp);

            keyView.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                onSymbolClicked(sym);
            });
            layoutKeysContainer.addView(keyView);
        }
    }

    /**
     * Binds an active CodeEditText instance to this bar and attaches a cursor listener.
     */
    public void bindEditor(@Nullable CodeEditText editor) {
        CodeEditText oldEditor = activeEditorRef != null ? activeEditorRef.get() : null;
        if (oldEditor != null) {
            // Note: listeners don't leak since editor lifecycle cleans up
        }

        activeEditorRef = editor != null ? new WeakReference<>(editor) : null;
        if (editor != null) {
            editor.addCursorChangeListener(cursorChangeListener);
            checkQuickFixAvailable();
        } else {
            setQuickFixVisible(false);
        }
    }

    /**
     * Re-checks whether quick fixes are available at the editor's cursor position.
     */
    public void checkQuickFixAvailable() {
        CodeEditText editor = activeEditorRef != null ? activeEditorRef.get() : null;
        if (editor == null) {
            setQuickFixVisible(false);
            return;
        }

        List<Problem> problems = editor.getProblemsAtCursor();
        boolean hasFix = false;
        for (Problem p : problems) {
            if (QuickFixRegistry.hasFixes(p)) {
                hasFix = true;
                break;
            }
        }
        setQuickFixVisible(hasFix);
    }

    private void setQuickFixVisible(boolean visible) {
        int visibility = visible ? VISIBLE : GONE;
        if (btnQuickFix != null && btnQuickFix.getVisibility() != visibility) {
            btnQuickFix.setVisibility(visibility);
        }
        if (dividerQuickFix != null && dividerQuickFix.getVisibility() != visibility) {
            dividerQuickFix.setVisibility(visibility);
        }
    }

    private void onQuickFixClicked() {
        CodeEditText editor = activeEditorRef != null ? activeEditorRef.get() : null;
        if (editor == null) return;

        List<Problem> problems = editor.getProblemsAtCursor();
        Problem targetProblem = null;
        List<QuickFix> fixes = null;

        for (Problem p : problems) {
            List<QuickFix> candidateFixes = QuickFixRegistry.getFixesForProblem(p, editor);
            if (!candidateFixes.isEmpty()) {
                targetProblem = p;
                fixes = candidateFixes;
                break;
            }
        }

        if (targetProblem == null || fixes == null || fixes.isEmpty()) {
            Toast.makeText(getContext(), R.string.vcode_no_quick_fixes, Toast.LENGTH_SHORT).show();
            setQuickFixVisible(false);
            return;
        }

        if (fixes.size() == 1) {
            QuickFix fix = fixes.get(0);
            fix.apply(editor, targetProblem);
            Toast.makeText(getContext(), fix.getTitle(), Toast.LENGTH_SHORT).show();
            checkQuickFixAvailable();
        } else {
            PopupMenu popup = new PopupMenu(getContext(), btnQuickFix);
            for (int i = 0; i < fixes.size(); i++) {
                popup.getMenu().add(0, i, i, fixes.get(i).getTitle());
            }
            final Problem finalProb = targetProblem;
            final List<QuickFix> finalFixes = fixes;
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id >= 0 && id < finalFixes.size()) {
                    finalFixes.get(id).apply(editor, finalProb);
                    Toast.makeText(getContext(), finalFixes.get(id).getTitle(), Toast.LENGTH_SHORT).show();
                    checkQuickFixAvailable();
                    return true;
                }
                return false;
            });
            popup.show();
        }
    }

    private void onSymbolClicked(String sym) {
        CodeEditText editor = activeEditorRef != null ? activeEditorRef.get() : null;
        if (editor == null) return;

        switch (sym) {
            case "Tab":
                editor.insertText("    ");
                break;
            case "{":
                editor.wrapSelection("{", "}");
                break;
            case "}":
                editor.insertText("}");
                break;
            case "(":
                editor.wrapSelection("(", ")");
                break;
            case ")":
                editor.insertText(")");
                break;
            case "[":
                editor.wrapSelection("[", "]");
                break;
            case "]":
                editor.insertText("]");
                break;
            case "\"":
                editor.wrapSelection("\"", "\"");
                break;
            case "'":
                editor.wrapSelection("'", "'");
                break;
            case "->":
                editor.insertText("->");
                break;
            case "=>":
                editor.insertText("=>");
                break;
            default:
                editor.insertText(sym);
                break;
        }
    }
}

package com.cocode.vcode.ide.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.core.lsp.LspEditorBridge;
import com.cocode.vcode.ide.core.lsp.model.CodeAction;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

import com.cocode.vcode.ide.data.model.AppSettings;

import com.cocode.vcode.ide.core.command.CommandItem;
import com.cocode.vcode.ide.core.command.CommandRegistry;
import com.cocode.vcode.ide.core.events.Disposable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Keyboard accessory toolbar for inserting programming symbols
 * and triggering quick fixes.
 */
public class QuickSymbolBar extends LinearLayout {

    private List<String> currentSymbols = new ArrayList<>(AppSettings.DEFAULT_QUICK_SYMBOLS);
    private final List<View> customChipViews = new ArrayList<>();

    private ImageView btnQuickFix;
    private View dividerQuickFix;
    private LinearLayout layoutKeysContainer;
    private WeakReference<CodeEditText> activeEditorRef;
    private final Runnable cursorChangeListener = this::checkQuickFixAvailable;
    private final Runnable diagnosticsChangeListener = this::checkQuickFixAvailable;

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

        for (String sym : currentSymbols) {
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

        for (View customChip : customChipViews) {
            if (customChip.getParent() == null) {
                layoutKeysContainer.addView(customChip);
            }
        }
    }

    /**
     * Adds a custom action chip dynamically to the symbol bar (e.g. contributed by an extension).
     *
     * @param label  Text label displayed on the chip
     * @param action Action executed when the chip is tapped
     * @return Disposable token to remove the chip when deactivated
     */
    @NonNull
    public Disposable addCustomActionChip(@NonNull String label, @NonNull Runnable action) {
        Context context = getContext();
        FontManager fm = FontManager.getInstance();

        int heightPx = UiUtils.dpToPx(context, 28);
        int marginPx = UiUtils.dpToPx(context, 2);
        int textColor = ContextCompat.getColor(context, R.color.vcode_text_primary);

        TextView chipView = new TextView(context);
        chipView.setText(label);
        chipView.setGravity(Gravity.CENTER);
        chipView.setTextColor(textColor);
        chipView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        chipView.setTypeface(fm.getUiMedium(context));
        chipView.setBackgroundResource(R.drawable.vcode_bg_symbol_key_interactive);
        chipView.setClickable(true);
        chipView.setFocusable(true);

        int minWidth = UiUtils.dpToPx(context, 42);
        chipView.setMinWidth(minWidth);
        chipView.setPadding(UiUtils.dpToPx(context, 6), 0, UiUtils.dpToPx(context, 6), 0);

        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, heightPx);
        lp.setMargins(marginPx, 0, marginPx, 0);
        chipView.setLayoutParams(lp);

        chipView.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            if (action != null) {
                action.run();
            }
        });

        customChipViews.add(chipView);
        layoutKeysContainer.addView(chipView);

        return () -> {
            customChipViews.remove(chipView);
            layoutKeysContainer.removeView(chipView);
        };
    }

    /**
     * Queries {@link CommandRegistry} for commands categorized as "QuickSymbol" or "SymbolBar"
     * and adds them as dynamic action chips.
     */
    @NonNull
    public Disposable loadExtensionActionChips() {
        List<Disposable> disposables = new ArrayList<>();
        List<CommandItem> commands = CommandRegistry.getInstance().getAllCommands();
        for (CommandItem cmd : commands) {
            if ("QuickSymbol".equalsIgnoreCase(cmd.getCategory()) || "SymbolBar".equalsIgnoreCase(cmd.getCategory())) {
                Disposable d = addCustomActionChip(cmd.getTitle(), () -> {
                    CodeEditText editor = activeEditorRef != null ? activeEditorRef.get() : null;
                    cmd.execute(editor);
                });
                disposables.add(d);
            }
        }
        return () -> {
            for (Disposable d : disposables) {
                d.dispose();
            }
        };
    }

    /**
     * Returns the default list of quick symbol keys.
     */
    public static List<String> getDefaultSymbols() {
        return AppSettings.DEFAULT_QUICK_SYMBOLS;
    }

    /**
     * Updates the active symbols displayed in this bar and re-populates keycaps.
     */
    public void setSymbols(@Nullable List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            this.currentSymbols = new ArrayList<>(AppSettings.DEFAULT_QUICK_SYMBOLS);
        } else {
            this.currentSymbols = new ArrayList<>(symbols);
        }
        populateSymbolKeys(getContext());
    }

    /**
     * Returns the current list of symbols displayed in this bar.
     */
    public List<String> getSymbols() {
        return new ArrayList<>(currentSymbols);
    }

    /**
     * Binds an active CodeEditText instance to this bar and attaches cursor and diagnostic listeners.
     */
    public void bindEditor(@Nullable CodeEditText editor) {
        CodeEditText oldEditor = activeEditorRef != null ? activeEditorRef.get() : null;
        if (oldEditor != null) {
            oldEditor.removeCursorChangeListener(cursorChangeListener);
            oldEditor.removeDiagnosticsChangeListener(diagnosticsChangeListener);
        }

        activeEditorRef = editor != null ? new WeakReference<>(editor) : null;
        if (editor != null) {
            editor.addCursorChangeListener(cursorChangeListener);
            editor.addDiagnosticsChangeListener(diagnosticsChangeListener);
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
            if (LspEditorBridge.hasCodeActionsForProblem(p, editor)) {
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
        if (problems == null || problems.isEmpty()) {
            setQuickFixVisible(false);
            return;
        }

        CustomPopupMenu popup = new CustomPopupMenu(getContext(), btnQuickFix);
        if (editor != null) {
            popup.setTokenView(editor);
        }
        popup.setCardBackgroundColor(ContextCompat.getColor(getContext(), R.color.vcode_bg_surface));
        popup.setTextColor(ContextCompat.getColor(getContext(), R.color.vcode_text_primary));
        int totalFixes = 0;

        for (Problem p : problems) {
            List<CodeAction> candidateFixes = LspEditorBridge.getCodeActionsForProblem(p, editor);
            if (candidateFixes != null) {
                for (CodeAction fix : candidateFixes) {
                    totalFixes++;
                    final Problem finalProb = p;
                    popup.addItem(R.drawable.ic_lightbulb, R.color.vcode_accent_warning, fix.title, () -> {
                        if (fix.edit != null) {
                            editor.applyWorkspaceEdit(fix.edit);
                        }
                        editor.removeProblem(finalProb);
                        checkQuickFixAvailable();
                    });
                }
            }
        }

        if (totalFixes == 0) {
            setQuickFixVisible(false);
            return;
        }

        popup.show();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        bindEditor(null);
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
            case "`":
                editor.wrapSelection("`", "`");
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
